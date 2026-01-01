package com.tchat.data.service

import android.util.Log
import com.tchat.data.database.entity.KnowledgeBaseEntity
import com.tchat.data.database.entity.KnowledgeChunkEntity
import com.tchat.data.database.entity.KnowledgeItemEntity
import com.tchat.data.database.entity.KnowledgeItemType
import com.tchat.data.database.entity.ProcessingStatus
import com.tchat.data.knowledge.FileLoader
import com.tchat.data.knowledge.TextLoader
import com.tchat.data.knowledge.UrlLoader
import com.tchat.data.repository.KnowledgeRepository
import com.tchat.data.util.TextChunker
import com.tchat.data.util.VectorUtils
import com.tchat.network.provider.EmbeddingProvider
import com.tchat.network.provider.EmbeddingProviderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val TAG = "KnowledgeService"

/**
 * 知识库服务
 * 负责处理内容加载、分块、向量化和检索
 */
@OptIn(ExperimentalUuidApi::class)
class KnowledgeService(
    private val repository: KnowledgeRepository
) {

    /**
     * 知识库搜索结果
     */
    data class SearchResult(
        val chunk: KnowledgeChunkEntity,
        val item: KnowledgeItemEntity?,
        val score: Float
    )

    /**
     * 处理知识条目
     * 加载内容 -> 分块 -> 生成向量 -> 存储
     */
    suspend fun processItem(
        item: KnowledgeItemEntity,
        embeddingProvider: EmbeddingProvider,
        embeddingModel: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 更新状态为处理中
            repository.updateItemStatus(item.id, ProcessingStatus.PROCESSING)

            // 加载内容
            val content = loadContent(item)
            if (content.isBlank()) {
                throw Exception("内容为空")
            }

            Log.d(TAG, "Loaded content for item ${item.id}, length: ${content.length}")

            // 分块
            val chunks = TextChunker.chunkByParagraphs(content)
            if (chunks.isEmpty()) {
                throw Exception("分块结果为空")
            }

            Log.d(TAG, "Created ${chunks.size} chunks for item ${item.id}")

            // 生成向量
            val embeddings = embeddingProvider.embed(chunks, embeddingModel)

            Log.d(TAG, "Generated ${embeddings.size} embeddings for item ${item.id}")

            // 删除旧的块（如果有）
            repository.deleteChunksByItemId(item.id)

            // 存储新的块
            val chunkEntities = chunks.mapIndexed { index, text ->
                KnowledgeChunkEntity(
                    id = Uuid.random().toString(),
                    itemId = item.id,
                    knowledgeBaseId = item.knowledgeBaseId,
                    content = text,
                    embedding = floatArrayToJson(embeddings[index]),
                    chunkIndex = index
                )
            }

            repository.addChunks(chunkEntities)

            // 更新状态为完成
            repository.updateItemStatus(item.id, ProcessingStatus.COMPLETED)

            Log.d(TAG, "Successfully processed item ${item.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process item ${item.id}", e)
            repository.updateItemStatus(item.id, ProcessingStatus.FAILED, e.message)
            Result.failure(e)
        }
    }

    /**
     * 处理知识库中所有待处理的条目
     */
    suspend fun processAllPending(
        baseId: String,
        embeddingProvider: EmbeddingProvider,
        embeddingModel: String
    ): List<Result<Unit>> = withContext(Dispatchers.IO) {
        val pendingItems = repository.getItemsByBaseIdAndStatus(baseId, ProcessingStatus.PENDING)
        pendingItems.map { item ->
            processItem(item, embeddingProvider, embeddingModel)
        }
    }

    /**
     * 搜索知识库
     */
    suspend fun search(
        baseId: String,
        query: String,
        embeddingProvider: EmbeddingProvider,
        embeddingModel: String,
        topK: Int = 5,
        threshold: Float = 0.5f
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        try {
            // 生成查询向量
            val queryEmbedding = embeddingProvider.embed(query, embeddingModel)

            // 获取所有块
            val chunks = repository.getChunksByBaseId(baseId)

            if (chunks.isEmpty()) {
                return@withContext emptyList()
            }

            // 计算相似度并排序
            val results = chunks.mapNotNull { chunk ->
                val chunkEmbedding = jsonToFloatArray(chunk.embedding)
                val score = VectorUtils.cosineSimilarity(queryEmbedding, chunkEmbedding)

                if (score >= threshold) {
                    val item = repository.getItemById(chunk.itemId)
                    SearchResult(chunk, item, score)
                } else {
                    null
                }
            }
                .sortedByDescending { it.score }
                .take(topK)

            Log.d(TAG, "Search found ${results.size} results for query: $query")
            results
        } catch (e: Exception) {
            Log.e(TAG, "Search failed", e)
            emptyList()
        }
    }

    /**
     * 加载条目内容
     */
    private suspend fun loadContent(item: KnowledgeItemEntity): String {
        return when (item.getItemType()) {
            KnowledgeItemType.TEXT -> {
                TextLoader(item.content).load()
            }
            KnowledgeItemType.URL -> {
                val url = item.sourceUri ?: item.content
                UrlLoader(url).load()
            }
            KnowledgeItemType.FILE -> {
                val filePath = item.sourceUri ?: throw Exception("文件路径为空")
                FileLoader(File(filePath)).load()
            }
        }
    }

    /**
     * 将 FloatArray 转换为 JSON 字符串
     */
    private fun floatArrayToJson(array: FloatArray): String {
        val jsonArray = JSONArray()
        array.forEach { jsonArray.put(it.toDouble()) }
        return jsonArray.toString()
    }

    /**
     * 将 JSON 字符串转换为 FloatArray
     */
    private fun jsonToFloatArray(json: String): FloatArray {
        val jsonArray = JSONArray(json)
        return FloatArray(jsonArray.length()) { i ->
            jsonArray.getDouble(i).toFloat()
        }
    }

    companion object {
        /**
         * 创建 Embedding Provider
         */
        fun createEmbeddingProvider(
            providerType: EmbeddingProviderFactory.EmbeddingProviderType,
            apiKey: String,
            baseUrl: String?
        ): EmbeddingProvider {
            return EmbeddingProviderFactory.create(providerType, apiKey, baseUrl)
        }
    }
}
