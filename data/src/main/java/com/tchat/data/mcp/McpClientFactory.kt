package com.tchat.data.mcp

import com.tchat.data.model.McpServer
import com.tchat.data.model.McpServerType

/**
 * MCP 客户端工厂
 */
object McpClientFactory {

    /**
     * 根据服务器配置创建对应的 MCP 客户端
     */
    fun create(server: McpServer): McpClient {
        return when (server.type) {
            McpServerType.SSE -> McpSseClient(server)
            McpServerType.STREAMABLE_HTTP -> McpSseClient(server) // 使用相同实现
        }
    }
}
