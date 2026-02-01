package com.tchat.data.repository.impl

import com.tchat.core.util.Result
import com.tchat.data.database.dao.ChatDao
import com.tchat.data.database.dao.MessageDao
import com.tchat.data.database.entity.ChatEntity
import com.tchat.data.database.entity.MessageEntity
import com.tchat.data.model.Chat
import com.tchat.data.model.Message
import com.tchat.data.model.MessagePart
import com.tchat.data.model.MessageRole
import com.tchat.data.model.MessageVariant
import com.tchat.data.repository.ChatConfig
import com.tchat.data.repository.ChatRepository
import com.tchat.data.repository.MessageResult
import com.tchat.data.repository.SkillRepository
import com.tchat.data.skill.SkillService
import com.tchat.data.tool.InputSchema
import com.tchat.data.tool.Tool
import com.tchat.data.util.MessagePartSerializer
import com.tchat.data.util.RegexRuleData
import com.tchat.data.util.RegexStreamProcessor
import com.tchat.network.provider.AIProvider
import com.tchat.network.provider.AIProviderFactory
import com.tchat.network.provider.ChatMessage
import com.tchat.network.provider.ImageGenerationOptions
import com.tchat.network.provider.MessageContent
import com.tchat.network.provider.StreamChunk
import com.tchat.network.provider.ToolCallInfo
import com.tchat.network.provider.ToolDefinition
import com.tchat.network.provider.MessageRole as ProviderMessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.Base64

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
    private val messageDao: MessageDao,
    private val providerType: AIProviderFactory.ProviderType,
    private val mediaDir: File,
    private val skillRepository: SkillRepository? = null
) : ChatRepository {

    private val skillService: SkillService? by lazy {
        skillRepository?.let { SkillService(it) }
    }

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
        config: ChatConfig?,
        mediaParts: List<MessagePart>
    ): Flow<Result<Message>> = flow {
        try {
            println("=== sendMessage 开始 ===")
            println("chatId: $chatId")
            println("config tools: ${config?.tools?.size ?: 0}")

            val normalizedMediaParts = mediaParts.filter { it is MessagePart.Image || it is MessagePart.Video }
            if (providerType != AIProviderFactory.ProviderType.GEMINI &&
                normalizedMediaParts.any { it is MessagePart.Video }
            ) {
                throw IllegalArgumentException("当前服务商不支持视频输入（仅 Gemini 支持）")
            }
            val userParts = buildList {
                if (content.isNotBlank()) add(MessagePart.Text(content))
                addAll(normalizedMediaParts)
            }

            if (userParts.isEmpty()) {
                throw IllegalArgumentException("消息内容为空")
            }

            // 添加用户消息（支持多模态）
            val userMessage = Message(
                id = UUID.randomUUID().toString(),
                chatId = chatId,
                role = MessageRole.USER,
                parts = userParts
            )
            addMessage(userMessage)
            emit(Result.Success(userMessage))

            // 自动生成标题
            val titleSeed = content.takeIf { it.isNotBlank() }
                ?: when {
                    normalizedMediaParts.any { it is MessagePart.Video } -> "视频"
                    normalizedMediaParts.any { it is MessagePart.Image } -> "图片"
                    else -> ""
                }
            autoGenerateTitle(chatId, titleSeed)

            // 技能匹配和激活
            val enabledSkillIds = config?.enabledSkillIds ?: emptyList()
            val activatedSkills = if (enabledSkillIds.isNotEmpty() && skillService != null) {
                skillService!!.getActivatedSkills(content, enabledSkillIds)
            } else {
                emptyList()
            }

            // 构建增强的系统提示（包含激活的技能内容）
            val enhancedSystemPrompt = if (activatedSkills.isNotEmpty() && skillService != null) {
                skillService!!.buildSystemPromptWithSkills(
                    baseSystemPrompt = config?.systemPrompt ?: "",
                    activatedSkills = activatedSkills
                )
            } else {
                config?.systemPrompt
            }

            // 获取技能工具并合并
            val skillTools = if (activatedSkills.isNotEmpty() && skillService != null) {
                skillService!!.getToolsFromSkills(activatedSkills)
            } else {
                emptyList()
            }
            val allTools = (config?.tools ?: emptyList()) + skillTools

            // 获取聊天历史
            val messages = getMessagesForAI(chatId, enhancedSystemPrompt)

            // 转换工具定义
            val toolDefinitions = allTools.map { it.toToolDefinition() }

            // 创建 AI 消息占位符
            val assistantId = UUID.randomUUID().toString()
            val initialAssistantMessage = Message(
                id = assistantId,
                chatId = chatId,
                role = MessageRole.ASSISTANT,
                parts = emptyList(),
                isStreaming = true
            )
            emit(Result.Success(initialAssistantMessage))

            // 执行带工具调用的聊天流程
            val finalMessage = executeWithToolCalls(
                chatId = chatId,
                assistantId = assistantId,
                messages = messages,
                tools = allTools,
                toolDefinitions = toolDefinitions,
                modelName = config?.modelName,
                providerId = config?.providerId,
                shouldRecordTokens = config?.shouldRecordTokens ?: true,
                regexRules = config?.regexRules ?: emptyList(),
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
        config: ChatConfig?,
        mediaParts: List<MessagePart>
    ): Flow<Result<MessageResult>> = flow {
        try {
            // 创建新聊天
            val normalizedMediaParts = mediaParts.filter { it is MessagePart.Image || it is MessagePart.Video }
            if (providerType != AIProviderFactory.ProviderType.GEMINI &&
                normalizedMediaParts.any { it is MessagePart.Video }
            ) {
                throw IllegalArgumentException("当前服务商不支持视频输入（仅 Gemini 支持）")
            }
            val title = content.takeIf { it.isNotBlank() }?.take(50)?.let {
                if (content.length > 50) "$it..." else it
            } ?: when {
                normalizedMediaParts.any { it is MessagePart.Video } -> "视频"
                normalizedMediaParts.any { it is MessagePart.Image } -> "图片"
                else -> "新对话"
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

            val userParts = buildList {
                if (content.isNotBlank()) add(MessagePart.Text(content))
                addAll(normalizedMediaParts)
            }

            if (userParts.isEmpty()) {
                throw IllegalArgumentException("消息内容为空")
            }

            // 添加用户消息（支持多模态）
            val userMessage = Message(
                id = UUID.randomUUID().toString(),
                chatId = chatId,
                role = MessageRole.USER,
                parts = userParts
            )
            saveMessageToDb(userMessage)
            emit(Result.Success(MessageResult(chatId, userMessage)))

            // 技能匹配和激活
            val enabledSkillIds = config?.enabledSkillIds ?: emptyList()
            val activatedSkills = if (enabledSkillIds.isNotEmpty() && skillService != null) {
                skillService!!.getActivatedSkills(content, enabledSkillIds)
            } else {
                emptyList()
            }

            // 构建增强的系统提示（包含激活的技能内容）
            val enhancedSystemPrompt = if (activatedSkills.isNotEmpty() && skillService != null) {
                skillService!!.buildSystemPromptWithSkills(
                    baseSystemPrompt = config?.systemPrompt ?: "",
                    activatedSkills = activatedSkills
                )
            } else {
                config?.systemPrompt
            }

            // 获取技能工具并合并
            val skillTools = if (activatedSkills.isNotEmpty() && skillService != null) {
                skillService!!.getToolsFromSkills(activatedSkills)
            } else {
                emptyList()
            }
            val allTools = (config?.tools ?: emptyList()) + skillTools

            // 构建消息历史
            val messages = mutableListOf<ChatMessage>()
            enhancedSystemPrompt?.let {
                if (it.isNotBlank()) {
                    messages.add(ChatMessage(role = ProviderMessageRole.SYSTEM, content = it))
                }
            }
            messages.add(buildUserChatMessage(userMessage))

            // 转换工具定义
            val toolDefinitions = allTools.map { it.toToolDefinition() }

            // 创建 AI 消息占位符
            val assistantId = UUID.randomUUID().toString()
            val initialAssistantMessage = Message(
                id = assistantId,
                chatId = chatId,
                role = MessageRole.ASSISTANT,
                parts = emptyList(),
                isStreaming = true
            )
            emit(Result.Success(MessageResult(chatId, initialAssistantMessage)))

            // 执行带工具调用的聊天流程
            val finalMessage = executeWithToolCalls(
                chatId = chatId,
                assistantId = assistantId,
                messages = messages,
                tools = allTools,
                toolDefinitions = toolDefinitions,
                modelName = config?.modelName,
                providerId = config?.providerId,
                shouldRecordTokens = config?.shouldRecordTokens ?: true,
                regexRules = config?.regexRules ?: emptyList(),
                onStreamingUpdate = { msg -> emit(Result.Success(MessageResult(chatId, msg))) }
            )

            emit(Result.Success(MessageResult(chatId, finalMessage)))
        } catch (e: Exception) {
            emit(Result.Error(e))
        }
    }

    override suspend fun generateImage(
        chatId: String,
        prompt: String,
        config: ChatConfig?
    ): Flow<Result<Message>> = flow {
        try {
            val trimmedPrompt = prompt.trim()
            if (trimmedPrompt.isBlank()) throw IllegalArgumentException("提示词不能为空")

            // 记录用户提示词消息（作为对话上下文）
            val userMessage = Message.createTextMessage(
                id = UUID.randomUUID().toString(),
                chatId = chatId,
                role = MessageRole.USER,
                content = trimmedPrompt
            )
            addMessage(userMessage)
            emit(Result.Success(userMessage))

            autoGenerateTitle(chatId, trimmedPrompt)

            // 生成占位 AI 消息
            val assistantId = UUID.randomUUID().toString()
            emit(Result.Success(Message(
                id = assistantId,
                chatId = chatId,
                role = MessageRole.ASSISTANT,
                parts = listOf(MessagePart.Text("图片生成中...")),
                isStreaming = true
            )))

            val result = aiProvider.generateImage(
                prompt = trimmedPrompt,
                options = ImageGenerationOptions()
            )

            val savedParts = mutableListOf<MessagePart>()
            result.images.forEachIndexed { index, img ->
                savedParts.add(saveGeneratedImage(img, index))
            }

            if (savedParts.isEmpty()) throw IllegalStateException("图片生成失败：没有返回图片")

            val finalMessage = Message(
                id = assistantId,
                chatId = chatId,
                role = MessageRole.ASSISTANT,
                parts = savedParts,
                isStreaming = false,
                modelName = config?.modelName,
                providerId = config?.providerId
            )

            saveMessageToDb(finalMessage)
            chatDao.updateChatTimestamp(chatId, System.currentTimeMillis())

            emit(Result.Success(finalMessage))
        } catch (e: Exception) {
            emit(Result.Error(e))
        }
    }

    override suspend fun generateImageToNewChat(
        prompt: String,
        config: ChatConfig?
    ): Flow<Result<MessageResult>> = flow {
        try {
            val trimmedPrompt = prompt.trim()
            if (trimmedPrompt.isBlank()) throw IllegalArgumentException("提示词不能为空")

            // 创建新聊天
            val title = trimmedPrompt.take(50).let {
                if (trimmedPrompt.length > 50) "$it..." else it
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

            // 保存用户提示词消息
            val userMessage = Message.createTextMessage(
                id = UUID.randomUUID().toString(),
                chatId = chatId,
                role = MessageRole.USER,
                content = trimmedPrompt
            )
            saveMessageToDb(userMessage)
            emit(Result.Success(MessageResult(chatId, userMessage)))

            // 生成占位 AI 消息
            val assistantId = UUID.randomUUID().toString()
            emit(Result.Success(MessageResult(chatId, Message(
                id = assistantId,
                chatId = chatId,
                role = MessageRole.ASSISTANT,
                parts = listOf(MessagePart.Text("图片生成中...")),
                isStreaming = true
            ))))

            val result = aiProvider.generateImage(
                prompt = trimmedPrompt,
                options = ImageGenerationOptions()
            )

            val savedParts = mutableListOf<MessagePart>()
            result.images.forEachIndexed { index, img ->
                savedParts.add(saveGeneratedImage(img, index))
            }

            if (savedParts.isEmpty()) throw IllegalStateException("图片生成失败：没有返回图片")

            val finalMessage = Message(
                id = assistantId,
                chatId = chatId,
                role = MessageRole.ASSISTANT,
                parts = savedParts,
                isStreaming = false,
                modelName = config?.modelName,
                providerId = config?.providerId
            )
            saveMessageToDb(finalMessage)
            chatDao.updateChatTimestamp(chatId, System.currentTimeMillis())

            emit(Result.Success(MessageResult(chatId, finalMessage)))
        } catch (e: Exception) {
            emit(Result.Error(e))
        }
    }

    private suspend fun saveGeneratedImage(
        image: com.tchat.network.provider.GeneratedImage,
        index: Int
    ): MessagePart.Image = withContext(Dispatchers.IO) {
        val bytes = try {
            Base64.getDecoder().decode(image.base64Data)
        } catch (e: Exception) {
            throw IllegalArgumentException("生成图片数据解析失败", e)
        }

        val ext = when {
            image.mimeType.contains("png", ignoreCase = true) -> "png"
            image.mimeType.contains("webp", ignoreCase = true) -> "webp"
            image.mimeType.contains("jpg", ignoreCase = true) || image.mimeType.contains("jpeg", ignoreCase = true) -> "jpg"
            else -> "png"
        }

        val outDir = File(mediaDir, "generated").apply { mkdirs() }
        val fileName = "gen_${System.currentTimeMillis()}_${index}.$ext"
        val outFile = File(outDir, fileName)
        outFile.writeBytes(bytes)

        MessagePart.Image(
            filePath = outFile.absolutePath,
            mimeType = image.mimeType,
            fileName = outFile.name,
            sizeBytes = bytes.size.toLong()
        )
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
        modelName: String? = null,
        providerId: String? = null,
        shouldRecordTokens: Boolean = true,
        regexRules: List<RegexRuleData> = emptyList(),
        onStreamingUpdate: suspend (Message) -> Unit
    ): Message {
        var currentContent = ""
        var rawContent = ""  // 原始内容（未经正则处理）
        var inputTokens = 0
        var outputTokens = 0
        var tokensPerSecond = 0.0
        var firstTokenLatency = 0L
        var pendingToolCalls: List<ToolCallInfo>? = null
        val allParts = mutableListOf<MessagePart>()  // 存储所有消息部分

        // 创建正则流处理器
        val regexProcessor = if (regexRules.isNotEmpty()) {
            RegexStreamProcessor(regexRules)
        } else {
            null
        }

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
            rawContent = ""

            flow.collect { chunk ->
                when (chunk) {
                    is StreamChunk.Content -> {
                        rawContent += chunk.text
                        // 使用正则处理器处理流式内容
                        val processedText = if (regexProcessor != null) {
                            regexProcessor.processChunk(chunk.text)
                        } else {
                            chunk.text
                        }
                        currentContent += processedText

                        // 流式更新：显示文本内容和已执行的工具结果
                        val streamingParts = mutableListOf<MessagePart>()
                        if (currentContent.isNotEmpty()) {
                            streamingParts.add(MessagePart.Text(currentContent))
                        }
                        streamingParts.addAll(allParts.filterIsInstance<MessagePart.ToolResult>())

                        onStreamingUpdate(Message(
                            id = assistantId,
                            chatId = chatId,
                            role = MessageRole.ASSISTANT,
                            parts = streamingParts,
                            isStreaming = true
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
                println("=== 执行工具 ===")
                println("工具名称: ${toolCall.name}")
                println("工具ID: ${toolCall.id}")
                println("参数: ${toolCall.arguments}")

                // 添加 ToolCall Part
                allParts.add(MessagePart.ToolCall(
                    toolCallId = toolCall.id,
                    toolName = toolCall.name,
                    arguments = toolCall.arguments.ifBlank { "{}" }
                ))

                val tool = tools.find { it.name == toolCall.name }
                val startTime = System.currentTimeMillis()
                // 安全的参数字符串：空白时默认为 "{}"
                val safeArgs = toolCall.arguments.ifBlank { "{}" }
                val result = if (tool != null) {
                    try {
                        val argsObject = JSONObject(safeArgs)
                        println("开始执行工具...")
                        val resultObject = tool.execute(argsObject)
                        println("工具执行完成")
                        resultObject.toString()
                    } catch (e: Exception) {
                        println("工具执行异常: ${e.message}")
                        e.printStackTrace()
                        """{"error": "${e.message?.replace("\"", "\\\"") ?: "执行失败"}"}"""
                    }
                } else {
                    println("未找到工具: ${toolCall.name}")
                    """{"error": "未知工具: ${toolCall.name}"}"""
                }
                val executionTime = System.currentTimeMillis() - startTime

                println("工具结果: $result")
                println("执行耗时: ${executionTime}ms")

                // 添加 ToolResult Part
                allParts.add(MessagePart.ToolResult(
                    toolCallId = toolCall.id,
                    toolName = toolCall.name,
                    arguments = safeArgs,
                    result = result,
                    isError = result.contains("\"error\""),
                    executionTimeMs = executionTime
                ))
                println("当前 Parts 总数: ${allParts.size}")

                // 添加工具结果到消息历史
                messages.add(ChatMessage(
                    role = ProviderMessageRole.TOOL,
                    content = result,
                    toolCallId = toolCall.id,
                    name = toolCall.name
                ))
            }

            // 更新UI显示工具执行状态
            val streamingParts = mutableListOf<MessagePart>()
            if (currentContent.isNotEmpty()) {
                streamingParts.add(MessagePart.Text(currentContent))
            }
            streamingParts.addAll(allParts.filterIsInstance<MessagePart.ToolResult>())

            onStreamingUpdate(Message(
                id = assistantId,
                chatId = chatId,
                role = MessageRole.ASSISTANT,
                parts = streamingParts,
                isStreaming = true
            ))

            pendingToolCalls = null
        }

        // 流结束时，刷新正则处理器的剩余缓冲区
        if (regexProcessor != null) {
            val remainingContent = regexProcessor.flush()
            if (remainingContent.isNotEmpty()) {
                currentContent += remainingContent
            }
        }

        // 构建最终的 parts 列表
        val finalParts = mutableListOf<MessagePart>()
        if (currentContent.isNotEmpty()) {
            finalParts.add(MessagePart.Text(currentContent))
        }
        finalParts.addAll(allParts.filterIsInstance<MessagePart.ToolCall>())
        finalParts.addAll(allParts.filterIsInstance<MessagePart.ToolResult>())

        // 保存最终消息到数据库
        println("=== 准备保存最终消息 ===")
        println("Parts 数量: ${finalParts.size}")
        finalParts.forEach { part ->
            when (part) {
                is MessagePart.Text -> println("  文本: ${part.content.take(50)}...")
                is MessagePart.ToolCall -> println("  工具调用: ${part.toolName}")
                is MessagePart.ToolResult -> println("  工具结果: ${part.toolName}, ID: ${part.toolCallId}")
                is MessagePart.Image -> println("  图片: ${part.fileName ?: part.filePath}")
                is MessagePart.Video -> println("  视频: ${part.fileName ?: part.filePath}")
            }
        }

        val finalMessage = Message(
            id = assistantId,
            chatId = chatId,
            role = MessageRole.ASSISTANT,
            parts = finalParts,
            isStreaming = false,
            inputTokens = if (shouldRecordTokens) inputTokens else 0,
            outputTokens = if (shouldRecordTokens) outputTokens else 0,
            tokensPerSecond = if (shouldRecordTokens) tokensPerSecond else 0.0,
            firstTokenLatency = if (shouldRecordTokens) firstTokenLatency else 0,
            modelName = modelName,
            providerId = if (shouldRecordTokens) providerId else null
        )

        println("finalMessage.parts 数量: ${finalMessage.parts.size}")
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
                val msg = it.toMessage()
                ChatMessage(
                    role = when (it.role) {
                        "user" -> ProviderMessageRole.USER
                        "assistant" -> ProviderMessageRole.ASSISTANT
                        else -> ProviderMessageRole.SYSTEM
                    },
                    content = msg.getTextContent()
                )
            })

            val streamingMessage = originalMessage.copy(parts = emptyList(), isStreaming = true)
            emit(Result.Success(streamingMessage))

            // 转换工具定义
            val toolDefinitions = config?.tools?.map { it.toToolDefinition() } ?: emptyList()

            // 执行带工具调用的聊天流程
            val finalResult = executeWithToolCallsForRegenerate(
                messages = messages,
                tools = config?.tools ?: emptyList(),
                toolDefinitions = toolDefinitions,
                regexRules = config?.regexRules ?: emptyList(),
                onStreamingUpdate = { parts ->
                    emit(Result.Success(originalMessage.copy(
                        parts = parts,
                        isStreaming = true
                    )))
                }
            )

            // 创建新变体
            val newVariant = MessageVariant(
                content = finalResult.first,
                inputTokens = finalResult.second,
                outputTokens = finalResult.third
            )

            val existingVariants = originalMessage.variants.toMutableList()
            if (existingVariants.isEmpty()) {
                existingVariants.add(MessageVariant(
                    content = originalMessage.getTextContent(),
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
                parts = finalResult.fourth,
                isStreaming = false,
                inputTokens = finalResult.second,
                outputTokens = finalResult.third,
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
     * 返回: (文本内容, inputTokens, outputTokens, 完整的parts列表)
     */
    private suspend fun executeWithToolCallsForRegenerate(
        messages: MutableList<ChatMessage>,
        tools: List<Tool>,
        toolDefinitions: List<ToolDefinition>,
        regexRules: List<RegexRuleData> = emptyList(),
        onStreamingUpdate: suspend (List<MessagePart>) -> Unit
    ): Quadruple<String, Int, Int, List<MessagePart>> {
        var currentContent = ""
        var rawContent = ""  // 原始内容（未经正则处理）
        var inputTokens = 0
        var outputTokens = 0
        var pendingToolCalls: List<ToolCallInfo>? = null
        val allParts = mutableListOf<MessagePart>()

        // 创建正则流处理器
        val regexProcessor = if (regexRules.isNotEmpty()) {
            RegexStreamProcessor(regexRules)
        } else {
            null
        }

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
            rawContent = ""

            flow.collect { chunk ->
                when (chunk) {
                    is StreamChunk.Content -> {
                        rawContent += chunk.text
                        // 使用正则处理器处理流式内容
                        val processedText = if (regexProcessor != null) {
                            regexProcessor.processChunk(chunk.text)
                        } else {
                            chunk.text
                        }
                        currentContent += processedText

                        // 流式更新
                        val streamingParts = mutableListOf<MessagePart>()
                        if (currentContent.isNotEmpty()) {
                            streamingParts.add(MessagePart.Text(currentContent))
                        }
                        streamingParts.addAll(allParts.filterIsInstance<MessagePart.ToolResult>())
                        onStreamingUpdate(streamingParts)
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
                // 添加 ToolCall Part
                allParts.add(MessagePart.ToolCall(
                    toolCallId = toolCall.id,
                    toolName = toolCall.name,
                    arguments = toolCall.arguments.ifBlank { "{}" }
                ))

                val tool = tools.find { it.name == toolCall.name }
                val startTime = System.currentTimeMillis()
                val safeArgs = toolCall.arguments.ifBlank { "{}" }
                val result = if (tool != null) {
                    try {
                        val argsObject = JSONObject(safeArgs)
                        val resultObject = tool.execute(argsObject)
                        resultObject.toString()
                    } catch (e: Exception) {
                        """{"error": "${e.message?.replace("\"", "\\\"") ?: "执行失败"}"}"""
                    }
                } else {
                    """{"error": "未知工具: ${toolCall.name}"}"""
                }
                val executionTime = System.currentTimeMillis() - startTime

                // 添加 ToolResult Part
                allParts.add(MessagePart.ToolResult(
                    toolCallId = toolCall.id,
                    toolName = toolCall.name,
                    arguments = safeArgs,
                    result = result,
                    isError = result.contains("\"error\""),
                    executionTimeMs = executionTime
                ))

                messages.add(ChatMessage(
                    role = ProviderMessageRole.TOOL,
                    content = result,
                    toolCallId = toolCall.id,
                    name = toolCall.name
                ))
            }

            // 流式更新
            val streamingParts = mutableListOf<MessagePart>()
            if (currentContent.isNotEmpty()) {
                streamingParts.add(MessagePart.Text(currentContent))
            }
            streamingParts.addAll(allParts.filterIsInstance<MessagePart.ToolResult>())
            onStreamingUpdate(streamingParts)

            pendingToolCalls = null
        }

        // 流结束时，刷新正则处理器的剩余缓冲区
        if (regexProcessor != null) {
            val remainingContent = regexProcessor.flush()
            if (remainingContent.isNotEmpty()) {
                currentContent += remainingContent
            }
        }

        // 构建最终 parts
        val finalParts = mutableListOf<MessagePart>()
        if (currentContent.isNotEmpty()) {
            finalParts.add(MessagePart.Text(currentContent))
        }
        finalParts.addAll(allParts.filterIsInstance<MessagePart.ToolCall>())
        finalParts.addAll(allParts.filterIsInstance<MessagePart.ToolResult>())

        return Quadruple(currentContent, inputTokens, outputTokens, finalParts)
    }

    // 辅助数据类：四元组
    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

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

    override suspend fun deleteMessage(messageId: String): Result<Unit> {
        return try {
            messageDao.deleteMessage(messageId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    private suspend fun getMessagesForAI(chatId: String, systemPrompt: String?): MutableList<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()

        // 添加系统提示
        systemPrompt?.let {
            if (it.isNotBlank()) {
                messages.add(ChatMessage(role = ProviderMessageRole.SYSTEM, content = it))
            }
        }

        // 添加历史消息（支持多模态）
        for (entity in messageDao.getMessagesByChatIdOnce(chatId)) {
            val message = entity.toMessage()
            val providerMessage = when (entity.role) {
                "user" -> buildUserChatMessage(message)
                "assistant" -> buildAssistantChatMessage(message)
                "tool" -> ChatMessage(
                    role = ProviderMessageRole.TOOL,
                    content = message.getTextContent()
                )
                else -> ChatMessage(
                    role = ProviderMessageRole.SYSTEM,
                    content = message.getTextContent()
                )
            }
            messages.add(providerMessage)
        }

        return messages
    }

    private fun buildAssistantChatMessage(message: Message): ChatMessage {
        val text = buildString {
            val base = message.getTextContent()
            if (base.isNotBlank()) append(base)

            val images = message.parts.filterIsInstance<MessagePart.Image>()
            val videos = message.parts.filterIsInstance<MessagePart.Video>()
            if (images.isNotEmpty()) {
                if (isNotEmpty()) append("\n")
                append("（包含 ${images.size} 张图片）")
            }
            if (videos.isNotEmpty()) {
                if (isNotEmpty()) append("\n")
                append("（包含 ${videos.size} 个视频）")
            }
        }

        return ChatMessage(
            role = ProviderMessageRole.ASSISTANT,
            content = text
        )
    }

    private suspend fun buildUserChatMessage(message: Message): ChatMessage {
        val textContent = message.getTextContent()
        val contentParts = buildUserContentParts(message)
        return ChatMessage(
            role = ProviderMessageRole.USER,
            content = textContent,
            contentParts = contentParts
        )
    }

    private suspend fun buildUserContentParts(message: Message): List<MessageContent>? = withContext(Dispatchers.IO) {
        val parts = mutableListOf<MessageContent>()

        val text = message.getTextContent()
        if (text.isNotBlank()) {
            parts.add(MessageContent.Text(text))
        }

        // 图片
        message.parts.filterIsInstance<MessagePart.Image>().forEach { image ->
            val filePath = image.filePath
            if (filePath.isBlank()) return@forEach
            val file = File(filePath)
            if (!file.exists() || !file.isFile) {
                parts.add(
                    MessageContent.Text("（图片附件已丢失：${image.fileName ?: filePath}）")
                )
                return@forEach
            }
            val bytes = file.readBytes()
            val encoded = Base64.getEncoder().encodeToString(bytes)
            parts.add(
                MessageContent.Image(
                    base64Data = encoded,
                    mimeType = image.mimeType
                )
            )
        }

        // 视频（仅 Gemini 支持；其他服务商降级为文字提示）
        message.parts.filterIsInstance<MessagePart.Video>().forEach { video ->
            val filePath = video.filePath
            if (filePath.isBlank()) return@forEach

            if (providerType != AIProviderFactory.ProviderType.GEMINI) {
                parts.add(
                    MessageContent.Text("（视频附件仅 Gemini 支持，已省略：${video.fileName ?: filePath}）")
                )
                return@forEach
            }

            val file = File(filePath)
            if (!file.exists() || !file.isFile) {
                parts.add(
                    MessageContent.Text("（视频附件已丢失：${video.fileName ?: filePath}）")
                )
                return@forEach
            }
            val bytes = file.readBytes()
            val encoded = Base64.getEncoder().encodeToString(bytes)
            parts.add(
                MessageContent.Video(
                    base64Data = encoded,
                    mimeType = video.mimeType
                )
            )
        }

        parts.takeIf { it.isNotEmpty() }
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
        println("=== 保存消息到数据库 ===")
        println("消息ID: ${message.id}")
        println("角色: ${message.role}")
        println("Parts 数量: ${message.parts.size}")
        message.parts.forEach { part ->
            when (part) {
                is MessagePart.Text -> println("  - 文本: ${part.content.take(50)}...")
                is MessagePart.ToolCall -> println("  - 工具调用: ${part.toolName}")
                is MessagePart.ToolResult -> println("  - 工具结果: ${part.toolName}, 错误: ${part.isError}")
                is MessagePart.Image -> println("  - 图片: ${part.fileName ?: part.filePath}")
                is MessagePart.Video -> println("  - 视频: ${part.fileName ?: part.filePath}")
            }
        }

        val entity = MessageEntity(
            id = message.id,
            chatId = message.chatId,
            role = message.role.name.lowercase(),
            partsJson = MessagePartSerializer.serializeParts(message.parts),
            timestamp = message.timestamp,
            inputTokens = message.inputTokens,
            outputTokens = message.outputTokens,
            tokensPerSecond = message.tokensPerSecond,
            firstTokenLatency = message.firstTokenLatency,
            modelName = message.modelName,
            providerId = message.providerId,
            variantsJson = if (message.variants.isNotEmpty()) variantsToJson(message.variants) else null,
            selectedVariantIndex = message.selectedVariantIndex
        )

        println("partsJson 长度: ${entity.partsJson.length}")
        messageDao.insertMessage(entity)
        println("=== 消息保存完成 ===")
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

        val parts = MessagePartSerializer.deserializeParts(partsJson)
        println("=== 加载消息 ===")
        println("消息ID: $id")
        println("Parts 数量: ${parts.size}")

        return Message(
            id = id,
            chatId = chatId,
            role = MessageRole.valueOf(role.uppercase()),
            parts = parts,
            timestamp = timestamp,
            isStreaming = false,
            inputTokens = currentVariant?.inputTokens ?: inputTokens,
            outputTokens = currentVariant?.outputTokens ?: outputTokens,
            tokensPerSecond = currentVariant?.tokensPerSecond ?: tokensPerSecond,
            firstTokenLatency = currentVariant?.firstTokenLatency ?: firstTokenLatency,
            modelName = modelName,
            providerId = providerId,
            variants = variants,
            selectedVariantIndex = selectedVariantIndex
        )
    }
}
