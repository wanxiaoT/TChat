package com.tchat.data.repository

import com.tchat.data.database.entity.KnowledgeBaseEntity
import com.tchat.data.database.entity.KnowledgeChunkEntity
import com.tchat.data.database.entity.KnowledgeItemEntity
import com.tchat.data.database.entity.ProcessingStatus
import kotlinx.coroutines.flow.Flow

/**
 * 知识库仓库接口
 */
interface KnowledgeRepository {

    // ===== 知识库操作 =====

    /**
     * 获取所有知识库
     */
    fun getAllBases(): Flow<List<KnowledgeBaseEntity>>

    /**
     * 根据ID获取知识库
     */
    suspend fun getBaseById(id: String): KnowledgeBaseEntity?

    /**
     * 观察知识库变化
     */
    fun observeBaseById(id: String): Flow<KnowledgeBaseEntity?>

    /**
     * 创建知识库
     */
    suspend fun createBase(base: KnowledgeBaseEntity)

    /**
     * 更新知识库
     */
    suspend fun updateBase(base: KnowledgeBaseEntity)

    /**
     * 删除知识库
     */
    suspend fun deleteBase(id: String)

    // ===== 知识条目操作 =====

    /**
     * 获取知识库下的所有条目
     */
    fun getItemsByBaseId(baseId: String): Flow<List<KnowledgeItemEntity>>

    /**
     * 同步获取知识库下的所有条目
     */
    suspend fun getItemsByBaseIdSync(baseId: String): List<KnowledgeItemEntity>

    /**
     * 根据ID获取条目
     */
    suspend fun getItemById(id: String): KnowledgeItemEntity?

    /**
     * 获取指定状态的条目
     */
    suspend fun getItemsByStatus(status: ProcessingStatus): List<KnowledgeItemEntity>

    /**
     * 获取知识库下指定状态的条目
     */
    suspend fun getItemsByBaseIdAndStatus(baseId: String, status: ProcessingStatus): List<KnowledgeItemEntity>

    /**
     * 添加条目
     */
    suspend fun addItem(item: KnowledgeItemEntity)

    /**
     * 更新条目
     */
    suspend fun updateItem(item: KnowledgeItemEntity)

    /**
     * 更新条目状态
     */
    suspend fun updateItemStatus(id: String, status: ProcessingStatus, errorMessage: String? = null)

    /**
     * 删除条目
     */
    suspend fun deleteItem(id: String)

    // ===== 知识块操作 =====

    /**
     * 获取知识库下的所有块
     */
    suspend fun getChunksByBaseId(baseId: String): List<KnowledgeChunkEntity>

    /**
     * 获取条目下的所有块
     */
    suspend fun getChunksByItemId(itemId: String): List<KnowledgeChunkEntity>

    /**
     * 添加知识块
     */
    suspend fun addChunks(chunks: List<KnowledgeChunkEntity>)

    /**
     * 删除条目的所有块
     */
    suspend fun deleteChunksByItemId(itemId: String)

    /**
     * 删除知识库的所有块
     */
    suspend fun deleteChunksByBaseId(baseId: String)

    // ===== 统计 =====

    /**
     * 获取知识库条目数量
     */
    suspend fun getItemsCount(baseId: String): Int

    /**
     * 获取知识库块数量
     */
    suspend fun getChunksCount(baseId: String): Int
}
