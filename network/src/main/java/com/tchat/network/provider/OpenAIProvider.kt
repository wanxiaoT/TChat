package com.tchat.network.provider

import com.tchat.network.log.NetworkLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.withContext
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
    private val model: String = "gpt-3.5-turbo",
    private val customParams: CustomParams? = null
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

        // 记录请求日志
        val requestId = NetworkLogger.logRequest(
            provider = "OpenAI",
            model = model,
            url = "$normalizedBaseUrl/chat/completions",
            headers = mapOf(
                "Authorization" to "Bearer ${apiKey.take(8)}...",
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

                    processStreamResponse(response) { chunk ->
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
                                return@processStreamResponse true
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

    override suspend fun generateImage(
        prompt: String,
        options: ImageGenerationOptions
    ): ImageGenerationResult = withContext(Dispatchers.IO) {
        val effectiveModel = options.model ?: "gpt-image-1"
        val requestBodyJson = JSONObject().apply {
            put("model", effectiveModel)
            put("prompt", prompt)
            put("n", options.n.coerceIn(1, 10))
            options.size?.takeIf { it.isNotBlank() }?.let { put("size", it) }
            options.quality?.takeIf { it.isNotBlank() }?.let { put("quality", it) }
            put("response_format", "b64_json")
        }

        val request = Request.Builder()
            .url("$normalizedBaseUrl/images/generations")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                throw handleErrorResponse(response.code, errorBody)
            }

            val responseBody = response.body?.string().orEmpty()
            val json = JSONObject(responseBody)
            val dataArray = json.optJSONArray("data") ?: JSONArray()

            val images = mutableListOf<GeneratedImage>()
            for (i in 0 until dataArray.length()) {
                val item = dataArray.optJSONObject(i) ?: continue
                val b64 = item.optString("b64_json").takeIf { it.isNotBlank() }
                if (b64 != null) {
                    images.add(GeneratedImage(base64Data = b64, mimeType = "image/png"))
                    continue
                }

                // 一些兼容服务可能返回 url，这里做一次兜底下载再转 base64
                val url = item.optString("url").takeIf { it.isNotBlank() } ?: continue
                val imageRequest = Request.Builder().url(url).build()
                client.newCall(imageRequest).execute().use { imageResp ->
                    if (!imageResp.isSuccessful) return@use
                    val bytes = imageResp.body?.bytes() ?: return@use
                    val mime = imageResp.header("Content-Type")?.takeIf { it.isNotBlank() } ?: "image/png"
                    val encoded = java.util.Base64.getEncoder().encodeToString(bytes)
                    images.add(GeneratedImage(base64Data = encoded, mimeType = mime))
                }
            }

            if (images.isEmpty()) {
                throw AIProviderException.UnknownError("图片生成失败：响应不包含图片数据")
            }

            ImageGenerationResult(images = images)
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
        
        // 用于跟踪是否在思考块中 (thinking models)
        var isInThinkingBlock = false

        // 用于收集流式工具调用
        val toolCallsBuilder = mutableMapOf<Int, ToolCallBuilder>()

        response.body?.byteStream()?.bufferedReader()?.use { reader ->
            reader.lineSequence().forEach { line ->
                if (line.startsWith("data: ")) {
                    val data = line.substring(6).trim()

                    if (data == "[DONE]") {
                        // 如果还在思考块中，先关闭思考标签
                        if (isInThinkingBlock) {
                            onChunk(StreamChunk.Content("\n</thinking>\n\n"))
                        }
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
                                // 处理思考内容 (thinking models like Kimi-K2-Thinking, DeepSeek-R1)
                                // reasoning_content 字段包含模型的思考过程
                                if (delta.has("reasoning_content") && !delta.isNull("reasoning_content")) {
                                    val reasoningContent = delta.optString("reasoning_content", "")
                                    if (reasoningContent.isNotEmpty()) {
                                        // 判断是否需要添加 thinking 标签
                                        // 首次收到 reasoning_content 时发送开始标签
                                        if (!isInThinkingBlock) {
                                            isInThinkingBlock = true
                                            onChunk(StreamChunk.Content("<thinking>\n"))
                                        }
                                        onChunk(StreamChunk.Content(reasoningContent))
                                    }
                                }
                                
                                // 处理文本内容
                                // 使用 optString 避免 null 值被转换为 "null" 字符串
                                if (delta.has("content") && !delta.isNull("content")) {
                                    val content = delta.optString("content", "")
                                    if (content.isNotEmpty()) {
                                        // 如果之前在思考块中，现在收到正式内容，先关闭思考标签
                                        if (isInThinkingBlock) {
                                            isInThinkingBlock = false
                                            onChunk(StreamChunk.Content("\n</thinking>\n\n"))
                                        }
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

            // 如果还在思考块中，先关闭思考标签
            if (isInThinkingBlock) {
                onChunk(StreamChunk.Content("\n</thinking>\n\n"))
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

        // 添加自定义参数
        customParams?.let { params ->
            params.temperature?.let { jsonObject.put("temperature", it.toDouble()) }
            params.topP?.let { jsonObject.put("top_p", it.toDouble()) }
            params.topK?.let { jsonObject.put("top_k", it) }
            params.presencePenalty?.let { jsonObject.put("presence_penalty", it.toDouble()) }
            params.frequencyPenalty?.let { jsonObject.put("frequency_penalty", it.toDouble()) }
            params.repetitionPenalty?.let { jsonObject.put("repetition_penalty", it.toDouble()) }
            params.maxTokens?.let { jsonObject.put("max_tokens", it) }

            // 合并额外的 JSON 参数
            if (params.extraParams.isNotEmpty() && params.extraParams != "{}") {
                try {
                    val extraJson = JSONObject(params.extraParams)
                    val keys = extraJson.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        jsonObject.put(key, extraJson.get(key))
                    }
                } catch (e: Exception) {
                    // 忽略无效的 JSON
                }
            }
        }

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
                    // USER 和其他角色
                    if (msg.contentParts != null && msg.contentParts.isNotEmpty()) {
                        // 多模态内容
                        val contentArray = JSONArray()
                        msg.contentParts.forEach { part ->
                            when (part) {
                                is MessageContent.Text -> {
                                    val textObj = JSONObject()
                                    textObj.put("type", "text")
                                    textObj.put("text", part.text)
                                    contentArray.put(textObj)
                                }
                                is MessageContent.Image -> {
                                    val imageObj = JSONObject()
                                    imageObj.put("type", "image_url")
                                    val imageUrlObj = JSONObject()
                                    imageUrlObj.put("url", "data:${part.mimeType};base64,${part.base64Data}")
                                    imageObj.put("image_url", imageUrlObj)
                                    contentArray.put(imageObj)
                                }
                                is MessageContent.Video -> {
                                    throw AIProviderException.InvalidRequestError(
                                        "当前 OpenAI Chat Completions 接口不支持视频输入（仅 Gemini 支持）"
                                    )
                                }
                            }
                        }
                        msgObj.put("content", contentArray)
                    } else {
                        msgObj.put("content", msg.content)
                    }
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
