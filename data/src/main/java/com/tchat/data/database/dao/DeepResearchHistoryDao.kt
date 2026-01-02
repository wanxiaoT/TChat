package com.tchat.data.database.dao

import androidx.room.*
import com.tchat.data.database.entity.DeepResearchHistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * 深度研究历史记录 DAO
 */
@Dao
interface DeepResearchHistoryDao {

    @Query("SELECT * FROM deep_research_history ORDER BY endTime DESC")
    fun getAllHistory(): Flow<List<DeepResearchHistoryEntity>>

    @Query("SELECT * FROM deep_research_history ORDER BY endTime DESC LIMIT :limit")
    fun getRecentHistory(limit: Int): Flow<List<DeepResearchHistoryEntity>>

    @Query("SELECT * FROM deep_research_history WHERE id = :id")
    suspend fun getHistoryById(id: String): DeepResearchHistoryEntity?

    @Query("SELECT * FROM deep_research_history WHERE query LIKE '%' || :keyword || '%' ORDER BY endTime DESC")
    fun searchHistory(keyword: String): Flow<List<DeepResearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DeepResearchHistoryEntity)

    @Delete
    suspend fun delete(entity: DeepResearchHistoryEntity)

    @Query("DELETE FROM deep_research_history WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM deep_research_history")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM deep_research_history")
    suspend fun getCount(): Int
}
