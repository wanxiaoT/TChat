package com.tchat.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * 知识块实体 (用于向量检索)
 */
@OptIn(ExperimentalUuidApi::class)
@Entity(
    tableName = "knowledge_chunks",
    foreignKeys = [
        ForeignKey(
            entity = KnowledgeItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["itemId"]), Index(value = ["knowledgeBaseId"])]
)
data class KnowledgeChunkEntity(
    @PrimaryKey
    val id: String = Uuid.random().toString(),
    val itemId: String,
    val knowledgeBaseId: String,
    val content: String,
    val embedding: String, // JSON string of float array
    val chunkIndex: Int,
    val createdAt: Long = Instant.now().toEpochMilli()
)
