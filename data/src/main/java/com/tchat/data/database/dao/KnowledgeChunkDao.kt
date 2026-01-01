package com.tchat.data.database.dao

import androidx.room.*
import com.tchat.data.database.entity.KnowledgeChunkEntity
import kotlinx.coroutines.flow.Flow

/**
 * 知识块数据访问对象
 */
@Dao
interface KnowledgeChunkDao {
    @Query("SELECT * FROM knowledge_chunks WHERE itemId = :itemId ORDER BY chunkIndex")
    suspend fun getChunksByItemId(itemId: String): List<KnowledgeChunkEntity>

    @Query("SELECT * FROM knowledge_chunks WHERE knowledgeBaseId = :baseId")
    suspend fun getChunksByBaseId(baseId: String): List<KnowledgeChunkEntity>

    @Query("SELECT * FROM knowledge_chunks WHERE id = :id")
    suspend fun getChunkById(id: String): KnowledgeChunkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunk(chunk: KnowledgeChunkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<KnowledgeChunkEntity>)

    @Update
    suspend fun updateChunk(chunk: KnowledgeChunkEntity)

    @Delete
    suspend fun deleteChunk(chunk: KnowledgeChunkEntity)

    @Query("DELETE FROM knowledge_chunks WHERE id = :id")
    suspend fun deleteChunkById(id: String)

    @Query("DELETE FROM knowledge_chunks WHERE itemId = :itemId")
    suspend fun deleteChunksByItemId(itemId: String)

    @Query("DELETE FROM knowledge_chunks WHERE knowledgeBaseId = :baseId")
    suspend fun deleteChunksByBaseId(baseId: String)

    @Query("SELECT COUNT(*) FROM knowledge_chunks WHERE knowledgeBaseId = :baseId")
    suspend fun getChunksCountByBaseId(baseId: String): Int
}
