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

    @Query("UPDATE messages SET content = :content WHERE id = :messageId")
    suspend fun updateMessageContent(messageId: String, content: String)

    @Query("UPDATE messages SET variantsJson = :variantsJson, selectedVariantIndex = :selectedIndex WHERE id = :messageId")
    suspend fun updateMessageVariants(messageId: String, variantsJson: String?, selectedIndex: Int)

    @Query("UPDATE messages SET selectedVariantIndex = :selectedIndex WHERE id = :messageId")
    suspend fun updateSelectedVariant(messageId: String, selectedIndex: Int)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: String)

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteMessagesByChatId(chatId: String)
}
