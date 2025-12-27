package com.tchat.network.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader

class OpenAIService(
    private val apiKey: String,
    private val baseUrl: String = "https://api.openai.com/v1"
) : AIService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    override suspend fun sendMessage(
        messages: List<ChatMessage>,
        onStream: (String) -> Unit
    ): Flow<String> = flow {
        withContext(Dispatchers.IO) {
            val jsonBody = buildRequestBody(messages)
            val request = buildRequest(jsonBody)

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorMessage = when (response.code) {
                        401 -> "API Key 无效或已过期，请检查设置中的 API Key"
                        403 -> "API 访问被拒绝，请检查 API Key 权限"
                        429 -> "请求过于频繁，请稍后再试"
                        500, 502, 503 -> "API 服务暂时不可用，请稍后再试"
                        else -> "API 错误 (${response.code}): ${response.message}"
                    }
                    throw Exception(errorMessage)
                }

                response.body?.byteStream()?.bufferedReader()?.use { reader ->
                    processStreamResponse(reader, onStream) { chunk ->
                        emit(chunk)
                    }
                }
            }
        }
    }

    private fun buildRequestBody(messages: List<ChatMessage>): String {
        val jsonObject = JSONObject()
        jsonObject.put("model", "gpt-3.5-turbo")
        jsonObject.put("stream", true)

        val messagesArray = JSONArray()
        messages.forEach { msg ->
            val msgObj = JSONObject()
            msgObj.put("role", msg.role)
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
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()
    }

    private fun processStreamResponse(
        reader: BufferedReader,
        onStream: (String) -> Unit,
        emit: suspend (String) -> Unit
    ) {
        reader.lineSequence().forEach { line ->
            if (line.startsWith("data: ")) {
                val data = line.substring(6)
                if (data != "[DONE]") {
                    try {
                        val json = JSONObject(data)
                        val choices = json.getJSONArray("choices")
                        if (choices.length() > 0) {
                            val delta = choices.getJSONObject(0).getJSONObject("delta")
                            if (delta.has("content")) {
                                val content = delta.getString("content")
                                onStream(content)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }
}
