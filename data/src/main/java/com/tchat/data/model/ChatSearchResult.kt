package com.tchat.data.model

data class ChatSearchResult(
    val messageId: String,
    val chatId: String,
    val chatTitle: String,
    val role: MessageRole,
    val snippet: String,
    val timestamp: Long,
    val modelName: String? = null,
    val providerId: String? = null,
    val groupId: String? = null,
    val groupAssistantName: String? = null
)
