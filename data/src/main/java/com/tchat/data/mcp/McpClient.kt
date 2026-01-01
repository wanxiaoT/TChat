package com.tchat.data.mcp

import com.tchat.data.model.McpServer
import com.tchat.data.model.McpToolDefinition
import org.json.JSONObject

/**
 * MCP 客户端接口
 */
interface McpClient {
    /**
     * 连接到 MCP 服务器
     */
    suspend fun connect(): Result<Unit>

    /**
     * 断开连接
     */
    suspend fun disconnect()

    /**
     * 获取服务器提供的工具列表
     */
    suspend fun listTools(): Result<List<McpToolDefinition>>

    /**
     * 调用工具
     */
    suspend fun callTool(name: String, arguments: JSONObject): Result<JSONObject>

    /**
     * 检查连接状态
     */
    fun isConnected(): Boolean
}
