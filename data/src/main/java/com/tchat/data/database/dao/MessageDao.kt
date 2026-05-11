package com.tchat.data.database.dao

import androidx.room.*
import com.tchat.data.database.entity.MessageEntity
import com.tchat.data.database.entity.MessageSearchIndexEntity
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

    @Query(
        """
        SELECT * FROM (
            SELECT * FROM messages
            WHERE chatId = :chatId
            ORDER BY timestamp DESC
            LIMIT :limit
        ) ORDER BY timestamp ASC
        """
    )
    suspend fun getRecentMessagesByChatIdOnce(chatId: String, limit: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE chatId = :chatId AND timestamp <= :upToTimestamp ORDER BY timestamp ASC")
    suspend fun getMessagesUpTo(chatId: String, upToTimestamp: Long): List<MessageEntity>

    @Query(
        """
        SELECT * FROM (
            SELECT * FROM messages
            WHERE chatId = :chatId AND timestamp <= :upToTimestamp
            ORDER BY timestamp DESC
            LIMIT :limit
        ) ORDER BY timestamp ASC
        """
    )
    suspend fun getMessagesUpTo(chatId: String, upToTimestamp: Long, limit: Int): List<MessageEntity>

    @Query(
        """
        SELECT * FROM (
            SELECT * FROM messages
            WHERE chatId = :chatId
            ORDER BY timestamp DESC
            LIMIT :limit
        ) ORDER BY timestamp ASC
        """
    )
    fun observeRecentMessages(chatId: String, limit: Int): Flow<List<MessageEntity>>

    @Query(
        """
        SELECT * FROM (
            SELECT * FROM messages
            WHERE chatId = :chatId AND timestamp < :beforeTimestamp
            ORDER BY timestamp DESC
            LIMIT :limit
        ) ORDER BY timestamp ASC
        """
    )
    suspend fun getMessagesBefore(
        chatId: String,
        beforeTimestamp: Long,
        limit: Int
    ): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: String): MessageEntity?

    @Query(
        """
        SELECT
            messages.id AS messageId,
            messages.chatId AS chatId,
            chats.title AS chatTitle,
            messages.role AS role,
            messages.partsJson AS partsJson,
            messages.timestamp AS timestamp,
            messages.modelName AS modelName,
            messages.providerId AS providerId,
            messages.groupId AS groupId,
            messages.groupAssistantName AS groupAssistantName,
            messages.variantsJson AS variantsJson,
            messages.selectedVariantIndex AS selectedVariantIndex,
            messages.isBookmarked AS isBookmarked,
            messages.replyToMessageId AS replyToMessageId,
            messages.replyPreview AS replyPreview
        FROM messages
        INNER JOIN message_search_index ON message_search_index.messageId = messages.id
        INNER JOIN chats ON chats.id = messages.chatId
        WHERE
            message_search_index.normalizedText LIKE :pattern ESCAPE '\'
            OR LOWER(chats.title) LIKE :pattern ESCAPE '\'
        ORDER BY messages.timestamp DESC
        LIMIT :limit
        """
    )
    suspend fun searchMessages(pattern: String, limit: Int): List<MessageSearchRow>

    @Query(
        """
        SELECT
            messages.id AS messageId,
            messages.chatId AS chatId,
            chats.title AS chatTitle,
            messages.role AS role,
            messages.partsJson AS partsJson,
            messages.timestamp AS timestamp,
            messages.modelName AS modelName,
            messages.providerId AS providerId,
            messages.groupId AS groupId,
            messages.groupAssistantName AS groupAssistantName,
            messages.variantsJson AS variantsJson,
            messages.selectedVariantIndex AS selectedVariantIndex,
            messages.isBookmarked AS isBookmarked,
            messages.replyToMessageId AS replyToMessageId,
            messages.replyPreview AS replyPreview
        FROM messages
        INNER JOIN chats ON chats.id = messages.chatId
        WHERE messages.isBookmarked = 1
        ORDER BY messages.timestamp DESC
        LIMIT :limit
        """
    )
    fun observeBookmarkedMessages(limit: Int): Flow<List<MessageSearchRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSearchIndex(index: MessageSearchIndexEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSearchIndexes(indexes: List<MessageSearchIndexEntity>)

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("UPDATE messages SET partsJson = :partsJson WHERE id = :messageId")
    suspend fun updateMessageParts(messageId: String, partsJson: String)

    @Query("UPDATE messages SET variantsJson = :variantsJson, selectedVariantIndex = :selectedIndex WHERE id = :messageId")
    suspend fun updateMessageVariants(messageId: String, variantsJson: String?, selectedIndex: Int)

    @Query("UPDATE messages SET selectedVariantIndex = :selectedIndex WHERE id = :messageId")
    suspend fun updateSelectedVariant(messageId: String, selectedIndex: Int)

    @Query("UPDATE messages SET isBookmarked = :isBookmarked WHERE id = :messageId")
    suspend fun updateMessageBookmarked(messageId: String, isBookmarked: Boolean)

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

    @Query("SELECT COUNT(*) FROM messages WHERE groupId = :groupId AND role = 'assistant'")
    suspend fun getAssistantMessageCountByGroup(groupId: String): Int

    @Query("SELECT groupAssistantId, COUNT(*) AS count FROM messages WHERE groupId = :groupId AND role = 'assistant' AND groupAssistantId IS NOT NULL GROUP BY groupAssistantId")
    suspend fun getAssistantMessageCountsByGroup(groupId: String): List<GroupAssistantMessageCount>

    @Query("SELECT MAX(timestamp) FROM messages WHERE groupId = :groupId")
    suspend fun getLastGroupMessageTimestamp(groupId: String): Long?
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

/**
 * 全局消息搜索原始结果。
 */
data class MessageSearchRow(
    val messageId: String,
    val chatId: String,
    val chatTitle: String,
    val role: String,
    val partsJson: String,
    val timestamp: Long,
    val modelName: String?,
    val providerId: String?,
    val groupId: String?,
    val groupAssistantName: String?,
    val variantsJson: String?,
    val selectedVariantIndex: Int,
    val isBookmarked: Boolean,
    val replyToMessageId: String?,
    val replyPreview: String?
)

data class GroupAssistantMessageCount(
    val groupAssistantId: String,
    val count: Int
)
