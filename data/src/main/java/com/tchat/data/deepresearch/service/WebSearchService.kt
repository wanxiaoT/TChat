package com.tchat.data.deepresearch.service

import android.util.Log
import com.tchat.data.deepresearch.model.WebSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 网络搜索服务接口
 */
interface WebSearchService {
    /**
     * 执行网络搜索
     * @param query 搜索查询
     * @param maxResults 最大结果数
     * @param language 搜索语言
     * @return 搜索结果列表
     */
    suspend fun search(
        query: String,
        maxResults: Int = 5,
        language: String = "en"
    ): Result<List<WebSearchResult>>
}

/**
 * Tavily API 搜索服务实现
 * https://tavily.com - 每月 1000 次免费调用
 */
class TavilySearchService(
    private val apiKey: String,
    private val advancedSearch: Boolean = false,
    private val searchTopic: String = "general"  // general, news, finance
) : WebSearchService {

    companion object {
        private const val TAG = "TavilySearchService"
        private const val BASE_URL = "https://api.tavily.com/search"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun search(
        query: String,
        maxResults: Int,
        language: String
    ): Result<List<WebSearchResult>> = withContext(Dispatchers.IO) {
        try {
            val requestBody = JSONObject().apply {
                put("api_key", apiKey)
                put("query", query)
                put("max_results", maxResults)
                put("search_depth", if (advancedSearch) "advanced" else "basic")
                put("topic", searchTopic)
                put("include_raw_content", false)
                put("include_images", false)
            }

            val request = Request.Builder()
                .url(BASE_URL)
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            Log.d(TAG, "Searching: $query")

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "Search failed: ${response.code} - $errorBody")
                return@withContext Result.failure(
                    Exception("Tavily API error: ${response.code} - $errorBody")
                )
            }

            val responseBody = response.body?.string()
                ?: return@withContext Result.failure(Exception("Empty response"))

            val json = JSONObject(responseBody)
            val resultsArray = json.optJSONArray("results") ?: run {
                Log.w(TAG, "No results found for: $query")
                return@withContext Result.success(emptyList())
            }

            val results = mutableListOf<WebSearchResult>()
            for (i in 0 until resultsArray.length()) {
                val item = resultsArray.getJSONObject(i)
                val url = item.optString("url", "")
                val content = item.optString("content", "")

                if (url.isNotEmpty() && content.isNotEmpty()) {
                    results.add(
                        WebSearchResult(
                            url = url,
                            title = item.optString("title").takeIf { it.isNotEmpty() },
                            content = content
                        )
                    )
                }
            }

            Log.d(TAG, "Found ${results.size} results for: $query")
            Result.success(results)

        } catch (e: Exception) {
            Log.e(TAG, "Search error", e)
            Result.failure(e)
        }
    }
}

/**
 * Firecrawl API 搜索服务实现
 * 支持自部署
 */
class FirecrawlSearchService(
    private val apiKey: String,
    private val baseUrl: String = "https://api.firecrawl.dev"
) : WebSearchService {

    companion object {
        private const val TAG = "FirecrawlSearchService"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun search(
        query: String,
        maxResults: Int,
        language: String
    ): Result<List<WebSearchResult>> = withContext(Dispatchers.IO) {
        try {
            val normalizedBaseUrl = baseUrl.trimEnd('/')
            val requestBody = JSONObject().apply {
                put("query", query)
                put("limit", maxResults)
                put("scrapeOptions", JSONObject().apply {
                    put("formats", org.json.JSONArray().apply {
                        put("markdown")
                    })
                })
            }

            val request = Request.Builder()
                .url("$normalizedBaseUrl/v1/search")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            Log.d(TAG, "Searching: $query")

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "Search failed: ${response.code} - $errorBody")
                return@withContext Result.failure(
                    Exception("Firecrawl API error: ${response.code} - $errorBody")
                )
            }

            val responseBody = response.body?.string()
                ?: return@withContext Result.failure(Exception("Empty response"))

            val json = JSONObject(responseBody)

            // 检查错误
            if (json.has("error")) {
                val error = json.getString("error")
                return@withContext Result.failure(Exception("Firecrawl error: $error"))
            }

            val dataArray = json.optJSONArray("data") ?: run {
                Log.w(TAG, "No data found for: $query")
                return@withContext Result.success(emptyList())
            }

            val results = mutableListOf<WebSearchResult>()
            for (i in 0 until dataArray.length()) {
                val item = dataArray.getJSONObject(i)
                val url = item.optString("url", "")
                val markdown = item.optString("markdown", "")

                if (url.isNotEmpty() && markdown.isNotEmpty()) {
                    results.add(
                        WebSearchResult(
                            url = url,
                            title = item.optString("title").takeIf { it.isNotEmpty() },
                            content = markdown
                        )
                    )
                }
            }

            Log.d(TAG, "Found ${results.size} results for: $query")
            Result.success(results)

        } catch (e: Exception) {
            Log.e(TAG, "Search error", e)
            Result.failure(e)
        }
    }
}

/**
 * 搜索服务类型
 */
enum class WebSearchProvider(val displayName: String) {
    TAVILY("Tavily"),
    FIRECRAWL("Firecrawl")
}

/**
 * 搜索服务工厂
 */
object WebSearchServiceFactory {
    fun create(
        provider: WebSearchProvider,
        apiKey: String,
        baseUrl: String? = null,
        advancedSearch: Boolean = false,
        searchTopic: String = "general"
    ): WebSearchService {
        return when (provider) {
            WebSearchProvider.TAVILY -> TavilySearchService(
                apiKey = apiKey,
                advancedSearch = advancedSearch,
                searchTopic = searchTopic
            )
            WebSearchProvider.FIRECRAWL -> FirecrawlSearchService(
                apiKey = apiKey,
                baseUrl = baseUrl ?: "https://api.firecrawl.dev"
            )
        }
    }
}
