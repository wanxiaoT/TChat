package com.tchat.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 聊天文件夹实体
 */
@Entity(
    tableName = "chat_folders",
    indices = [Index("parentId")]
)
data class ChatFolderEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val parentId: String? = null,
    val icon: String? = null,
    val color: String? = null,
    @ColumnInfo(name = "sort_order", defaultValue = "0")
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 聊天与文件夹的关联关系实体
 */
@Entity(
    tableName = "chat_folder_relations",
    primaryKeys = ["chatId", "folderId"],
    indices = [
        Index("chatId"),
        Index("folderId")
    ],
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ChatFolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ChatFolderRelationEntity(
    val chatId: String,
    val folderId: String,
    val addedAt: Long = System.currentTimeMillis()
)
