package com.tchat.data.deepresearch.repository

import com.tchat.data.deepresearch.model.*
import com.tchat.data.deepresearch.service.DeepResearchService
import com.tchat.data.deepresearch.service.WebSearchProvider
import com.tchat.data.deepresearch.service.WebSearchService
import com.tchat.data.deepresearch.service.WebSearchServiceFactory
import com.tchat.network.provider.AIProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest

/**
 * 深度研究 Repository 接口
 */
interface DeepResearchRepository {
    /**
     * 执行深度研究
     * @param query 用户问题
     * @param config 研究配置
     * @return 研究进度 Flow
     */
    fun research(
        query: String,
        config: DeepResearchConfig = DeepResearchConfig()
    ): Flow<ResearchStep>

    /**
     * 生成最终报告
     * @param prompt 原始问题
     * @param learnings 所有学习成果
     * @param language 语言
     * @return 报告生成进度 Flow
     */
    fun generateReport(
        prompt: String,
        learnings: List<Learning>,
        language: String = "zh"
    ): Flow<ResearchStep>

    /**
     * 执行完整的深度研究（包括生成报告）
     * @param query 用户问题
     * @param config 研究配置
     * @return 完整研究结果
     */
    fun researchWithReport(
        query: String,
        config: DeepResearchConfig = DeepResearchConfig()
    ): Flow<ResearchStep>
}

/**
 * 深度研究 Repository 实现
 */
class DeepResearchRepositoryImpl(
    private val aiProvider: AIProvider,
    private val webSearchService: WebSearchService
) : DeepResearchRepository {

    private val deepResearchService = DeepResearchService(aiProvider, webSearchService)

    override fun research(
        query: String,
        config: DeepResearchConfig
    ): Flow<ResearchStep> {
        return deepResearchService.research(query, config)
    }

    override fun generateReport(
        prompt: String,
        learnings: List<Learning>,
        language: String
    ): Flow<ResearchStep> {
        return deepResearchService.generateReport(prompt, learnings, language)
    }

    override fun researchWithReport(
        query: String,
        config: DeepResearchConfig
    ): Flow<ResearchStep> = channelFlow {
        val allLearnings = mutableListOf<Learning>()

        // 执行研究
        deepResearchService.research(query, config).collectLatest { step ->
            send(step)

            // 收集学习成果
            when (step) {
                is ResearchStep.Complete -> {
                    allLearnings.addAll(step.learnings)
                }
                else -> {}
            }
        }

        // 如果有学习成果，生成报告
        if (allLearnings.isNotEmpty()) {
            deepResearchService.generateReport(
                prompt = query,
                learnings = allLearnings,
                language = config.language
            ).collectLatest { step ->
                send(step)
            }
        }
    }
}

/**
 * 深度研究配置
 */
data class DeepResearchSettings(
    // AI 设置
    val aiProviderType: String = "openai",
    val aiApiKey: String = "",
    val aiApiBase: String = "",
    val aiModel: String = "",

    // 搜索设置
    val webSearchProvider: WebSearchProvider = WebSearchProvider.TAVILY,
    val webSearchApiKey: String = "",
    val webSearchApiBase: String? = null,
    val tavilyAdvancedSearch: Boolean = false,
    val tavilySearchTopic: String = "general",

    // 研究设置
    val breadth: Int = 3,
    val maxDepth: Int = 2,
    val language: String = "zh",
    val searchLanguage: String = "en",
    val maxSearchResults: Int = 5,
    val concurrencyLimit: Int = 2
)

/**
 * 深度研究 Repository 工厂
 */
object DeepResearchRepositoryFactory {

    /**
     * 创建 Repository
     * @param aiProvider AI 提供者
     * @param settings 深度研究设置
     */
    fun create(
        aiProvider: AIProvider,
        settings: DeepResearchSettings
    ): DeepResearchRepository {
        val webSearchService = WebSearchServiceFactory.create(
            provider = settings.webSearchProvider,
            apiKey = settings.webSearchApiKey,
            baseUrl = settings.webSearchApiBase,
            advancedSearch = settings.tavilyAdvancedSearch,
            searchTopic = settings.tavilySearchTopic
        )

        return DeepResearchRepositoryImpl(aiProvider, webSearchService)
    }

    /**
     * 创建 Repository（使用自定义 WebSearchService）
     */
    fun create(
        aiProvider: AIProvider,
        webSearchService: WebSearchService
    ): DeepResearchRepository {
        return DeepResearchRepositoryImpl(aiProvider, webSearchService)
    }
}
