package com.tchat.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tchat.data.database.entity.AssistantEntity
import kotlinx.coroutines.flow.Flow

/**
 * 助手数据访问对象
 */
@Dao
interface AssistantDao {
    /**
     * 获取所有助手
     */
    @Query("SELECT * FROM assistants ORDER BY createdAt DESC")
    fun getAllAssistants(): Flow<List<AssistantEntity>>

    /**
     * 根据ID获取助手
     */
    @Query("SELECT * FROM assistants WHERE id = :id")
    suspend fun getAssistantById(id: String): AssistantEntity?

    /**
     * 根据ID获取助手（Flow）
     */
    @Query("SELECT * FROM assistants WHERE id = :id")
    fun getAssistantByIdFlow(id: String): Flow<AssistantEntity?>

    /**
     * 插入助手
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssistant(assistant: AssistantEntity)

    /**
     * 更新助手
     */
    @Update
    suspend fun updateAssistant(assistant: AssistantEntity)

    /**
     * 删除助手
     */
    @Delete
    suspend fun deleteAssistant(assistant: AssistantEntity)

    /**
     * 根据ID删除助手
     */
    @Query("DELETE FROM assistants WHERE id = :id")
    suspend fun deleteAssistantById(id: String)

    /**
     * 获取助手数量
     */
    @Query("SELECT COUNT(*) FROM assistants")
    suspend fun getAssistantCount(): Int
}
