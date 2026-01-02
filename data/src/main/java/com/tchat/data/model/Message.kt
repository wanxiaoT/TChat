package com.tchat.data.model

/**
 * 消息部分 - 消息可以包含多种类型的部分（文本、工具调用、工具结果等）
 *
 * 采用类似 rikkahub 的 MessagePart 架构设计
 */
sealed class MessagePart {
    /**
     * 文本内容部分
     */
    data class Text(
        val content: String
    ) : MessagePart()

    /**
     * 工具调用部分 - AI 请求执行工具
     */
    data class ToolCall(
        val toolCallId: String,
        val toolName: String,
        val arguments: String  // JSON 格式的参数
    ) : MessagePart()

    /**
     * 工具执行结果部分
     */
    data class ToolResult(
        val toolCallId: String,
        val toolName: String,
        val arguments: String,      // 原始调用参数（JSON）
        val result: String,         // 执行结果（JSON）
        val isError: Boolean = false,
        val executionTimeMs: Long = 0
    ) : MessagePart()
}

/**
 * 消息变体 - 存储 AI 回复的多个版本
 */
data class MessageVariant(
    val content: String,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val tokensPerSecond: Double = 0.0,
    val firstTokenLatency: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 工具调用信息（保留用于兼容性）
 * @deprecated 使用 MessagePart.ToolCall 代替
 */
@Deprecated("Use MessagePart.ToolCall instead")
data class ToolCallData(
    val id: String,
    val name: String,
    val arguments: String
)

/**
 * 工具执行结果（保留用于兼容性）
 * @deprecated 使用 MessagePart.ToolResult 代替
 */
@Deprecated("Use MessagePart.ToolResult instead")
data class ToolResultData(
    val toolCallId: String,
    val name: String,
    val arguments: String = "{}",
    val result: String,
    val isError: Boolean = false,
    val executionTimeMs: Long = 0
)

/**
 * 消息数据模型
 *
 * 采用 MessagePart 架构：消息由多个部分（parts）组成
 * - Text: 文本内容
 * - ToolCall: AI 请求执行的工具调用
 * - ToolResult: 工具执行结果
 */
data class Message(
    val id: String,
    val chatId: String,
    val role: MessageRole,
    val parts: List<MessagePart>,  // 消息部分列表（核心字段）
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
    // Token 统计信息
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val tokensPerSecond: Double = 0.0,
    val firstTokenLatency: Long = 0,
    // 模型名称
    val modelName: String? = null,
    // 变体支持（仅 AI 消息使用）
    val variants: List<MessageVariant> = emptyList(),
    val selectedVariantIndex: Int = 0
) {
    /**
     * 获取文本内容（从 parts 中提取所有 Text 部分）
     */
    fun getTextContent(): String {
        return parts.filterIsInstance<MessagePart.Text>()
            .joinToString("\n") { it.content }
    }

    /**
     * 获取所有工具调用
     */
    fun getToolCalls(): List<MessagePart.ToolCall> {
        return parts.filterIsInstance<MessagePart.ToolCall>()
    }

    /**
     * 获取所有工具结果
     */
    fun getToolResults(): List<MessagePart.ToolResult> {
        return parts.filterIsInstance<MessagePart.ToolResult>()
    }

    /**
     * 获取当前选中的变体内容
     */
    fun getCurrentContent(): String {
        return if (variants.isNotEmpty() && selectedVariantIndex in variants.indices) {
            variants[selectedVariantIndex].content
        } else {
            getTextContent()
        }
    }

    /**
     * 获取当前选中的变体统计信息
     */
    fun getCurrentStats(): MessageVariant? {
        return if (variants.isNotEmpty() && selectedVariantIndex in variants.indices) {
            variants[selectedVariantIndex]
        } else {
            null
        }
    }

    /**
     * 变体总数
     */
    fun variantCount(): Int = if (variants.isEmpty()) 1 else variants.size

    companion object {
        /**
         * 创建文本消息的便捷方法
         */
        fun createTextMessage(
            id: String,
            chatId: String,
            role: MessageRole,
            content: String,
            timestamp: Long = System.currentTimeMillis()
        ): Message {
            return Message(
                id = id,
                chatId = chatId,
                role = role,
                parts = listOf(MessagePart.Text(content)),
                timestamp = timestamp
            )
        }
    }
}

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL  // 工具执行结果
}
