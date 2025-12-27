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
 * 使用异步 OkHttp 实现
 */
class OpenAIProvider(
    private val apiKey: String,
    private val baseUrl: String = "https://api.openai.com/v1",
    private val model: String = "gpt-3.5-turbo"
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
                                if (data == "[DONE]") {
                                    trySend(StreamChunk.Done)
                                    close()
                                    return@use
                                }

                                try {
                                    val json = JSONObject(data)
                                    val choices = json.getJSONArray("choices")
                                    if (choices.length() > 0) {
                                        val delta = choices.getJSONObject(0).getJSONObject("delta")
                                        if (delta.has("content")) {
                                            val content = delta.getString("content")
                                            trySend(StreamChunk.Content(content))
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
        // 添加调试日志
        println("OpenAI Request URL: $baseUrl/chat/completions")
        println("OpenAI API Key: ${apiKey.take(10)}...")
        println("OpenAI Model: $model")
        println("OpenAI Request Body: $jsonBody")

        return Request.Builder()
            .url("$baseUrl/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()
    }

    private fun handleErrorResponse(response: Response): AIProviderException {
        val errorBody = response.body?.string() ?: ""

        // 添加调试日志
        println("API Error - Code: ${response.code}")
        println("API Error - Message: ${response.message}")
        println("API Error - Body: $errorBody")

        return when (response.code) {
            401 -> AIProviderException.AuthenticationError(
                "认证失败: API Key 无效或已过期，请检查设置中的 API Key\n详情: $errorBody"
            )
            403 -> AIProviderException.AuthenticationError(
                "API 访问被拒绝，请检查 API Key 权限\n详情: $errorBody"
            )
            429 -> AIProviderException.RateLimitError(
                "请求过于频繁，请稍后再试\n详情: $errorBody"
            )
            400 -> AIProviderException.InvalidRequestError(
                "无效的请求参数\n详情: $errorBody"
            )
            500, 502, 503, 504 -> AIProviderException.ServiceUnavailableError(
                "API 服务暂时不可用，请稍后再试\n详情: $errorBody"
            )
            else -> AIProviderException.UnknownError(
                "API 错误 (${response.code}): ${response.message}\n详情: $errorBody"
            )
        }
    }
}
