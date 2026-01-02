package com.tchat.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 深度研究历史记录实体
 * learnings 存储为 JSON 字符串，在 Repository 层转换
 */
@Entity(tableName = "deep_research_history")
data class DeepResearchHistoryEntity(
    @PrimaryKey
    val id: String,
    val query: String,
    val report: String,
    val learningsJson: String, // JSON 格式存储 List<Learning>
    val startTime: Long,
    val endTime: Long,
    val status: String // "complete", "error"
)
