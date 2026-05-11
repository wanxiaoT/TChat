package com.tchat.data

import com.tchat.core.util.Result
import com.tchat.data.model.Chat
import com.tchat.data.model.ChatSearchResult
import com.tchat.data.model.Message
import com.tchat.data.model.MessagePart
import com.tchat.data.model.MessageRole
import com.tchat.data.repository.ChatConfig
import com.tchat.data.repository.ChatRepository
import com.tchat.data.repository.MessageResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MessageSenderTest {

    @Test
    fun `per chat config stays isolated for existing chats`() = runTest {
        val repository = FakeChatRepository()
        val sender = MessageSender(this).apply { init(repository) }

        sender.setConfig("chat-a", ChatConfig(modelName = "model-a"))
        sender.setConfig("chat-b", ChatConfig(modelName = "model-b"))

        sender.sendMessage(chatId = "chat-a", content = "hello a")
        sender.sendMessage(chatId = "chat-b", content = "hello b")

        advanceUntilIdle()

        assertEquals("model-a", repository.sentConfigs["chat-a"]?.modelName)
        assertEquals("model-b", repository.sentConfigs["chat-b"]?.modelName)
    }

    @Test
    fun `new chat request binds config to created real chat id`() = runTest {
        val repository = FakeChatRepository()
        val sender = MessageSender(this).apply { init(repository) }
        val config = ChatConfig(modelName = "new-chat-model")

        var createdChatId: String? = null
        sender.sendMessageToNewChat(
            content = "hello",
            config = config,
            onChatCreated = { createdChatId = it }
        )

        advanceUntilIdle()
        assertNotNull(createdChatId)

        sender.sendMessage(chatId = createdChatId!!, content = "follow up")
        advanceUntilIdle()

        assertEquals("new-chat-model", repository.sentConfigs[createdChatId!!]?.modelName)
    }

    private class FakeChatRepository : ChatRepository {
        val sentConfigs = linkedMapOf<String, ChatConfig?>()

        override fun getAllChats(): Flow<List<Chat>> = emptyFlow()

        override fun getChatById(chatId: String): Flow<Chat?> = emptyFlow()

        override fun getMessagesByChatId(chatId: String): Flow<List<Message>> = emptyFlow()

        override fun observeRecentMessages(chatId: String, limit: Int): Flow<List<Message>> = emptyFlow()

        override fun observeBookmarkedMessages(limit: Int): Flow<List<ChatSearchResult>> = emptyFlow()

        override suspend fun getMessagesBefore(
            chatId: String,
            beforeTimestamp: Long,
            limit: Int
        ): List<Message> = emptyList()

        override suspend fun searchMessages(query: String, limit: Int): List<ChatSearchResult> = emptyList()

        override suspend fun createChat(title: String): Result<Chat> {
            error("Not needed in this test")
        }

        override suspend fun updateChatTitle(chatId: String, title: String): Result<Unit> {
            return Result.Success(Unit)
        }

        override suspend fun updateChatPinned(chatId: String, isPinned: Boolean): Result<Unit> {
            return Result.Success(Unit)
        }

        override suspend fun deleteChat(chatId: String): Result<Unit> {
            return Result.Success(Unit)
        }

        override suspend fun sendMessage(
            chatId: String,
            content: String,
            config: ChatConfig?,
            mediaParts: List<MessagePart>,
            replyToMessageId: String?,
            replyPreview: String?
        ): Flow<Result<Message>> = flow {
            sentConfigs[chatId] = config
            emit(Result.Success(assistantMessage(chatId)))
        }

        override suspend fun addMessage(message: Message): Result<Unit> {
            return Result.Success(Unit)
        }

        override suspend fun updateMessageBookmarked(messageId: String, isBookmarked: Boolean): Result<Unit> {
            return Result.Success(Unit)
        }

        override suspend fun createBranchFromMessage(messageId: String): Result<Chat> {
            return Result.Success(Chat(id = "branch-chat-id", title = "branch"))
        }

        override suspend fun sendMessageToNewChat(
            content: String,
            config: ChatConfig?,
            mediaParts: List<MessagePart>,
            replyToMessageId: String?,
            replyPreview: String?
        ): Flow<Result<MessageResult>> = flow {
            emit(
                Result.Success(
                    MessageResult(
                        chatId = GENERATED_CHAT_ID,
                        message = assistantMessage(GENERATED_CHAT_ID)
                    )
                )
            )
        }

        override suspend fun generateImage(
            chatId: String,
            prompt: String,
            config: ChatConfig?
        ): Flow<Result<Message>> = flow {
            sentConfigs[chatId] = config
            emit(Result.Success(assistantMessage(chatId)))
        }

        override suspend fun generateImageToNewChat(
            prompt: String,
            config: ChatConfig?
        ): Flow<Result<MessageResult>> = flow {
            emit(
                Result.Success(
                    MessageResult(
                        chatId = GENERATED_CHAT_ID,
                        message = assistantMessage(GENERATED_CHAT_ID)
                    )
                )
            )
        }

        override suspend fun regenerateMessage(
            chatId: String,
            userMessageId: String,
            aiMessageId: String,
            config: ChatConfig?
        ): Flow<Result<Message>> = flow {
            sentConfigs[chatId] = config
            emit(Result.Success(assistantMessage(chatId)))
        }

        override suspend fun selectVariant(messageId: String, variantIndex: Int): Result<Unit> {
            return Result.Success(Unit)
        }

        override suspend fun getMessageById(messageId: String): Message? = null

        override suspend fun deleteMessage(messageId: String): Result<Unit> {
            return Result.Success(Unit)
        }

        private fun assistantMessage(chatId: String): Message {
            return Message(
                id = "assistant-$chatId",
                chatId = chatId,
                role = MessageRole.ASSISTANT,
                parts = listOf(MessagePart.Text("ok"))
            )
        }

        private companion object {
            const val GENERATED_CHAT_ID = "generated-chat-id"
        }
    }
}
