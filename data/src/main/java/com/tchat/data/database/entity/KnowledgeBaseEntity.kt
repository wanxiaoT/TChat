package com.tchat.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * 知识库实体
 */
@OptIn(ExperimentalUuidApi::class)
@Entity(
    tableName = "knowledge_bases",
    indices = [Index(value = ["updatedAt"])]
)
data class KnowledgeBaseEntity(
    @PrimaryKey
    val id: String = Uuid.random().toString(),
    val name: String,
    val description: String? = null,
    val embeddingProviderId: String,
    val embeddingModelId: String,
    val createdAt: Long = Instant.now().toEpochMilli(),
    val updatedAt: Long = Instant.now().toEpochMilli()
)
