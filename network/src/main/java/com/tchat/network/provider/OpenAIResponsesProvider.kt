package com.tchat.network.provider

import com.tchat.network.log.NetworkLogger
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Collections
import java.util.concurrent.TimeUnit

/**
 * OpenAI Responses API Provider.
 *
 * 用于 /v1/responses 兼容端点，保留与现有 ChatMessage/StreamChunk 的统一接口。
 */
class OpenAIResponsesProvider(
    private val apiKey: String,
    baseUrl: String = "https://api.openai.com/v1",
    private val model: String = "gpt-4.1-mini",
    private val customParams: CustomParams? = null,
    private val extraHeaders: Map<String, String> = emptyMap(),
    responsesPath: String = "/responses",
    private val authHeaderName: String = "Authorization",
    private val authHeaderValue: String? = null
) : AIProvider {

    private val normalizedBaseUrl = baseUrl.trimEnd('/')
    private val normalizedResponsesPath = normalizePath(responsesPath, "/responses")
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .protocols(listOf(Protocol.HTTP_1_1))
        .build()
    private val activeCalls = Collections.synchronizedSet(mutableSetOf<Call>())

    override suspend fun streamChat(messages: List<ChatMessage>): Flow<StreamChunk> = callbackFlow {
        val jsonBody = buildRequestBody(messages)
        val request = buildRequest(jsonBody)
        val requestUrl = "$normalizedBaseUrl$normalizedResponsesPath"
        val requestId = NetworkLogger.logRequest(
            provider = "OpenAI Responses",
            model = model,
            url = requestUrl,
            headers = buildLogHeaders(),
            body = jsonBody
        )

        val startTime = System.currentTimeMillis()
        var firstTokenTime: Long? = null
        var outputTokenCount = 0
        var inputTokens = 0
        var outputTokens = 0
        val responseContent = NetworkLogger.newBodyCapture()

        val call = client.newCall(request)
        activeCalls += call
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                val duration = System.currentTimeMillis() - startTime
                NetworkLogger.logError(requestId, "网络连接失败: ${e.message}", duration)
                trySend(
                    StreamChunk.Error(
                        AIProviderException.NetworkError(
                            detail = "网络连接失败: ${e.message}",
                            originalError = e
                        )
                    )
                )
                close()
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val duration = System.currentTimeMillis() - startTime
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string().orEmpty()
                        NetworkLogger.logResponse(
                            requestId = requestId,
                            responseCode = response.code,
                            responseBody = errorBody,
                            durationMs = duration
                        )
                        trySend(StreamChunk.Error(handleErrorResponse(response.code, errorBody)))
                        close()
                        return
                    }

                    processStreamResponse(response) { chunk ->
                        when (chunk) {
                            is StreamChunk.Content -> {
                                if (firstTokenTime == null) firstTokenTime = System.currentTimeMillis()
                                outputTokenCount += (chunk.text.length * 0.5).toInt()
                                responseContent.append(chunk.text)
                            }
                            is StreamChunk.Done -> {
                                inputTokens = if (chunk.inputTokens > 0) chunk.inputTokens else inputTokens
                                outputTokens = if (chunk.outputTokens > 0) chunk.outputTokens else outputTokens
                                val finalOutput = if (outputTokens > 0) outputTokens else outputTokenCount
                                val endTime = System.currentTimeMillis()
                                val totalDuration = (endTime - startTime) / 1000.0
                                val tps = if (totalDuration > 0) finalOutput / totalDuration else 0.0
                                val latency = firstTokenTime?.let { it - startTime } ?: 0L

                                NetworkLogger.logResponse(
                                    requestId = requestId,
                                    responseCode = 200,
                                    responseBody = responseContent.toString(),
                                    durationMs = endTime - startTime
                                )
                                trySend(
                                    StreamChunk.Done(
                                        inputTokens = inputTokens,
                                        outputTokens = finalOutput,
                                        tokensPerSecond = tps,
                                        firstTokenLatency = latency
                                    )
                                )
                                return@processStreamResponse true
                            }
                            else -> Unit
                        }

                        trySend(chunk)
                        chunk is StreamChunk.Done || chunk is StreamChunk.Error
                    }
                } catch (e: Exception) {
                    val duration = System.currentTimeMillis() - startTime
                    NetworkLogger.logError(requestId, "处理响应时出错: ${e.message}", duration)
                    trySend(
                        StreamChunk.Error(
                            AIProviderException.UnknownError(
                                detail = "处理响应时出错: ${e.message}",
                                originalError = e
                            )
                        )
                    )
                } finally {
                    activeCalls.remove(call)
                    response.close()
                    close()
                }
            }
        })

        awaitClose {
            activeCalls.remove(call)
            call.cancel()
        }
    }

    override suspend fun streamChatWithTools(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>
    ): Flow<StreamChunk> {
        return streamChat(messages)
    }

    override fun cancel() {
        activeCalls.toList().forEach { it.cancel() }
        activeCalls.clear()
    }

    private fun buildRequestBody(messages: List<ChatMessage>): String {
        val jsonObject = JSONObject()
        jsonObject.put("model", model)
        jsonObject.put("stream", true)

        val inputArray = JSONArray()
        messages.forEach { message ->
            when (message.role) {
                MessageRole.SYSTEM -> {
                    val existing = jsonObject.optString("instructions", "")
                    val next = listOf(existing, message.content)
                        .filter { it.isNotBlank() }
                        .joinToString("\n")
                    jsonObject.put("instructions", next)
                }
                MessageRole.TOOL -> {
                    inputArray.put(
                        JSONObject().apply {
                            put("role", "user")
                            put("content", "工具 ${message.name ?: ""} 返回：${message.content}")
                        }
                    )
                }
                MessageRole.USER -> {
                    inputArray.put(
                        JSONObject().apply {
                            put("role", "user")
                            put("content", buildUserContent(message))
                        }
                    )
                }
                MessageRole.ASSISTANT -> {
                    inputArray.put(
                        JSONObject().apply {
                            put("role", "assistant")
                            put("content", message.content)
                        }
                    )
                }
            }
        }
        jsonObject.put("input", inputArray)

        customParams?.let { params ->
            params.temperature?.let { jsonObject.put("temperature", it.toDouble()) }
            params.topP?.let { jsonObject.put("top_p", it.toDouble()) }
            params.maxTokens?.let { jsonObject.put("max_output_tokens", it) }
            if (params.extraParams.isNotEmpty() && params.extraParams != "{}") {
                runCatching {
                    val extraJson = JSONObject(params.extraParams)
                    val keys = extraJson.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        jsonObject.put(key, extraJson.get(key))
                    }
                }
            }
        }

        return jsonObject.toString()
    }

    private fun buildUserContent(message: ChatMessage): Any {
        val parts = message.contentParts
        if (parts.isNullOrEmpty()) return message.content

        val array = JSONArray()
        parts.forEach { part ->
            when (part) {
                is MessageContent.Text -> {
                    array.put(
                        JSONObject().apply {
                            put("type", "input_text")
                            put("text", part.text)
                        }
                    )
                }
                is MessageContent.Image -> {
                    array.put(
                        JSONObject().apply {
                            put("type", "input_image")
                            put("image_url", "data:${part.mimeType};base64,${part.base64Data}")
                        }
                    )
                }
                is MessageContent.Video -> {
                    array.put(
                        JSONObject().apply {
                            put("type", "input_text")
                            put("text", "（视频附件暂不支持 Responses 输入，已省略）")
                        }
                    )
                }
            }
        }
        return array
    }

    private fun buildRequest(jsonBody: String): Request {
        return Request.Builder()
            .url("$normalizedBaseUrl$normalizedResponsesPath")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .applyAuthHeader()
            .applyExtraHeaders()
            .build()
    }

    private fun processStreamResponse(
        response: Response,
        onChunk: (StreamChunk) -> Boolean
    ) {
        response.body?.byteStream()?.bufferedReader()?.use { reader ->
            var currentEvent: String? = null
            val dataBuffer = StringBuilder()

            reader.lineSequence().forEach { line ->
                when {
                    line.startsWith("event: ") -> {
                        currentEvent = line.substring(7).trim()
                    }
                    line.startsWith("data: ") -> {
                        val data = line.substring(6).trim()
                        if (data == "[DONE]") {
                            onChunk(StreamChunk.Done())
                            return@use
                        }
                        dataBuffer.append(data)
                    }
                    line.isEmpty() && dataBuffer.isNotEmpty() -> {
                        val data = dataBuffer.toString()
                        dataBuffer.clear()
                        val shouldStop = processEvent(currentEvent, data, onChunk)
                        if (shouldStop) return@use
                        currentEvent = null
                    }
                }
            }

            if (dataBuffer.isNotEmpty()) {
                processEvent(currentEvent, dataBuffer.toString(), onChunk)
            } else {
                onChunk(StreamChunk.Done())
            }
        }
    }

    private fun processEvent(
        event: String?,
        data: String,
        onChunk: (StreamChunk) -> Boolean
    ): Boolean {
        return try {
            val json = JSONObject(data)
            val type = event ?: json.optString("type", "")
            when (type) {
                "response.output_text.delta",
                "response.refusal.delta" -> {
                    val delta = json.optString("delta", "")
                    if (delta.isNotEmpty()) onChunk(StreamChunk.Content(delta))
                    false
                }
                "response.completed" -> {
                    val response = json.optJSONObject("response")
                    val usage = response?.optJSONObject("usage") ?: json.optJSONObject("usage")
                    onChunk(
                        StreamChunk.Done(
                            inputTokens = usage?.optInt("input_tokens", 0) ?: 0,
                            outputTokens = usage?.optInt("output_tokens", 0) ?: 0
                        )
                    )
                    true
                }
                "response.failed",
                "response.incomplete",
                "error" -> {
                    val error = json.optJSONObject("error")
                        ?: json.optJSONObject("response")?.optJSONObject("error")
                    val message = error?.optString("message").orEmpty()
                        .ifBlank { json.optString("message", "Responses 请求失败") }
                    onChunk(StreamChunk.Error(AIProviderException.UnknownError(message)))
                    true
                }
                else -> {
                    parseChatCompatibleChunk(json, onChunk)
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun parseChatCompatibleChunk(
        json: JSONObject,
        onChunk: (StreamChunk) -> Boolean
    ): Boolean {
        val choices = json.optJSONArray("choices")
        if (choices != null && choices.length() > 0) {
            val delta = choices.optJSONObject(0)?.optJSONObject("delta")
            val content = delta?.optString("content", "").orEmpty()
            if (content.isNotEmpty()) {
                onChunk(StreamChunk.Content(content))
            }
            return false
        }

        val output = json.optJSONArray("output") ?: return false
        for (i in 0 until output.length()) {
            val item = output.optJSONObject(i) ?: continue
            val content = item.optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val contentItem = content.optJSONObject(j) ?: continue
                val text = contentItem.optString("text", "")
                if (text.isNotEmpty()) onChunk(StreamChunk.Content(text))
            }
        }
        return false
    }

    private fun Request.Builder.applyAuthHeader(): Request.Builder {
        val value = effectiveAuthHeaderValue()
        val name = authHeaderName.trim()
        if (!value.isNullOrBlank() && name.isNotBlank()) {
            addHeader(name, value)
        }
        return this
    }

    private fun Request.Builder.applyExtraHeaders(): Request.Builder {
        extraHeaders.forEach { (name, value) ->
            val normalizedName = name.trim()
            val normalizedValue = value.trim()
            if (normalizedName.isBlank() || normalizedValue.isBlank()) return@forEach
            if (normalizedName.equals(authHeaderName, ignoreCase = true) ||
                normalizedName.equals("Authorization", ignoreCase = true) ||
                normalizedName.equals("Content-Type", ignoreCase = true) ||
                normalizedName.equals("Content-Length", ignoreCase = true)
            ) {
                return@forEach
            }
            addHeader(normalizedName, normalizedValue)
        }
        return this
    }

    private fun effectiveAuthHeaderValue(): String? {
        return authHeaderValue ?: apiKey.takeIf { it.isNotBlank() }?.let { "Bearer $it" }
    }

    private fun buildLogHeaders(): Map<String, String> {
        val headers = linkedMapOf("Content-Type" to "application/json")
        val name = authHeaderName.trim()
        val value = effectiveAuthHeaderValue()
        if (name.isNotBlank() && !value.isNullOrBlank()) {
            headers[name] = maskHeaderValue(value)
        }
        extraHeaders.forEach { (key, headerValue) ->
            if (key.isNotBlank() && headerValue.isNotBlank()) {
                headers[key] = maskHeaderValue(headerValue)
            }
        }
        return headers
    }

    private fun handleErrorResponse(responseCode: Int, errorBody: String): AIProviderException {
        val detailedMessage = try {
            val json = JSONObject(errorBody)
            val error = json.optJSONObject("error")
            error?.optString("message", errorBody) ?: errorBody
        } catch (e: Exception) {
            errorBody
        }

        return when (responseCode) {
            401 -> AIProviderException.AuthenticationError("API Key 无效或已过期\n$detailedMessage")
            403 -> AIProviderException.AuthenticationError("API 访问被拒绝\n$detailedMessage")
            429 -> AIProviderException.RateLimitError("请求过于频繁，请稍后再试\n$detailedMessage")
            400 -> AIProviderException.InvalidRequestError("无效的请求参数\n$detailedMessage")
            404 -> AIProviderException.InvalidRequestError("模型不存在或 API 端点错误\n$detailedMessage")
            500, 502, 503, 504 -> AIProviderException.ServiceUnavailableError("API 服务暂时不可用\n$detailedMessage")
            else -> AIProviderException.UnknownError("API 错误 ($responseCode)\n$detailedMessage")
        }
    }

    private fun maskHeaderValue(value: String): String {
        val trimmed = value.trim()
        return when {
            trimmed.isBlank() -> ""
            trimmed.length <= 8 -> "****"
            else -> "${trimmed.take(4)}****${trimmed.takeLast(4)}"
        }
    }

    private fun normalizePath(path: String, fallback: String): String {
        val value = path.trim().ifBlank { fallback }.trim()
        return when {
            value.startsWith("/") -> value
            else -> "/$value"
        }
    }
}
