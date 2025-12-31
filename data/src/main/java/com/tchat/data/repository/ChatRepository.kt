package com.tchat.data.repository

import com.tchat.core.util.Result
import com.tchat.data.model.Chat
import com.tchat.data.model.Message
import kotlinx.coroutines.flow.Flow

/**
 * 聊天消息结果，包含chatId用于新建聊天时返回真实ID
 */
data class MessageResult(
    val chatId: String,
    val message: Message
)

interface ChatRepository {
    fun getAllChats(): Flow<List<Chat>>
    fun getChatById(chatId: String): Flow<Chat?>
    fun getMessagesByChatId(chatId: String): Flow<List<Message>>
    suspend fun createChat(title: String): Result<Chat>
    suspend fun updateChatTitle(chatId: String, title: String): Result<Unit>
    suspend fun deleteChat(chatId: String): Result<Unit>
    suspend fun sendMessage(chatId: String, content: String): Flow<Result<Message>>
    suspend fun addMessage(message: Message): Result<Unit>

    /**
     * 发送消息到新聊天（懒创建）
     * 创建聊天并发送第一条消息，返回包含真实chatId的结果
     */
    suspend fun sendMessageToNewChat(content: String): Flow<Result<MessageResult>>

    /**
     * 重新生成 AI 回复
     * @param chatId 聊天ID
     * @param userMessageId 用户消息ID（触发重新生成的消息）
     * @param aiMessageId 要添加变体的 AI 消息ID
     */
    suspend fun regenerateMessage(
        chatId: String,
        userMessageId: String,
        aiMessageId: String
    ): Flow<Result<Message>>

    /**
     * 选择消息的变体
     * @param messageId 消息ID
     * @param variantIndex 变体索引
     */
    suspend fun selectVariant(messageId: String, variantIndex: Int): Result<Unit>

    /**
     * 获取单条消息
     */
    suspend fun getMessageById(messageId: String): Message?
}
