package com.tchat.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 消息实体
 *
 * 支持工具调用存储
 */
@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["chatId"])]
)
data class MessageEntity(
    @PrimaryKey
    val id: String,
    val chatId: String,
    val content: String,
    val role: String,  // "user", "assistant", "system", "tool"
    val timestamp: Long,
    // 统计信息
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val tokensPerSecond: Double = 0.0,
    val firstTokenLatency: Long = 0,
    // 变体支持（JSON 格式存储）
    val variantsJson: String? = null,
    val selectedVariantIndex: Int = 0,
    // 工具调用支持
    val toolCallId: String? = null,      // 工具结果对应的调用ID
    val toolName: String? = null,        // 工具名称（用于工具结果消息）
    val toolCallsJson: String? = null,   // AI发起的工具调用（JSON数组）
    val toolResultsJson: String? = null  // 工具执行结果（JSON数组，用于显示）
)
