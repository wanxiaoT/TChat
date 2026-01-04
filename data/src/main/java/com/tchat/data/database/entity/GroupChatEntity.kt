package com.tchat.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tchat.data.model.GroupActivationStrategy
import com.tchat.data.model.GroupGenerationMode

/**
 * 群聊实体
 */
@Entity(tableName = "group_chats")
data class GroupChatEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val avatar: String? = null,
    val description: String? = null,
    val activationStrategy: String = GroupActivationStrategy.MANUAL.name,
    val generationMode: String = GroupGenerationMode.APPEND.name,
    @ColumnInfo(defaultValue = "0")
    val autoModeEnabled: Boolean = false,
    @ColumnInfo(defaultValue = "5")
    val autoModeDelay: Int = 5,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 群聊成员配置实体
 */
@Entity(
    tableName = "group_members",
    primaryKeys = ["groupId", "assistantId"],
    indices = [
        Index("groupId"),
        Index("assistantId")
    ],
    foreignKeys = [
        ForeignKey(
            entity = GroupChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AssistantEntity::class,
            parentColumns = ["id"],
            childColumns = ["assistantId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class GroupMemberEntity(
    val groupId: String,
    val assistantId: String,
    @ColumnInfo(defaultValue = "1")
    val enabled: Boolean = true,
    @ColumnInfo(defaultValue = "0")
    val priority: Int = 0,
    @ColumnInfo(defaultValue = "0.5")
    val talkativeness: Float = 0.5f,
    val joinedAt: Long = System.currentTimeMillis()
)
