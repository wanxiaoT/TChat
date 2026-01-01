package com.tchat.data.repository.impl

import com.tchat.data.database.dao.McpServerDao
import com.tchat.data.database.entity.McpServerEntity
import com.tchat.data.mcp.McpClient
import com.tchat.data.mcp.McpClientFactory
import com.tchat.data.model.McpServer
import com.tchat.data.model.McpServerType
import com.tchat.data.model.McpToolDefinition
import com.tchat.data.repository.McpServerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

/**
 * MCP 服务器 Repository 实现
 */
class McpServerRepositoryImpl(
    private val mcpServerDao: McpServerDao
) : McpServerRepository {

    // 缓存已连接的客户端
    private val clientCache = mutableMapOf<String, McpClient>()

    override fun getAllServers(): Flow<List<McpServer>> {
        return mcpServerDao.getAllServers().map { entities ->
            entities.map { it.toModel() }
        }
    }

    override fun getEnabledServers(): Flow<List<McpServer>> {
        return mcpServerDao.getEnabledServers().map { entities ->
            entities.map { it.toModel() }
        }
    }

    override suspend fun getServerById(id: String): McpServer? {
        return mcpServerDao.getServerById(id)?.toModel()
    }

    override suspend fun addServer(server: McpServer) {
        mcpServerDao.insertServer(server.toEntity())
    }

    override suspend fun updateServer(server: McpServer) {
        mcpServerDao.updateServer(server.toEntity())
        // 清除缓存的客户端
        clientCache.remove(server.id)?.disconnect()
    }

    override suspend fun deleteServer(id: String) {
        mcpServerDao.deleteServerById(id)
        clientCache.remove(id)?.disconnect()
    }

    override suspend fun testConnection(server: McpServer): Result<List<McpToolDefinition>> {
        val client = McpClientFactory.create(server)
        return try {
            val connectResult = client.connect()
            if (connectResult.isFailure) {
                return Result.failure(connectResult.exceptionOrNull()!!)
            }
            val tools = client.listTools()
            client.disconnect()
            tools
        } catch (e: Exception) {
            client.disconnect()
            Result.failure(e)
        }
    }

    override suspend fun getServerTools(server: McpServer): Result<List<McpToolDefinition>> {
        val client = getOrCreateClient(server)
        return client.listTools()
    }

    override suspend fun callTool(
        server: McpServer,
        toolName: String,
        arguments: JSONObject
    ): Result<JSONObject> {
        val client = getOrCreateClient(server)
        return client.callTool(toolName, arguments)
    }

    private suspend fun getOrCreateClient(server: McpServer): McpClient {
        val cached = clientCache[server.id]
        if (cached != null && cached.isConnected()) {
            return cached
        }

        val client = McpClientFactory.create(server)
        client.connect()
        clientCache[server.id] = client
        return client
    }

    private fun McpServerEntity.toModel(): McpServer {
        return McpServer(
            id = id,
            name = name,
            description = description,
            type = McpServerType.fromValue(type),
            url = url,
            enabled = enabled,
            timeout = timeout,
            headers = headers,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun McpServer.toEntity(): McpServerEntity {
        return McpServerEntity(
            id = id,
            name = name,
            description = description,
            type = type.value,
            url = url,
            enabled = enabled,
            timeout = timeout,
            headers = headers,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
