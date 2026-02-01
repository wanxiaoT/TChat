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
 * Google Gemini API Provider
 *
 * 支持完整的 SSE 流式响应处理和工具调用
 */
class GeminiProvider(
    private val apiKey: String,
    baseUrl: String = "https://generativelanguage.googleapis.com/v1",
    private val model: String = "gemini-pro",
    private val customParams: CustomParams? = null
) : AIProvider {

    private val normalizedBaseUrl = baseUrl
        .trim()
        .trimEnd('/')
        // 允许用户把 endpoint 填成 .../models（避免拼出 /models/models）
        .removeSuffix("/models")

    private val normalizedModel = model
        .trim()
        .removePrefix("models/")
        .removePrefix("/models/")

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
        val requestUrl = buildRequestUrl()
        val request = buildRequest(requestUrl, jsonBody)

        // 记录请求日志（避免在 URL 中暴露完整 key）
        val safeUrlForLog = "$normalizedBaseUrl/models/$normalizedModel:streamGenerateContent?alt=sse"
        val requestId = NetworkLogger.logRequest(
            provider = "Gemini",
            model = model,
            url = safeUrlForLog,
            headers = mapOf(
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

                        val error = handleErrorResponse(
                            responseCode = response.code,
                            responseMessage = response.message,
                            errorBody = errorBody
                        )
                        trySend(StreamChunk.Error(error))
                        close()
                        return
                    }

                    // 收集完整响应，用于日志展示
                    val responseContent = StringBuilder()

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
            return if (name.isNotEmpty()) {
                // Gemini 不提供 id，我们生成一个
                val finalId = id.ifEmpty { "call_${System.currentTimeMillis()}_${name.hashCode()}" }
                ToolCallInfo(finalId, name, arguments.toString().ifEmpty { "{}" })
            } else {
                null
            }
        }
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

    /**
     * 处理 SSE 流式响应（支持工具调用）
     */
    private fun processStreamResponseWithTools(
        response: Response,
        toolCallsBuilder: MutableMap<Int, ToolCallBuilder>,
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

                        val shouldStop = processSSEDataWithTools(data, toolCallsBuilder, onChunk)
                        if (shouldStop) {
                            return@use
                        }
                    }
                    // 直接 JSON（非 SSE 格式兼容）
                    line.startsWith("{") -> {
                        val shouldStop = processSSEDataWithTools(line, toolCallsBuilder, onChunk)
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
     * 处理 SSE 数据（支持工具调用）
     */
    private fun processSSEDataWithTools(
        data: String,
        toolCallsBuilder: MutableMap<Int, ToolCallBuilder>,
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
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)
                            
                            // 处理文本内容
                            if (part.has("text")) {
                                val text = part.getString("text")
                                if (text.isNotEmpty()) {
                                    onChunk(StreamChunk.Content(text))
                                }
                            }
                            
                            // 处理函数调用
                            if (part.has("functionCall")) {
                                val functionCall = part.getJSONObject("functionCall")
                                val name = functionCall.optString("name", "")
                                val args = functionCall.optJSONObject("args")
                                
                                if (name.isNotEmpty()) {
                                    val builder = ToolCallBuilder()
                                    builder.name = name
                                    if (args != null) {
                                        builder.arguments.append(args.toString())
                                    }
                                    toolCallsBuilder[i] = builder
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

    private fun buildRequestBody(messages: List<ChatMessage>, tools: List<ToolDefinition>): String {
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

        // 添加工具定义
        if (tools.isNotEmpty()) {
            val toolsArray = JSONArray()
            val toolDeclarationsArray = JSONArray()
            tools.forEach { tool ->
                val funcDecl = JSONObject()
                funcDecl.put("name", tool.name)
                funcDecl.put("description", tool.description)
                if (tool.parametersJson != null) {
                    funcDecl.put("parameters", JSONObject(tool.parametersJson))
                } else {
                    // 无参数工具
                    funcDecl.put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject())
                    })
                }
                toolDeclarationsArray.put(funcDecl)
            }
            val toolsObj = JSONObject()
            toolsObj.put("functionDeclarations", toolDeclarationsArray)
            toolsArray.put(toolsObj)
            jsonObject.put("tools", toolsArray)
        }

        val contents = JSONArray()
        otherMessages.forEach { msg ->
            val contentObj = JSONObject()

            when (msg.role) {
                MessageRole.TOOL -> {
                    // 工具结果作为 functionResponse
                    contentObj.put("role", "function")
                    val parts = JSONArray()
                    val partObj = JSONObject()
                    val funcResponse = JSONObject()
                    funcResponse.put("name", msg.name ?: "")
                    // 尝试解析 content 为 JSON，否则包装为对象
                    try {
                        funcResponse.put("response", JSONObject(msg.content))
                    } catch (e: Exception) {
                        funcResponse.put("response", JSONObject().apply {
                            put("result", msg.content)
                        })
                    }
                    partObj.put("functionResponse", funcResponse)
                    parts.put(partObj)
                    contentObj.put("parts", parts)
                }
                MessageRole.ASSISTANT -> {
                    contentObj.put("role", "model")
                    val parts = JSONArray()
                    
                    // 检查是否有工具调用
                    if (msg.toolCalls != null && msg.toolCalls.isNotEmpty()) {
                        // 如果有文本内容，先添加
                        if (msg.content.isNotEmpty()) {
                            val textPart = JSONObject()
                            textPart.put("text", msg.content)
                            parts.put(textPart)
                        }
                        // 添加工具调用
                        msg.toolCalls.forEach { tc ->
                            val funcCallPart = JSONObject()
                            val funcCall = JSONObject()
                            funcCall.put("name", tc.name)
                            try {
                                funcCall.put("args", JSONObject(tc.arguments))
                            } catch (e: Exception) {
                                funcCall.put("args", JSONObject())
                            }
                            funcCallPart.put("functionCall", funcCall)
                            parts.put(funcCallPart)
                        }
                    } else {
                        val partObj = JSONObject()
                        partObj.put("text", msg.content)
                        parts.put(partObj)
                    }
                    contentObj.put("parts", parts)
                }
                else -> {
                    // USER 和其他角色
                    contentObj.put("role", "user")
                    val parts = JSONArray()

                    if (msg.contentParts != null && msg.contentParts.isNotEmpty()) {
                        // 多模态内容
                        msg.contentParts.forEach { part ->
                            when (part) {
                                is MessageContent.Text -> {
                                    val textPart = JSONObject()
                                    textPart.put("text", part.text)
                                    parts.put(textPart)
                                }
                                is MessageContent.Image -> {
                                    val imagePart = JSONObject()
                                    val inlineData = JSONObject()
                                    inlineData.put("mimeType", part.mimeType)
                                    inlineData.put("data", part.base64Data)
                                    imagePart.put("inlineData", inlineData)
                                    parts.put(imagePart)
                                }
                                is MessageContent.Video -> {
                                    val videoPart = JSONObject()
                                    val inlineData = JSONObject()
                                    inlineData.put("mimeType", part.mimeType)
                                    inlineData.put("data", part.base64Data)
                                    videoPart.put("inlineData", inlineData)
                                    parts.put(videoPart)
                                }
                            }
                        }
                    } else {
                        val partObj = JSONObject()
                        partObj.put("text", msg.content)
                        parts.put(partObj)
                    }
                    contentObj.put("parts", parts)
                }
            }
            contents.put(contentObj)
        }

        jsonObject.put("contents", contents)

        // 添加生成配置
        val generationConfig = JSONObject()

        // 使用自定义参数中的 maxTokens，如果没有则使用默认值
        val effectiveMaxTokens = customParams?.maxTokens ?: 8192
        generationConfig.put("maxOutputTokens", effectiveMaxTokens)

        // 添加自定义参数 (Gemini 使用 temperature, topP, topK)
        customParams?.let { params ->
            params.temperature?.let { generationConfig.put("temperature", it.toDouble()) }
            params.topP?.let { generationConfig.put("topP", it.toDouble()) }
            params.topK?.let { generationConfig.put("topK", it) }
            // Gemini 不支持 presence_penalty 和 frequency_penalty
            // 但可以通过 extraParams 传递其他参数

            // 合并额外的 JSON 参数到 generationConfig
            if (params.extraParams.isNotEmpty() && params.extraParams != "{}") {
                try {
                    val extraJson = JSONObject(params.extraParams)
                    val keys = extraJson.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        generationConfig.put(key, extraJson.get(key))
                    }
                } catch (e: Exception) {
                    // 忽略无效的 JSON
                }
            }
        }

        jsonObject.put("generationConfig", generationConfig)

        return jsonObject.toString()
    }

    private fun buildRequestUrl(): String {
        return "$normalizedBaseUrl/models/$normalizedModel:streamGenerateContent?key=$apiKey&alt=sse"
    }

    private fun buildRequest(url: String, jsonBody: String): Request {
        return Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()
    }

    private fun handleErrorResponse(
        responseCode: Int,
        responseMessage: String,
        errorBody: String
    ): AIProviderException {
        // 尝试从 JSON 中提取详细错误
        val detailedMessage = try {
            val json = JSONObject(errorBody)
            val error = json.optJSONObject("error")
            error?.optString("message", errorBody) ?: errorBody
        } catch (e: Exception) {
            errorBody
        }

        return when (responseCode) {
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
                "API 错误 ($responseCode): $responseMessage\n$detailedMessage"
            )
        }
    }
}
