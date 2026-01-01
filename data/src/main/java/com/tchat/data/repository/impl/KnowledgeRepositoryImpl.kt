package com.tchat.data.repository.impl

import com.tchat.data.database.dao.KnowledgeBaseDao
import com.tchat.data.database.dao.KnowledgeChunkDao
import com.tchat.data.database.dao.KnowledgeItemDao
import com.tchat.data.database.entity.KnowledgeBaseEntity
import com.tchat.data.database.entity.KnowledgeChunkEntity
import com.tchat.data.database.entity.KnowledgeItemEntity
import com.tchat.data.database.entity.ProcessingStatus
import com.tchat.data.repository.KnowledgeRepository
import kotlinx.coroutines.flow.Flow

/**
 * 知识库仓库实现
 */
class KnowledgeRepositoryImpl(
    private val baseDao: KnowledgeBaseDao,
    private val itemDao: KnowledgeItemDao,
    private val chunkDao: KnowledgeChunkDao
) : KnowledgeRepository {

    // ===== 知识库操作 =====

    override fun getAllBases(): Flow<List<KnowledgeBaseEntity>> {
        return baseDao.getAllBases()
    }

    override suspend fun getBaseById(id: String): KnowledgeBaseEntity? {
        return baseDao.getBaseById(id)
    }

    override fun observeBaseById(id: String): Flow<KnowledgeBaseEntity?> {
        return baseDao.observeBaseById(id)
    }

    override suspend fun createBase(base: KnowledgeBaseEntity) {
        baseDao.insertBase(base)
    }

    override suspend fun updateBase(base: KnowledgeBaseEntity) {
        baseDao.updateBase(base)
    }

    override suspend fun deleteBase(id: String) {
        baseDao.deleteBaseById(id)
    }

    // ===== 知识条目操作 =====

    override fun getItemsByBaseId(baseId: String): Flow<List<KnowledgeItemEntity>> {
        return itemDao.getItemsByBaseId(baseId)
    }

    override suspend fun getItemsByBaseIdSync(baseId: String): List<KnowledgeItemEntity> {
        return itemDao.getItemsByBaseIdSync(baseId)
    }

    override suspend fun getItemById(id: String): KnowledgeItemEntity? {
        return itemDao.getItemById(id)
    }

    override suspend fun getItemsByStatus(status: ProcessingStatus): List<KnowledgeItemEntity> {
        return itemDao.getItemsByStatus(status.name)
    }

    override suspend fun getItemsByBaseIdAndStatus(
        baseId: String,
        status: ProcessingStatus
    ): List<KnowledgeItemEntity> {
        return itemDao.getItemsByBaseIdAndStatus(baseId, status.name)
    }

    override suspend fun addItem(item: KnowledgeItemEntity) {
        itemDao.insertItem(item)
    }

    override suspend fun updateItem(item: KnowledgeItemEntity) {
        itemDao.updateItem(item)
    }

    override suspend fun updateItemStatus(
        id: String,
        status: ProcessingStatus,
        errorMessage: String?
    ) {
        itemDao.updateItemStatus(id, status.name, errorMessage)
    }

    override suspend fun deleteItem(id: String) {
        // 先删除相关块
        chunkDao.deleteChunksByItemId(id)
        // 再删除条目
        itemDao.deleteItemById(id)
    }

    // ===== 知识块操作 =====

    override suspend fun getChunksByBaseId(baseId: String): List<KnowledgeChunkEntity> {
        return chunkDao.getChunksByBaseId(baseId)
    }

    override suspend fun getChunksByItemId(itemId: String): List<KnowledgeChunkEntity> {
        return chunkDao.getChunksByItemId(itemId)
    }

    override suspend fun addChunks(chunks: List<KnowledgeChunkEntity>) {
        chunkDao.insertChunks(chunks)
    }

    override suspend fun deleteChunksByItemId(itemId: String) {
        chunkDao.deleteChunksByItemId(itemId)
    }

    override suspend fun deleteChunksByBaseId(baseId: String) {
        chunkDao.deleteChunksByBaseId(baseId)
    }

    // ===== 统计 =====

    override suspend fun getItemsCount(baseId: String): Int {
        return itemDao.getItemsCountByBaseId(baseId)
    }

    override suspend fun getChunksCount(baseId: String): Int {
        return chunkDao.getChunksCountByBaseId(baseId)
    }
}
