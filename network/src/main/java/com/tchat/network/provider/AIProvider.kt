package com.tchat.network.provider

import kotlinx.coroutines.flow.Flow

/**
 * AI 服务提供商接口
 * 支持异步流式聊天
 */
interface AIProvider {
    /**
     * 发送消息并接收流式响应
     * @param messages 消息列表
     * @return 流式文本响应
     */
    suspend fun streamChat(messages: List<ChatMessage>): Flow<StreamChunk>

    /**
     * 取消当前请求
     */
    fun cancel()
}

/**
 * 聊天消息
 */
data class ChatMessage(
    val role: MessageRole,
    val content: String
)

/**
 * 消息角色
 */
enum class MessageRole(val value: String) {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant")
}

/**
 * 流式数据块
 */
sealed class StreamChunk {
    /** 文本内容块 */
    data class Content(val text: String) : StreamChunk()

    /** 流结束标记（包含统计信息） */
    data class Done(
        val inputTokens: Int = 0,  // 输入 token 数
        val outputTokens: Int = 0,  // 输出 token 数
        val tokensPerSecond: Double = 0.0,  // 每秒 token 数（TPS）
        val firstTokenLatency: Long = 0  // 首字延时（毫秒）
    ) : StreamChunk()

    /** 错误 */
    data class Error(val error: AIProviderException) : StreamChunk()
}

/**
 * AI Provider 异常
 */
sealed class AIProviderException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** 认证失败 */
    data class AuthenticationError(val detail: String) : AIProviderException("认证失败: $detail")

    /** API 配额超限 */
    data class RateLimitError(val detail: String) : AIProviderException("请求过于频繁: $detail")

    /** 无效请求 */
    data class InvalidRequestError(val detail: String) : AIProviderException("无效请求: $detail")

    /** 服务不可用 */
    data class ServiceUnavailableError(val detail: String) : AIProviderException("服务不可用: $detail")

    /** 网络错误 */
    data class NetworkError(val detail: String, val originalError: Throwable) : AIProviderException("网络错误: $detail", originalError)

    /** 未知错误 */
    data class UnknownError(val detail: String, val originalError: Throwable? = null) : AIProviderException("未知错误: $detail", originalError)
}
