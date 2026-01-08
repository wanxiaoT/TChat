package com.tchat.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 消息实体
 *
 * 采用 MessagePart 架构：partsJson 字段存储序列化的消息部分列表
 * 包含文本内容、工具调用、工具结果等
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
    val role: String,  // "user", "assistant", "system", "tool"
    val partsJson: String,  // 序列化的 MessagePart 列表（核心字段）
    val timestamp: Long,
    // 统计信息
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val tokensPerSecond: Double = 0.0,
    val firstTokenLatency: Long = 0,
    // 模型名称
    val modelName: String? = null,
    // 提供商ID（用于按提供商统计token）
    val providerId: String? = null,
    // 变体支持（JSON 格式存储）
    val variantsJson: String? = null,
    val selectedVariantIndex: Int = 0
)
