package com.tchat.data.repository

import com.tchat.core.util.Result
import com.tchat.data.model.Chat
import com.tchat.data.model.Message
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun getAllChats(): Flow<List<Chat>>
    fun getChatById(chatId: String): Flow<Chat?>
    suspend fun createChat(title: String): Result<Chat>
    suspend fun deleteChat(chatId: String): Result<Unit>
    suspend fun sendMessage(chatId: String, content: String): Flow<Result<Message>>
    suspend fun addMessage(message: Message): Result<Unit>
}
