package com.tchat.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tchat.data.database.entity.SkillEntity
import kotlinx.coroutines.flow.Flow

/**
 * 技能 DAO
 */
@Dao
interface SkillDao {

    /**
     * 获取所有技能（按优先级和创建时间排序）
     */
    @Query("SELECT * FROM skills ORDER BY priority DESC, createdAt DESC")
    fun getAllSkills(): Flow<List<SkillEntity>>

    /**
     * 获取所有技能（同步版本）
     */
    @Query("SELECT * FROM skills ORDER BY priority DESC, createdAt DESC")
    suspend fun getAllSkillsSync(): List<SkillEntity>

    /**
     * 获取所有启用的技能
     */
    @Query("SELECT * FROM skills WHERE enabled = 1 ORDER BY priority DESC")
    fun getEnabledSkills(): Flow<List<SkillEntity>>

    /**
     * 根据 ID 获取技能
     */
    @Query("SELECT * FROM skills WHERE id = :id")
    suspend fun getSkillById(id: String): SkillEntity?

    /**
     * 根据 ID 列表获取技能
     */
    @Query("SELECT * FROM skills WHERE id IN (:ids)")
    suspend fun getSkillsByIds(ids: List<String>): List<SkillEntity>

    /**
     * 根据名称获取技能
     */
    @Query("SELECT * FROM skills WHERE name = :name")
    suspend fun getSkillByName(name: String): SkillEntity?

    /**
     * 插入技能
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSkill(skill: SkillEntity)

    /**
     * 批量插入技能
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSkills(skills: List<SkillEntity>)

    /**
     * 更新技能
     */
    @Update
    suspend fun updateSkill(skill: SkillEntity)

    /**
     * 删除技能
     */
    @Delete
    suspend fun deleteSkill(skill: SkillEntity)

    /**
     * 根据 ID 删除技能
     */
    @Query("DELETE FROM skills WHERE id = :id")
    suspend fun deleteSkillById(id: String)

    /**
     * 删除所有非内置技能
     */
    @Query("DELETE FROM skills WHERE isBuiltIn = 0")
    suspend fun deleteAllCustomSkills()

    /**
     * 获取技能数量
     */
    @Query("SELECT COUNT(*) FROM skills")
    suspend fun getSkillCount(): Int

    /**
     * 获取启用的技能数量
     */
    @Query("SELECT COUNT(*) FROM skills WHERE enabled = 1")
    suspend fun getEnabledSkillCount(): Int
}
