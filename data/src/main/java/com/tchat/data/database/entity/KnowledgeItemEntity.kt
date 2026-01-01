package com.tchat.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

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
    val createdAt: Long = Instant.now().toEpochMilli(),
    val updatedAt: Long = Instant.now().toEpochMilli()
)
