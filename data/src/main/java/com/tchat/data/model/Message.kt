package com.tchat.data.model

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
 * 工具调用信息
 */
data class ToolCallData(
    val id: String,
    val name: String,
    val arguments: String
)

/**
 * 工具执行结果
 * 
 * 包含完整的工具调用信息：调用ID、工具名称、输入参数、执行结果、执行状态和耗时
 */
data class ToolResultData(
    val toolCallId: String,
    val name: String,
    val arguments: String = "{}",  // 工具调用参数（JSON格式）
    val result: String,
    val isError: Boolean = false,
    val executionTimeMs: Long = 0  // 工具执行耗时（毫秒）
)

data class Message(
    val id: String,
    val chatId: String,
    val content: String,
    val role: MessageRole,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
    // Token 统计信息（当前选中变体的）
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val tokensPerSecond: Double = 0.0,
    val firstTokenLatency: Long = 0,
    // 变体支持（仅 AI 消息使用）
    val variants: List<MessageVariant> = emptyList(),
    val selectedVariantIndex: Int = 0,
    // 工具调用支持
    val toolCalls: List<ToolCallData>? = null,      // AI发起的工具调用
    val toolCallId: String? = null,                  // 工具结果对应的调用ID
    val toolResults: List<ToolResultData>? = null    // 工具执行结果（用于显示）
) {
    /**
     * 获取当前选中的变体内容
     * 如果没有变体或索引无效，返回主内容
     */
    fun getCurrentContent(): String {
        return if (variants.isNotEmpty() && selectedVariantIndex in variants.indices) {
            variants[selectedVariantIndex].content
        } else {
            content
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
}

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL  // 工具执行结果
}
