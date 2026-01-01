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
 * Google Gemini Embedding API 提供者
 * 使用 Gemini 的 embedContent 和 batchEmbedContents 接口
 */
class GeminiEmbeddingProvider(
    private val apiKey: String,
    baseUrl: String = "https://generativelanguage.googleapis.com/v1"
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
        if (texts.size == 1) {
            listOf(embedSingle(texts.first(), model))
        } else {
            // Gemini 批量嵌入每次最多 100 个
            val batchSize = 100
            val results = mutableListOf<FloatArray>()

            texts.chunked(batchSize).forEach { batch ->
                val batchResults = embedBatch(batch, model)
                results.addAll(batchResults)
            }

            results
        }
    }

    private suspend fun embedSingle(text: String, model: String): FloatArray = suspendCancellableCoroutine { continuation ->
        val jsonBody = buildSingleRequestBody(text)
        val request = buildSingleRequest(model, jsonBody)

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
                    val embedding = parseSingleResponse(responseBody)
                    continuation.resume(embedding)
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

    private suspend fun embedBatch(texts: List<String>, model: String): List<FloatArray> = suspendCancellableCoroutine { continuation ->
        val jsonBody = buildBatchRequestBody(texts, model)
        val request = buildBatchRequest(model, jsonBody)

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
                    val embeddings = parseBatchResponse(responseBody)
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

    private fun buildSingleRequestBody(text: String): String {
        val jsonObject = JSONObject()

        val contentObject = JSONObject()
        val partsArray = JSONArray()
        val partObject = JSONObject()
        partObject.put("text", text)
        partsArray.put(partObject)
        contentObject.put("parts", partsArray)

        jsonObject.put("content", contentObject)

        return jsonObject.toString()
    }

    private fun buildBatchRequestBody(texts: List<String>, model: String): String {
        val jsonObject = JSONObject()

        val requestsArray = JSONArray()
        texts.forEach { text ->
            val requestObject = JSONObject()
            requestObject.put("model", "models/$model")

            val contentObject = JSONObject()
            val partsArray = JSONArray()
            val partObject = JSONObject()
            partObject.put("text", text)
            partsArray.put(partObject)
            contentObject.put("parts", partsArray)

            requestObject.put("content", contentObject)
            requestsArray.put(requestObject)
        }

        jsonObject.put("requests", requestsArray)

        return jsonObject.toString()
    }

    private fun buildSingleRequest(model: String, jsonBody: String): Request {
        return Request.Builder()
            .url("$normalizedBaseUrl/models/$model:embedContent?key=$apiKey")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()
    }

    private fun buildBatchRequest(model: String, jsonBody: String): Request {
        return Request.Builder()
            .url("$normalizedBaseUrl/models/$model:batchEmbedContents?key=$apiKey")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()
    }

    private fun parseSingleResponse(responseBody: String): FloatArray {
        val json = JSONObject(responseBody)
        val embeddingObject = json.getJSONObject("embedding")
        val valuesArray = embeddingObject.getJSONArray("values")

        return FloatArray(valuesArray.length()) { i ->
            valuesArray.getDouble(i).toFloat()
        }
    }

    private fun parseBatchResponse(responseBody: String): List<FloatArray> {
        val json = JSONObject(responseBody)
        val embeddingsArray = json.getJSONArray("embeddings")

        return (0 until embeddingsArray.length()).map { i ->
            val embeddingObject = embeddingsArray.getJSONObject(i)
            val valuesArray = embeddingObject.getJSONArray("values")
            FloatArray(valuesArray.length()) { j ->
                valuesArray.getDouble(j).toFloat()
            }
        }
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
            401, 403 -> EmbeddingException.AuthenticationError("API Key 无效或已过期\n$detailedMessage")
            429 -> EmbeddingException.RateLimitError("请求过于频繁，请稍后再试\n$detailedMessage")
            400 -> EmbeddingException.InvalidRequestError("无效的请求参数\n$detailedMessage")
            404 -> EmbeddingException.InvalidRequestError("模型不存在或 API 端点错误\n$detailedMessage")
            500, 502, 503, 504 -> EmbeddingException.ServiceUnavailableError("API 服务暂时不可用\n$detailedMessage")
            else -> EmbeddingException.UnknownError("API 错误 (${response.code}): ${response.message}\n$detailedMessage")
        }
    }

    companion object {
        /**
         * Gemini Embedding 模型
         */
        val DEFAULT_MODELS = listOf(
            "text-embedding-004",
            "embedding-001"
        )
    }
}
