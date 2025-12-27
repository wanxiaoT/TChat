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
 * 使用异步 OkHttp 实现
 */
class AnthropicProvider(
    private val apiKey: String,
    private val baseUrl: String = "https://api.anthropic.com/v1",
    private val model: String = "claude-3-5-sonnet-20241022"
) : AIProvider {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private var currentCall: Call? = null

    override suspend fun streamChat(messages: List<ChatMessage>): Flow<StreamChunk> = callbackFlow {
        val jsonBody = buildRequestBody(messages)
        val request = buildRequest(jsonBody)

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

                    response.body?.byteStream()?.bufferedReader()?.use { reader ->
                        reader.lineSequence().forEach { line ->
                            if (line.startsWith("data: ")) {
                                val data = line.substring(6)

                                try {
                                    val json = JSONObject(data)
                                    val type = json.optString("type")

                                    when (type) {
                                        "content_block_delta" -> {
                                            val delta = json.getJSONObject("delta")
                                            if (delta.getString("type") == "text_delta") {
                                                val text = delta.getString("text")
                                                trySend(StreamChunk.Content(text))
                                            }
                                        }
                                        "message_stop" -> {
                                            trySend(StreamChunk.Done)
                                            close()
                                            return@use
                                        }
                                        "error" -> {
                                            val error = json.getJSONObject("error")
                                            val errorMsg = error.optString("message", "Unknown error")
                                            trySend(StreamChunk.Error(
                                                AIProviderException.UnknownError(errorMsg)
                                            ))
                                            close()
                                            return@use
                                        }
                                    }
                                } catch (e: Exception) {
                                    // 忽略解析错误，继续处理下一行
                                }
                            }
                        }
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

    private fun buildRequestBody(messages: List<ChatMessage>): String {
        val jsonObject = JSONObject()
        jsonObject.put("model", model)
        jsonObject.put("stream", true)
        jsonObject.put("max_tokens", 4096)

        // 分离 system 消息和其他消息
        val systemMessages = messages.filter { it.role == MessageRole.SYSTEM }
        val otherMessages = messages.filter { it.role != MessageRole.SYSTEM }

        // Anthropic API 要求 system 作为单独的字段
        if (systemMessages.isNotEmpty()) {
            jsonObject.put("system", systemMessages.joinToString("\n") { it.content })
        }

        val messagesArray = JSONArray()
        otherMessages.forEach { msg ->
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
            .url("$baseUrl/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()
    }

    private fun handleErrorResponse(response: Response): AIProviderException {
        val errorBody = response.body?.string() ?: ""
        return when (response.code) {
            401 -> AIProviderException.AuthenticationError(
                "API Key 无效或已过期，请检查设置中的 API Key"
            )
            403 -> AIProviderException.AuthenticationError(
                "API 访问被拒绝，请检查 API Key 权限"
            )
            429 -> AIProviderException.RateLimitError(
                "请求过于频繁，请稍后再试"
            )
            400 -> AIProviderException.InvalidRequestError(
                "无效的请求参数: $errorBody"
            )
            500, 502, 503, 529 -> AIProviderException.ServiceUnavailableError(
                "API 服务暂时不可用，请稍后再试"
            )
            else -> AIProviderException.UnknownError(
                "API 错误 (${response.code}): ${response.message}"
            )
        }
    }
}
