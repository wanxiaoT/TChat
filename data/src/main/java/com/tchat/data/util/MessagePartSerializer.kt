package com.tchat.data.util

import com.tchat.data.model.MessagePart
import org.json.JSONArray
import org.json.JSONObject

/**
 * MessagePart 序列化/反序列化工具
 *
 * 负责将 MessagePart 列表与 JSON 字符串之间相互转换
 */
object MessagePartSerializer {

    /**
     * 将 MessagePart 列表序列化为 JSON 字符串
     */
    fun serializeParts(parts: List<MessagePart>): String {
        val jsonArray = JSONArray()
        parts.forEach { part ->
            val jsonObj = when (part) {
                is MessagePart.Text -> {
                    JSONObject().apply {
                        put("type", "text")
                        put("content", part.content)
                    }
                }
                is MessagePart.ToolCall -> {
                    JSONObject().apply {
                        put("type", "tool_call")
                        put("toolCallId", part.toolCallId)
                        put("toolName", part.toolName)
                        put("arguments", part.arguments)
                    }
                }
                is MessagePart.ToolResult -> {
                    JSONObject().apply {
                        put("type", "tool_result")
                        put("toolCallId", part.toolCallId)
                        put("toolName", part.toolName)
                        put("arguments", part.arguments)
                        put("result", part.result)
                        put("isError", part.isError)
                        put("executionTimeMs", part.executionTimeMs)
                    }
                }
            }
            jsonArray.put(jsonObj)
        }
        return jsonArray.toString()
    }

    /**
     * 从 JSON 字符串反序列化为 MessagePart 列表
     */
    fun deserializeParts(json: String): List<MessagePart> {
        if (json.isEmpty()) return emptyList()

        return try {
            val jsonArray = JSONArray(json)
            (0 until jsonArray.length()).mapNotNull { i ->
                val jsonObj = jsonArray.getJSONObject(i)
                when (jsonObj.optString("type")) {
                    "text" -> {
                        MessagePart.Text(
                            content = jsonObj.optString("content", "")
                        )
                    }
                    "tool_call" -> {
                        MessagePart.ToolCall(
                            toolCallId = jsonObj.optString("toolCallId", ""),
                            toolName = jsonObj.optString("toolName", ""),
                            arguments = jsonObj.optString("arguments", "{}")
                        )
                    }
                    "tool_result" -> {
                        MessagePart.ToolResult(
                            toolCallId = jsonObj.optString("toolCallId", ""),
                            toolName = jsonObj.optString("toolName", ""),
                            arguments = jsonObj.optString("arguments", "{}"),
                            result = jsonObj.optString("result", ""),
                            isError = jsonObj.optBoolean("isError", false),
                            executionTimeMs = jsonObj.optLong("executionTimeMs", 0)
                        )
                    }
                    else -> null  // 未知类型，跳过
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 从旧版本数据迁移：将 content 字符串转换为 Text part
     */
    fun fromLegacyContent(content: String): List<MessagePart> {
        return if (content.isNotEmpty()) {
            listOf(MessagePart.Text(content))
        } else {
            emptyList()
        }
    }

    /**
     * 从旧版本数据迁移：合并旧的 toolCallsJson 和 toolResultsJson
     */
    fun fromLegacyToolData(
        toolCallsJson: String?,
        toolResultsJson: String?
    ): List<MessagePart> {
        val parts = mutableListOf<MessagePart>()

        // 解析工具调用
        toolCallsJson?.takeIf { it.isNotEmpty() }?.let { json ->
            try {
                val jsonArray = JSONArray(json)
                for (i in 0 until jsonArray.length()) {
                    val jsonObj = jsonArray.getJSONObject(i)
                    parts.add(
                        MessagePart.ToolCall(
                            toolCallId = jsonObj.optString("id", ""),
                            toolName = jsonObj.optString("name", ""),
                            arguments = jsonObj.optString("arguments", "{}")
                        )
                    )
                }
            } catch (e: Exception) {
                // 解析失败，忽略
            }
        }

        // 解析工具结果
        toolResultsJson?.takeIf { it.isNotEmpty() }?.let { json ->
            try {
                val jsonArray = JSONArray(json)
                for (i in 0 until jsonArray.length()) {
                    val jsonObj = jsonArray.getJSONObject(i)
                    parts.add(
                        MessagePart.ToolResult(
                            toolCallId = jsonObj.optString("toolCallId", ""),
                            toolName = jsonObj.optString("name", ""),
                            arguments = jsonObj.optString("arguments", "{}"),
                            result = jsonObj.optString("result", ""),
                            isError = jsonObj.optBoolean("isError", false),
                            executionTimeMs = jsonObj.optLong("executionTimeMs", 0)
                        )
                    )
                }
            } catch (e: Exception) {
                // 解析失败，忽略
            }
        }

        return parts
    }
}
