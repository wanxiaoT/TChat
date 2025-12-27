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
 * Google Gemini API Provider
 * 使用异步 OkHttp 实现
 */
class GeminiProvider(
    private val apiKey: String,
    private val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta",
    private val model: String = "gemini-pro"
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
                            if (line.isBlank()) return@forEach

                            try {
                                val json = JSONObject(line)

                                // 检查错误
                                if (json.has("error")) {
                                    val error = json.getJSONObject("error")
                                    val errorMsg = error.optString("message", "Unknown error")
                                    trySend(StreamChunk.Error(
                                        AIProviderException.UnknownError(errorMsg)
                                    ))
                                    close()
                                    return@use
                                }

                                // 提取内容
                                if (json.has("candidates")) {
                                    val candidates = json.getJSONArray("candidates")
                                    if (candidates.length() > 0) {
                                        val candidate = candidates.getJSONObject(0)
                                        val content = candidate.optJSONObject("content")

                                        if (content != null && content.has("parts")) {
                                            val parts = content.getJSONArray("parts")
                                            if (parts.length() > 0) {
                                                val part = parts.getJSONObject(0)
                                                if (part.has("text")) {
                                                    val text = part.getString("text")
                                                    trySend(StreamChunk.Content(text))
                                                }
                                            }
                                        }

                                        // 检查是否完成
                                        val finishReason = candidate.optString("finishReason", "")
                                        if (finishReason.isNotEmpty() && finishReason != "STOP") {
                                            trySend(StreamChunk.Done)
                                            close()
                                            return@use
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                // 忽略解析错误，继续处理下一行
                            }
                        }

                        // 流结束
                        trySend(StreamChunk.Done)
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

        val contents = JSONArray()
        messages.forEach { msg ->
            val contentObj = JSONObject()

            // Gemini 使用 "user" 和 "model" 作为角色
            val role = when (msg.role) {
                MessageRole.USER -> "user"
                MessageRole.ASSISTANT -> "model"
                MessageRole.SYSTEM -> "user" // Gemini 不支持 system，转为 user
            }
            contentObj.put("role", role)

            val parts = JSONArray()
            val partObj = JSONObject()
            partObj.put("text", msg.content)
            parts.put(partObj)

            contentObj.put("parts", parts)
            contents.put(contentObj)
        }

        jsonObject.put("contents", contents)

        return jsonObject.toString()
    }

    private fun buildRequest(jsonBody: String): Request {
        val url = "$baseUrl/models/$model:streamGenerateContent?key=$apiKey&alt=sse"

        return Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()
    }

    private fun handleErrorResponse(response: Response): AIProviderException {
        val errorBody = response.body?.string() ?: ""
        return when (response.code) {
            400 -> {
                // 尝试解析错误详情
                try {
                    val json = JSONObject(errorBody)
                    val error = json.optJSONObject("error")
                    val message = error?.optString("message") ?: "无效的请求参数"
                    AIProviderException.InvalidRequestError(message)
                } catch (e: Exception) {
                    AIProviderException.InvalidRequestError("无效的请求参数: $errorBody")
                }
            }
            401, 403 -> AIProviderException.AuthenticationError(
                "API Key 无效或已过期，请检查设置中的 API Key"
            )
            429 -> AIProviderException.RateLimitError(
                "请求过于频繁，请稍后再试"
            )
            500, 502, 503 -> AIProviderException.ServiceUnavailableError(
                "API 服务暂时不可用，请稍后再试"
            )
            else -> AIProviderException.UnknownError(
                "API 错误 (${response.code}): ${response.message}"
            )
        }
    }
}
