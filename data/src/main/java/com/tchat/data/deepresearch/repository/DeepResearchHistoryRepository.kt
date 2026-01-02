package com.tchat.data.deepresearch.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tchat.data.database.dao.DeepResearchHistoryDao
import com.tchat.data.database.entity.DeepResearchHistoryEntity
import com.tchat.data.deepresearch.model.Learning
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 深度研究历史记录
 */
data class DeepResearchHistory(
    val id: String,
    val query: String,
    val report: String,
    val learnings: List<Learning>,
    val startTime: Long,
    val endTime: Long,
    val status: String
)

/**
 * 深度研究历史记录 Repository
 */
class DeepResearchHistoryRepository(
    private val dao: DeepResearchHistoryDao
) {
    private val gson = Gson()

    /**
     * 获取所有历史记录
     */
    fun getAllHistory(): Flow<List<DeepResearchHistory>> {
        return dao.getAllHistory().map { entities ->
            entities.map { it.toHistory() }
        }
    }

    /**
     * 获取最近的历史记录
     */
    fun getRecentHistory(limit: Int = 20): Flow<List<DeepResearchHistory>> {
        return dao.getRecentHistory(limit).map { entities ->
            entities.map { it.toHistory() }
        }
    }

    /**
     * 根据 ID 获取历史记录
     */
    suspend fun getHistoryById(id: String): DeepResearchHistory? {
        return dao.getHistoryById(id)?.toHistory()
    }

    /**
     * 搜索历史记录
     */
    fun searchHistory(keyword: String): Flow<List<DeepResearchHistory>> {
        return dao.searchHistory(keyword).map { entities ->
            entities.map { it.toHistory() }
        }
    }

    /**
     * 保存历史记录
     */
    suspend fun saveHistory(history: DeepResearchHistory) {
        dao.insert(history.toEntity())
    }

    /**
     * 删除历史记录
     */
    suspend fun deleteHistory(id: String) {
        dao.deleteById(id)
    }

    /**
     * 删除所有历史记录
     */
    suspend fun deleteAllHistory() {
        dao.deleteAll()
    }

    /**
     * 获取历史记录数量
     */
    suspend fun getHistoryCount(): Int {
        return dao.getCount()
    }

    private fun DeepResearchHistoryEntity.toHistory(): DeepResearchHistory {
        val learningsType = object : TypeToken<List<Learning>>() {}.type
        val learnings: List<Learning> = try {
            gson.fromJson(learningsJson, learningsType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        return DeepResearchHistory(
            id = id,
            query = query,
            report = report,
            learnings = learnings,
            startTime = startTime,
            endTime = endTime,
            status = status
        )
    }

    private fun DeepResearchHistory.toEntity() = DeepResearchHistoryEntity(
        id = id,
        query = query,
        report = report,
        learningsJson = gson.toJson(learnings),
        startTime = startTime,
        endTime = endTime,
        status = status
    )
}
