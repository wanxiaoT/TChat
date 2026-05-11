package com.tchat.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 聊天会话实体
 *
 * 借鉴cherry-studio的Topic模型设计
 */
@Entity(
    tableName = "chats",
    indices = [
        Index(value = ["updatedAt"]),
        Index(value = ["isPinned", "updatedAt"])
    ]
)
data class ChatEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isPinned: Boolean = false,
    val isNameManuallyEdited: Boolean = false  // 标题是否手动编辑过
)
