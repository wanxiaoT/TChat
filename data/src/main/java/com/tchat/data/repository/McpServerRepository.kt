package com.tchat.data.repository

import com.tchat.data.model.McpServer
import com.tchat.data.model.McpToolDefinition
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

/**
 * MCP 服务器 Repository 接口
 */
interface McpServerRepository {
    /**
     * 获取所有 MCP 服务器
     */
    fun getAllServers(): Flow<List<McpServer>>

    /**
     * 获取已启用的 MCP 服务器
     */
    fun getEnabledServers(): Flow<List<McpServer>>

    /**
     * 根据 ID 获取服务器
     */
    suspend fun getServerById(id: String): McpServer?

    /**
     * 添加服务器
     */
    suspend fun addServer(server: McpServer)

    /**
     * 更新服务器
     */
    suspend fun updateServer(server: McpServer)

    /**
     * 删除服务器
     */
    suspend fun deleteServer(id: String)

    /**
     * 测试服务器连接
     */
    suspend fun testConnection(server: McpServer): Result<List<McpToolDefinition>>

    /**
     * 获取服务器的工具列表
     */
    suspend fun getServerTools(server: McpServer): Result<List<McpToolDefinition>>

    /**
     * 调用 MCP 工具
     */
    suspend fun callTool(server: McpServer, toolName: String, arguments: JSONObject): Result<JSONObject>
}
