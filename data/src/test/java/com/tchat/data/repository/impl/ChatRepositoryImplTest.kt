package com.tchat.data.repository.impl

import com.tchat.core.util.Result
import com.tchat.data.database.dao.ChatDao
import com.tchat.data.database.dao.MessageDao
import com.tchat.data.database.dao.MessageSearchRow
import com.tchat.data.database.dao.ModelUsageStat
import com.tchat.data.database.dao.ProviderUsageStat
import com.tchat.data.database.entity.ChatEntity
import com.tchat.data.database.entity.MessageEntity
import com.tchat.data.database.entity.MessageSearchIndexEntity
import com.tchat.data.model.MessagePart
import com.tchat.data.repository.ChatConfig
import com.tchat.data.repository.ChatSearchRepository
import com.tchat.data.util.MessagePartSerializer
import com.tchat.network.provider.AIProvider
import com.tchat.network.provider.AIProviderFactory
import com.tchat.network.provider.ChatMessage
import com.tchat.network.provider.StreamChunk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ChatRepositoryImplTest {

    @Test
    fun `sendMessage only sends configured recent context to provider`() = runTest {
        val provider = CapturingProvider()
        val chatDao = FakeChatDao()
        val messageDao = FakeMessageDao()
        val repository = ChatRepositoryImpl(
            aiProvider = provider,
            chatDao = chatDao,
            messageDao = messageDao,
            providerType = AIProviderFactory.ProviderType.OPENAI,
            mediaDir = File("build/test_media")
        )

        chatDao.insertChat(
            ChatEntity(
                id = CHAT_ID,
                title = "Existing",
                createdAt = 1L,
                updatedAt = 1L,
                isNameManuallyEdited = true
            )
        )
        (1..5).forEach { index ->
            messageDao.insertMessage(
                messageEntity(
                    id = "message-$index",
                    chatId = CHAT_ID,
                    role = if (index % 2 == 0) "assistant" else "user",
                    content = "old-$index",
                    timestamp = index.toLong()
                )
            )
        }

        repository.sendMessage(
            chatId = CHAT_ID,
            content = "latest",
            config = ChatConfig(contextMessageSize = 3)
        ).toList()

        assertEquals(
            listOf("old-4", "old-5", "latest"),
            provider.capturedMessages.map { it.content }
        )
    }

    @Test
    fun `searchMessages finds message content tool results variants and metadata`() = runTest {
        val chatDao = FakeChatDao()
        val messageDao = FakeMessageDao(chatTitleForId = chatDao::titleForChat)
        val repository = ChatSearchRepository(messageDao)

        chatDao.insertChat(
            ChatEntity(
                id = CHAT_ID,
                title = "Project Alpha",
                createdAt = 1L,
                updatedAt = 1L
            )
        )

        messageDao.insertMessage(
            messageEntity(
                id = "message-text",
                chatId = CHAT_ID,
                role = "user",
                content = "Find the launch checklist",
                timestamp = 1L
            )
        )
        messageDao.insertMessage(
            messageEntity(
                id = "message-tool",
                chatId = CHAT_ID,
                role = "assistant",
                parts = listOf(
                    MessagePart.ToolResult(
                        toolCallId = "tool-1",
                        toolName = "read_file",
                        arguments = """{"path":"notes.md"}""",
                        result = """{"content":"launch checklist status is ready"}"""
                    )
                ),
                timestamp = 2L
            )
        )
        messageDao.insertMessage(
            messageEntity(
                id = "message-variant",
                chatId = CHAT_ID,
                role = "assistant",
                content = "Base answer",
                timestamp = 3L,
                variantsJson = variantsJson("Archived launch checklist variant")
            )
        )
        messageDao.insertMessage(
            messageEntity(
                id = "message-model",
                chatId = CHAT_ID,
                role = "assistant",
                content = "Model metadata only",
                timestamp = 4L,
                modelName = "gpt-launch"
            )
        )

        val checklistResults = repository.searchMessages("launch checklist")

        assertEquals(
            listOf("message-variant", "message-tool", "message-text"),
            checklistResults.map { it.messageId }
        )
        assertEquals("Project Alpha", checklistResults.first().chatTitle)
        assertTrue(checklistResults.first().snippet.contains("launch checklist", ignoreCase = true))

        val modelResults = repository.searchMessages("gpt-launch")

        assertEquals(listOf("message-model"), modelResults.map { it.messageId })
        assertEquals("gpt-launch", modelResults.single().modelName)
    }

    private class CapturingProvider : AIProvider {
        var capturedMessages: List<ChatMessage> = emptyList()

        override suspend fun streamChat(messages: List<ChatMessage>): Flow<StreamChunk> = flow {
            capturedMessages = messages
            emit(StreamChunk.Content("ok"))
            emit(StreamChunk.Done(inputTokens = 3, outputTokens = 1))
        }

        override fun cancel() = Unit
    }

    private class FakeChatDao : ChatDao {
        private val chats = linkedMapOf<String, ChatEntity>()

        override fun getAllChats(): Flow<List<ChatEntity>> = flow {
            emit(chats.values.sortedByDescending { it.updatedAt })
        }

        override fun observeChatById(chatId: String): Flow<ChatEntity?> = flow {
            emit(chats[chatId])
        }

        override suspend fun getChatById(chatId: String): ChatEntity? = chats[chatId]

        override suspend fun insertChat(chat: ChatEntity) {
            chats[chat.id] = chat
        }

        override suspend fun updateChat(chat: ChatEntity) {
            chats[chat.id] = chat
        }

        override suspend fun updateChatTitle(chatId: String, title: String, updatedAt: Long) {
            chats[chatId] = chats.getValue(chatId).copy(title = title, updatedAt = updatedAt)
        }

        override suspend fun updateChatTimestamp(chatId: String, updatedAt: Long) {
            chats[chatId] = chats.getValue(chatId).copy(updatedAt = updatedAt)
        }

        override suspend fun updateChatPinned(chatId: String, isPinned: Boolean) {
            chats[chatId] = chats.getValue(chatId).copy(isPinned = isPinned)
        }

        override suspend fun deleteChat(chatId: String) {
            chats.remove(chatId)
        }

        override suspend fun deleteAllChats() {
            chats.clear()
        }

        fun titleForChat(chatId: String): String {
            return chats[chatId]?.title ?: chatId
        }
    }

    private class FakeMessageDao(
        private val chatTitleForId: (String) -> String = { it }
    ) : MessageDao {
        private val messages = mutableListOf<MessageEntity>()

        override fun getMessagesByChatId(chatId: String): Flow<List<MessageEntity>> = flow {
            emit(sortedForChat(chatId))
        }

        override suspend fun getMessagesByChatIdOnce(chatId: String): List<MessageEntity> {
            return sortedForChat(chatId)
        }

        override suspend fun getRecentMessagesByChatIdOnce(
            chatId: String,
            limit: Int
        ): List<MessageEntity> {
            return sortedForChat(chatId)
                .sortedByDescending { it.timestamp }
                .take(limit)
                .sortedWith(messageOrder)
        }

        override suspend fun getMessagesUpTo(
            chatId: String,
            upToTimestamp: Long
        ): List<MessageEntity> {
            return sortedForChat(chatId).filter { it.timestamp <= upToTimestamp }
        }

        override suspend fun getMessagesUpTo(
            chatId: String,
            upToTimestamp: Long,
            limit: Int
        ): List<MessageEntity> {
            return sortedForChat(chatId)
                .filter { it.timestamp <= upToTimestamp }
                .sortedByDescending { it.timestamp }
                .take(limit)
                .sortedWith(messageOrder)
        }

        override fun observeRecentMessages(chatId: String, limit: Int): Flow<List<MessageEntity>> = flow {
            emit(getRecentMessagesByChatIdOnce(chatId, limit))
        }

        override suspend fun getMessagesBefore(
            chatId: String,
            beforeTimestamp: Long,
            limit: Int
        ): List<MessageEntity> {
            return sortedForChat(chatId)
                .filter { it.timestamp < beforeTimestamp }
                .takeLast(limit)
        }

        override suspend fun getMessageById(messageId: String): MessageEntity? {
            return messages.firstOrNull { it.id == messageId }
        }

        override suspend fun searchMessages(pattern: String, limit: Int): List<MessageSearchRow> {
            val query = pattern.toFakeSearchQuery()
            return messages
                .filter { message ->
                    listOfNotNull(
                        chatTitleForId(message.chatId),
                        message.partsJson,
                        message.variantsJson,
                        message.modelName,
                        message.providerId,
                        message.groupAssistantName
                    ).joinToString("\n")
                        .contains(query, ignoreCase = true)
                }
                .sortedWith(compareByDescending<MessageEntity> { it.timestamp }.thenBy { it.id })
                .take(limit)
                .map { message ->
                    MessageSearchRow(
                        messageId = message.id,
                        chatId = message.chatId,
                        chatTitle = chatTitleForId(message.chatId),
                        role = message.role,
                        partsJson = message.partsJson,
                        timestamp = message.timestamp,
                        modelName = message.modelName,
                        providerId = message.providerId,
                        groupId = message.groupId,
                        groupAssistantName = message.groupAssistantName,
                        variantsJson = message.variantsJson,
                        selectedVariantIndex = message.selectedVariantIndex,
                        isBookmarked = message.isBookmarked,
                        replyToMessageId = message.replyToMessageId,
                        replyPreview = message.replyPreview
                    )
                }
        }

        override fun observeBookmarkedMessages(limit: Int): Flow<List<MessageSearchRow>> = flow {
            emit(
                messages
                    .filter { it.isBookmarked }
                    .sortedByDescending { it.timestamp }
                    .take(limit)
                    .map { message ->
                        MessageSearchRow(
                            messageId = message.id,
                            chatId = message.chatId,
                            chatTitle = chatTitleForId(message.chatId),
                            role = message.role,
                            partsJson = message.partsJson,
                            timestamp = message.timestamp,
                            modelName = message.modelName,
                            providerId = message.providerId,
                            groupId = message.groupId,
                            groupAssistantName = message.groupAssistantName,
                            variantsJson = message.variantsJson,
                            selectedVariantIndex = message.selectedVariantIndex,
                            isBookmarked = message.isBookmarked,
                            replyToMessageId = message.replyToMessageId,
                            replyPreview = message.replyPreview
                        )
                    }
            )
        }

        override suspend fun insertMessage(message: MessageEntity) {
            messages.removeAll { it.id == message.id }
            messages.add(message)
        }

        override suspend fun insertMessages(messages: List<MessageEntity>) {
            messages.forEach { insertMessage(it) }
        }

        override suspend fun upsertSearchIndex(index: MessageSearchIndexEntity) = Unit

        override suspend fun upsertSearchIndexes(indexes: List<MessageSearchIndexEntity>) = Unit

        override suspend fun updateMessage(message: MessageEntity) {
            insertMessage(message)
        }

        override suspend fun updateMessageParts(messageId: String, partsJson: String) {
            replaceMessage(messageId) { it.copy(partsJson = partsJson) }
        }

        override suspend fun updateMessageVariants(
            messageId: String,
            variantsJson: String?,
            selectedIndex: Int
        ) {
            replaceMessage(messageId) {
                it.copy(variantsJson = variantsJson, selectedVariantIndex = selectedIndex)
            }
        }

        override suspend fun updateSelectedVariant(messageId: String, selectedIndex: Int) {
            replaceMessage(messageId) { it.copy(selectedVariantIndex = selectedIndex) }
        }

        override suspend fun updateMessageBookmarked(messageId: String, isBookmarked: Boolean) {
            replaceMessage(messageId) { it.copy(isBookmarked = isBookmarked) }
        }

        override suspend fun deleteMessage(messageId: String) {
            messages.removeAll { it.id == messageId }
        }

        override suspend fun deleteMessagesByChatId(chatId: String) {
            messages.removeAll { it.chatId == chatId }
        }

        override suspend fun getTotalInputTokens(): Long? = null

        override suspend fun getTotalOutputTokens(): Long? = null

        override suspend fun getTotalAssistantMessages(): Int = 0

        override suspend fun getModelUsageStats(): List<ModelUsageStat> = emptyList()

        override suspend fun getProviderUsageStats(): List<ProviderUsageStat> = emptyList()

        override suspend fun clearAllTokenStats() = Unit

        override suspend fun getAssistantMessageCountByGroup(groupId: String): Int {
            return messages.count { it.groupId == groupId && it.role == "assistant" }
        }

        override suspend fun getAssistantMessageCountsByGroup(groupId: String): List<com.tchat.data.database.dao.GroupAssistantMessageCount> {
            return messages
                .filter { it.groupId == groupId && it.role == "assistant" && it.groupAssistantId != null }
                .groupingBy { it.groupAssistantId!! }
                .eachCount()
                .map { (assistantId, count) ->
                    com.tchat.data.database.dao.GroupAssistantMessageCount(assistantId, count)
                }
        }

        override suspend fun getLastGroupMessageTimestamp(groupId: String): Long? {
            return messages
                .filter { it.groupId == groupId }
                .maxOfOrNull { it.timestamp }
        }

        private fun sortedForChat(chatId: String): List<MessageEntity> {
            return messages
                .filter { it.chatId == chatId }
                .sortedWith(messageOrder)
        }

        private fun replaceMessage(messageId: String, transform: (MessageEntity) -> MessageEntity) {
            val index = messages.indexOfFirst { it.id == messageId }
            if (index >= 0) {
                messages[index] = transform(messages[index])
            }
        }

        private fun String.toFakeSearchQuery(): String {
            return removePrefix("%")
                .removeSuffix("%")
                .replace("\\%", "%")
                .replace("\\_", "_")
                .replace("\\\\", "\\")
        }
    }

    private companion object {
        const val CHAT_ID = "chat-1"

        val messageOrder: Comparator<MessageEntity> =
            compareBy<MessageEntity> { it.timestamp }.thenBy { it.id }

        fun messageEntity(
            id: String,
            chatId: String,
            role: String,
            content: String,
            timestamp: Long,
            modelName: String? = null,
            variantsJson: String? = null
        ): MessageEntity {
            return messageEntity(
                id = id,
                chatId = chatId,
                role = role,
                parts = listOf(MessagePart.Text(content)),
                timestamp = timestamp,
                modelName = modelName,
                variantsJson = variantsJson
            )
        }

        fun messageEntity(
            id: String,
            chatId: String,
            role: String,
            parts: List<MessagePart>,
            timestamp: Long,
            modelName: String? = null,
            variantsJson: String? = null
        ): MessageEntity {
            return MessageEntity(
                id = id,
                chatId = chatId,
                role = role,
                partsJson = MessagePartSerializer.serializeParts(parts),
                timestamp = timestamp,
                modelName = modelName,
                variantsJson = variantsJson
            )
        }

        fun variantsJson(vararg contents: String): String {
            return contents.joinToString(prefix = "[", postfix = "]") { content ->
                """{"content":${org.json.JSONObject.quote(content)}}"""
            }
        }
    }
}
