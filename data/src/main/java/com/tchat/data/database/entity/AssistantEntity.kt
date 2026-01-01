package com.tchat.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.tchat.data.model.LocalToolOption
import org.json.JSONArray

/**
 * 助手数据库实体
 */
@Entity(tableName = "assistants")
data class AssistantEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val avatar: String?,
    val systemPrompt: String,
    val temperature: Float?,
    val topP: Float?,
    val maxTokens: Int?,
    val contextMessageSize: Int,
    val streamOutput: Boolean,
    val localTools: String, // JSON序列化的LocalToolOption列表
    val knowledgeBaseId: String? = null, // 关联的知识库ID
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * LocalToolOption列表的类型转换器
 */
class LocalToolOptionConverter {

    @TypeConverter
    fun fromLocalToolOptions(options: List<LocalToolOption>): String {
        val jsonArray = JSONArray()
        options.forEach { option ->
            jsonArray.put(option.id)
        }
        return jsonArray.toString()
    }

    @TypeConverter
    fun toLocalToolOptions(value: String): List<LocalToolOption> {
        return try {
            val jsonArray = JSONArray(value)
            (0 until jsonArray.length()).mapNotNull { i ->
                LocalToolOption.fromId(jsonArray.getString(i))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
