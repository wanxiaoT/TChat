package com.tchat.data.repository.impl

import com.tchat.core.util.Result
import com.tchat.data.database.dao.ChatDao
import com.tchat.data.database.dao.MessageDao
import com.tchat.data.database.entity.ChatEntity
import com.tchat.data.database.entity.MessageEntity
import com.tchat.data.model.Chat
import com.tchat.data.model.Message
import com.tchat.data.model.MessageRole
import com.tchat.data.model.MessageVariant
import com.tchat.data.model.ToolCallData
import com.tchat.data.model.ToolResultData
import com.tchat.data.repository.ChatConfig
import com.tchat.data.repository.ChatRepository
import com.tchat.data.repository.MessageResult
import com.tchat.data.tool.InputSchema
import com.tchat.data.tool.Tool
import com.tchat.network.provider.AIProvider
import com.tchat.network.provider.ChatMessage
import com.tchat.network.provider.StreamChunk
import com.tchat.network.provider.ToolCallInfo
import com.tchat.network.provider.ToolDefinition
import com.tchat.network.provider.MessageRole as ProviderMessageRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 聊天仓库实现
 *
 * 支持工具调用的完整流程：
 * 1. 发送消息给AI，携带可用工具定义
 * 2. 如果AI返回工具调用，执行工具
 * 3. 将工具结果发送回AI
 * 4. 循环直到AI返回最终回复
 */
