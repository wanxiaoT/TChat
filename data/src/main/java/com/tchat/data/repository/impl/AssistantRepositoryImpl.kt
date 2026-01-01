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
            createdAt = createdAt,
            updatedAt = System.currentTimeMillis()
        )
    }
}
