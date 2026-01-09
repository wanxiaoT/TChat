package com.tchat.data.apikey

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Key 测试结果
 */
sealed class KeyTestResult {
    data class Success(
        val keyId: String,
        val responseTimeMs: Long
    ) : KeyTestResult()

    data class AuthError(
        val keyId: String,
        val message: String
    ) : KeyTestResult()

    data class RateLimited(
        val keyId: String,
        val retryAfterSec: Int? = null
    ) : KeyTestResult()

    data class NetworkError(
        val keyId: String,
        val message: String
    ) : KeyTestResult()

    data class UnknownError(
        val keyId: String,
        val message: String
    ) : KeyTestResult()
}

/**
 * 批量测试进度
 */
data class KeyTestProgress(
    val total: Int,
    val completed: Int,
    val currentKeyId: String?,
    val results: List<KeyTestResult>
)

/**
 * Provider 类型
 */
enum class ProviderType {
    OPENAI,
    ANTHROPIC,
    GEMINI
}

/**
 * API Key 健康检测服务
 * 用于测试 API Key 的有效性
 */
class ApiKeyHealthService {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 测试单个 API Key
     *
     * @param keyInfo Key 信息
     * @param providerType Provider 类型
     * @param endpoint API 端点
     * @param model 测试用模型
     * @return 测试结果
     */
    suspend fun testKey(
        keyInfo: ApiKeyInfo,
        providerType: ProviderType,
        endpoint: String,
        model: String
    ): KeyTestResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        try {
            val request = buildTestRequest(keyInfo.key, providerType, endpoint, model)
            val response = httpClient.newCall(request).execute()
            val responseTime = System.currentTimeMillis() - startTime

            when (response.code) {
                200 -> KeyTestResult.Success(keyInfo.id, responseTime)
                401, 403 -> {
                    val errorBody = response.body?.string() ?: "认证失败"
                    KeyTestResult.AuthError(keyInfo.id, parseErrorMessage(errorBody))
                }
                429 -> {
                    val retryAfter = response.header("Retry-After")?.toIntOrNull()
                    KeyTestResult.RateLimited(keyInfo.id, retryAfter)
                }
                else -> {
                    val errorBody = response.body?.string() ?: "未知错误"
                    KeyTestResult.UnknownError(keyInfo.id, "HTTP ${response.code}: ${parseErrorMessage(errorBody)}")
                }
            }
        } catch (e: java.net.UnknownHostException) {
            KeyTestResult.NetworkError(keyInfo.id, "无法连接到服务器: ${e.message}")
        } catch (e: java.net.SocketTimeoutException) {
            KeyTestResult.NetworkError(keyInfo.id, "连接超时")
        } catch (e: java.io.IOException) {
            KeyTestResult.NetworkError(keyInfo.id, "网络错误: ${e.message}")
        } catch (e: Exception) {
            KeyTestResult.UnknownError(keyInfo.id, "测试失败: ${e.message}")
        }
    }

    /**
     * 批量测试所有 API Keys
     *
     * @param keys Key 列表
     * @param providerType Provider 类型
     * @param endpoint API 端点
     * @param model 测试用模型
     * @param delayMs 每次测试之间的延迟（毫秒）
     * @return 测试进度流
     */
    fun testAllKeys(
        keys: List<ApiKeyInfo>,
        providerType: ProviderType,
        endpoint: String,
        model: String,
        delayMs: Long = 500
    ): Flow<KeyTestProgress> = flow {
        val results = mutableListOf<KeyTestResult>()

        emit(KeyTestProgress(
            total = keys.size,
            completed = 0,
            currentKeyId = null,
            results = emptyList()
        ))

        keys.forEachIndexed { index, keyInfo ->
            emit(KeyTestProgress(
                total = keys.size,
                completed = index,
                currentKeyId = keyInfo.id,
                results = results.toList()
            ))

            val result = testKey(keyInfo, providerType, endpoint, model)
            results.add(result)

            emit(KeyTestProgress(
                total = keys.size,
                completed = index + 1,
                currentKeyId = null,
                results = results.toList()
            ))

            // 添加延迟避免请求过快
            if (index < keys.size - 1) {
                delay(delayMs)
            }
        }
    }

    /**
     * 构建测试请求
     */
    private fun buildTestRequest(
        apiKey: String,
        providerType: ProviderType,
        endpoint: String,
        model: String
    ): Request {
        return when (providerType) {
            ProviderType.OPENAI -> buildOpenAITestRequest(apiKey, endpoint, model)
            ProviderType.ANTHROPIC -> buildAnthropicTestRequest(apiKey, endpoint, model)
            ProviderType.GEMINI -> buildGeminiTestRequest(apiKey, endpoint, model)
        }
    }

    /**
     * 构建 OpenAI 格式的测试请求
     */
    private fun buildOpenAITestRequest(apiKey: String, endpoint: String, model: String): Request {
        val normalizedEndpoint = endpoint.trimEnd('/')
        val url = "$normalizedEndpoint/chat/completions"

        val requestBody = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "Hi")
                })
            })
            put("max_tokens", 1)
        }

        return Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()
    }

    /**
     * 构建 Anthropic 格式的测试请求
     */
    private fun buildAnthropicTestRequest(apiKey: String, endpoint: String, model: String): Request {
        val normalizedEndpoint = endpoint.trimEnd('/')
        val baseUrl = if (normalizedEndpoint.endsWith("/v1")) {
            normalizedEndpoint
        } else {
            "$normalizedEndpoint/v1"
        }
        val url = "$baseUrl/messages"

        val requestBody = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "Hi")
                })
            })
            put("max_tokens", 1)
        }

        return Request.Builder()
            .url(url)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()
    }

    /**
     * 构建 Gemini 格式的测试请求
     */
    private fun buildGeminiTestRequest(apiKey: String, endpoint: String, model: String): Request {
        val normalizedEndpoint = endpoint.trimEnd('/')
        val url = "$normalizedEndpoint/models/$model:generateContent?key=$apiKey"

        val requestBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "Hi")
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("maxOutputTokens", 1)
            })
        }

        return Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()
    }

    /**
     * 解析错误消息
     */
    private fun parseErrorMessage(errorBody: String): String {
        return try {
            val json = JSONObject(errorBody)
            json.optJSONObject("error")?.optString("message")
                ?: json.optString("message")
                ?: json.optString("error")
                ?: errorBody.take(200)
        } catch (e: Exception) {
            errorBody.take(200)
        }
    }
}
