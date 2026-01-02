package com.tchat.network.provider

import com.tchat.network.log.NetworkLogger
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
 * Anthropic Claude API Provider
 *
 * 参考 cherry-studio 的设计理念实现：
 * - 动态端点格式化：支持自定义 API 端点
 * - 完整的 SSE 事件处理：处理所有 Anthropic 流事件类型
 * - 增强的错误处理：从错误响应中提取详细信息
 * - 工具调用支持：完整的 Function Calling 实现
 */
class AnthropicProvider(
    private val apiKey: String,
    private val baseUrl: String = "https://api.anthropic.com",
    private val model: String = "claude-sonnet-4-20250514",
    private val maxTokens: Int = 8192,
    private val apiVersion: String = "2023-06-01"
) : AIProvider {

    companion object {
        private const val TAG = "AnthropicProvider"

        // Anthropic SSE 事件类型
        private const val EVENT_MESSAGE_START = "message_start"
        private const val EVENT_CONTENT_BLOCK_START = "content_block_start"
        private const val EVENT_CONTENT_BLOCK_DELTA = "content_block_delta"
        private const val EVENT_CONTENT_BLOCK_STOP = "content_block_stop"
        private const val EVENT_MESSAGE_DELTA = "message_delta"
        private const val EVENT_MESSAGE_STOP = "message_stop"
        private const val EVENT_PING = "ping"
        private const val EVENT_ERROR = "error"

        // Delta 类型
        private const val DELTA_TEXT = "text_delta"
        private const val DELTA_INPUT_JSON = "input_json_delta"

        // Content Block 类型
        private const val CONTENT_TYPE_TEXT = "text"
        private const val CONTENT_TYPE_TOOL_USE = "tool_use"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)  // 增加读取超时以支持长响应
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
        val requestUrl = formatApiEndpoint(baseUrl)
        val request = buildRequest(requestUrl, jsonBody)

        // 记录请求日志
        val requestId = NetworkLogger.logRequest(
            provider = "Anthropic",
            model = model,
            url = requestUrl,
            headers = mapOf(
                "x-api-key" to "${apiKey.take(8)}...",
                "anthropic-version" to apiVersion,
                "Content-Type" to "application/json"
            ),
            body = jsonBody
        )

        // 统计信息
        val startTime = System.currentTimeMillis()
        var firstTokenTime: Long? = null
        var outputTokenCount = 0
        var inputTokens = 0
        var outputTokens = 0

        // 工具调用构建器
        val toolCallsBuilder = mutableMapOf<Int, ToolCallBuilder>()

        // 收集完整响应
        val responseContent = StringBuilder()

        currentCall = client.newCall(request)
        currentCall?.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                val duration = System.currentTimeMillis() - startTime
                NetworkLogger.logError(requestId, "网络连接失败: ${e.message}", duration)

                trySend(StreamChunk.Error(AIProviderException.NetworkError(
                    detail = "网络连接失败: ${e.message}",
                    originalError = e
                )))
                close()
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val duration = System.currentTimeMillis() - startTime

                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string() ?: ""
                        NetworkLogger.logResponse(
                            requestId = requestId,
                            responseCode = response.code,
                            responseBody = errorBody,
                            durationMs = duration
                        )

                        val error = handleErrorResponse(response.code, errorBody)
                        trySend(StreamChunk.Error(error))
                        close()
                        return
                    }

                    processStreamResponseWithTools(response, toolCallsBuilder) { chunk ->
                        // 收集统计信息
                        when (chunk) {
                            is StreamChunk.Content -> {
                                if (firstTokenTime == null) {
                                    firstTokenTime = System.currentTimeMillis()
                                }
                                outputTokenCount += (chunk.text.length * 0.5).toInt()
                                responseContent.append(chunk.text)
                            }
                            is StreamChunk.Done -> {
                                // 如果有工具调用，先发送
                                if (toolCallsBuilder.isNotEmpty()) {
                                    val toolCalls = toolCallsBuilder.values.mapNotNull { it.build() }
                                    if (toolCalls.isNotEmpty()) {
                                        trySend(StreamChunk.ToolCall(toolCalls))
                                    }
                                }

                                // 优先使用 API 返回的真实 token 数
                                val finalInputTokens = if (chunk.inputTokens > 0) chunk.inputTokens else inputTokens
                                val finalOutputTokens = if (chunk.outputTokens > 0) chunk.outputTokens else outputTokenCount

                                val endTime = System.currentTimeMillis()
                                val totalDuration = (endTime - startTime) / 1000.0
                                val tps = if (totalDuration > 0) finalOutputTokens / totalDuration else 0.0
                                val latency = firstTokenTime?.let { it - startTime } ?: 0L

                                // 记录响应日志
                                NetworkLogger.logResponse(
                                    requestId = requestId,
                                    responseCode = 200,
                                    responseBody = responseContent.toString(),
                                    durationMs = endTime - startTime
                                )

                                trySend(StreamChunk.Done(
                                    inputTokens = finalInputTokens,
                                    outputTokens = finalOutputTokens,
                                    tokensPerSecond = tps,
                                    firstTokenLatency = latency
                                ))
                                return@processStreamResponseWithTools true
                            }
                            else -> {}
                        }

                        trySend(chunk)
                        chunk is StreamChunk.Done || chunk is StreamChunk.Error
                    }
                } catch (e: Exception) {
                    val duration = System.currentTimeMillis() - startTime
                    NetworkLogger.logError(requestId, "处理响应时出错: ${e.message}", duration)

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
     * 用于构建流式工具调用
     */
    private class ToolCallBuilder {
        var id: String = ""
        var name: String = ""
        val arguments = StringBuilder()

        fun build(): ToolCallInfo? {
            return if (id.isNotEmpty() && name.isNotEmpty()) {
                ToolCallInfo(id, name, arguments.toString().ifEmpty { "{}" })
            } else {
                null
            }
        }
    }

    /**
     * 格式化 API 端点
     *
     * 支持多种输入格式：
     * - https://api.anthropic.com -> https://api.anthropic.com/v1/messages
     * - https://api.anthropic.com/ -> https://api.anthropic.com/v1/messages
     * - https://api.anthropic.com/v1 -> https://api.anthropic.com/v1/messages
     * - https://api.anthropic.com/v1/ -> https://api.anthropic.com/v1/messages
     * - https://custom.proxy.com/anthropic/v1/ -> https://custom.proxy.com/anthropic/v1/messages
     */
    private fun formatApiEndpoint(baseUrl: String): String {
        var url = baseUrl.trimEnd('/')

        // 如果 URL 不以版本号结尾，添加 /v1
        if (!url.endsWith("/v1") && !url.contains("/v1/")) {
            url = "$url/v1"
        }

        return "$url/messages"
    }

    /**
     * 处理 SSE 流式响应（支持工具调用）
     *
     * 支持完整的 Anthropic 事件类型：
     * - message_start: 消息开始
     * - content_block_start: 内容块开始（可能是文本或工具调用）
     * - content_block_delta: 内容增量（文本或工具调用参数）
     * - content_block_stop: 内容块结束
     * - message_delta: 消息增量（包含 stop_reason）
     * - message_stop: 消息结束
     * - ping: 心跳
     * - error: 错误
     */
    private fun processStreamResponseWithTools(
        response: Response,
        toolCallsBuilder: MutableMap<Int, ToolCallBuilder>,
        onChunk: (StreamChunk) -> Boolean
    ) {
        response.body?.byteStream()?.bufferedReader()?.use { reader ->
            var currentEventType: String? = null
            val dataBuffer = StringBuilder()

            reader.lineSequence().forEach { line ->
                when {
                    // SSE 事件类型行
                    line.startsWith("event: ") -> {
                        currentEventType = line.substring(7).trim()
                    }
                    // SSE 数据行
                    line.startsWith("data: ") -> {
                        dataBuffer.append(line.substring(6))
                    }
                    // 空行表示事件结束，处理累积的数据
                    line.isEmpty() && dataBuffer.isNotEmpty() -> {
                        val data = dataBuffer.toString()
                        dataBuffer.clear()

                        val shouldStop = processSSEEventWithTools(currentEventType, data, toolCallsBuilder, onChunk)
                        if (shouldStop) {
                            return@use
                        }
                        currentEventType = null
                    }
                }
            }
        }
    }

    /**
     * 处理单个 SSE 事件（支持工具调用）
     * 返回 true 表示应该停止处理
     */
    private fun processSSEEventWithTools(
        eventType: String?,
        data: String,
        toolCallsBuilder: MutableMap<Int, ToolCallBuilder>,
        onChunk: (StreamChunk) -> Boolean
    ): Boolean {
        try {
            val json = JSONObject(data)
            val type = eventType ?: json.optString("type")

            return when (type) {
                EVENT_CONTENT_BLOCK_START -> {
                    // 内容块开始，检查是否是工具调用
                    val index = json.optInt("index", 0)
                    val contentBlock = json.optJSONObject("content_block")
                    if (contentBlock != null) {
                        val blockType = contentBlock.optString("type", "")
                        if (blockType == CONTENT_TYPE_TOOL_USE) {
                            // 开始一个新的工具调用
                            val builder = ToolCallBuilder()
                            builder.id = contentBlock.optString("id", "")
                            builder.name = contentBlock.optString("name", "")
                            toolCallsBuilder[index] = builder
                        }
                    }
                    false
                }

                EVENT_CONTENT_BLOCK_DELTA -> {
                    val index = json.optInt("index", 0)
                    val delta = json.getJSONObject("delta")
                    val deltaType = delta.optString("type")

                    when (deltaType) {
                        DELTA_TEXT -> {
                            val text = delta.optString("text", "")
                            if (text.isNotEmpty()) {
                                onChunk(StreamChunk.Content(text))
                            } else {
                                false
                            }
                        }
                        DELTA_INPUT_JSON -> {
                            // 工具调用的 JSON 增量
                            val partialJson = delta.optString("partial_json", "")
                            val builder = toolCallsBuilder[index]
                            if (builder != null && partialJson.isNotEmpty()) {
                                builder.arguments.append(partialJson)
                            }
                            false
                        }
                        else -> false
                    }
                }

                EVENT_MESSAGE_DELTA -> {
                    // 解析 usage 信息
                    val usage = json.optJSONObject("usage")
                    if (usage != null) {
                        val outputTokens = usage.optInt("output_tokens", 0)
                        // Anthropic 在 message_delta 中只返回 output_tokens
                        if (outputTokens > 0) {
                            onChunk(StreamChunk.Done(
                                outputTokens = outputTokens
                            ))
                            return true
                        }
                    }
                    false
                }

                EVENT_MESSAGE_STOP -> {
                    onChunk(StreamChunk.Done())
                    true
                }

                EVENT_ERROR -> {
                    val error = json.optJSONObject("error")
                    val errorType = error?.optString("type", "unknown_error") ?: "unknown_error"
                    val errorMsg = error?.optString("message", "未知错误") ?: "未知错误"

                    val exception = mapStreamErrorToException(errorType, errorMsg)
                    onChunk(StreamChunk.Error(exception))
                    true
                }

                EVENT_MESSAGE_START -> {
                    // 解析 message_start 中的 usage 获取 input_tokens
                    val message = json.optJSONObject("message")
                    val usage = message?.optJSONObject("usage")
                    if (usage != null) {
                        val inputTokens = usage.optInt("input_tokens", 0)
                        // 暂时忽略，input_tokens 会在 Done 中传递
                    }
                    false
                }

                EVENT_CONTENT_BLOCK_STOP, EVENT_PING -> {
                    // 这些事件不需要特殊处理，继续
                    false
                }

                else -> false
            }
        } catch (e: Exception) {
            // 解析错误时继续处理，不中断流
            return false
        }
    }

    /**
     * 处理单个 SSE 事件
     * 返回 true 表示应该停止处理
     */
    private fun processSSEEvent(
        eventType: String?,
        data: String,
        onChunk: (StreamChunk) -> Boolean
    ): Boolean {
        try {
            val json = JSONObject(data)
            val type = eventType ?: json.optString("type")

            return when (type) {
                EVENT_CONTENT_BLOCK_DELTA -> {
                    val delta = json.getJSONObject("delta")
                    val deltaType = delta.optString("type")

                    when (deltaType) {
                        DELTA_TEXT -> {
                            val text = delta.optString("text", "")
                            if (text.isNotEmpty()) {
                                onChunk(StreamChunk.Content(text))
                            } else {
                                false
                            }
                        }
                        DELTA_INPUT_JSON -> {
                            // 工具调用的 JSON 增量，暂时忽略
                            false
                        }
                        else -> false
                    }
                }

                EVENT_MESSAGE_DELTA -> {
                    // 解析 usage 信息
                    val usage = json.optJSONObject("usage")
                    if (usage != null) {
                        val outputTokens = usage.optInt("output_tokens", 0)
                        // Anthropic 在 message_delta 中只返回 output_tokens
                        // input_tokens 需要从 message_start 或最终的响应中获取
                        // 这里先传递 outputTokens,inputTokens 会在外层补充
                        if (outputTokens > 0) {
                            onChunk(StreamChunk.Done(
                                outputTokens = outputTokens
                            ))
                            return true
                        }
                    }
                    false
                }

                EVENT_MESSAGE_STOP -> {
                    // 注意：这里的 Done 会被外层的统计逻辑覆盖，这里只是标记流结束
                    onChunk(StreamChunk.Done())
                    true
                }

                EVENT_ERROR -> {
                    val error = json.optJSONObject("error")
                    val errorType = error?.optString("type", "unknown_error") ?: "unknown_error"
                    val errorMsg = error?.optString("message", "未知错误") ?: "未知错误"

                    val exception = mapStreamErrorToException(errorType, errorMsg)
                    onChunk(StreamChunk.Error(exception))
                    true
                }

                EVENT_MESSAGE_START -> {
                    // 解析 message_start 中的 usage 获取 input_tokens
                    val message = json.optJSONObject("message")
                    val usage = message?.optJSONObject("usage")
                    if (usage != null) {
                        val inputTokens = usage.optInt("input_tokens", 0)
                        // 将 input_tokens 临时存储,但不发送 Done,等待流结束
                        // 这里我们可以通过其他方式传递,或者暂时忽略
                    }
                    false
                }

                EVENT_CONTENT_BLOCK_START, EVENT_CONTENT_BLOCK_STOP, EVENT_PING -> {
                    // 这些事件不需要特殊处理，继续
                    false
                }

                else -> false
            }
        } catch (e: Exception) {
            // 解析错误时继续处理，不中断流
            return false
        }
    }

    /**
     * 将流内错误类型映射到异常
     */
    private fun mapStreamErrorToException(errorType: String, message: String): AIProviderException {
        return when (errorType) {
            "authentication_error" -> AIProviderException.AuthenticationError(message)
            "permission_error" -> AIProviderException.AuthenticationError(message)
            "rate_limit_error" -> AIProviderException.RateLimitError(message)
            "invalid_request_error" -> AIProviderException.InvalidRequestError(message)
            "overloaded_error", "api_error" -> AIProviderException.ServiceUnavailableError(message)
            else -> AIProviderException.UnknownError(message)
        }
    }

    private fun buildRequestBody(messages: List<ChatMessage>, tools: List<ToolDefinition>): String {
        val jsonObject = JSONObject()
        jsonObject.put("model", model)
        jsonObject.put("stream", true)
        jsonObject.put("max_tokens", maxTokens)

        // 分离 system 消息和其他消息
        val systemMessages = messages.filter { it.role == MessageRole.SYSTEM }
        val otherMessages = messages.filter { it.role != MessageRole.SYSTEM }

        // Anthropic API 要求 system 作为单独的字段
        if (systemMessages.isNotEmpty()) {
            jsonObject.put("system", systemMessages.joinToString("\n") { it.content })
        }

        // 添加工具定义
        if (tools.isNotEmpty()) {
            val toolsArray = JSONArray()
            tools.forEach { tool ->
                val toolObj = JSONObject()
                toolObj.put("name", tool.name)
                toolObj.put("description", tool.description)
                if (tool.parametersJson != null) {
                    toolObj.put("input_schema", JSONObject(tool.parametersJson))
                } else {
                    // 无参数工具需要空对象
                    toolObj.put("input_schema", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject())
                    })
                }
                toolsArray.put(toolObj)
            }
            jsonObject.put("tools", toolsArray)
        }

        // 构建消息数组
        val messagesArray = JSONArray()

        otherMessages.forEach { msg ->
            val msgObj = JSONObject()

            when (msg.role) {
                MessageRole.TOOL -> {
                    // 工具结果消息
                    msgObj.put("role", "user")
                    val contentArray = JSONArray()
                    val toolResultObj = JSONObject()
                    toolResultObj.put("type", "tool_result")
                    toolResultObj.put("tool_use_id", msg.toolCallId ?: "")
                    toolResultObj.put("content", msg.content)
                    contentArray.put(toolResultObj)
                    msgObj.put("content", contentArray)
                }
                MessageRole.ASSISTANT -> {
                    msgObj.put("role", "assistant")
                    // 检查是否有工具调用
                    if (msg.toolCalls != null && msg.toolCalls.isNotEmpty()) {
                        val contentArray = JSONArray()
                        // 如果有文本内容，先添加
                        if (msg.content.isNotEmpty()) {
                            val textObj = JSONObject()
                            textObj.put("type", "text")
                            textObj.put("text", msg.content)
                            contentArray.put(textObj)
                        }
                        // 添加工具调用
                        msg.toolCalls.forEach { tc ->
                            val toolUseObj = JSONObject()
                            toolUseObj.put("type", "tool_use")
                            toolUseObj.put("id", tc.id)
                            toolUseObj.put("name", tc.name)
                            toolUseObj.put("input", JSONObject(tc.arguments))
                            contentArray.put(toolUseObj)
                        }
                        msgObj.put("content", contentArray)
                    } else {
                        msgObj.put("content", msg.content)
                    }
                }
                else -> {
                    // USER 和其他角色
                    msgObj.put("role", msg.role.value)
                    msgObj.put("content", msg.content)
                }
            }
            messagesArray.put(msgObj)
        }
        jsonObject.put("messages", messagesArray)

        return jsonObject.toString()
    }

    private fun buildRequest(url: String, jsonBody: String): Request {
        return Request.Builder()
            .url(url)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", apiVersion)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")  // 明确请求 SSE
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()
    }

    /**
     * 处理 HTTP 错误响应
     * 从 Anthropic API 错误响应中提取详细信息
     */
    private fun handleErrorResponse(responseCode: Int, errorBody: String): AIProviderException {
        // 尝试从 JSON 响应中提取详细错误信息
        val detailedMessage = try {
            val json = JSONObject(errorBody)
            val error = json.optJSONObject("error")
            val type = error?.optString("type", "") ?: ""
            val message = error?.optString("message", "") ?: ""

            if (message.isNotEmpty()) {
                "$type: $message"
            } else {
                errorBody
            }
        } catch (e: Exception) {
            errorBody
        }

        return when (responseCode) {
            401 -> AIProviderException.AuthenticationError(
                "API Key 无效或已过期\n$detailedMessage"
            )
            403 -> AIProviderException.AuthenticationError(
                "API 访问被拒绝\n$detailedMessage"
            )
            429 -> AIProviderException.RateLimitError(
                "请求过于频繁，请稍后再试\n$detailedMessage"
            )
            400 -> AIProviderException.InvalidRequestError(
                "无效的请求参数\n$detailedMessage"
            )
            404 -> AIProviderException.InvalidRequestError(
                "API 端点不存在，请检查 baseUrl 配置\n$detailedMessage"
            )
            500, 502, 503, 529 -> AIProviderException.ServiceUnavailableError(
                "API 服务暂时不可用\n$detailedMessage"
            )
            else -> AIProviderException.UnknownError(
                "API 错误 ($responseCode)\n$detailedMessage"
            )
        }
    }
}
