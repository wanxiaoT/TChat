package com.tchat.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "message_search_index",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["messageId"], unique = true),
        Index(value = ["chatId"]),
        Index(value = ["updatedAt"])
    ]
)
data class MessageSearchIndexEntity(
    @PrimaryKey
    val messageId: String,
    val chatId: String,
    val normalizedText: String,
    val updatedAt: Long
)
