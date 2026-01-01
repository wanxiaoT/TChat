package com.tchat.data.model

import java.util.UUID

/**
 * 助手数据模型
 */
data class Assistant(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val avatar: String? = null,
    val systemPrompt: String = "",
    val temperature: Float? = null,
    val topP: Float? = null,
    val maxTokens: Int? = null,
    val contextMessageSize: Int = 64,
    val streamOutput: Boolean = true,
    val localTools: List<LocalToolOption> = emptyList(),
    /** 关联的知识库ID，用于RAG检索 */
    val knowledgeBaseId: String? = null,
    /** 关联的MCP服务器ID列表 */
    val mcpServerIds: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * 创建默认助手
         */
        fun createDefault(): Assistant = Assistant(
            name = "默认助手",
            systemPrompt = "你是一个有帮助的AI助手。"
        )
    }
}

/**
 * 快捷消息
 */
data class QuickMessage(
    val title: String = "",
    val content: String = ""
)
