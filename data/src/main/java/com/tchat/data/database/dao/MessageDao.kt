package com.tchat.data.database.dao

import androidx.room.*
import com.tchat.data.database.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

/**
 * 消息DAO
 */
@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessagesByChatId(chatId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    suspend fun getMessagesByChatIdOnce(chatId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("UPDATE messages SET partsJson = :partsJson WHERE id = :messageId")
    suspend fun updateMessageParts(messageId: String, partsJson: String)

    @Query("UPDATE messages SET variantsJson = :variantsJson, selectedVariantIndex = :selectedIndex WHERE id = :messageId")
    suspend fun updateMessageVariants(messageId: String, variantsJson: String?, selectedIndex: Int)

    @Query("UPDATE messages SET selectedVariantIndex = :selectedIndex WHERE id = :messageId")
    suspend fun updateSelectedVariant(messageId: String, selectedIndex: Int)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: String)

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteMessagesByChatId(chatId: String)

    // 统计查询
    @Query("SELECT SUM(inputTokens) FROM messages WHERE role = 'assistant'")
    suspend fun getTotalInputTokens(): Long?

    @Query("SELECT SUM(outputTokens) FROM messages WHERE role = 'assistant'")
    suspend fun getTotalOutputTokens(): Long?

    @Query("SELECT COUNT(*) FROM messages WHERE role = 'assistant'")
    suspend fun getTotalAssistantMessages(): Int

    @Query("SELECT modelName, COUNT(*) as count FROM messages WHERE role = 'assistant' AND modelName IS NOT NULL GROUP BY modelName")
    suspend fun getModelUsageStats(): List<ModelUsageStat>

    // 按提供商统计查询
    @Query("SELECT providerId, SUM(inputTokens) as inputTokens, SUM(outputTokens) as outputTokens, COUNT(*) as messageCount FROM messages WHERE role = 'assistant' AND providerId IS NOT NULL GROUP BY providerId")
    suspend fun getProviderUsageStats(): List<ProviderUsageStat>

    // 清空所有token统计数据
    @Query("UPDATE messages SET inputTokens = 0, outputTokens = 0, tokensPerSecond = 0.0, firstTokenLatency = 0 WHERE role = 'assistant'")
    suspend fun clearAllTokenStats()
}

/**
 * 模型使用统计数据类
 */
data class ModelUsageStat(
    val modelName: String,
    val count: Int
)

/**
 * 提供商使用统计数据类
 */
data class ProviderUsageStat(
    val providerId: String,
    val inputTokens: Long,
    val outputTokens: Long,
    val messageCount: Int
)
