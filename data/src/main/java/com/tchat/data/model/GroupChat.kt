package com.tchat.data.model

import java.util.UUID

/**
 * 群聊数据模型
 * 支持多个助手在同一个聊天中协作对话
 */
data class GroupChat(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val avatar: String? = null,  // 群聊头像
    val description: String? = null,
    val memberIds: List<String> = emptyList(),  // 助手ID列表
    val activationStrategy: GroupActivationStrategy = GroupActivationStrategy.MANUAL,
    val generationMode: GroupGenerationMode = GroupGenerationMode.APPEND,
    val autoModeEnabled: Boolean = false,
    val autoModeDelay: Int = 5,  // 自动模式延迟（秒）
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 群聊激活策略
 * 决定哪个助手在群聊中响应
 */
enum class GroupActivationStrategy {
    /**
     * 自然模式：AI自动判断哪个助手应该响应
     */
    NATURAL,

    /**
     * 列表模式：按照成员列表顺序轮流响应
     */
    LIST,

    /**
     * 手动模式：用户手动选择哪个助手响应
     */
    MANUAL,

    /**
     * 池化模式：随机选择一个助手响应
     */
    POOLED
}

/**
 * 群聊生成模式
 * 决定消息如何生成和显示
 */
enum class GroupGenerationMode {
    /**
     * 交换模式：替换之前的响应
     */
    SWAP,

    /**
     * 追加模式：添加到聊天记录
     */
    APPEND,

    /**
     * 追加禁用模式：添加但不自动继续
     */
    APPEND_DISABLED
}

/**
 * 群聊成员配置
 * 控制单个助手在群聊中的行为
 */
data class GroupMemberConfig(
    val groupId: String,
    val assistantId: String,
    val enabled: Boolean = true,  // 是否启用该成员
    val priority: Int = 0,  // 优先级（用于LIST模式）
    val talkativeness: Float = 0.5f,  // 话语权（0.0-1.0，用于NATURAL模式）
    val joinedAt: Long = System.currentTimeMillis()
)

/**
 * 群聊消息扩展信息
 * 用于标识消息来自哪个助手
 */
data class GroupMessageMetadata(
    val groupId: String,
    val assistantId: String,
    val activationStrategy: GroupActivationStrategy,
    val generationId: String = UUID.randomUUID().toString()  // 用于重新生成
)

/**
 * 群聊统计信息
 */
data class GroupChatStats(
    val groupId: String,
    val totalMessages: Int = 0,
    val messagesByAssistant: Map<String, Int> = emptyMap(),  // 每个助手的消息数量
    val lastActiveAt: Long = System.currentTimeMillis()
)
