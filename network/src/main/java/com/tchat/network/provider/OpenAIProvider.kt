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
 * 支持完整的 SSE 流式响应处理
 * 兼容 OpenAI API 格式的所有提供商
 */
class OpenAIProvider(
    private val apiKey: String,
    private val baseUrl: String = "https://api.openai.com/v1",
    private val model: String = "gpt-3.5-turbo"
) : AIProvider {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private var currentCall: Call? = null

    override suspend fun streamChat(messages: List<ChatMessage>): Flow<StreamChunk> = callbackFlow {
        val jsonBody = buildRequestBody(messages)
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
                                // 粗略估算：每个字符约 0.5 token（适用于中英文混合）
                                outputTokenCount += (chunk.text.length * 0.5).toInt()
                            }
                            is StreamChunk.Done -> {
                                // 优先使用 API 返回的真实 token 数
                                val finalInputTokens = if (chunk.inputTokens > 0) chunk.inputTokens else inputTokens
                                val finalOutputTokens = if (chunk.outputTokens > 0) chunk.outputTokens else outputTokenCount

                                // 计算统计数据
                                val endTime = System.currentTimeMillis()
                                val totalDuration = (endTime - startTime) / 1000.0
                                val tps = if (totalDuration > 0) finalOutputTokens / totalDuration else 0.0
                                val latency = firstTokenTime?.let { it - startTime } ?: 0L

                                // 发送带统计信息的 Done
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
        // 保存 usage 信息，等到流结束再发送
        var savedInputTokens = 0
        var savedOutputTokens = 0

        response.body?.byteStream()?.bufferedReader()?.use { reader ->
            reader.lineSequence().forEach { line ->
                if (line.startsWith("data: ")) {
                    val data = line.substring(6).trim()

                    // 检查流结束标记
                    if (data == "[DONE]") {
                        onChunk(StreamChunk.Done(
                            inputTokens = savedInputTokens,
                            outputTokens = savedOutputTokens
                        ))
                        return@use
                    }

                    try {
                        val json = JSONObject(data)

                        // 检查流内错误
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

                        // 先提取内容（优先处理内容，避免 usage 导致提前退出）
                        val choices = json.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val choice = choices.getJSONObject(0)
                            val delta = choice.optJSONObject("delta")

                            if (delta != null && delta.has("content")) {
                                val content = delta.getString("content")
                                if (content.isNotEmpty()) {
                                    onChunk(StreamChunk.Content(content))
                                }
                            }
                        }

                        // 保存 usage 信息（不立即结束，等待 [DONE] 标记）
                        if (json.has("usage")) {
                            val usage = json.getJSONObject("usage")
                            savedInputTokens = usage.optInt("prompt_tokens", 0)
                            savedOutputTokens = usage.optInt("completion_tokens", 0)
                        }
                    } catch (e: Exception) {
                        // 解析错误时继续处理，不中断流
                    }
                }
            }

            // 如果没有收到 [DONE] 但流结束了，也发送 Done
            onChunk(StreamChunk.Done(
                inputTokens = savedInputTokens,
                outputTokens = savedOutputTokens
            ))
        }
    }

    private fun buildRequestBody(messages: List<ChatMessage>): String {
        val jsonObject = JSONObject()
        jsonObject.put("model", model)
        jsonObject.put("stream", true)

        // 启用流式 usage 统计
        val streamOptions = JSONObject()
        streamOptions.put("include_usage", true)
        jsonObject.put("stream_options", streamOptions)

        val messagesArray = JSONArray()
        messages.forEach { msg ->
            val msgObj = JSONObject()
            msgObj.put("role", msg.role.value)
            msgObj.put("content", msg.content)
            messagesArray.put(msgObj)
        }
        jsonObject.put("messages", messagesArray)

        return jsonObject.toString()
    }

    private fun buildRequest(jsonBody: String): Request {
        return Request.Builder()
            .url("$baseUrl/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()
    }

    private fun handleErrorResponse(response: Response): AIProviderException {
        val errorBody = response.body?.string() ?: ""

        // 尝试从 JSON 中提取详细错误
        val detailedMessage = try {
            val json = JSONObject(errorBody)
            val error = json.optJSONObject("error")
            error?.optString("message", errorBody) ?: errorBody
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
                "模型不存在或 API 端点错误\n$detailedMessage"
            )
            500, 502, 503, 504 -> AIProviderException.ServiceUnavailableError(
                "API 服务暂时不可用\n$detailedMessage"
            )
            else -> AIProviderException.UnknownError(
                "API 错误 (${response.code}): ${response.message}\n$detailedMessage"
            )
        }
    }
}
