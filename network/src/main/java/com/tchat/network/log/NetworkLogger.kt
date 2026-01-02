package com.tchat.network.log

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 网络请求日志条目
 */
data class NetworkLogEntry(
    val id: Long = System.currentTimeMillis(),
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val provider: String,
    val model: String,
    val url: String,
    val method: String = "POST",
    val requestHeaders: Map<String, String> = emptyMap(),
    val requestBody: String,
    val responseCode: Int? = null,
    val responseHeaders: Map<String, String> = emptyMap(),
    val responseBody: String? = null,
    val error: String? = null,
    val durationMs: Long? = null,
    val status: NetworkLogStatus = NetworkLogStatus.PENDING
) {
    fun formattedTimestamp(): String {
        return timestamp.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"))
    }

    fun formattedDate(): String {
        return timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    }
}

/**
 * 日志状态
 */
enum class NetworkLogStatus {
    PENDING,    // 请求中
    SUCCESS,    // 成功
    ERROR       // 失败
}

/**
 * 网络日志管理器（单例）
 *
 * 用于记录所有 AI API 的请求和响应信息
 */
object NetworkLogger {
    private val logs = CopyOnWriteArrayList<NetworkLogEntry>()
    private const val MAX_LOGS = 100

    // 日志变化监听器
    private val listeners = mutableListOf<() -> Unit>()

    /**
     * 记录新的请求
     */
    fun logRequest(
        provider: String,
        model: String,
        url: String,
        headers: Map<String, String>,
        body: String
    ): Long {
        val entry = NetworkLogEntry(
            provider = provider,
            model = model,
            url = url,
            requestHeaders = headers,
            requestBody = body
        )
        addLog(entry)
        return entry.id
    }

    /**
     * 更新请求的响应信息
     */
    fun logResponse(
        requestId: Long,
        responseCode: Int,
        responseHeaders: Map<String, String> = emptyMap(),
        responseBody: String,
        durationMs: Long
    ) {
        val index = logs.indexOfFirst { it.id == requestId }
        if (index >= 0) {
            val updated = logs[index].copy(
                responseCode = responseCode,
                responseHeaders = responseHeaders,
                responseBody = responseBody,
                durationMs = durationMs,
                status = if (responseCode in 200..299) NetworkLogStatus.SUCCESS else NetworkLogStatus.ERROR
            )
            logs[index] = updated
            notifyListeners()
        }
    }

    /**
     * 记录请求错误
     */
    fun logError(
        requestId: Long,
        error: String,
        durationMs: Long
    ) {
        val index = logs.indexOfFirst { it.id == requestId }
        if (index >= 0) {
            val updated = logs[index].copy(
                error = error,
                durationMs = durationMs,
                status = NetworkLogStatus.ERROR
            )
            logs[index] = updated
            notifyListeners()
        }
    }

    /**
     * 获取所有日志（按时间倒序）
     */
    fun getLogs(): List<NetworkLogEntry> {
        return logs.sortedByDescending { it.timestamp }
    }

    /**
     * 清除所有日志
     */
    fun clear() {
        logs.clear()
        notifyListeners()
    }

    /**
     * 添加日志变化监听器
     */
    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    /**
     * 移除日志变化监听器
     */
    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun addLog(entry: NetworkLogEntry) {
        logs.add(entry)
        // 保持最大日志数量
        while (logs.size > MAX_LOGS) {
            logs.removeAt(0)
        }
        notifyListeners()
    }

    private fun notifyListeners() {
        listeners.forEach { it.invoke() }
    }
}
