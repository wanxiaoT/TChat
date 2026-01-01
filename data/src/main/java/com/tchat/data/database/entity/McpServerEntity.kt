package com.tchat.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * MCP 服务器数据库实体
 */
@Entity(tableName = "mcp_servers")
data class McpServerEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String,
    val type: String, // sse 或 streamable_http
    val url: String,
    val enabled: Boolean,
    val timeout: Int,
    val headers: String, // JSON 格式的请求头
    val createdAt: Long,
    val updatedAt: Long
)
