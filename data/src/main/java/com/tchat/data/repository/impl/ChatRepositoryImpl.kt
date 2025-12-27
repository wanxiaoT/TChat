package com.tchat.data.repository.impl

import com.tchat.core.util.Result
import com.tchat.data.model.Chat
import com.tchat.data.model.Message
import com.tchat.data.model.MessageRole
import com.tchat.data.repository.ChatRepository
import com.tchat.network.provider.AIProvider
import com.tchat.network.provider.ChatMessage
import com.tchat.network.provider.StreamChunk
import com.tchat.network.provider.MessageRole as ProviderMessageRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import java.util.UUID

class ChatRepositoryImpl(
    private val aiProvider: AIProvider
) : ChatRepository {

    private val chats = MutableStateFlow<List<Chat>>(emptyList())

    override fun getAllChats(): Flow<List<Chat>> = chats

    override fun getChatById(chatId: String): Flow<Chat?> = flow {
        chats.collect { chatList ->
            emit(chatList.find { it.id == chatId })
        }
    }

    override suspend fun createChat(title: String): Result<Chat> {
        return try {
            val newChat = Chat(
                id = UUID.randomUUID().toString(),
                title = title
            )
            val currentChats = chats.value.toMutableList()
            currentChats.add(newChat)
            chats.value = currentChats
            Result.Success(newChat)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun deleteChat(chatId: String): Result<Unit> {
        return try {
            val currentChats = chats.value.toMutableList()
            currentChats.removeAll { it.id == chatId }
            chats.value = currentChats
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun sendMessage(chatId: String, content: String): Flow<Result<Message>> = flow {
        try {
            // 添加用户消息
            val userMessage = Message(
                id = UUID.randomUUID().toString(),
                chatId = chatId,
                content = content,
                role = MessageRole.USER
            )
            addMessage(userMessage)
            emit(Result.Success(userMessage))

            // 获取聊天历史
            val chat = chats.value.find { it.id == chatId }
            val messages = chat?.messages?.map {
                ChatMessage(
                    role = when (it.role) {
                        MessageRole.USER -> ProviderMessageRole.USER
                        MessageRole.ASSISTANT -> ProviderMessageRole.ASSISTANT
                        MessageRole.SYSTEM -> ProviderMessageRole.SYSTEM
                    },
                    content = it.content
                )
            } ?: emptyList()

            // 流式接收 AI 响应
            var assistantContent = ""
            val assistantId = UUID.randomUUID().toString()

            aiProvider.streamChat(messages).collect { chunk ->
                when (chunk) {
                    is StreamChunk.Content -> {
                        assistantContent += chunk.text
                        val assistantMessage = Message(
                            id = assistantId,
                            chatId = chatId,
                            content = assistantContent,
                            role = MessageRole.ASSISTANT,
                            isStreaming = true
                        )
                        emit(Result.Success(assistantMessage))
                    }
                    is StreamChunk.Done -> {
                        // 流结束，保存完整消息
                        val finalMessage = Message(
                            id = assistantId,
                            chatId = chatId,
                            content = assistantContent,
                            role = MessageRole.ASSISTANT,
                            isStreaming = false
                        )
                        addMessage(finalMessage)
                        emit(Result.Success(finalMessage))
                    }
                    is StreamChunk.Error -> {
                        emit(Result.Error(chunk.error))
                    }
                }
            }
        } catch (e: Exception) {
            emit(Result.Error(e))
        }
    }

    override suspend fun addMessage(message: Message): Result<Unit> {
        return try {
            val currentChats = chats.value.toMutableList()
            val chatIndex = currentChats.indexOfFirst { it.id == message.chatId }
            if (chatIndex != -1) {
                val chat = currentChats[chatIndex]
                val updatedMessages = chat.messages.toMutableList()
                updatedMessages.add(message)
                currentChats[chatIndex] = chat.copy(
                    messages = updatedMessages,
                    updatedAt = System.currentTimeMillis()
                )
                chats.value = currentChats
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
