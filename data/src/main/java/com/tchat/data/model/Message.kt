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
    val selectedVariantIndex: Int = 0
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
    SYSTEM
}
