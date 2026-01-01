package com.tchat.data.tool

import com.tchat.data.repository.KnowledgeRepository
import com.tchat.data.service.KnowledgeService
import com.tchat.network.provider.EmbeddingProvider
import org.json.JSONArray
import org.json.JSONObject

/**
 * 知识库搜索工具
 * 
 * 用于在助手对话中进行知识库检索（RAG）
 * 使用知识库自己配置的 Embedding Provider，与对话模型提供商独立
 */
class KnowledgeSearchTool(
    private val knowledgeService: KnowledgeService,
    private val repository: KnowledgeRepository,
    private val getEmbeddingProvider: (knowledgeBaseId: String) -> EmbeddingProvider?,
    private val knowledgeBaseId: String
) {
    /**
     * 创建知识库搜索工具
     */
    fun createTool(): Tool {
        return Tool(
            name = "search_knowledge_base",
            description = "在知识库中搜索相关信息。请优先使用此工具来查找和回答用户的问题。当用户提问时，你应该先调用此工具搜索相关内容，然后基于搜索结果来回答。搜索结果将包含与查询最相关的知识片段。",
            parameters = {
                InputSchema.Obj(
                    properties = mapOf(
                        "query" to PropertyDef(
                            type = "string",
                            description = "搜索查询词，描述你想要查找的信息"
                        ),
                        "top_k" to PropertyDef(
                            type = "integer",
                            description = "返回的最大结果数量，默认5"
                        )
                    ),
                    required = listOf("query")
                )
            },
            execute = { args ->
                val query = args.optString("query", "")
                val topK = args.optInt("top_k", 5)
                
                println("=== 知识库搜索工具执行 ===")
                println("查询: $query")
                println("知识库ID: $knowledgeBaseId")
                
                JSONObject().apply {
                    if (query.isBlank()) {
                        put("success", false)
                        put("error", "查询内容不能为空")
                        println("错误: 查询内容为空")
                    } else {
                        try {
                            // 获取知识库配置的 Embedding Provider
                            println("正在获取 Embedding Provider...")
                            val embeddingProvider = getEmbeddingProvider(knowledgeBaseId)
                            if (embeddingProvider == null) {
                                put("success", false)
                                put("error", "无法获取知识库的Embedding提供商，请检查知识库配置")
                                println("错误: Embedding Provider 为 null")
                                return@apply
                            }
                            println("Embedding Provider 获取成功")
                            
                            // 获取知识库信息
                            println("正在获取知识库信息...")
                            val base = repository.getBaseById(knowledgeBaseId)
                            if (base == null) {
                                put("success", false)
                                put("error", "知识库不存在")
                                println("错误: 知识库不存在")
                                return@apply
                            }
                            println("知识库: ${base.name}, 模型: ${base.embeddingModelId}")
                            
                            // 执行搜索
                            println("正在执行搜索...")
                            val results = knowledgeService.search(
                                baseId = knowledgeBaseId,
                                query = query,
                                embeddingProvider = embeddingProvider,
                                embeddingModel = base.embeddingModelId,
                                topK = topK
                            )
                            println("搜索完成，结果数量: ${results.size}")
                            
                            if (results.isEmpty()) {
                                put("success", true)
                                put("message", "未找到相关内容")
                                put("results", JSONArray())
                            } else {
                                put("success", true)
                                put("results", JSONArray().apply {
                                    results.forEach { result ->
                                        put(JSONObject().apply {
                                            put("content", result.chunk.content)
                                            put("score", result.score)
                                            put("source", result.item?.title ?: "未知来源")
                                        })
                                    }
                                })
                                put("count", results.size)
                            }
                            println("=== 知识库搜索完成 ===")
                        } catch (e: Exception) {
                            put("success", false)
                            put("error", "搜索失败: ${e.message}")
                            println("异常: ${e.message}")
                            e.printStackTrace()
                        }
                    }
                }
            }
        )
    }
    
    companion object {
        /**
         * 创建知识库搜索工具
         * 
         * @param knowledgeService 知识库服务
         * @param repository 知识库仓库
         * @param getEmbeddingProvider 获取 Embedding Provider 的函数，使用知识库自己的配置
         * @param knowledgeBaseId 知识库ID
         */
        fun create(
            knowledgeService: KnowledgeService,
            repository: KnowledgeRepository,
            getEmbeddingProvider: (knowledgeBaseId: String) -> EmbeddingProvider?,
            knowledgeBaseId: String
        ): Tool {
            return KnowledgeSearchTool(
                knowledgeService = knowledgeService,
                repository = repository,
                getEmbeddingProvider = getEmbeddingProvider,
                knowledgeBaseId = knowledgeBaseId
            ).createTool()
        }
    }
}
