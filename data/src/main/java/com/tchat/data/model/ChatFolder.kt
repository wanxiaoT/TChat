package com.tchat.data.model

import java.util.UUID

/**
 * 聊天文件夹数据模型
 * 支持嵌套文件夹结构
 */
data class ChatFolder(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val parentId: String? = null,  // null表示根文件夹
    val icon: String? = null,  // 图标名称或emoji
    val color: String? = null,  // 文件夹颜色
    val order: Int = 0,  // 排序顺序
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 智能分组类型
 */
enum class SmartGroupType {
    BY_TIME,      // 按时间分组（今天、昨天、本周、本月、更早）
    BY_MODEL,     // 按使用的模型分组
    BY_ASSISTANT, // 按助手分组
    NONE          // 不分组
}

/**
 * 聊天与文件夹的关联关系
 */
data class ChatFolderRelation(
    val chatId: String,
    val folderId: String,
    val addedAt: Long = System.currentTimeMillis()
)

/**
 * 智能分组配置
 */
data class SmartGroupConfig(
    val enabled: Boolean = false,
    val groupType: SmartGroupType = SmartGroupType.NONE,
    val autoAssign: Boolean = true  // 是否自动分配新聊天到智能分组
)

/**
 * 扩展Chat类，添加文件夹相关属性
 */
data class ChatWithFolder(
    val chat: Chat,
    val folderId: String? = null,
    val folderName: String? = null,
    val folderPath: List<String> = emptyList()  // 完整的文件夹路径（用于嵌套显示）
)

/**
 * 文件夹树节点（用于UI显示）
 */
data class FolderTreeNode(
    val folder: ChatFolder,
    val children: List<FolderTreeNode> = emptyList(),
    val chatCount: Int = 0,  // 该文件夹中的聊天数量（不包括子文件夹）
    val totalChatCount: Int = 0  // 包括子文件夹的总聊天数量
)
