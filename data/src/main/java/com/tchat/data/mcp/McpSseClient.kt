package com.tchat.data.mcp

import android.util.Log
import com.tchat.data.model.McpInputSchema
import com.tchat.data.model.McpPropertyDef
import com.tchat.data.model.McpServer
import com.tchat.data.model.McpServerType
import com.tchat.data.model.McpToolDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * MCP SSE 客户端实现
 */
class McpSseClient(
    private val server: McpServer
) : McpClient {

    companion object {
        private const val TAG = "McpSseClient"
        private const val JSON_RPC_VERSION = "2.0"
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(server.timeout.toLong(), TimeUnit.SECONDS)
            .readTimeout(server.timeout.toLong(), TimeUnit.SECONDS)
            .writeTimeout(server.timeout.toLong(), TimeUnit.SECONDS)
            .build()
    }

    private val requestIdCounter = AtomicInteger(0)
    private var sessionId: String? = null
    private var messagesEndpoint: String? = null
    private var connected = false

    override suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Connecting to MCP server: ${server.url}")

            // 对于 SSE 类型，先建立 SSE 连接获取 endpoint
            if (server.type == McpServerType.SSE) {
                establishSseConnection()
            } else {
                // Streamable HTTP 直接使用 URL
                messagesEndpoint = server.url
            }

            // 发送 initialize 请求
            val initResult = sendInitialize()
            if (initResult.isFailure) {
                return@withContext Result.failure(initResult.exceptionOrNull()!!)
            }

            connected = true
            Log.d(TAG, "Connected to MCP server successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to MCP server", e)
            Result.failure(e)
        }
    }

    private suspend fun establishSseConnection() = suspendCancellableCoroutine { cont ->
        val request = Request.Builder()
            .url(server.url)
            .apply {
                parseHeaders().forEach { (key, value) ->
                    addHeader(key, value)
                }
            }
            .build()

        val eventSourceFactory = EventSources.createFactory(client)
        var resumed = false

        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                Log.d(TAG, "SSE Event: type=$type, data=$data")
                if (type == "endpoint" && !resumed) {
                    resumed = true
                    // 解析 endpoint URL
                    messagesEndpoint = resolveEndpoint(data.trim())
                    cont.resume(Unit)
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                Log.e(TAG, "SSE connection failed", t)
                if (!resumed) {
                    resumed = true
                    cont.resumeWithException(t ?: IOException("SSE connection failed"))
                }
            }
        }

        eventSourceFactory.newEventSource(request, listener)
    }

    private fun resolveEndpoint(endpoint: String): String {
        return if (endpoint.startsWith("http")) {
            endpoint
        } else {
            // 相对路径，基于服务器 URL 解析
            val baseUrl = server.url.substringBeforeLast("/")
            "$baseUrl$endpoint"
        }
    }

    private suspend fun sendInitialize(): Result<JSONObject> {
        val params = JSONObject().apply {
            put("protocolVersion", "2024-11-05")
            put("capabilities", JSONObject().apply {
                put("tools", JSONObject())
            })
            put("clientInfo", JSONObject().apply {
                put("name", "TChat")
                put("version", "1.0.0")
            })
        }
        return sendRequest("initialize", params)
    }

    override suspend fun disconnect() {
        connected = false
        sessionId = null
        messagesEndpoint = null
    }

    override suspend fun listTools(): Result<List<McpToolDefinition>> = withContext(Dispatchers.IO) {
        try {
            val result = sendRequest("tools/list", JSONObject())
            if (result.isFailure) {
                return@withContext Result.failure(result.exceptionOrNull()!!)
            }

            val response = result.getOrThrow()
            val toolsArray = response.optJSONArray("tools") ?: JSONArray()
            val tools = mutableListOf<McpToolDefinition>()

            for (i in 0 until toolsArray.length()) {
                val toolJson = toolsArray.getJSONObject(i)
                tools.add(parseToolDefinition(toolJson))
            }

            Result.success(tools)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list tools", e)
            Result.failure(e)
        }
    }

    private fun parseToolDefinition(json: JSONObject): McpToolDefinition {
        val inputSchemaJson = json.optJSONObject("inputSchema")
        val inputSchema = inputSchemaJson?.let { parseInputSchema(it) }

        return McpToolDefinition(
            name = json.getString("name"),
            description = json.optString("description", ""),
            inputSchema = inputSchema
        )
    }

    private fun parseInputSchema(json: JSONObject): McpInputSchema {
        val propertiesJson = json.optJSONObject("properties") ?: JSONObject()
        val properties = mutableMapOf<String, McpPropertyDef>()

        propertiesJson.keys().forEach { key ->
            val propJson = propertiesJson.getJSONObject(key)
            properties[key] = McpPropertyDef(
                type = propJson.optString("type", "string"),
                description = propJson.optString("description", null),
                enum = propJson.optJSONArray("enum")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                }
            )
        }

        val requiredArray = json.optJSONArray("required")
        val required = requiredArray?.let { arr ->
            (0 until arr.length()).map { arr.getString(it) }
        } ?: emptyList()

        return McpInputSchema(
            type = json.optString("type", "object"),
            properties = properties,
            required = required
        )
    }

    override suspend fun callTool(name: String, arguments: JSONObject): Result<JSONObject> =
        withContext(Dispatchers.IO) {
            try {
                val params = JSONObject().apply {
                    put("name", name)
                    put("arguments", arguments)
                }
                val result = sendRequest("tools/call", params)
                if (result.isFailure) {
                    return@withContext Result.failure(result.exceptionOrNull()!!)
                }

                val response = result.getOrThrow()
                Result.success(response)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to call tool: $name", e)
                Result.failure(e)
            }
        }

    override fun isConnected(): Boolean = connected

    private suspend fun sendRequest(method: String, params: JSONObject): Result<JSONObject> =
        withContext(Dispatchers.IO) {
            val endpoint = messagesEndpoint
                ?: return@withContext Result.failure(IOException("Not connected"))

            val requestId = requestIdCounter.incrementAndGet()
            val requestBody = JSONObject().apply {
                put("jsonrpc", JSON_RPC_VERSION)
                put("id", requestId)
                put("method", method)
                put("params", params)
            }

            Log.d(TAG, "Sending request: $method")

            val request = Request.Builder()
                .url(endpoint)
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .apply {
                    parseHeaders().forEach { (key, value) ->
                        addHeader(key, value)
                    }
                    sessionId?.let { addHeader("X-Session-Id", it) }
                }
                .build()

            try {
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("HTTP ${response.code}: ${response.message}")
                    )
                }

                // 保存 session ID
                response.header("X-Session-Id")?.let { sessionId = it }

                val responseBody = response.body?.string()
                    ?: return@withContext Result.failure(IOException("Empty response"))

                val jsonResponse = JSONObject(responseBody)

                // 检查错误
                jsonResponse.optJSONObject("error")?.let { error ->
                    val errorMsg = error.optString("message", "Unknown error")
                    return@withContext Result.failure(IOException("MCP Error: $errorMsg"))
                }

                val resultObj = jsonResponse.optJSONObject("result") ?: JSONObject()
                Result.success(resultObj)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun parseHeaders(): Map<String, String> {
        return try {
            val headersJson = JSONObject(server.headers)
            val headers = mutableMapOf<String, String>()
            headersJson.keys().forEach { key ->
                headers[key] = headersJson.getString(key)
            }
            headers
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
