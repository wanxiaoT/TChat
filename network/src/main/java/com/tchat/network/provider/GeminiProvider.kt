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
 *
 * 支持完整的 SSE 流式响应处理
 */
class GeminiProvider(
    private val apiKey: String,
    private val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta",
    private val model: String = "gemini-pro"
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
     * 处理 SSE 流式响应
     */
    private fun processStreamResponse(
        response: Response,
        onChunk: (StreamChunk) -> Boolean
    ) {
        response.body?.byteStream()?.bufferedReader()?.use { reader ->
            val dataBuffer = StringBuilder()

            reader.lineSequence().forEach { line ->
                when {
                    // SSE 数据行
                    line.startsWith("data: ") -> {
                        dataBuffer.append(line.substring(6))
                    }
                    // 空行表示事件结束
                    line.isEmpty() && dataBuffer.isNotEmpty() -> {
                        val data = dataBuffer.toString()
                        dataBuffer.clear()

                        val shouldStop = processSSEData(data, onChunk)
                        if (shouldStop) {
                            return@use
                        }
                    }
                    // 直接 JSON（非 SSE 格式兼容）
                    line.startsWith("{") -> {
                        val shouldStop = processSSEData(line, onChunk)
                        if (shouldStop) {
                            return@use
                        }
                    }
                }
            }

            // 流结束
            onChunk(StreamChunk.Done())
        }
    }

    /**
     * 处理 SSE 数据
     */
    private fun processSSEData(
        data: String,
        onChunk: (StreamChunk) -> Boolean
    ): Boolean {
        try {
            val json = JSONObject(data)

            // 检查错误
            if (json.has("error")) {
                val error = json.getJSONObject("error")
                val errorMsg = error.optString("message", "Unknown error")
                val errorCode = error.optInt("code", 0)

                val exception = when (errorCode) {
                    400 -> AIProviderException.InvalidRequestError(errorMsg)
                    401, 403 -> AIProviderException.AuthenticationError(errorMsg)
                    429 -> AIProviderException.RateLimitError(errorMsg)
                    500, 503 -> AIProviderException.ServiceUnavailableError(errorMsg)
                    else -> AIProviderException.UnknownError(errorMsg)
                }
                onChunk(StreamChunk.Error(exception))
                return true
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
                                if (text.isNotEmpty()) {
                                    onChunk(StreamChunk.Content(text))
                                }
                            }
                        }
                    }

                    // 检查是否完成
                    val finishReason = candidate.optString("finishReason", "")
                    if (finishReason == "STOP" || finishReason == "MAX_TOKENS" ||
                        finishReason == "SAFETY" || finishReason == "RECITATION") {
                        onChunk(StreamChunk.Done())
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            // 解析错误时继续处理，不中断流
        }
        return false
    }

    private fun buildRequestBody(messages: List<ChatMessage>): String {
        val jsonObject = JSONObject()

        // 分离 system 消息和其他消息
        val systemMessages = messages.filter { it.role == MessageRole.SYSTEM }
        val otherMessages = messages.filter { it.role != MessageRole.SYSTEM }

        // Gemini 支持 systemInstruction 字段
        if (systemMessages.isNotEmpty()) {
            val systemContent = JSONObject()
            val systemParts = JSONArray()
            val systemPart = JSONObject()
            systemPart.put("text", systemMessages.joinToString("\n") { it.content })
            systemParts.put(systemPart)
            systemContent.put("parts", systemParts)
            jsonObject.put("systemInstruction", systemContent)
        }

        val contents = JSONArray()
        otherMessages.forEach { msg ->
            val contentObj = JSONObject()

            // Gemini 使用 "user" 和 "model" 作为角色
            val role = when (msg.role) {
                MessageRole.USER -> "user"
                MessageRole.ASSISTANT -> "model"
                MessageRole.SYSTEM -> "user" // 已在上面处理
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

        // 添加生成配置
        val generationConfig = JSONObject()
        generationConfig.put("maxOutputTokens", 8192)
        jsonObject.put("generationConfig", generationConfig)

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

        // 尝试从 JSON 中提取详细错误
        val detailedMessage = try {
            val json = JSONObject(errorBody)
            val error = json.optJSONObject("error")
            error?.optString("message", errorBody) ?: errorBody
        } catch (e: Exception) {
            errorBody
        }

        return when (response.code) {
            400 -> AIProviderException.InvalidRequestError(
                "无效的请求参数\n$detailedMessage"
            )
            401, 403 -> AIProviderException.AuthenticationError(
                "API Key 无效或已过期\n$detailedMessage"
            )
            429 -> AIProviderException.RateLimitError(
                "请求过于频繁，请稍后再试\n$detailedMessage"
            )
            404 -> AIProviderException.InvalidRequestError(
                "模型不存在或 API 端点错误\n$detailedMessage"
            )
            500, 502, 503 -> AIProviderException.ServiceUnavailableError(
                "API 服务暂时不可用\n$detailedMessage"
            )
            else -> AIProviderException.UnknownError(
                "API 错误 (${response.code}): ${response.message}\n$detailedMessage"
            )
        }
    }
}
