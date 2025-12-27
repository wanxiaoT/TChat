package com.tchat.network.api

import kotlinx.coroutines.flow.Flow

interface AIService {
    suspend fun sendMessage(
        messages: List<ChatMessage>,
        onStream: (String) -> Unit
    ): Flow<String>
}

data class ChatMessage(
    val role: String,
    val content: String
)