class ChatRepositoryImpl(
    private val aiProvider: AIProvider,
    private val chatDao: ChatDao,
    private val messageDao: MessageDao
) : ChatRepository {

    override fun getAllChats(): Flow<List<Chat>> {
        return chatDao.getAllChats().map { entities ->
            entities.map { it.toChat() }
        }
    }

    override fun getChatById(chatId: String): Flow<Chat?> = flow {
        val chatEntity = chatDao.getChatById(chatId)
        emit(chatEntity?.toChat())
    }

    override fun getMessagesByChatId(chatId: String): Flow<List<Message>> {
        return messageDao.getMessagesByChatId(chatId).map { entities ->
            entities.map { it.toMessage() }
        }
    }

    override suspend fun createChat(title: String): Result<Chat> {
        return try {
            val now = System.currentTimeMillis()
            val chatEntity = ChatEntity(
                id = UUID.randomUUID().toString(),
                title = title,
                createdAt = now,
                updatedAt = now
            )
            chatDao.insertChat(chatEntity)
            Result.Success(chatEntity.toChat())
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun updateChatTitle(chatId: String, title: String): Result<Unit> {
        return try {
            chatDao.updateChatTitle(chatId, title, System.currentTimeMillis())
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun deleteChat(chatId: String): Result<Unit> {
        return try {
            chatDao.deleteChat(chatId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun sendMessage(
        chatId: String,
        content: String,
        config: ChatConfig?
    ): Flow<Result<Message>> = flow {
        try {
            // 添加用户消息
            val userMessage = Message(
                id = UUID.randomUUID().toString(),
                chatId = chatId,
                content = content,
                role = MessageRole.USER
            )
            addMessage(userMessage)
            emit(Result.Success(userMessage))

            // 自动生成标题
            autoGenerateTitle(chatId, content)

            // 获取聊天历史
            val messages = getMessagesForAI(chatId, config?.systemPrompt)

            // 转换工具定义
            val toolDefinitions = config?.tools?.map { it.toToolDefinition() } ?: emptyList()

            // 创建 AI 消息占位符
            val assistantId = UUID.randomUUID().toString()
            val initialAssistantMessage = Message(
                id = assistantId,
                chatId = chatId,
                content = "",
                role = MessageRole.ASSISTANT,
                isStreaming = true
            )
            emit(Result.Success(initialAssistantMessage))

            // 执行带工具调用的聊天流程
            val finalMessage = executeWithToolCalls(
                chatId = chatId,
                assistantId = assistantId,
                messages = messages,
                tools = config?.tools ?: emptyList(),
                toolDefinitions = toolDefinitions,
                onStreamingUpdate = { msg -> emit(Result.Success(msg)) }
            )

            emit(Result.Success(finalMessage))
        } catch (e: Exception) {
            emit(Result.Error(e))
        }
    }

    override suspend fun addMessage(message: Message): Result<Unit> {
        return try {
            saveMessageToDb(message)
            chatDao.updateChatTimestamp(message.chatId, System.currentTimeMillis())
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun sendMessageToNewChat(
        content: String,
        config: ChatConfig?
    ): Flow<Result<MessageResult>> = flow {
        try {
            // 创建新聊天
            val title = content.take(50).let {
                if (content.length > 50) "$it..." else it
            }
            val now = System.currentTimeMillis()
            val chatEntity = ChatEntity(
                id = UUID.randomUUID().toString(),
                title = title,
                createdAt = now,
                updatedAt = now,
                isNameManuallyEdited = false
            )
            chatDao.insertChat(chatEntity)
            val chatId = chatEntity.id

            // 添加用户消息
            val userMessage = Message(
                id = UUID.randomUUID().toString(),
                chatId = chatId,
                content = content,
                role = MessageRole.USER
            )
            saveMessageToDb(userMessage)
            emit(Result.Success(MessageResult(chatId, userMessage)))

            // 构建消息历史
            val messages = mutableListOf<ChatMessage>()
            config?.systemPrompt?.let {
                if (it.isNotBlank()) {
                    messages.add(ChatMessage(role = ProviderMessageRole.SYSTEM, content = it))
                }
            }
            messages.add(ChatMessage(role = ProviderMessageRole.USER, content = content))

            // 转换工具定义
            val toolDefinitions = config?.tools?.map { it.toToolDefinition() } ?: emptyList()

            // 创建 AI 消息占位符
            val assistantId = UUID.randomUUID().toString()
            val initialAssistantMessage = Message(
                id = assistantId,
                chatId = chatId,
                content = "",
                role = MessageRole.ASSISTANT,
                isStreaming = true
            )
            emit(Result.Success(MessageResult(chatId, initialAssistantMessage)))

            // 执行带工具调用的聊天流程
            val finalMessage = executeWithToolCalls(
                chatId = chatId,
                assistantId = assistantId,
                messages = messages,
                tools = config?.tools ?: emptyList(),
                toolDefinitions = toolDefinitions,
                onStreamingUpdate = { msg -> emit(Result.Success(MessageResult(chatId, msg))) }
            )

            emit(Result.Success(MessageResult(chatId, finalMessage)))
        } catch (e: Exception) {
            emit(Result.Error(e))
        }
    }

    /**
     * 执行带工具调用的聊天流程
     *
     * 核心逻辑：循环处理AI回复，直到收到纯文本回复
     */
    private suspend fun executeWithToolCalls(
        chatId: String,
        assistantId: String,
        messages: MutableList<ChatMessage>,
        tools: List<Tool>,
        toolDefinitions: List<ToolDefinition>,
        onStreamingUpdate: suspend (Message) -> Unit
    ): Message {
        var currentContent = ""
        var inputTokens = 0
        var outputTokens = 0
        var tokensPerSecond = 0.0
        var firstTokenLatency = 0L
        var pendingToolCalls: List<ToolCallInfo>? = null
        val allToolResults = mutableListOf<ToolResultData>()

        // 工具调用循环，最多执行10轮避免无限循环
        var iteration = 0
        val maxIterations = 10

        while (iteration < maxIterations) {
            iteration++

            // 调试日志：检查工具定义是否传递
            println("=== 工具调用调试 ===")
            println("工具定义数量: ${toolDefinitions.size}")
            toolDefinitions.forEach { tool ->
                println("  - ${tool.name}: ${tool.description.take(50)}...")
            }
            println("==================")

            val flow = if (toolDefinitions.isNotEmpty()) {
                aiProvider.streamChatWithTools(messages, toolDefinitions)
            } else {
                aiProvider.streamChat(messages)
            }

            var hasToolCall = false
            currentContent = ""

            flow.collect { chunk ->
                when (chunk) {
                    is StreamChunk.Content -> {
                        currentContent += chunk.text
                        onStreamingUpdate(Message(
                            id = assistantId,
                            chatId = chatId,
                            content = currentContent,
                            role = MessageRole.ASSISTANT,
                            isStreaming = true,
                            toolResults = if (allToolResults.isNotEmpty()) allToolResults.toList() else null
                        ))
                    }
                    is StreamChunk.ToolCall -> {
                        hasToolCall = true
                        pendingToolCalls = chunk.toolCalls
                    }
                    is StreamChunk.Done -> {
                        inputTokens += chunk.inputTokens
                        outputTokens += chunk.outputTokens
                        tokensPerSecond = chunk.tokensPerSecond
                        if (firstTokenLatency == 0L) {
                            firstTokenLatency = chunk.firstTokenLatency
                        }
                    }
                    is StreamChunk.Error -> {
                        throw chunk.error
                    }
                }
            }

            // 如果没有工具调用，退出循环
            if (!hasToolCall || pendingToolCalls.isNullOrEmpty()) {
                break
            }

            // 执行工具调用
            val toolCalls = pendingToolCalls!!

            // 添加助手的工具调用消息到历史
            messages.add(ChatMessage(
                role = ProviderMessageRole.ASSISTANT,
                content = currentContent.ifEmpty { null } ?: "",
                toolCalls = toolCalls
            ))

            // 执行每个工具并收集结果
            for (toolCall in toolCalls) {
                val tool = tools.find { it.name == toolCall.name }
                val result = if (tool != null) {
                    try {
                        val argsObject = JSONObject(toolCall.arguments)
                        val resultObject = tool.execute(argsObject)
                        resultObject.toString()
                    } catch (e: Exception) {
                        """{"error": "${e.message?.replace("\"", "\\\"") ?: "执行失败"}"}"""
                    }
                } else {
                    """{"error": "未知工具: ${toolCall.name}"}"""
                }

                // 记录工具结果用于显示
                allToolResults.add(ToolResultData(
                    toolCallId = toolCall.id,
                    name = toolCall.name,
                    result = result,
                    isError = result.contains("\"error\"")
                ))

                // 添加工具结果到消息历史
                messages.add(ChatMessage(
                    role = ProviderMessageRole.TOOL,
                    content = result,
                    toolCallId = toolCall.id,
                    name = toolCall.name
                ))
            }

            // 更新UI显示工具执行状态
            onStreamingUpdate(Message(
                id = assistantId,
                chatId = chatId,
                content = currentContent,
                role = MessageRole.ASSISTANT,
                isStreaming = true,
                toolResults = allToolResults.toList()
            ))

            pendingToolCalls = null
        }

        // 保存最终消息到数据库
        val finalMessage = Message(
            id = assistantId,
            chatId = chatId,
            content = currentContent,
            role = MessageRole.ASSISTANT,
            isStreaming = false,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            tokensPerSecond = tokensPerSecond,
            firstTokenLatency = firstTokenLatency,
            toolResults = if (allToolResults.isNotEmpty()) allToolResults.toList() else null
        )
        saveMessageToDb(finalMessage)
        chatDao.updateChatTimestamp(chatId, System.currentTimeMillis())

        return finalMessage
    }

    override suspend fun regenerateMessage(
        chatId: String,
        userMessageId: String,
        aiMessageId: String,
        config: ChatConfig?
    ): Flow<Result<Message>> = flow {
        try {
            val originalEntity = messageDao.getMessageById(aiMessageId)
                ?: throw Exception("消息不存在")
            val originalMessage = originalEntity.toMessage()

            val allMessages = messageDao.getMessagesByChatIdOnce(chatId)
            val userMessageIndex = allMessages.indexOfFirst { it.id == userMessageId }
            if (userMessageIndex < 0) throw Exception("用户消息不存在")

            // 构建消息历史
            val messages = mutableListOf<ChatMessage>()
            config?.systemPrompt?.let {
                if (it.isNotBlank()) {
                    messages.add(ChatMessage(role = ProviderMessageRole.SYSTEM, content = it))
                }
            }
            messages.addAll(allMessages.take(userMessageIndex + 1).map {
                ChatMessage(
                    role = when (it.role) {
                        "user" -> ProviderMessageRole.USER
                        "assistant" -> ProviderMessageRole.ASSISTANT
                        else -> ProviderMessageRole.SYSTEM
                    },
                    content = it.content
                )
            })

            val streamingMessage = originalMessage.copy(content = "", isStreaming = true)
            emit(Result.Success(streamingMessage))

            // 转换工具定义
            val toolDefinitions = config?.tools?.map { it.toToolDefinition() } ?: emptyList()

            // 执行带工具调用的聊天流程
            val finalContent = executeWithToolCallsForRegenerate(
                messages = messages,
                tools = config?.tools ?: emptyList(),
                toolDefinitions = toolDefinitions,
                onStreamingUpdate = { content, toolResults ->
                    emit(Result.Success(originalMessage.copy(
                        content = content,
                        isStreaming = true,
                        toolResults = toolResults
                    )))
                }
            )

            // 创建新变体
            val newVariant = MessageVariant(
                content = finalContent.first,
                inputTokens = finalContent.second,
                outputTokens = finalContent.third
            )

            val existingVariants = originalMessage.variants.toMutableList()
            if (existingVariants.isEmpty()) {
                existingVariants.add(MessageVariant(
                    content = originalMessage.content,
                    inputTokens = originalMessage.inputTokens,
                    outputTokens = originalMessage.outputTokens,
                    tokensPerSecond = originalMessage.tokensPerSecond,
                    firstTokenLatency = originalMessage.firstTokenLatency,
                    createdAt = originalMessage.timestamp
                ))
            }
            existingVariants.add(newVariant)
            val newSelectedIndex = existingVariants.size - 1

            val variantsJson = variantsToJson(existingVariants)
            messageDao.updateMessageVariants(aiMessageId, variantsJson, newSelectedIndex)

            val finalMessage = originalMessage.copy(
                content = finalContent.first,
                isStreaming = false,
                inputTokens = finalContent.second,
                outputTokens = finalContent.third,
                variants = existingVariants,
                selectedVariantIndex = newSelectedIndex
            )
            emit(Result.Success(finalMessage))
        } catch (e: Exception) {
            emit(Result.Error(e))
        }
    }

    /**
     * 为重新生成执行工具调用流程
     */
    private suspend fun executeWithToolCallsForRegenerate(
        messages: MutableList<ChatMessage>,
        tools: List<Tool>,
        toolDefinitions: List<ToolDefinition>,
        onStreamingUpdate: suspend (String, List<ToolResultData>?) -> Unit
    ): Triple<String, Int, Int> {
        var currentContent = ""
        var inputTokens = 0
        var outputTokens = 0
        var pendingToolCalls: List<ToolCallInfo>? = null
        val allToolResults = mutableListOf<ToolResultData>()

        var iteration = 0
        val maxIterations = 10

        while (iteration < maxIterations) {
            iteration++

            val flow = if (toolDefinitions.isNotEmpty()) {
                aiProvider.streamChatWithTools(messages, toolDefinitions)
            } else {
                aiProvider.streamChat(messages)
            }

            var hasToolCall = false
            currentContent = ""

            flow.collect { chunk ->
                when (chunk) {
                    is StreamChunk.Content -> {
                        currentContent += chunk.text
                        onStreamingUpdate(
                            currentContent,
                            if (allToolResults.isNotEmpty()) allToolResults.toList() else null
                        )
                    }
                    is StreamChunk.ToolCall -> {
                        hasToolCall = true
                        pendingToolCalls = chunk.toolCalls
                    }
                    is StreamChunk.Done -> {
                        inputTokens += chunk.inputTokens
                        outputTokens += chunk.outputTokens
                    }
                    is StreamChunk.Error -> {
                        throw chunk.error
                    }
                }
            }

            if (!hasToolCall || pendingToolCalls.isNullOrEmpty()) {
                break
            }

            val toolCalls = pendingToolCalls!!

            messages.add(ChatMessage(
                role = ProviderMessageRole.ASSISTANT,
                content = currentContent.ifEmpty { null } ?: "",
                toolCalls = toolCalls
            ))

            for (toolCall in toolCalls) {
                val tool = tools.find { it.name == toolCall.name }
                val result = if (tool != null) {
                    try {
                        val argsObject = JSONObject(toolCall.arguments)
                        val resultObject = tool.execute(argsObject)
                        resultObject.toString()
                    } catch (e: Exception) {
                        """{"error": "${e.message?.replace("\"", "\\\"") ?: "执行失败"}"}"""
                    }
                } else {
                    """{"error": "未知工具: ${toolCall.name}"}"""
                }

                allToolResults.add(ToolResultData(
                    toolCallId = toolCall.id,
                    name = toolCall.name,
                    result = result,
                    isError = result.contains("\"error\"")
                ))

                messages.add(ChatMessage(
                    role = ProviderMessageRole.TOOL,
                    content = result,
                    toolCallId = toolCall.id,
                    name = toolCall.name
                ))
            }

            onStreamingUpdate(currentContent, allToolResults.toList())
            pendingToolCalls = null
        }

        return Triple(currentContent, inputTokens, outputTokens)
    }

    override suspend fun selectVariant(messageId: String, variantIndex: Int): Result<Unit> {
        return try {
            messageDao.updateSelectedVariant(messageId, variantIndex)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun getMessageById(messageId: String): Message? {
        return messageDao.getMessageById(messageId)?.toMessage()
    }

    private suspend fun getMessagesForAI(chatId: String, systemPrompt: String?): MutableList<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()

        // 添加系统提示
        systemPrompt?.let {
            if (it.isNotBlank()) {
                messages.add(ChatMessage(role = ProviderMessageRole.SYSTEM, content = it))
            }
        }

        // 添加历史消息
        messages.addAll(messageDao.getMessagesByChatIdOnce(chatId).map { entity ->
            val message = entity.toMessage()
            ChatMessage(
                role = when (entity.role) {
                    "user" -> ProviderMessageRole.USER
                    "assistant" -> ProviderMessageRole.ASSISTANT
                    "tool" -> ProviderMessageRole.TOOL
                    else -> ProviderMessageRole.SYSTEM
                },
                content = message.getCurrentContent(),
                toolCallId = entity.toolCallId,
                name = entity.toolName
            )
        })

        return messages
    }

    private suspend fun autoGenerateTitle(chatId: String, firstUserMessage: String) {
        try {
            val chat = chatDao.getChatById(chatId) ?: return
            if (chat.isNameManuallyEdited) return
            if (chat.title == "新对话" || chat.title.isEmpty()) {
                val newTitle = firstUserMessage.take(50).let {
                    if (firstUserMessage.length > 50) "$it..." else it
                }
                chatDao.updateChatTitle(chatId, newTitle, System.currentTimeMillis())
            }
        } catch (e: Exception) {
            // 忽略
        }
    }

    private suspend fun saveMessageToDb(message: Message) {
        val entity = MessageEntity(
            id = message.id,
            chatId = message.chatId,
            content = message.content,
            role = message.role.name.lowercase(),
            timestamp = message.timestamp,
            inputTokens = message.inputTokens,
            outputTokens = message.outputTokens,
            tokensPerSecond = message.tokensPerSecond,
            firstTokenLatency = message.firstTokenLatency,
            variantsJson = if (message.variants.isNotEmpty()) variantsToJson(message.variants) else null,
            selectedVariantIndex = message.selectedVariantIndex,
            toolCallId = message.toolCallId,
            toolName = if (message.role == MessageRole.TOOL) message.toolCallId else null,
            toolCallsJson = message.toolCalls?.let { toolCallsToJson(it) },
            toolResultsJson = message.toolResults?.let { toolResultsToJson(it) }
        )
        messageDao.insertMessage(entity)
    }

    /**
     * Tool -> ToolDefinition 转换
     */
    private fun Tool.toToolDefinition(): ToolDefinition {
        val inputSchema = this.parameters()
        val parametersJsonString: String? = when (inputSchema) {
            is InputSchema.Obj -> {
                try {
                    val builder = JSONObject()
                    builder.put("type", "object")

                    // 构建 properties 对象
                    val propertiesObj = JSONObject()
                    inputSchema.properties.forEach { (key, prop) ->
                        val propObj = JSONObject()
                        propObj.put("type", prop.type)
                        prop.description?.let { propObj.put("description", it) }
                        prop.enum?.let { enumList ->
                            val enumArray = JSONArray()
                            enumList.forEach { enumArray.put(it) }
                            propObj.put("enum", enumArray)
                        }
                        propertiesObj.put(key, propObj)
                    }
                    builder.put("properties", propertiesObj)

                    inputSchema.required?.let { req ->
                        val reqArray = JSONArray()
                        req.forEach { reqArray.put(it) }
                        builder.put("required", reqArray)
                    }
                    builder.toString()
                } catch (e: Exception) {
                    null
                }
            }
            null -> null
        }
        return ToolDefinition(
            name = this.name,
            description = this.description,
            parametersJson = parametersJsonString
        )
    }

    // JSON 序列化
    private fun variantsToJson(variants: List<MessageVariant>): String {
        val jsonArray = JSONArray()
        variants.forEach { variant ->
            val jsonObj = JSONObject()
            jsonObj.put("content", variant.content)
            jsonObj.put("inputTokens", variant.inputTokens)
            jsonObj.put("outputTokens", variant.outputTokens)
            jsonObj.put("tokensPerSecond", variant.tokensPerSecond)
            jsonObj.put("firstTokenLatency", variant.firstTokenLatency)
            jsonObj.put("createdAt", variant.createdAt)
            jsonArray.put(jsonObj)
        }
        return jsonArray.toString()
    }

    private fun toolCallsToJson(toolCalls: List<ToolCallData>): String {
        val jsonArray = JSONArray()
        toolCalls.forEach { tc ->
            val jsonObj = JSONObject()
            jsonObj.put("id", tc.id)
            jsonObj.put("name", tc.name)
            jsonObj.put("arguments", tc.arguments)
            jsonArray.put(jsonObj)
        }
        return jsonArray.toString()
    }

    private fun toolResultsToJson(toolResults: List<ToolResultData>): String {
        val jsonArray = JSONArray()
        toolResults.forEach { tr ->
            val jsonObj = JSONObject()
            jsonObj.put("toolCallId", tr.toolCallId)
            jsonObj.put("name", tr.name)
            jsonObj.put("result", tr.result)
            jsonObj.put("isError", tr.isError)
            jsonArray.put(jsonObj)
        }
        return jsonArray.toString()
    }

    private fun jsonToVariants(json: String?): List<MessageVariant> {
        if (json.isNullOrEmpty()) return emptyList()
        return try {
            val jsonArray = JSONArray(json)
            (0 until jsonArray.length()).map { i ->
                val jsonObj = jsonArray.getJSONObject(i)
                MessageVariant(
                    content = jsonObj.optString("content", ""),
                    inputTokens = jsonObj.optInt("inputTokens", 0),
                    outputTokens = jsonObj.optInt("outputTokens", 0),
                    tokensPerSecond = jsonObj.optDouble("tokensPerSecond", 0.0),
                    firstTokenLatency = jsonObj.optLong("firstTokenLatency", 0),
                    createdAt = jsonObj.optLong("createdAt", System.currentTimeMillis())
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun jsonToToolCalls(json: String?): List<ToolCallData>? {
        if (json.isNullOrEmpty()) return null
        return try {
            val jsonArray = JSONArray(json)
            (0 until jsonArray.length()).map { i ->
                val jsonObj = jsonArray.getJSONObject(i)
                ToolCallData(
                    id = jsonObj.optString("id", ""),
                    name = jsonObj.optString("name", ""),
                    arguments = jsonObj.optString("arguments", "{}")
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun jsonToToolResults(json: String?): List<ToolResultData>? {
        if (json.isNullOrEmpty()) return null
        return try {
            val jsonArray = JSONArray(json)
            (0 until jsonArray.length()).map { i ->
                val jsonObj = jsonArray.getJSONObject(i)
                ToolResultData(
                    toolCallId = jsonObj.optString("toolCallId", ""),
                    name = jsonObj.optString("name", ""),
                    result = jsonObj.optString("result", ""),
                    isError = jsonObj.optBoolean("isError", false)
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun ChatEntity.toChat() = Chat(
        id = id,
        title = title,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun MessageEntity.toMessage(): Message {
        val variants = jsonToVariants(variantsJson)
        val currentVariant = if (variants.isNotEmpty() && selectedVariantIndex in variants.indices) {
            variants[selectedVariantIndex]
        } else {
            null
        }

        return Message(
            id = id,
            chatId = chatId,
            content = currentVariant?.content ?: content,
            role = MessageRole.valueOf(role.uppercase()),
            timestamp = timestamp,
            isStreaming = false,
            inputTokens = currentVariant?.inputTokens ?: inputTokens,
            outputTokens = currentVariant?.outputTokens ?: outputTokens,
            tokensPerSecond = currentVariant?.tokensPerSecond ?: tokensPerSecond,
            firstTokenLatency = currentVariant?.firstTokenLatency ?: firstTokenLatency,
            variants = variants,
            selectedVariantIndex = selectedVariantIndex,
            toolCallId = toolCallId,
            toolCalls = jsonToToolCalls(toolCallsJson),
            toolResults = jsonToToolResults(toolResultsJson)
        )
    }
}
