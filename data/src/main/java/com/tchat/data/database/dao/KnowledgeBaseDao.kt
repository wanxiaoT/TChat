package com.tchat.data.database.dao

import androidx.room.*
import com.tchat.data.database.entity.KnowledgeBaseEntity
import kotlinx.coroutines.flow.Flow

/**
 * 知识库数据访问对象
 */
@Dao
interface KnowledgeBaseDao {
    @Query("SELECT * FROM knowledge_bases ORDER BY updatedAt DESC")
    fun getAllBases(): Flow<List<KnowledgeBaseEntity>>

    @Query("SELECT * FROM knowledge_bases WHERE id = :id")
    suspend fun getBaseById(id: String): KnowledgeBaseEntity?

    @Query("SELECT * FROM knowledge_bases WHERE id = :id")
    fun observeBaseById(id: String): Flow<KnowledgeBaseEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBase(base: KnowledgeBaseEntity)

    @Update
    suspend fun updateBase(base: KnowledgeBaseEntity)

    @Delete
    suspend fun deleteBase(base: KnowledgeBaseEntity)

    @Query("DELETE FROM knowledge_bases WHERE id = :id")
    suspend fun deleteBaseById(id: String)

    @Query("SELECT COUNT(*) FROM knowledge_bases")
    suspend fun getBasesCount(): Int
}
