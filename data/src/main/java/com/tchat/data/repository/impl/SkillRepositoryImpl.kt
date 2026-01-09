package com.tchat.data.repository.impl

import com.tchat.data.database.dao.SkillDao
import com.tchat.data.database.entity.SkillEntity
import com.tchat.data.model.Skill
import com.tchat.data.model.SkillToolDefinition
import com.tchat.data.model.SkillToolExecuteType
import com.tchat.data.repository.SkillRepository
import com.tchat.data.skill.BuiltInSkills
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/**
 * 技能仓库实现
 */
class SkillRepositoryImpl(
    private val skillDao: SkillDao
) : SkillRepository {

    override fun getAllSkills(): Flow<List<Skill>> {
        return skillDao.getAllSkills().map { entities ->
            entities.map { it.toSkill() }
        }
    }

    override fun getEnabledSkills(): Flow<List<Skill>> {
        return skillDao.getEnabledSkills().map { entities ->
            entities.map { it.toSkill() }
        }
    }

    override suspend fun getSkillById(id: String): Skill? {
        return skillDao.getSkillById(id)?.toSkill()
    }

    override suspend fun getSkillsByIds(ids: List<String>): List<Skill> {
        if (ids.isEmpty()) return emptyList()
        return skillDao.getSkillsByIds(ids).map { it.toSkill() }
    }

    override suspend fun getSkillByName(name: String): Skill? {
        return skillDao.getSkillByName(name)?.toSkill()
    }

    override suspend fun saveSkill(skill: Skill) {
        skillDao.insertSkill(skill.toEntity())
    }

    override suspend fun saveSkills(skills: List<Skill>) {
        skillDao.insertSkills(skills.map { it.toEntity() })
    }

    override suspend fun deleteSkill(id: String) {
        skillDao.deleteSkillById(id)
    }

    override suspend fun getSkillCount(): Int {
        return skillDao.getSkillCount()
    }

    override suspend fun getEnabledSkillCount(): Int {
        return skillDao.getEnabledSkillCount()
    }

    override suspend fun initBuiltInSkills() {
        val builtInSkills = BuiltInSkills.getAll()
        for (skill in builtInSkills) {
            // 只有当内置技能不存在时才插入
            val existing = skillDao.getSkillByName(skill.name)
            if (existing == null) {
                skillDao.insertSkill(skill.toEntity())
            }
        }
    }

    /**
     * 将 SkillEntity 转换为 Skill
     */
    private fun SkillEntity.toSkill(): Skill {
        val keywords: List<String> = try {
            val jsonArray = JSONArray(triggerKeywords)
            (0 until jsonArray.length()).map { i ->
                jsonArray.getString(i)
            }
        } catch (e: Exception) {
            emptyList()
        }

        val toolsList: List<SkillToolDefinition> = try {
            val jsonArray = JSONArray(toolsJson)
            (0 until jsonArray.length()).map { i ->
                val obj = jsonArray.getJSONObject(i)
                SkillToolDefinition(
                    name = obj.optString("name", ""),
                    description = obj.optString("description", ""),
                    parametersJson = obj.optString("parametersJson", "{}"),
                    executeType = try {
                        SkillToolExecuteType.valueOf(obj.optString("executeType", "TEMPLATE_RESPONSE"))
                    } catch (e: Exception) {
                        SkillToolExecuteType.TEMPLATE_RESPONSE
                    },
                    executeConfig = obj.optString("executeConfig", "{}")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }

        return Skill(
            id = id,
            name = name,
            displayName = displayName,
            description = description,
            content = content,
            triggerKeywords = keywords,
            priority = priority,
            enabled = enabled,
            isBuiltIn = isBuiltIn,
            tools = toolsList,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    /**
     * 将 Skill 转换为 SkillEntity
     */
    private fun Skill.toEntity(): SkillEntity {
        val keywordsJson = JSONArray().apply {
            triggerKeywords.forEach { put(it) }
        }.toString()

        val toolsJsonArray = JSONArray().apply {
            tools.forEach { tool ->
                put(JSONObject().apply {
                    put("name", tool.name)
                    put("description", tool.description)
                    put("parametersJson", tool.parametersJson)
                    put("executeType", tool.executeType.name)
                    put("executeConfig", tool.executeConfig)
                })
            }
        }.toString()

        return SkillEntity(
            id = id,
            name = name,
            displayName = displayName,
            description = description,
            content = content,
            triggerKeywords = keywordsJson,
            priority = priority,
            enabled = enabled,
            isBuiltIn = isBuiltIn,
            toolsJson = toolsJsonArray,
            createdAt = createdAt,
            updatedAt = System.currentTimeMillis()
        )
    }
}
