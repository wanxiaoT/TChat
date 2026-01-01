package com.tchat.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * 知识条目处理状态
 */
enum class ProcessingStatus {
    PENDING,    // 待处理
    PROCESSING, // 处理中
    COMPLETED,  // 已完成
    FAILED      // 处理失败
}

/**
 * 知识条目类型
 */
enum class KnowledgeItemType {
    TEXT,  // 文本笔记
    FILE,  // 文件
    URL    // URL网页
}

/**
 * 知识条目实体
 */
@OptIn(ExperimentalUuidApi::class)
@Entity(
    tableName = "knowledge_items",
    foreignKeys = [
        ForeignKey(
            entity = KnowledgeBaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["knowledgeBaseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["knowledgeBaseId"])]
)
data class KnowledgeItemEntity(
    @PrimaryKey
    val id: String = Uuid.random().toString(),
    val knowledgeBaseId: String,
    val title: String,
    val content: String,
    val sourceType: String, // text, file, url
    val sourceUri: String? = null,
    val metadata: String? = null, // JSON string for additional metadata
    val status: String = ProcessingStatus.PENDING.name, // 处理状态
    val errorMessage: String? = null, // 错误信息
    val createdAt: Long = Instant.now().toEpochMilli(),
    val updatedAt: Long = Instant.now().toEpochMilli()
) {
    /**
     * 获取处理状态枚举
     */
    fun getProcessingStatus(): ProcessingStatus {
        return try {
            ProcessingStatus.valueOf(status)
        } catch (e: Exception) {
            ProcessingStatus.PENDING
        }
    }

    /**
     * 获取条目类型枚举
     */
    fun getItemType(): KnowledgeItemType {
        return when (sourceType.lowercase()) {
            "text", "note" -> KnowledgeItemType.TEXT
            "file" -> KnowledgeItemType.FILE
            "url" -> KnowledgeItemType.URL
            else -> KnowledgeItemType.TEXT
        }
    }
}
