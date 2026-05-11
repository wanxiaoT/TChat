package com.tchat.data.repository

import com.tchat.data.database.dao.MessageDao
import com.tchat.data.database.dao.MessageSearchRow
import com.tchat.data.database.entity.MessageEntity
import com.tchat.data.model.ChatSearchResult
import com.tchat.data.model.MessagePart
import com.tchat.data.model.MessageRole
import com.tchat.data.model.MessageVariant
import com.tchat.data.util.MessagePartSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import java.util.Locale

class ChatSearchRepository(
    private val messageDao: MessageDao
) {
    suspend fun searchMessages(query: String, limit: Int = DEFAULT_LIMIT): List<ChatSearchResult> {
        return MessageSearchMapper.search(messageDao, query, limit)
    }

    fun observeBookmarkedMessages(limit: Int = DEFAULT_LIMIT): Flow<List<ChatSearchResult>> {
        return messageDao.observeBookmarkedMessages(limit.coerceIn(1, 200))
            .map { rows -> rows.map { row -> MessageSearchMapper.toSearchResult(row, "") } }
            .flowOn(Dispatchers.Default)
    }

    private companion object {
        const val DEFAULT_LIMIT = 50
    }
}

internal object MessageSearchMapper {
    suspend fun search(
        messageDao: MessageDao,
        query: String,
        limit: Int
    ): List<ChatSearchResult> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return emptyList()

        val safeLimit = limit.coerceIn(1, 200)
        return messageDao.searchMessages(
            normalizedQuery.lowercase(Locale.ROOT).toSqlLikePattern(),
            safeLimit
        )
            .map { row -> toSearchResult(row, normalizedQuery) }
    }

    fun toSearchResult(row: MessageSearchRow, query: String): ChatSearchResult {
        val searchableText = buildSearchableText(row)
        return ChatSearchResult(
            messageId = row.messageId,
            chatId = row.chatId,
            chatTitle = row.chatTitle,
            role = row.role.toMessageRole(),
            snippet = searchableText.toSnippet(query),
            timestamp = row.timestamp,
            modelName = row.modelName,
            providerId = row.providerId,
            groupId = row.groupId,
            groupAssistantName = row.groupAssistantName
        )
    }

    private fun buildSearchableText(row: MessageSearchRow): String {
        return buildList {
            add(row.chatTitle)
            row.groupAssistantName?.takeIf { it.isNotBlank() }?.let(::add)
            row.modelName?.takeIf { it.isNotBlank() }?.let(::add)
            row.providerId?.takeIf { it.isNotBlank() }?.let(::add)
            addAll(MessagePartSerializer.deserializeParts(row.partsJson).toSearchTextParts())
            addAll(row.variantsJson.toVariants().toVariantText())
        }.joinToString("\n")
    }

    fun buildIndexText(entity: MessageEntity, chatTitle: String? = null): String {
        return buildString {
            chatTitle?.takeIf { it.isNotBlank() }?.let {
                appendLine(it)
            }
            entity.groupAssistantName?.takeIf { it.isNotBlank() }?.let {
                appendLine(it)
            }
            entity.modelName?.takeIf { it.isNotBlank() }?.let {
                appendLine(it)
            }
            entity.providerId?.takeIf { it.isNotBlank() }?.let {
                appendLine(it)
            }
            MessagePartSerializer.deserializeParts(entity.partsJson)
                .toSearchTextParts()
                .forEach(::appendLine)
            entity.variantsJson.toVariants()
                .toVariantText()
                .forEach(::appendLine)
        }.normalizeForIndex()
    }

    private fun List<MessagePart>.toSearchTextParts(): List<String> {
        return mapNotNull { part ->
            when (part) {
                is MessagePart.Text -> part.content
                is MessagePart.Image -> part.fileName ?: part.filePath.substringAfterLastPathSeparator()
                is MessagePart.Video -> part.fileName ?: part.filePath.substringAfterLastPathSeparator()
                is MessagePart.ToolCall -> listOf(
                    part.toolName,
                    part.arguments
                ).joinToString(" ")
                is MessagePart.ToolResult -> listOf(
                    part.toolName,
                    part.arguments,
                    part.result
                ).joinToString(" ")
            }.takeIf { it.isNotBlank() }
        }
    }

    private fun List<MessageVariant>.toVariantText(): List<String> {
        return map { it.content }.filter { it.isNotBlank() }
    }

    private fun String.toSqlLikePattern(): String {
        return buildString(length + 2) {
            append('%')
            for (char in this@toSqlLikePattern) {
                when (char) {
                    '\\' -> append("\\\\")
                    '%' -> append("\\%")
                    '_' -> append("\\_")
                    else -> append(char)
                }
            }
            append('%')
        }
    }

    private fun String.toSnippet(query: String): String {
        val compactText = replace(Regex("\\s+"), " ").trim()
        if (compactText.isBlank()) return "匹配到消息"
        if (query.isBlank()) return compactText.takeWithEllipsis(MAX_SNIPPET_LENGTH)

        val matchIndex = compactText.indexOf(query, ignoreCase = true)
        if (matchIndex < 0) return compactText.takeWithEllipsis(MAX_SNIPPET_LENGTH)

        val start = (matchIndex - SNIPPET_CONTEXT_CHARS).coerceAtLeast(0)
        val end = (matchIndex + query.length + SNIPPET_CONTEXT_CHARS).coerceAtMost(compactText.length)
        val prefix = if (start > 0) "..." else ""
        val suffix = if (end < compactText.length) "..." else ""
        return prefix + compactText.substring(start, end).trim() + suffix
    }

    private fun String.takeWithEllipsis(maxLength: Int): String {
        return if (length <= maxLength) this else take(maxLength).trimEnd() + "..."
    }

    private fun String.normalizeForIndex(): String {
        return replace(Regex("\\s+"), " ")
            .trim()
            .lowercase(Locale.ROOT)
    }

    private fun String?.toVariants(): List<MessageVariant> {
        if (isNullOrBlank()) return emptyList()
        return try {
            val jsonArray = JSONArray(this)
            (0 until jsonArray.length()).map { index ->
                val jsonObject = jsonArray.getJSONObject(index)
                MessageVariant(
                    content = jsonObject.optString("content", ""),
                    inputTokens = jsonObject.optInt("inputTokens", 0),
                    outputTokens = jsonObject.optInt("outputTokens", 0),
                    tokensPerSecond = jsonObject.optDouble("tokensPerSecond", 0.0),
                    firstTokenLatency = jsonObject.optLong("firstTokenLatency", 0),
                    createdAt = jsonObject.optLong("createdAt", System.currentTimeMillis())
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun String.toMessageRole(): MessageRole {
        return runCatching { MessageRole.valueOf(uppercase()) }.getOrDefault(MessageRole.USER)
    }

    private fun String.substringAfterLastPathSeparator(): String {
        return substringAfterLast('/').substringAfterLast('\\')
    }

    private const val SNIPPET_CONTEXT_CHARS = 56
    private const val MAX_SNIPPET_LENGTH = 132
}
