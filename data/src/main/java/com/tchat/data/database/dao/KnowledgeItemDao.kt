package com.tchat.data.database.dao

import androidx.room.*
import com.tchat.data.database.entity.KnowledgeItemEntity
import kotlinx.coroutines.flow.Flow

/**
 * 知识条目数据访问对象
 */
@Dao
interface KnowledgeItemDao {
    @Query("SELECT * FROM knowledge_items WHERE knowledgeBaseId = :baseId ORDER BY updatedAt DESC")
    fun getItemsByBaseId(baseId: String): Flow<List<KnowledgeItemEntity>>

    @Query("SELECT * FROM knowledge_items WHERE id = :id")
    suspend fun getItemById(id: String): KnowledgeItemEntity?

    @Query("SELECT * FROM knowledge_items WHERE knowledgeBaseId = :baseId")
    suspend fun getItemsByBaseIdSync(baseId: String): List<KnowledgeItemEntity>

    @Query("SELECT * FROM knowledge_items WHERE status = :status")
    suspend fun getItemsByStatus(status: String): List<KnowledgeItemEntity>

    @Query("SELECT * FROM knowledge_items WHERE knowledgeBaseId = :baseId AND status = :status")
    suspend fun getItemsByBaseIdAndStatus(baseId: String, status: String): List<KnowledgeItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: KnowledgeItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<KnowledgeItemEntity>)

    @Update
    suspend fun updateItem(item: KnowledgeItemEntity)

    @Query("UPDATE knowledge_items SET status = :status, errorMessage = :errorMessage, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateItemStatus(id: String, status: String, errorMessage: String? = null, updatedAt: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteItem(item: KnowledgeItemEntity)

    @Query("DELETE FROM knowledge_items WHERE id = :id")
    suspend fun deleteItemById(id: String)

    @Query("DELETE FROM knowledge_items WHERE knowledgeBaseId = :baseId")
    suspend fun deleteItemsByBaseId(baseId: String)

    @Query("SELECT COUNT(*) FROM knowledge_items WHERE knowledgeBaseId = :baseId")
    suspend fun getItemsCountByBaseId(baseId: String): Int

    @Query("SELECT COUNT(*) FROM knowledge_items WHERE knowledgeBaseId = :baseId AND status = :status")
    suspend fun getItemsCountByBaseIdAndStatus(baseId: String, status: String): Int
}
