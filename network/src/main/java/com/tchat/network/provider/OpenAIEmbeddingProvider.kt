package com.tchat.network.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OpenAI Embedding API 提供者
 * 支持 OpenAI API 格式的所有提供商（包括兼容API）
 */
class OpenAIEmbeddingProvider(
    private val apiKey: String,
    baseUrl: String = "https://api.openai.com/v1"
) : EmbeddingProvider {

    private val normalizedBaseUrl = baseUrl.trimEnd('/')

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .protocols(listOf(Protocol.HTTP_1_1))
        .build()

    private var currentCall: Call? = null

    override suspend fun embed(texts: List<String>, model: String): List<FloatArray> = withContext(Dispatchers.IO) {
        // OpenAI API 限制每次最多 2048 个输入，这里按 100 个分批处理
        val batchSize = 100
        val results = mutableListOf<FloatArray>()

        texts.chunked(batchSize).forEach { batch ->
            val batchResults = embedBatch(batch, model)
            results.addAll(batchResults)
        }

        results
    }

    private suspend fun embedBatch(texts: List<String>, model: String): List<FloatArray> = suspendCancellableCoroutine { continuation ->
        val jsonBody = buildRequestBody(texts, model)
        val request = buildRequest(jsonBody)

        currentCall = client.newCall(request)

        continuation.invokeOnCancellation {
            currentCall?.cancel()
        }

        currentCall?.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCompleted) {
                    continuation.resumeWithException(
                        EmbeddingException.NetworkError("网络连接失败: ${e.message}", e)
                    )
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (!response.isSuccessful) {
                        val error = handleErrorResponse(response)
                        continuation.resumeWithException(error)
                        return
                    }

                    val responseBody = response.body?.string() ?: ""
                    val embeddings = parseResponse(responseBody)
                    continuation.resume(embeddings)
                } catch (e: Exception) {
                    if (!continuation.isCompleted) {
                        continuation.resumeWithException(
                            EmbeddingException.UnknownError("解析响应失败: ${e.message}", e)
                        )
                    }
                } finally {
                    response.close()
                }
            }
        })
    }

    override fun cancel() {
        currentCall?.cancel()
    }

    private fun buildRequestBody(texts: List<String>, model: String): String {
        val jsonObject = JSONObject()
        jsonObject.put("model", model)

        val inputArray = JSONArray()
        texts.forEach { inputArray.put(it) }
        jsonObject.put("input", inputArray)

        return jsonObject.toString()
    }

    private fun buildRequest(jsonBody: String): Request {
        return Request.Builder()
            .url("$normalizedBaseUrl/embeddings")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()
    }

    private fun parseResponse(responseBody: String): List<FloatArray> {
        val json = JSONObject(responseBody)
        val dataArray = json.getJSONArray("data")

        val embeddings = mutableListOf<Pair<Int, FloatArray>>()

        for (i in 0 until dataArray.length()) {
            val item = dataArray.getJSONObject(i)
            val index = item.getInt("index")
            val embeddingArray = item.getJSONArray("embedding")

            val embedding = FloatArray(embeddingArray.length()) { j ->
                embeddingArray.getDouble(j).toFloat()
            }

            embeddings.add(index to embedding)
        }

        // 按索引排序，确保返回顺序与输入一致
        return embeddings.sortedBy { it.first }.map { it.second }
    }

    private fun handleErrorResponse(response: Response): EmbeddingException {
        val errorBody = response.body?.string() ?: ""

        val detailedMessage = try {
            val json = JSONObject(errorBody)
            val error = json.optJSONObject("error")
            error?.optString("message", errorBody) ?: errorBody
        } catch (e: Exception) {
            errorBody
        }

        return when (response.code) {
            401 -> EmbeddingException.AuthenticationError("API Key 无效或已过期\n$detailedMessage")
            403 -> EmbeddingException.AuthenticationError("API 访问被拒绝\n$detailedMessage")
            429 -> EmbeddingException.RateLimitError("请求过于频繁，请稍后再试\n$detailedMessage")
            400 -> EmbeddingException.InvalidRequestError("无效的请求参数\n$detailedMessage")
            404 -> EmbeddingException.InvalidRequestError("模型不存在或 API 端点错误\n$detailedMessage")
            500, 502, 503, 504 -> EmbeddingException.ServiceUnavailableError("API 服务暂时不可用\n$detailedMessage")
            else -> EmbeddingException.UnknownError("API 错误 (${response.code}): ${response.message}\n$detailedMessage")
        }
    }

    companion object {
        /**
         * 常用的 OpenAI Embedding 模型
         */
        val DEFAULT_MODELS = listOf(
            "text-embedding-3-small",
            "text-embedding-3-large",
            "text-embedding-ada-002"
        )
    }
}
