package com.tchat.data.deepresearch.service

import android.util.Log
import com.tchat.data.deepresearch.model.*
import com.tchat.network.provider.AIProvider
import com.tchat.network.provider.ChatMessage
import com.tchat.network.provider.MessageRole
import com.tchat.network.provider.StreamChunk
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * 深度研究服务
 * 实现迭代式深度研究，通过搜索引擎和 AI 进行多轮研究
 */
class DeepResearchService(
    private val aiProvider: AIProvider,
    private val webSearchService: WebSearchService
) {
    companion object {
        private const val TAG = "DeepResearchService"
    }

    /**
     * 执行深度研究
     * @param query 用户问题
     * @param config 研究配置
     * @return 研究进度 Flow
     */
    fun research(
        query: String,
        config: DeepResearchConfig = DeepResearchConfig()
    ): Flow<ResearchStep> = channelFlow {
        val semaphore = Semaphore(config.concurrencyLimit)

        try {
            val learnings = deepResearch(
                query = query,
                breadth = config.breadth,
                maxDepth = config.maxDepth,
                currentDepth = 1,
                nodeId = "0",
                languageCode = config.language,
                searchLanguageCode = config.searchLanguage ?: config.language,
                maxSearchResults = config.maxSearchResults,
                learnings = emptyList(),
                semaphore = semaphore,
                onProgress = { step -> trySend(step) }
            )

            send(ResearchStep.Complete(learnings))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Research error", e)
            send(ResearchStep.Error("0", e.message ?: "Unknown error"))
        }
    }

    /**
     * 生成最终报告
     * @param prompt 原始问题
     * @param learnings 所有学习成果
     * @param language 语言
     * @return 报告内容 Flow
     */
    fun generateReport(
        prompt: String,
        learnings: List<Learning>,
        language: String = "zh"
    ): Flow<ResearchStep> = channelFlow {
        try {
            val learningsText = learnings.mapIndexed { index, learning ->
                "<learning index=\"${index + 1}\">\n${learning.learning}\n</learning>"
            }.joinToString("\n")

            val systemPrompt = buildSystemPrompt()
            val userPrompt = buildReportPrompt(prompt, learningsText, language)

            val messages = listOf(
                ChatMessage(role = MessageRole.SYSTEM, content = systemPrompt),
                ChatMessage(role = MessageRole.USER, content = userPrompt)
            )

            val reportBuilder = StringBuilder()

            aiProvider.streamChat(messages).collect { chunk ->
                when (chunk) {
                    is StreamChunk.Content -> {
                        reportBuilder.append(chunk.text)
                        send(ResearchStep.GeneratingReport(chunk.text))
                    }
                    is StreamChunk.Done -> {
                        // 添加来源列表
                        val sources = learnings.mapIndexed { index, learning ->
                            "[${index + 1}] ${learning.title ?: learning.url}: ${learning.url}"
                        }.joinToString("\n")

                        val fullReport = reportBuilder.toString() + "\n\n## 来源\n\n$sources"
                        send(ResearchStep.ReportComplete(fullReport))
                    }
                    is StreamChunk.Error -> {
                        send(ResearchStep.Error("report", chunk.error.message ?: "Unknown error"))
                    }
                    else -> {}
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Report generation error", e)
            send(ResearchStep.Error("report", e.message ?: "Unknown error"))
        }
    }

    private suspend fun deepResearch(
        query: String,
        breadth: Int,
        maxDepth: Int,
        currentDepth: Int,
        nodeId: String,
        languageCode: String,
        searchLanguageCode: String,
        maxSearchResults: Int,
        learnings: List<Learning>,
        semaphore: Semaphore,
        onProgress: suspend (ResearchStep) -> Unit
    ): List<Learning> {
        Log.d(TAG, "Deep research: depth=$currentDepth/$maxDepth, breadth=$breadth, nodeId=$nodeId")

        // 1. 生成搜索查询
        val searchQueries = generateSearchQueries(
            query = query,
            numQueries = breadth,
            learnings = learnings.map { it.learning },
            language = languageCode,
            searchLanguage = searchLanguageCode,
            nodeId = nodeId,
            onProgress = onProgress
        )

        if (searchQueries.isEmpty()) {
            Log.w(TAG, "No search queries generated")
            return learnings
        }

        // 通知所有查询已生成
        searchQueries.forEach { searchQuery ->
            onProgress(ResearchStep.GeneratedQuery(searchQuery.nodeId, searchQuery))
        }

        onProgress(ResearchStep.NodeComplete(nodeId, null))

        // 2. 并行执行搜索和处理
        val allLearnings = mutableListOf<Learning>()
        allLearnings.addAll(learnings)

        coroutineScope {
            val results = searchQueries.mapIndexed { index, searchQuery ->
                async {
                    semaphore.withPermit {
                        processSearchQuery(
                            searchQuery = searchQuery,
                            breadth = breadth,
                            maxDepth = maxDepth,
                            currentDepth = currentDepth,
                            languageCode = languageCode,
                            searchLanguageCode = searchLanguageCode,
                            maxSearchResults = maxSearchResults,
                            existingLearnings = allLearnings.toList(),
                            semaphore = semaphore,
                            onProgress = onProgress
                        )
                    }
                }
            }.awaitAll()

            // 收集所有学习成果
            results.forEach { result ->
                allLearnings.addAll(result)
            }
        }

        // 去重
        return allLearnings.distinctBy { it.url }
    }

    private suspend fun processSearchQuery(
        searchQuery: SearchQuery,
        breadth: Int,
        maxDepth: Int,
        currentDepth: Int,
        languageCode: String,
        searchLanguageCode: String,
        maxSearchResults: Int,
        existingLearnings: List<Learning>,
        semaphore: Semaphore,
        onProgress: suspend (ResearchStep) -> Unit
    ): List<Learning> {
        val nodeId = searchQuery.nodeId

        try {
            // 执行搜索
            onProgress(ResearchStep.Searching(nodeId, searchQuery.query))

            val searchResult = webSearchService.search(
                query = searchQuery.query,
                maxResults = maxSearchResults,
                language = searchLanguageCode
            )

            if (searchResult.isFailure) {
                val error = searchResult.exceptionOrNull()?.message ?: "Search failed"
                onProgress(ResearchStep.Error(nodeId, error))
                return emptyList()
            }

            val webResults = searchResult.getOrThrow()
            if (webResults.isEmpty()) {
                onProgress(ResearchStep.Error(nodeId, "No search results found"))
                return emptyList()
            }

            Log.d(TAG, "Found ${webResults.size} results for: ${searchQuery.query}")
            onProgress(ResearchStep.SearchComplete(nodeId, webResults))

            // 处理搜索结果
            val nextBreadth = (breadth + 1) / 2
            val processedResult = processSearchResult(
                query = searchQuery.query,
                results = webResults,
                numFollowUpQuestions = nextBreadth,
                language = languageCode,
                nodeId = nodeId,
                onProgress = onProgress
            )

            // 为学习成果添加标题
            val learningsWithTitles = processedResult.learnings.map { learning ->
                val title = webResults.find { it.url == learning.url }?.title
                learning.copy(title = title)
            }

            val allLearnings = existingLearnings + learningsWithTitles
            val nextDepth = currentDepth + 1

            onProgress(ResearchStep.NodeComplete(nodeId, processedResult.copy(learnings = learningsWithTitles)))

            // 递归深入研究
            if (nextDepth <= maxDepth && processedResult.followUpQuestions.isNotEmpty()) {
                Log.d(TAG, "Going deeper: depth=$nextDepth, breadth=$nextBreadth")

                val nextQuery = """
                    Previous research goal: ${searchQuery.researchGoal}
                    Follow-up research directions: ${processedResult.followUpQuestions.joinToString("\n") { "- $it" }}
                """.trimIndent()

                return deepResearch(
                    query = nextQuery,
                    breadth = nextBreadth,
                    maxDepth = maxDepth,
                    currentDepth = nextDepth,
                    nodeId = nodeId,
                    languageCode = languageCode,
                    searchLanguageCode = searchLanguageCode,
                    maxSearchResults = maxSearchResults,
                    learnings = allLearnings,
                    semaphore = semaphore,
                    onProgress = onProgress
                )
            }

            return allLearnings
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error processing query: ${searchQuery.query}", e)
            onProgress(ResearchStep.Error(nodeId, e.message ?: "Unknown error"))
            return emptyList()
        }
    }

    private suspend fun generateSearchQueries(
        query: String,
        numQueries: Int,
        learnings: List<String>,
        language: String,
        searchLanguage: String,
        nodeId: String,
        onProgress: suspend (ResearchStep) -> Unit
    ): List<SearchQuery> {
        val systemPrompt = buildSystemPrompt()

        val learningsText = if (learnings.isNotEmpty()) {
            "Here are some learnings from previous research, use them to generate more specific queries:\n${learnings.joinToString("\n")}"
        } else ""

        val languagePrompt = buildLanguagePrompt(language, searchLanguage)

        val userPrompt = """
Given the following prompt from the user, generate a list of highly effective Google search queries to research the topic. Return a maximum of $numQueries queries, but feel free to return less if the original prompt is clear. Make sure each query is creative, unique and not similar to each other.

<prompt>$query</prompt>

$learningsText

You MUST respond in JSON format with the following structure:
{
    "queries": [
        {
            "query": "The search query string",
            "researchGoal": "The goal of this research query and how to advance the research"
        }
    ]
}

$languagePrompt
        """.trimIndent()

        val messages = listOf(
            ChatMessage(role = MessageRole.SYSTEM, content = systemPrompt),
            ChatMessage(role = MessageRole.USER, content = userPrompt)
        )

        val responseBuilder = StringBuilder()
        val queries = mutableListOf<SearchQuery>()

        try {
            aiProvider.streamChat(messages).collect { chunk ->
                when (chunk) {
                    is StreamChunk.Content -> {
                        responseBuilder.append(chunk.text)
                        onProgress(ResearchStep.GeneratingQueryReasoning(nodeId, chunk.text))

                        // 尝试解析部分 JSON
                        val partialQueries = tryParseSearchQueries(responseBuilder.toString(), nodeId)
                        if (partialQueries.isNotEmpty()) {
                            partialQueries.forEach { q ->
                                if (queries.none { it.nodeId == q.nodeId }) {
                                    queries.add(q)
                                    onProgress(ResearchStep.GeneratingQuery(q.nodeId, nodeId, q))
                                }
                            }
                        }
                    }
                    is StreamChunk.Done -> {
                        // 最终解析
                        val finalQueries = tryParseSearchQueries(responseBuilder.toString(), nodeId)
                        queries.clear()
                        queries.addAll(finalQueries)
                    }
                    is StreamChunk.Error -> {
                        Log.e(TAG, "AI error in generateSearchQueries: ${chunk.error}")
                    }
                    else -> {}
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating search queries", e)
        }

        return queries
    }

    private suspend fun processSearchResult(
        query: String,
        results: List<WebSearchResult>,
        numFollowUpQuestions: Int,
        language: String,
        nodeId: String,
        onProgress: suspend (ResearchStep) -> Unit
    ): ProcessedSearchResult {
        val systemPrompt = buildSystemPrompt()

        val contentsText = results.mapIndexed { index, result ->
            "<content url=\"${result.url}\">\n${trimContent(result.content)}\n</content>"
        }.joinToString("\n")

        val userPrompt = """
Given the following contents from a search for the query <query>$query</query>, extract up to 5 key learnings from the contents. Make sure each learning is unique and not similar to each other. The learnings should be as detailed and information dense as possible. Include any entities like people, places, companies, products, things, etc in the learnings, as well as any exact metrics, numbers, or dates. Also generate up to $numFollowUpQuestions follow-up questions that could help explore this topic further.

<contents>
$contentsText
</contents>

You MUST respond in JSON format with the following structure:
{
    "learnings": [
        {
            "url": "The source URL",
            "learning": "A detailed insight extracted from the content"
        }
    ],
    "followUpQuestions": ["Question 1", "Question 2"]
}

${buildLanguagePrompt(language)}
        """.trimIndent()

        val messages = listOf(
            ChatMessage(role = MessageRole.SYSTEM, content = systemPrompt),
            ChatMessage(role = MessageRole.USER, content = userPrompt)
        )

        val responseBuilder = StringBuilder()
        var result = ProcessedSearchResult(emptyList(), emptyList())

        try {
            aiProvider.streamChat(messages).collect { chunk ->
                when (chunk) {
                    is StreamChunk.Content -> {
                        responseBuilder.append(chunk.text)
                        onProgress(ResearchStep.ProcessingResultReasoning(nodeId, chunk.text))

                        val partialResult = tryParseSearchResult(responseBuilder.toString())
                        if (partialResult != null) {
                            result = partialResult
                            onProgress(ResearchStep.ProcessingResult(nodeId, query, result))
                        }
                    }
                    is StreamChunk.Done -> {
                        val finalResult = tryParseSearchResult(responseBuilder.toString())
                        if (finalResult != null) {
                            result = finalResult
                        }
                    }
                    is StreamChunk.Error -> {
                        Log.e(TAG, "AI error in processSearchResult: ${chunk.error}")
                    }
                    else -> {}
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing search result", e)
        }

        return result
    }

    private fun tryParseSearchQueries(text: String, parentNodeId: String): List<SearchQuery> {
        return try {
            val jsonText = extractJson(text) ?: return emptyList()
            val json = JSONObject(jsonText)
            val queriesArray = json.optJSONArray("queries") ?: return emptyList()

            val queries = mutableListOf<SearchQuery>()
            for (i in 0 until queriesArray.length()) {
                val item = queriesArray.getJSONObject(i)
                val query = item.optString("query", "")
                val goal = item.optString("researchGoal", "")
                if (query.isNotEmpty() && query != "undefined") {
                    queries.add(SearchQuery(
                        query = query,
                        researchGoal = goal,
                        nodeId = "$parentNodeId-$i"
                    ))
                }
            }
            queries
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun tryParseSearchResult(text: String): ProcessedSearchResult? {
        return try {
            val jsonText = extractJson(text) ?: return null
            val json = JSONObject(jsonText)

            val learningsArray = json.optJSONArray("learnings") ?: JSONArray()
            val learnings = mutableListOf<Learning>()
            for (i in 0 until learningsArray.length()) {
                val item = learningsArray.getJSONObject(i)
                learnings.add(Learning(
                    url = item.optString("url", ""),
                    title = null,
                    learning = item.optString("learning", "")
                ))
            }

            val followUpArray = json.optJSONArray("followUpQuestions") ?: JSONArray()
            val followUpQuestions = mutableListOf<String>()
            for (i in 0 until followUpArray.length()) {
                followUpQuestions.add(followUpArray.getString(i))
            }

            ProcessedSearchResult(learnings, followUpQuestions)
        } catch (e: Exception) {
            null
        }
    }

    private fun extractJson(text: String): String? {
        // 尝试找到 JSON 对象
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1)
        }
        return null
    }

    private fun trimContent(content: String, maxLength: Int = 8000): String {
        return if (content.length > maxLength) {
            content.take(maxLength) + "..."
        } else {
            content
        }
    }

    private fun buildSystemPrompt(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        val now = dateFormat.format(Date())

        return """
You are an expert researcher. Today is $now. Follow these instructions when responding:
- You may be asked to research subjects that is after your knowledge cutoff, assume the user is right when presented with news.
- The user is a highly experienced analyst, no need to simplify it, be as detailed as possible and make sure your response is correct.
- Be highly organized.
- Suggest solutions that I didn't think about.
- Be proactive and anticipate my needs.
- Treat me as an expert in all subject matter.
- Mistakes erode my trust, so be accurate and thorough.
- Provide detailed explanations, I'm comfortable with lots of detail.
- Value good arguments over authorities, the source is irrelevant.
- Consider new technologies and contrarian ideas, not just the conventional wisdom.
- You may use high levels of speculation or prediction, just flag it for me.
        """.trimIndent()
    }

    private fun buildLanguagePrompt(language: String, searchLanguage: String? = null): String {
        val langName = when (language) {
            "zh" -> "中文"
            "en" -> "English"
            else -> language
        }

        var prompt = "Respond in $langName."
        if (language == "zh") {
            prompt += " 在中文和英文之间添加适当的空格来提升可读性。"
        }
        if (searchLanguage != null && searchLanguage != language) {
            prompt += " Use $searchLanguage for the search queries."
        }
        return prompt
    }

    private fun buildReportPrompt(prompt: String, learningsText: String, language: String): String {
        return """
Given the following prompt from the user, write a final report on the topic using the learnings from research. Make it as detailed as possible, aim for 3 or more pages, include ALL the key insights from research.

<prompt>$prompt</prompt>

Here are all the learnings from previous research:
<learnings>
$learningsText
</learnings>

Write the report using Markdown. Be factual, NEVER lie or make things up. Cite learnings from previous research when needed, using numbered citations like "[1]". Each citation should correspond to the index of the source in your learnings list. DO NOT include the actual URLs in the report text - only use the citation numbers.

${buildLanguagePrompt(language)}

## Deep Research Report
        """.trimIndent()
    }
}
