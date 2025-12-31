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
import com.tchat.data.repository.ChatRepository
import com.tchat.data.repository.MessageResult
import com.tchat.network.provider.AIProvider
import com.tchat.network.provider.ChatMessage
import com.tchat.network.provider.StreamChunk
import com.tchat.network.provider.MessageRole as ProviderMessageRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 聊天仓库实现
 *
 * 借鉴cherry-studio的设计：
 * - 使用数据库持久化存储
 * - 自动标题生成（取首条消息前50字符）
 * - 流式消息完成后保存到数据库
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
            chatDao.deleteChat(chatId)  // 消息会级联删除
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun sendMessage(chatId: String, content: String): Flow<Result<Message>> = flow {
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

            // 自动生成标题（借鉴cherry-studio：首次消息时用前50字符作为标题）
            autoGenerateTitle(chatId, content)

            // 获取聊天历史
            val messages = getMessagesForAI(chatId)

            // 创建 AI 消息占位符（带流式标志）
            val assistantId = UUID.randomUUID().toString()
            val initialAssistantMessage = Message(
                id = assistantId,
                chatId = chatId,
                content = "",
                role = MessageRole.ASSISTANT,
                isStreaming = true
            )
            emit(Result.Success(initialAssistantMessage))

            // 流式接收 AI 响应
            var assistantContent = ""

            aiProvider.streamChat(messages).collect { chunk ->
                when (chunk) {
                    is StreamChunk.Content -> {
                        assistantContent += chunk.text
                        val streamingMessage = Message(
                            id = assistantId,
                            chatId = chatId,
                            content = assistantContent,
                            role = MessageRole.ASSISTANT,
                            isStreaming = true
                        )
                        emit(Result.Success(streamingMessage))
                    }
                    is StreamChunk.Done -> {
                        // 流结束，保存到数据库，携带统计信息
                        val finalMessage = Message(
                            id = assistantId,
                            chatId = chatId,
                            content = assistantContent,
                            role = MessageRole.ASSISTANT,
                            isStreaming = false,
                            inputTokens = chunk.inputTokens,
                            outputTokens = chunk.outputTokens,
                            tokensPerSecond = chunk.tokensPerSecond,
                            firstTokenLatency = chunk.firstTokenLatency
                        )
                        saveMessageToDb(finalMessage)
                        chatDao.updateChatTimestamp(chatId, System.currentTimeMillis())
                        emit(Result.Success(finalMessage))
                    }
                    is StreamChunk.Error -> {
                        // 发生错误时也保存消息
                        if (assistantContent.isNotEmpty()) {
                            val errorMessage = Message(
                                id = assistantId,
                                chatId = chatId,
                                content = assistantContent,
                                role = MessageRole.ASSISTANT,
                                isStreaming = false
                            )
                            saveMessageToDb(errorMessage)
                        }
                        emit(Result.Error(chunk.error))
                    }
                }
            }
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

    /**
     * 发送消息到新聊天（懒创建）
     *
     * 只有在用户真正发送消息时才创建聊天记录
     * 返回包含真实chatId的消息结果
     */
    override suspend fun sendMessageToNewChat(content: String): Flow<Result<MessageResult>> = flow {
        try {
            // 1. 创建新聊天，使用首条消息的前50字符作为标题
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

            // 2. 添加用户消息
            val userMessage = Message(
                id = UUID.randomUUID().toString(),
                chatId = chatId,
                content = content,
                role = MessageRole.USER
            )
            saveMessageToDb(userMessage)
            emit(Result.Success(MessageResult(chatId, userMessage)))

            // 3. 获取聊天历史（此时只有用户消息）
            val messages = listOf(
                ChatMessage(
                    role = ProviderMessageRole.USER,
                    content = content
                )
            )

            // 4. 创建 AI 消息占位符（带流式标志）
            val assistantId = UUID.randomUUID().toString()
            val initialAssistantMessage = Message(
                id = assistantId,
                chatId = chatId,
                content = "",
                role = MessageRole.ASSISTANT,
                isStreaming = true
            )
            emit(Result.Success(MessageResult(chatId, initialAssistantMessage)))

            // 5. 流式接收 AI 响应
            var assistantContent = ""

            aiProvider.streamChat(messages).collect { chunk ->
                when (chunk) {
                    is StreamChunk.Content -> {
                        assistantContent += chunk.text
                        val streamingMessage = Message(
                            id = assistantId,
                            chatId = chatId,
                            content = assistantContent,
                            role = MessageRole.ASSISTANT,
                            isStreaming = true
                        )
                        emit(Result.Success(MessageResult(chatId, streamingMessage)))
                    }
                    is StreamChunk.Done -> {
                        // 流结束，保存到数据库，携带统计信息
                        val finalMessage = Message(
                            id = assistantId,
                            chatId = chatId,
                            content = assistantContent,
                            role = MessageRole.ASSISTANT,
                            isStreaming = false,
                            inputTokens = chunk.inputTokens,
                            outputTokens = chunk.outputTokens,
                            tokensPerSecond = chunk.tokensPerSecond,
                            firstTokenLatency = chunk.firstTokenLatency
                        )
                        saveMessageToDb(finalMessage)
                        chatDao.updateChatTimestamp(chatId, System.currentTimeMillis())
                        emit(Result.Success(MessageResult(chatId, finalMessage)))
                    }
                    is StreamChunk.Error -> {
                        // 发生错误时也保存消息
                        if (assistantContent.isNotEmpty()) {
                            val errorMessage = Message(
                                id = assistantId,
                                chatId = chatId,
                                content = assistantContent,
                                role = MessageRole.ASSISTANT,
                                isStreaming = false
                            )
                            saveMessageToDb(errorMessage)
                        }
                        emit(Result.Error(chunk.error))
                    }
                }
            }
        } catch (e: Exception) {
            emit(Result.Error(e))
        }
    }

    /**
     * 重新生成 AI 回复
     */
    override suspend fun regenerateMessage(
        chatId: String,
        userMessageId: String,
        aiMessageId: String
    ): Flow<Result<Message>> = flow {
        try {
            // 1. 获取原 AI 消息
            val originalEntity = messageDao.getMessageById(aiMessageId)
                ?: throw Exception("消息不存在")
            val originalMessage = originalEntity.toMessage()

            // 2. 获取到用户消息为止的聊天历史（不包括之后的消息）
            val allMessages = messageDao.getMessagesByChatIdOnce(chatId)
            val userMessageIndex = allMessages.indexOfFirst { it.id == userMessageId }
            if (userMessageIndex < 0) throw Exception("用户消息不存在")

            val historyMessages = allMessages.take(userMessageIndex + 1).map {
                ChatMessage(
                    role = when (it.role) {
                        "user" -> ProviderMessageRole.USER
                        "assistant" -> ProviderMessageRole.ASSISTANT
                        else -> ProviderMessageRole.SYSTEM
                    },
                    content = it.content
                )
            }

            // 3. 发送流式消息开始标记
            val streamingMessage = originalMessage.copy(
                content = "",
                isStreaming = true
            )
            emit(Result.Success(streamingMessage))

            // 4. 流式接收新的 AI 响应
            var newContent = ""

            aiProvider.streamChat(historyMessages).collect { chunk ->
                when (chunk) {
                    is StreamChunk.Content -> {
                        newContent += chunk.text
                        emit(Result.Success(originalMessage.copy(
                            content = newContent,
                            isStreaming = true
                        )))
                    }
                    is StreamChunk.Done -> {
                        // 5. 创建新变体
                        val newVariant = MessageVariant(
                            content = newContent,
                            inputTokens = chunk.inputTokens,
                            outputTokens = chunk.outputTokens,
                            tokensPerSecond = chunk.tokensPerSecond,
                            firstTokenLatency = chunk.firstTokenLatency
                        )

                        // 6. 更新变体列表
                        val existingVariants = originalMessage.variants.toMutableList()

                        // 如果是第一次添加变体，先把原内容作为第一个变体
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

                        // 7. 更新数据库
                        val variantsJson = variantsToJson(existingVariants)
                        messageDao.updateMessageVariants(aiMessageId, variantsJson, newSelectedIndex)

                        // 8. 返回更新后的消息
                        val finalMessage = originalMessage.copy(
                            content = newContent,
                            isStreaming = false,
                            inputTokens = chunk.inputTokens,
                            outputTokens = chunk.outputTokens,
                            tokensPerSecond = chunk.tokensPerSecond,
                            firstTokenLatency = chunk.firstTokenLatency,
                            variants = existingVariants,
                            selectedVariantIndex = newSelectedIndex
                        )
                        emit(Result.Success(finalMessage))
                    }
                    is StreamChunk.Error -> {
                        emit(Result.Error(chunk.error))
                    }
                }
            }
        } catch (e: Exception) {
            emit(Result.Error(e))
        }
    }

    /**
     * 选择消息的变体
     */
    override suspend fun selectVariant(messageId: String, variantIndex: Int): Result<Unit> {
        return try {
            messageDao.updateSelectedVariant(messageId, variantIndex)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * 获取单条消息
     */
    override suspend fun getMessageById(messageId: String): Message? {
        return messageDao.getMessageById(messageId)?.toMessage()
    }

    /**
     * 获取用于 AI 请求的消息列表
     * 只使用每条消息当前选中的变体内容
     */
    private suspend fun getMessagesForAI(chatId: String): List<ChatMessage> {
        return messageDao.getMessagesByChatIdOnce(chatId).map { entity ->
            val message = entity.toMessage()
            ChatMessage(
                role = when (entity.role) {
                    "user" -> ProviderMessageRole.USER
                    "assistant" -> ProviderMessageRole.ASSISTANT
                    else -> ProviderMessageRole.SYSTEM
                },
                content = message.getCurrentContent()
            )
        }
    }

    /**
     * 自动生成标题
     *
     * 借鉴cherry-studio的设计：
     * - 如果标题是默认标题，用首条用户消息的前50字符作为标题
     * - 如果标题已手动编辑过，则不自动更新
     */
    private suspend fun autoGenerateTitle(chatId: String, firstUserMessage: String) {
        try {
            val chat = chatDao.getChatById(chatId) ?: return

            // 如果标题已手动编辑过，不自动更新
            if (chat.isNameManuallyEdited) return

            // 如果是默认标题（"新对话"），使用首条消息的前50字符
            if (chat.title == "新对话" || chat.title.isEmpty()) {
                val newTitle = firstUserMessage.take(50).let {
                    if (firstUserMessage.length > 50) "$it..." else it
                }
                chatDao.updateChatTitle(chatId, newTitle, System.currentTimeMillis())
            }
        } catch (e: Exception) {
            // 忽略标题生成错误
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
            selectedVariantIndex = message.selectedVariantIndex
        )
        messageDao.insertMessage(entity)
    }

    // JSON 序列化/反序列化
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

    // 扩展函数：Entity -> Model 转换
    private fun ChatEntity.toChat() = Chat(
        id = id,
        title = title,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun MessageEntity.toMessage(): Message {
        val variants = jsonToVariants(variantsJson)

        // 如果有变体且选中索引有效，使用变体的内容和统计信息
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
            selectedVariantIndex = selectedVariantIndex
        )
    }
}
