package com.tchat.data.repository

import com.tchat.core.util.Result
import com.tchat.data.model.Chat
import com.tchat.data.model.Message
import com.tchat.data.tool.Tool
import com.tchat.data.util.RegexRuleData
import kotlinx.coroutines.flow.Flow

/**
 * 聊天消息结果，包含chatId用于新建聊天时返回真实ID
 */
data class MessageResult(
    val chatId: String,
    val message: Message
)

/**
 * 聊天配置，包含工具和系统提示等
 */
data class ChatConfig(
    val systemPrompt: String? = null,
    val tools: List<Tool> = emptyList(),
    val temperature: Float? = null,
    val topP: Float? = null,
    val maxTokens: Int? = null,
    val modelName: String? = null,  // 模型名称（用于统计）
    val providerId: String? = null,  // 提供商ID（用于按提供商统计token）
    val shouldRecordTokens: Boolean = true,  // 是否记录token统计
    val regexRules: List<RegexRuleData> = emptyList(),  // 正则规则（用于流式处理）
    val enabledSkillIds: List<String> = emptyList()  // 启用的技能ID列表
)

interface ChatRepository {
    fun getAllChats(): Flow<List<Chat>>
    fun getChatById(chatId: String): Flow<Chat?>
    fun getMessagesByChatId(chatId: String): Flow<List<Message>>
    suspend fun createChat(title: String): Result<Chat>
    suspend fun updateChatTitle(chatId: String, title: String): Result<Unit>
    suspend fun deleteChat(chatId: String): Result<Unit>

    /**
     * 发送消息（支持工具调用）
     * @param chatId 聊天ID
     * @param content 消息内容
     * @param config 聊天配置（可选）
     */
    suspend fun sendMessage(
        chatId: String,
        content: String,
        config: ChatConfig? = null,
        mediaParts: List<com.tchat.data.model.MessagePart> = emptyList()
    ): Flow<Result<Message>>

    suspend fun addMessage(message: Message): Result<Unit>

    /**
     * 发送消息到新聊天（懒创建，支持工具调用）
     * @param content 消息内容
     * @param config 聊天配置（可选）
     */
    suspend fun sendMessageToNewChat(
        content: String,
        config: ChatConfig? = null,
        mediaParts: List<com.tchat.data.model.MessagePart> = emptyList()
    ): Flow<Result<MessageResult>>

    /**
     * 生成图片（如果当前服务商支持）
     */
    suspend fun generateImage(
        chatId: String,
        prompt: String,
        config: ChatConfig? = null
    ): Flow<Result<Message>>

    /**
     * 生成图片到新聊天（懒创建）
     */
    suspend fun generateImageToNewChat(
        prompt: String,
        config: ChatConfig? = null
    ): Flow<Result<MessageResult>>

    /**
     * 重新生成 AI 回复
     * @param chatId 聊天ID
     * @param userMessageId 用户消息ID（触发重新生成的消息）
     * @param aiMessageId 要添加变体的 AI 消息ID
     * @param config 聊天配置（可选）
     */
    suspend fun regenerateMessage(
        chatId: String,
        userMessageId: String,
        aiMessageId: String,
        config: ChatConfig? = null
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

    /**
     * 删除单条消息
     */
    suspend fun deleteMessage(messageId: String): Result<Unit>
}
