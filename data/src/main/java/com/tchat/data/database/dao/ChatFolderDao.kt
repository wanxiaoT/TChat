package com.tchat.data.database.dao

import androidx.room.*
import com.tchat.data.database.entity.ChatFolderEntity
import com.tchat.data.database.entity.ChatFolderRelationEntity
import kotlinx.coroutines.flow.Flow

/**
 * 聊天文件夹DAO
 */
@Dao
interface ChatFolderDao {
    /**
     * 插入文件夹
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: ChatFolderEntity)

    /**
     * 批量插入文件夹
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(folders: List<ChatFolderEntity>)

    /**
     * 更新文件夹
     */
    @Update
    suspend fun update(folder: ChatFolderEntity)

    /**
     * 删除文件夹
     */
    @Delete
    suspend fun delete(folder: ChatFolderEntity)

    /**
     * 根据ID删除文件夹
     */
    @Query("DELETE FROM chat_folders WHERE id = :folderId")
    suspend fun deleteById(folderId: String)

    /**
     * 获取所有文件夹
     */
    @Query("SELECT * FROM chat_folders ORDER BY sort_order ASC, name ASC")
    fun getAllFlow(): Flow<List<ChatFolderEntity>>

    /**
     * 获取所有文件夹（一次性）
     */
    @Query("SELECT * FROM chat_folders ORDER BY sort_order ASC, name ASC")
    suspend fun getAll(): List<ChatFolderEntity>

    /**
     * 根据ID获取文件夹
     */
    @Query("SELECT * FROM chat_folders WHERE id = :folderId")
    suspend fun getById(folderId: String): ChatFolderEntity?

    /**
     * 获取根文件夹（没有父文件夹）
     */
    @Query("SELECT * FROM chat_folders WHERE parentId IS NULL ORDER BY sort_order ASC, name ASC")
    fun getRootFoldersFlow(): Flow<List<ChatFolderEntity>>

    /**
     * 获取子文件夹
     */
    @Query("SELECT * FROM chat_folders WHERE parentId = :parentId ORDER BY sort_order ASC, name ASC")
    suspend fun getChildFolders(parentId: String): List<ChatFolderEntity>

    /**
     * 获取子文件夹（Flow）
     */
    @Query("SELECT * FROM chat_folders WHERE parentId = :parentId ORDER BY sort_order ASC, name ASC")
    fun getChildFoldersFlow(parentId: String): Flow<List<ChatFolderEntity>>

    /**
     * 检查文件夹名称是否存在（同一父文件夹下）
     */
    @Query("SELECT COUNT(*) FROM chat_folders WHERE name = :name AND parentId IS :parentId AND id != :excludeId")
    suspend fun checkNameExists(name: String, parentId: String?, excludeId: String = ""): Int

    /**
     * 添加聊天到文件夹
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addChatToFolder(relation: ChatFolderRelationEntity)

    /**
     * 从文件夹移除聊天
     */
    @Query("DELETE FROM chat_folder_relations WHERE chatId = :chatId AND folderId = :folderId")
    suspend fun removeChatFromFolder(chatId: String, folderId: String)

    /**
     * 移除聊天的所有文件夹关联
     */
    @Query("DELETE FROM chat_folder_relations WHERE chatId = :chatId")
    suspend fun removeChatFromAllFolders(chatId: String)

    /**
     * 获取文件夹中的所有聊天ID
     */
    @Query("SELECT chatId FROM chat_folder_relations WHERE folderId = :folderId ORDER BY addedAt DESC")
    suspend fun getChatIdsInFolder(folderId: String): List<String>

    /**
     * 获取文件夹中的所有聊天ID（Flow）
     */
    @Query("SELECT chatId FROM chat_folder_relations WHERE folderId = :folderId ORDER BY addedAt DESC")
    fun getChatIdsInFolderFlow(folderId: String): Flow<List<String>>

    /**
     * 获取聊天所在的文件夹ID
     */
    @Query("SELECT folderId FROM chat_folder_relations WHERE chatId = :chatId LIMIT 1")
    suspend fun getFolderIdByChat(chatId: String): String?

    /**
     * 获取文件夹中的聊天数量
     */
    @Query("SELECT COUNT(*) FROM chat_folder_relations WHERE folderId = :folderId")
    suspend fun getChatCountInFolder(folderId: String): Int

    /**
     * 删除文件夹时清理关联关系
     */
    @Query("DELETE FROM chat_folder_relations WHERE folderId = :folderId")
    suspend fun deleteRelationsByFolder(folderId: String)

    /**
     * 移动文件夹到新的父文件夹
     */
    @Query("UPDATE chat_folders SET parentId = :newParentId, updatedAt = :updatedAt WHERE id = :folderId")
    suspend fun moveFolder(folderId: String, newParentId: String?, updatedAt: Long = System.currentTimeMillis())

    /**
     * 批量更新文件夹排序
     */
    @Query("UPDATE chat_folders SET sort_order = :sortOrder WHERE id = :folderId")
    suspend fun updateOrder(folderId: String, sortOrder: Int)
}
