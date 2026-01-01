package com.tchat.data.mcp

import com.tchat.data.model.McpServer
import com.tchat.data.model.McpToolDefinition
import com.tchat.data.repository.McpServerRepository
import com.tchat.data.tool.InputSchema
import com.tchat.data.tool.PropertyDef
import com.tchat.data.tool.Tool
import org.json.JSONObject

/**
 * MCP 工具服务
 * 负责将 MCP 服务器的工具转换为本地 Tool 对象
 */
class McpToolService(
    private val mcpRepository: McpServerRepository
) {
    // 缓存已加载的工具
    private val toolCache = mutableMapOf<String, List<CachedMcpTool>>()

    /**
     * 根据服务器 ID 列表获取所有可用的工具
     */
    suspend fun getToolsForServers(serverIds: List<String>): List<Tool> {
        val tools = mutableListOf<Tool>()

        for (serverId in serverIds) {
            val server = mcpRepository.getServerById(serverId) ?: continue
            if (!server.enabled) continue

            val serverTools = getToolsForServer(server)
            tools.addAll(serverTools)
        }

        return tools
    }

    /**
     * 获取单个服务器的工具列表
     */
    private suspend fun getToolsForServer(server: McpServer): List<Tool> {
        // 尝试从缓存获取
        val cached = toolCache[server.id]
        if (cached != null) {
            return cached.map { it.toTool(server) }
        }

        // 从服务器获取工具定义
        val result = mcpRepository.getServerTools(server)
        if (result.isFailure) {
            return emptyList()
        }

        val mcpTools = result.getOrThrow()
        val cachedTools = mcpTools.map { CachedMcpTool(it) }
        toolCache[server.id] = cachedTools

        return cachedTools.map { it.toTool(server) }
    }

    /**
     * 清除指定服务器的工具缓存
     */
    fun clearCache(serverId: String) {
        toolCache.remove(serverId)
    }

    /**
     * 清除所有缓存
     */
    fun clearAllCache() {
        toolCache.clear()
    }

    /**
     * 缓存的 MCP 工具定义
     */
    private inner class CachedMcpTool(
        private val definition: McpToolDefinition
    ) {
        fun toTool(server: McpServer): Tool {
            return Tool(
                name = "${server.id}__${definition.name}",
                description = "[${server.name}] ${definition.description}",
                parameters = { convertInputSchema(definition.inputSchema) },
                execute = { args -> executeMcpTool(server, definition.name, args) }
            )
        }
    }

    /**
     * 转换 MCP 输入 Schema 为本地 InputSchema
     */
    private fun convertInputSchema(
        mcpSchema: com.tchat.data.model.McpInputSchema?
    ): InputSchema? {
        if (mcpSchema == null) return null

        val properties = mcpSchema.properties.mapValues { (_, prop) ->
            PropertyDef(
                type = prop.type,
                description = prop.description,
                enum = prop.enum
            )
        }

        return InputSchema.Obj(
            properties = properties,
            required = mcpSchema.required.ifEmpty { null }
        )
    }

    /**
     * 执行 MCP 工具调用
     */
    private suspend fun executeMcpTool(
        server: McpServer,
        toolName: String,
        arguments: JSONObject
    ): JSONObject {
        val result = mcpRepository.callTool(server, toolName, arguments)

        return if (result.isSuccess) {
            result.getOrThrow()
        } else {
            JSONObject().apply {
                put("error", result.exceptionOrNull()?.message ?: "MCP 工具调用失败")
            }
        }
    }
}
