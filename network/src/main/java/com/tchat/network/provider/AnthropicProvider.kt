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
 * Anthropic Claude API Provider
 *
 * 参考 cherry-studio 的设计理念实现：
 * - 动态端点格式化：支持自定义 API 端点
 * - 完整的 SSE 事件处理：处理所有 Anthropic 流事件类型
 * - 增强的错误处理：从错误响应中提取详细信息
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
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)  // 增加读取超时以支持长响应
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private var currentCall: Call? = null

    override suspend fun streamChat(messages: List<ChatMessage>): Flow<StreamChunk> = callbackFlow {
        val jsonBody = buildRequestBody(messages)
        val requestUrl = formatApiEndpoint(baseUrl)
        val request = buildRequest(requestUrl, jsonBody)

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
                                // 优先使用 API 返回的真实 token 数
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
     * 处理 SSE 流式响应
     *
     * 支持完整的 Anthropic 事件类型：
     * - message_start: 消息开始
     * - content_block_start: 内容块开始
     * - content_block_delta: 内容增量（文本或工具调用）
     * - content_block_stop: 内容块结束
     * - message_delta: 消息增量（包含 stop_reason）
     * - message_stop: 消息结束
     * - ping: 心跳
     * - error: 错误
     */
    private fun processStreamResponse(
        response: Response,
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

                        val shouldStop = processSSEEvent(currentEventType, data, onChunk)
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

    private fun buildRequestBody(messages: List<ChatMessage>): String {
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

        // 构建消息数组，确保消息角色交替
        val messagesArray = JSONArray()
        var lastRole: String? = null

        otherMessages.forEach { msg ->
            // Anthropic 要求 user/assistant 角色交替
            // 如果连续相同角色，需要合并或跳过
            if (msg.role.value != lastRole) {
                val msgObj = JSONObject()
                msgObj.put("role", msg.role.value)
                msgObj.put("content", msg.content)
                messagesArray.put(msgObj)
                lastRole = msg.role.value
            }
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
    private fun handleErrorResponse(response: Response): AIProviderException {
        val errorBody = response.body?.string() ?: ""

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

        return when (response.code) {
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
                "API 错误 (${response.code}): ${response.message}\n$detailedMessage"
            )
        }
    }
}
