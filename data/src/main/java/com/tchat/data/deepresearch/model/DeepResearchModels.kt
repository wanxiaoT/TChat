package com.tchat.data.deepresearch.model

/**
 * 搜索查询
 */
data class SearchQuery(
    val query: String,           // 搜索关键词
    val researchGoal: String,    // 研究目标
    val nodeId: String = "0"     // 节点ID（用于树状展示）
)

/**
 * 网络搜索结果
 */
data class WebSearchResult(
    val url: String,
    val title: String?,
    val content: String          // 网页内容摘要
)

/**
 * 学习成果 - 从搜索结果中提取的关键信息
 */
data class Learning(
    val url: String,
    val title: String?,
    val learning: String         // 关键信息
)

/**
 * 处理后的搜索结果
 */
data class ProcessedSearchResult(
    val learnings: List<Learning>,
    val followUpQuestions: List<String>  // 后续研究问题
)

/**
 * 深度研究配置
 */
data class DeepResearchConfig(
    val breadth: Int = 3,        // 广度：每层生成多少个搜索查询
    val maxDepth: Int = 2,       // 深度：递归多少层
    val language: String = "zh", // 语言代码
    val searchLanguage: String? = null,  // 搜索语言（可以与输出语言不同）
    val maxSearchResults: Int = 5,       // 每次搜索的最大结果数
    val concurrencyLimit: Int = 2        // 并发限制
)

/**
 * 深度研究结果
 */
data class DeepResearchResult(
    val learnings: List<Learning>,
    val report: String? = null           // 最终报告（Markdown）
)

/**
 * 研究进度步骤（用于 UI 展示）
 */
sealed class ResearchStep {
    /**
     * 正在生成搜索查询
     */
    data class GeneratingQuery(
        val nodeId: String,
        val parentNodeId: String? = null,
        val query: SearchQuery? = null
    ) : ResearchStep()

    /**
     * 生成查询时的推理过程（用于展示思维链）
     */
    data class GeneratingQueryReasoning(
        val nodeId: String,
        val delta: String
    ) : ResearchStep()

    /**
     * 搜索查询已生成
     */
    data class GeneratedQuery(
        val nodeId: String,
        val query: SearchQuery
    ) : ResearchStep()

    /**
     * 正在搜索
     */
    data class Searching(
        val nodeId: String,
        val query: String
    ) : ResearchStep()

    /**
     * 搜索完成
     */
    data class SearchComplete(
        val nodeId: String,
        val results: List<WebSearchResult>
    ) : ResearchStep()

    /**
     * 正在处理搜索结果
     */
    data class ProcessingResult(
        val nodeId: String,
        val query: String,
        val result: ProcessedSearchResult? = null
    ) : ResearchStep()

    /**
     * 处理结果时的推理过程
     */
    data class ProcessingResultReasoning(
        val nodeId: String,
        val delta: String
    ) : ResearchStep()

    /**
     * 节点处理完成
     */
    data class NodeComplete(
        val nodeId: String,
        val result: ProcessedSearchResult? = null
    ) : ResearchStep()

    /**
     * 发生错误
     */
    data class Error(
        val nodeId: String,
        val message: String
    ) : ResearchStep()

    /**
     * 研究完成
     */
    data class Complete(
        val learnings: List<Learning>
    ) : ResearchStep()

    /**
     * 正在生成报告
     */
    data class GeneratingReport(
        val delta: String
    ) : ResearchStep()

    /**
     * 报告生成完成
     */
    data class ReportComplete(
        val report: String
    ) : ResearchStep()
}
