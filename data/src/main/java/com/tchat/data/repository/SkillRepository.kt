package com.tchat.data.repository

import com.tchat.data.model.Skill
import kotlinx.coroutines.flow.Flow

/**
 * 技能仓库接口
 */
interface SkillRepository {

    /**
     * 获取所有技能
     */
    fun getAllSkills(): Flow<List<Skill>>

    /**
     * 获取所有启用的技能
     */
    fun getEnabledSkills(): Flow<List<Skill>>

    /**
     * 根据 ID 获取技能
     */
    suspend fun getSkillById(id: String): Skill?

    /**
     * 根据 ID 列表获取技能
     */
    suspend fun getSkillsByIds(ids: List<String>): List<Skill>

    /**
     * 根据名称获取技能
     */
    suspend fun getSkillByName(name: String): Skill?

    /**
     * 保存技能
     */
    suspend fun saveSkill(skill: Skill)

    /**
     * 批量保存技能
     */
    suspend fun saveSkills(skills: List<Skill>)

    /**
     * 删除技能
     */
    suspend fun deleteSkill(id: String)

    /**
     * 获取技能数量
     */
    suspend fun getSkillCount(): Int

    /**
     * 获取启用的技能数量
     */
    suspend fun getEnabledSkillCount(): Int

    /**
     * 初始化内置技能
     */
    suspend fun initBuiltInSkills()
}
