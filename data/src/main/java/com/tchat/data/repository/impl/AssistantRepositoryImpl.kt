package com.tchat.data.repository.impl

import com.tchat.data.database.dao.AssistantDao
import com.tchat.data.database.entity.AssistantEntity
import com.tchat.data.model.Assistant
import com.tchat.data.model.LocalToolOption
import com.tchat.data.repository.AssistantRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray

/**
 * 助手Repository实现
 */
class AssistantRepositoryImpl(
    private val assistantDao: AssistantDao
) : AssistantRepository {

    override fun getAllAssistants(): Flow<List<Assistant>> {
        return assistantDao.getAllAssistants().map { entities ->
            entities.map { it.toAssistant() }
        }
    }

    override suspend fun getAssistantById(id: String): Assistant? {
        return assistantDao.getAssistantById(id)?.toAssistant()
    }

    override fun getAssistantByIdFlow(id: String): Flow<Assistant?> {
        return assistantDao.getAssistantByIdFlow(id).map { it?.toAssistant() }
    }

    override suspend fun saveAssistant(assistant: Assistant) {
        val entity = assistant.toEntity()
        assistantDao.insertAssistant(entity)
    }

    override suspend fun deleteAssistant(id: String) {
        assistantDao.deleteAssistantById(id)
    }

    override suspend fun getAssistantCount(): Int {
        return assistantDao.getAssistantCount()
    }

    /**
     * 将AssistantEntity转换为Assistant
     */
    private fun AssistantEntity.toAssistant(): Assistant {
        val toolOptions: List<LocalToolOption> = try {
            val jsonArray = JSONArray(localTools)
            (0 until jsonArray.length()).mapNotNull { i ->
                LocalToolOption.fromId(jsonArray.getString(i))
            }
        } catch (e: Exception) {
            emptyList()
        }

        val mcpIds: List<String> = try {
            val jsonArray = JSONArray(mcpServerIds)
            (0 until jsonArray.length()).map { i ->
                jsonArray.getString(i)
            }
        } catch (e: Exception) {
            emptyList()
        }

        val regexRuleIds: List<String> = try {
            val jsonArray = JSONArray(enabledRegexRuleIds)
            (0 until jsonArray.length()).map { i ->
                jsonArray.getString(i)
            }
        } catch (e: Exception) {
            emptyList()
        }

        val skillIds: List<String> = try {
            val jsonArray = JSONArray(enabledSkillIds)
            (0 until jsonArray.length()).map { i ->
                jsonArray.getString(i)
            }
        } catch (e: Exception) {
            emptyList()
        }

        return Assistant(
            id = id,
            name = name,
            avatar = avatar,
            systemPrompt = systemPrompt,
            temperature = temperature,
            topP = topP,
            maxTokens = maxTokens,
            contextMessageSize = contextMessageSize,
            streamOutput = streamOutput,
            localTools = toolOptions,
            knowledgeBaseId = knowledgeBaseId,
            mcpServerIds = mcpIds,
            enabledRegexRuleIds = regexRuleIds,
            enabledSkillIds = skillIds,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    /**
     * 将Assistant转换为AssistantEntity
     */
    private fun Assistant.toEntity(): AssistantEntity {
        val toolsJson = JSONArray().apply {
            localTools.forEach { put(it.id) }
        }.toString()

        val mcpIdsJson = JSONArray().apply {
            mcpServerIds.forEach { put(it) }
        }.toString()

        val regexRuleIdsJson = JSONArray().apply {
            enabledRegexRuleIds.forEach { put(it) }
        }.toString()

        val skillIdsJson = JSONArray().apply {
            enabledSkillIds.forEach { put(it) }
        }.toString()

        return AssistantEntity(
            id = id,
            name = name,
            avatar = avatar,
            systemPrompt = systemPrompt,
            temperature = temperature,
            topP = topP,
            maxTokens = maxTokens,
            contextMessageSize = contextMessageSize,
            streamOutput = streamOutput,
            localTools = toolsJson,
            knowledgeBaseId = knowledgeBaseId,
            mcpServerIds = mcpIdsJson,
            enabledRegexRuleIds = regexRuleIdsJson,
            enabledSkillIds = skillIdsJson,
            createdAt = createdAt,
            updatedAt = System.currentTimeMillis()
        )
    }
}
