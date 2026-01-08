package com.tchat.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 应用设置数据库实体
 * 使用单行表存储所有设置，通过 JSON 序列化复杂对象
 */
@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey
    val id: Int = 1, // 固定为1，确保只有一行数据
    val currentProviderId: String = "",
    val currentModel: String = "",
    val currentAssistantId: String = "",
    val providersJson: String = "[]", // JSON序列化的供应商配置列表
    val deepResearchSettingsJson: String = "{}", // JSON序列化的深度研究设置
    val providerGridColumnCount: Int = 1,
    val regexRulesJson: String = "[]", // JSON序列化的正则规则列表
    val tokenRecordingStatus: String = "ENABLED" // Token记录状态: ENABLED, PAUSED, DISABLED
)
