package com.tchat.network.provider

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * OpenAI API Provider
 *
 * 支持完整的 SSE 流式响应处理和工具调用
 * 兼容 OpenAI API 格式的所有提供商
 */
class OpenAIProvider(
    private val apiKey: String,
    baseUrl: String = "https://api.openai.com/v1",
    private val model: String = "gpt-3.5-turbo"
) : AIProvider {

    // 规范化 baseUrl：移除末尾斜杠
    private val normalizedBaseUrl = baseUrl.trimEnd('/')

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        // 强制使用 HTTP/1.1，避免某些代理服务器的 HTTP/2 兼容问题
        .protocols(listOf(Protocol.HTTP_1_1))
        .build()

    private var currentCall: Call? = null

    override suspend fun streamChat(messages: List<ChatMessage>): Flow<StreamChunk> {
        return streamChatWithTools(messages, emptyList())
    }

    override suspend fun streamChatWithTools(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>
    ): Flow<StreamChunk> = callbackFlow {
        val jsonBody = buildRequestBody(messages, tools)
        val request = buildRequest(jsonBody)

        // 统计信息
        val startTime = System.currentTimeMillis()
        var firstTokenTime: Long? = null
        var outputTokenCount = 0
        var inputTokens = 0
        var outputTokens = 0

        currentCall = client.newCall(request)
        currentCall?.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                trySend(StreamChunk.Error(AIProviderException.NetworkError(
                    detail = "网络连接失败: ${e.message}",
                    originalError = e
                )))
                close()
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (!response.isSuccessful) {
                        val error = handleErrorResponse(response)
                        trySend(StreamChunk.Error(error))
                        close()
                        return
                    }

                    processStreamResponse(response) { chunk ->
                        // 收集统计信息
                        when (chunk) {
                            is StreamChunk.Content -> {
                                if (firstTokenTime == null) {
                                    firstTokenTime = System.currentTimeMillis()
                                }
                                outputTokenCount += (chunk.text.length * 0.5).toInt()
                            }
                            is StreamChunk.Done -> {
                                val finalInputTokens = if (chunk.inputTokens > 0) chunk.inputTokens else inputTokens
                                val finalOutputTokens = if (chunk.outputTokens > 0) chunk.outputTokens else outputTokenCount

                                val endTime = System.currentTimeMillis()
                                val totalDuration = (endTime - startTime) / 1000.0
                                val tps = if (totalDuration > 0) finalOutputTokens / totalDuration else 0.0
                                val latency = firstTokenTime?.let { it - startTime } ?: 0L

                                trySend(StreamChunk.Done(
                                    inputTokens = finalInputTokens,
                                    outputTokens = finalOutputTokens,
                                    tokensPerSecond = tps,
                                    firstTokenLatency = latency
                                ))
                                return@processStreamResponse true
                            }
                            else -> {}
                        }

                        trySend(chunk)
                        chunk is StreamChunk.Done || chunk is StreamChunk.Error
                    }
                } catch (e: Exception) {
                    trySend(StreamChunk.Error(AIProviderException.UnknownError(
                        detail = "处理响应时出错: ${e.message}",
                        originalError = e
                    )))
                } finally {
                    response.close()
                    close()
                }
            }
        })

        awaitClose {
            currentCall?.cancel()
        }
    }

    override fun cancel() {
        currentCall?.cancel()
    }

    /**
     * 处理 SSE 流式响应
     */
    private fun processStreamResponse(
        response: Response,
        onChunk: (StreamChunk) -> Boolean
    ) {
        var savedInputTokens = 0
        var savedOutputTokens = 0

        // 用于收集流式工具调用
        val toolCallsBuilder = mutableMapOf<Int, ToolCallBuilder>()

        response.body?.byteStream()?.bufferedReader()?.use { reader ->
            reader.lineSequence().forEach { line ->
                if (line.startsWith("data: ")) {
                    val data = line.substring(6).trim()

                    if (data == "[DONE]") {
                        // 如果有收集到的工具调用，先发送
                        if (toolCallsBuilder.isNotEmpty()) {
                            val toolCalls = toolCallsBuilder.values.mapNotNull { it.build() }
                            if (toolCalls.isNotEmpty()) {
                                onChunk(StreamChunk.ToolCall(toolCalls))
                            }
                        }
                        onChunk(StreamChunk.Done(
                            inputTokens = savedInputTokens,
                            outputTokens = savedOutputTokens
                        ))
                        return@use
                    }

                    try {
                        val json = JSONObject(data)

                        if (json.has("error")) {
                            val error = json.getJSONObject("error")
                            val errorMsg = error.optString("message", "Unknown error")
                            val errorType = error.optString("type", "")

                            val exception = when (errorType) {
                                "invalid_request_error" -> AIProviderException.InvalidRequestError(errorMsg)
                                "authentication_error" -> AIProviderException.AuthenticationError(errorMsg)
                                "rate_limit_error" -> AIProviderException.RateLimitError(errorMsg)
                                "server_error" -> AIProviderException.ServiceUnavailableError(errorMsg)
                                else -> AIProviderException.UnknownError(errorMsg)
                            }
                            onChunk(StreamChunk.Error(exception))
                            return@use
                        }

                        val choices = json.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val choice = choices.getJSONObject(0)
                            val delta = choice.optJSONObject("delta")

                            if (delta != null) {
                                // 处理文本内容
                                if (delta.has("content")) {
                                    val content = delta.getString("content")
                                    if (content.isNotEmpty()) {
                                        onChunk(StreamChunk.Content(content))
                                    }
                                }

                                // 处理工具调用（流式）
                                if (delta.has("tool_calls")) {
                                    val toolCalls = delta.getJSONArray("tool_calls")
                                    for (i in 0 until toolCalls.length()) {
                                        val toolCall = toolCalls.getJSONObject(i)
                                        val index = toolCall.optInt("index", i)

                                        val builder = toolCallsBuilder.getOrPut(index) { ToolCallBuilder() }

                                        if (toolCall.has("id")) {
                                            builder.id = toolCall.getString("id")
                                        }
                                        if (toolCall.has("function")) {
                                            val function = toolCall.getJSONObject("function")
                                            if (function.has("name")) {
                                                builder.name = function.getString("name")
                                            }
                                            if (function.has("arguments")) {
                                                builder.arguments.append(function.getString("arguments"))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (json.has("usage")) {
                            val usage = json.getJSONObject("usage")
                            savedInputTokens = usage.optInt("prompt_tokens", 0)
                            savedOutputTokens = usage.optInt("completion_tokens", 0)
                        }
                    } catch (e: Exception) {
                        // 解析错误时继续处理
                    }
                }
            }

            // 如果有工具调用，发送它们
            if (toolCallsBuilder.isNotEmpty()) {
                val toolCalls = toolCallsBuilder.values.mapNotNull { it.build() }
                if (toolCalls.isNotEmpty()) {
                    onChunk(StreamChunk.ToolCall(toolCalls))
                }
            }

            onChunk(StreamChunk.Done(
                inputTokens = savedInputTokens,
                outputTokens = savedOutputTokens
            ))
        }
    }

    private fun buildRequestBody(messages: List<ChatMessage>, tools: List<ToolDefinition>): String {
        val jsonObject = JSONObject()
        jsonObject.put("model", model)
        jsonObject.put("stream", true)

        val streamOptions = JSONObject()
        streamOptions.put("include_usage", true)
        jsonObject.put("stream_options", streamOptions)

        // 构建消息数组
        val messagesArray = JSONArray()
        messages.forEach { msg ->
            val msgObj = JSONObject()
            msgObj.put("role", msg.role.value)

            when (msg.role) {
                MessageRole.TOOL -> {
                    // 工具结果消息
                    msgObj.put("content", msg.content)
                    msgObj.put("tool_call_id", msg.toolCallId)
                }
                MessageRole.ASSISTANT -> {
                    // 助手消息可能包含工具调用
                    if (msg.toolCalls != null && msg.toolCalls.isNotEmpty()) {
                        msgObj.put("content", JSONObject.NULL)
                        val toolCallsArray = JSONArray()
                        msg.toolCalls.forEach { tc ->
                            val tcObj = JSONObject()
                            tcObj.put("id", tc.id)
                            tcObj.put("type", "function")
                            val funcObj = JSONObject()
                            funcObj.put("name", tc.name)
                            funcObj.put("arguments", tc.arguments)
                            tcObj.put("function", funcObj)
                            toolCallsArray.put(tcObj)
                        }
                        msgObj.put("tool_calls", toolCallsArray)
                    } else {
                        msgObj.put("content", msg.content)
                    }
                }
                else -> {
                    msgObj.put("content", msg.content)
                }
            }
            messagesArray.put(msgObj)
        }
        jsonObject.put("messages", messagesArray)

        // 添加工具定义
        if (tools.isNotEmpty()) {
            val toolsArray = JSONArray()
            tools.forEach { tool ->
                val toolObj = JSONObject()
                toolObj.put("type", "function")
                val funcObj = JSONObject()
                funcObj.put("name", tool.name)
                funcObj.put("description", tool.description)
                if (tool.parametersJson != null) {
                    funcObj.put("parameters", JSONObject(tool.parametersJson))
                }
                toolObj.put("function", funcObj)
                toolsArray.put(toolObj)
            }
            jsonObject.put("tools", toolsArray)
        }

        return jsonObject.toString()
    }

    private fun buildRequest(jsonBody: String): Request {
        return Request.Builder()
            .url("$normalizedBaseUrl/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()
    }

    private fun handleErrorResponse(response: Response): AIProviderException {
        val errorBody = response.body?.string() ?: ""

        val detailedMessage = try {
            val json = JSONObject(errorBody)
            val error = json.optJSONObject("error")
            error?.optString("message", errorBody) ?: errorBody
        } catch (e: Exception) {
            errorBody
        }

        return when (response.code) {
            401 -> AIProviderException.AuthenticationError("API Key 无效或已过期\n$detailedMessage")
            403 -> AIProviderException.AuthenticationError("API 访问被拒绝\n$detailedMessage")
            429 -> AIProviderException.RateLimitError("请求过于频繁，请稍后再试\n$detailedMessage")
            400 -> AIProviderException.InvalidRequestError("无效的请求参数\n$detailedMessage")
            404 -> AIProviderException.InvalidRequestError("模型不存在或 API 端点错误\n$detailedMessage")
            500, 502, 503, 504 -> AIProviderException.ServiceUnavailableError("API 服务暂时不可用\n$detailedMessage")
            else -> AIProviderException.UnknownError("API 错误 (${response.code}): ${response.message}\n$detailedMessage")
        }
    }

    /**
     * 用于构建流式工具调用
     */
    private class ToolCallBuilder {
        var id: String = ""
        var name: String = ""
        val arguments = StringBuilder()

        fun build(): ToolCallInfo? {
            return if (id.isNotEmpty() && name.isNotEmpty()) {
                ToolCallInfo(id, name, arguments.toString())
            } else {
                null
            }
        }
    }
}
