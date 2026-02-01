package com.tchat.network.provider

import kotlinx.coroutines.flow.Flow

/**
 * AI 服务提供商接口
 * 支持异步流式聊天和工具调用
 */
interface AIProvider {
    /**
     * 发送消息并接收流式响应
     * @param messages 消息列表
     * @return 流式文本响应
     */
    suspend fun streamChat(messages: List<ChatMessage>): Flow<StreamChunk>

    /**
     * 发送消息并接收流式响应（支持工具调用）
     * @param messages 消息列表
     * @param tools 可用的工具列表
     * @return 流式响应（可能包含工具调用）
     */
    suspend fun streamChatWithTools(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>
    ): Flow<StreamChunk> = streamChat(messages) // 默认实现忽略工具

    /**
     * 取消当前请求
     */
    fun cancel()

    /**
     * 生成图片（可选能力）
     *
     * 默认实现：不支持，直接抛出异常。
     * 目前主要用于 OpenAI 兼容的 Images API。
     */
    suspend fun generateImage(
        prompt: String,
        options: ImageGenerationOptions = ImageGenerationOptions()
    ): ImageGenerationResult {
        throw AIProviderException.InvalidRequestError("当前服务商不支持图片生成")
    }
}

/**
 * 自定义请求参数
 * 用于配置 AI 请求体中的额外参数
 */
data class CustomParams(
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
    val presencePenalty: Float? = null,
    val frequencyPenalty: Float? = null,
    val repetitionPenalty: Float? = null,
    val maxTokens: Int? = null,
    val extraParams: String = "{}"  // 自定义 JSON 参数，直接合并到请求体
)

/**
 * 工具定义
 */
data class ToolDefinition(
    val name: String,
    val description: String,
    val parametersJson: String?  // JSON字符串格式的参数定义
)

/**
 * 消息内容部分（支持多模态）
 */
sealed class MessageContent {
    data class Text(val text: String) : MessageContent()
    data class Image(
        val base64Data: String,
        val mimeType: String = "image/png"  // image/png, image/jpeg, image/webp, image/gif
    ) : MessageContent()

    /**
     * 视频内容（目前仅 Gemini 支持 inlineData 方式输入）
     */
    data class Video(
        val base64Data: String,
        val mimeType: String = "video/mp4"  // video/mp4, video/webm
    ) : MessageContent()
}

/**
 * 图片生成参数
 *
 * 注意：不同服务商支持的字段不同，这里保持最小集合。
 */
data class ImageGenerationOptions(
    val model: String? = null,
    val size: String? = null, // e.g. "1024x1024"
    val quality: String? = null, // e.g. "standard" / "hd"
    val n: Int = 1
)

data class GeneratedImage(
    val base64Data: String,
    val mimeType: String = "image/png"
)

data class ImageGenerationResult(
    val images: List<GeneratedImage>
)

/**
 * 聊天消息
 */
data class ChatMessage(
    val role: MessageRole,
    val content: String,
    val contentParts: List<MessageContent>? = null,  // 多模态内容（新增）
    val toolCalls: List<ToolCallInfo>? = null,  // AI返回的工具调用
    val toolCallId: String? = null,              // 工具结果的调用ID
    val name: String? = null                     // 工具名称（用于工具结果）
)

/**
 * 消息角色
 */
enum class MessageRole(val value: String) {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant"),
    TOOL("tool")
}

/**
 * 工具调用信息
 */
data class ToolCallInfo(
    val id: String,
    val name: String,
    val arguments: String  // JSON字符串
)

/**
 * 流式数据块
 */
sealed class StreamChunk {
    /** 文本内容块 */
    data class Content(val text: String) : StreamChunk()

    /** 工具调用块 */
    data class ToolCall(val toolCalls: List<ToolCallInfo>) : StreamChunk()

    /** 流结束标记（包含统计信息） */
    data class Done(
        val inputTokens: Int = 0,
        val outputTokens: Int = 0,
        val tokensPerSecond: Double = 0.0,
        val firstTokenLatency: Long = 0
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
