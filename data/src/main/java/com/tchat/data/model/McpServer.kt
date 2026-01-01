package com.tchat.data.model

import java.util.UUID

/**
 * MCP 服务器配置
 */
data class McpServer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    /** 服务器类型：sse 或 streamable_http */
    val type: McpServerType = McpServerType.SSE,
    /** 服务器 URL */
    val url: String,
    /** 是否启用 */
    val enabled: Boolean = true,
    /** 连接超时（秒） */
    val timeout: Int = 30,
    /** 自定义请求头（JSON格式） */
    val headers: String = "{}",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * MCP 服务器类型
 */
enum class McpServerType(val value: String) {
    /** Server-Sent Events 传输 */
    SSE("sse"),
    /** Streamable HTTP 传输 */
    STREAMABLE_HTTP("streamable_http");

    companion object {
        fun fromValue(value: String): McpServerType {
            return entries.find { it.value == value } ?: SSE
        }
    }
}

/**
 * MCP 工具定义（从服务器获取）
 */
data class McpToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: McpInputSchema?
)

/**
 * MCP 工具输入 Schema
 */
data class McpInputSchema(
    val type: String = "object",
    val properties: Map<String, McpPropertyDef> = emptyMap(),
    val required: List<String> = emptyList()
)

/**
 * MCP 属性定义
 */
data class McpPropertyDef(
    val type: String,
    val description: String? = null,
    val enum: List<String>? = null
)
