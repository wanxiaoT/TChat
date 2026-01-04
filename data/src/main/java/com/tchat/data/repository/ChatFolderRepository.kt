package com.tchat.data.repository

import com.tchat.data.model.*
import kotlinx.coroutines.flow.Flow

/**
 * 聊天文件夹仓库接口
 */
interface ChatFolderRepository {
    /**
     * 获取所有文件夹
     */
    fun getAllFolders(): Flow<List<ChatFolder>>

    /**
     * 获取根文件夹
     */
    fun getRootFolders(): Flow<List<ChatFolder>>

    /**
     * 获取子文件夹
     */
    suspend fun getChildFolders(parentId: String): List<ChatFolder>

    /**
     * 根据ID获取文件夹
     */
    suspend fun getFolderById(folderId: String): ChatFolder?

    /**
     * 创建文件夹
     */
    suspend fun createFolder(folder: ChatFolder): ChatFolder

    /**
     * 更新文件夹
     */
    suspend fun updateFolder(folder: ChatFolder)

    /**
     * 删除文件夹
     */
    suspend fun deleteFolder(folderId: String, deleteChats: Boolean = false)

    /**
     * 移动文件夹到新的父文件夹
     */
    suspend fun moveFolder(folderId: String, newParentId: String?)

    /**
     * 添加聊天到文件夹
     */
    suspend fun addChatToFolder(chatId: String, folderId: String)

    /**
     * 从文件夹移除聊天
     */
    suspend fun removeChatFromFolder(chatId: String, folderId: String)

    /**
     * 获取文件夹中的聊天ID列表
     */
    fun getChatIdsInFolder(folderId: String): Flow<List<String>>

    /**
     * 获取聊天所在的文件夹ID
     */
    suspend fun getFolderIdByChat(chatId: String): String?

    /**
     * 获取文件夹树结构
     */
    suspend fun getFolderTree(): List<FolderTreeNode>

    /**
     * 构建文件夹树（从指定父节点开始）
     */
    suspend fun buildFolderTree(parentId: String?): List<FolderTreeNode>

    /**
     * 获取文件夹中的聊天数量
     */
    suspend fun getChatCountInFolder(folderId: String): Int

    /**
     * 批量更新文件夹排序
     */
    suspend fun updateFoldersOrder(folderOrders: Map<String, Int>)

    /**
     * 智能分组：按时间
     */
    suspend fun groupChatsByTime(chats: List<Chat>): Map<String, List<Chat>>

    /**
     * 智能分组：按模型
     */
    suspend fun groupChatsByModel(chats: List<Chat>): Map<String, List<Chat>>

    /**
     * 智能分组：按助手
     */
    suspend fun groupChatsByAssistant(chats: List<Chat>): Map<String, List<Chat>>

    /**
     * 应用智能分组到文件夹
     */
    suspend fun applySmartGrouping(groupType: SmartGroupType)
}
