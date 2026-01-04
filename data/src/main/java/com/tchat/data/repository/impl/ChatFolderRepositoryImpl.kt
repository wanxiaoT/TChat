package com.tchat.data.repository.impl

import com.tchat.data.database.dao.ChatDao
import com.tchat.data.database.dao.ChatFolderDao
import com.tchat.data.database.entity.ChatFolderEntity
import com.tchat.data.database.entity.ChatFolderRelationEntity
import com.tchat.data.model.*
import com.tchat.data.repository.ChatFolderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.*

/**
 * 聊天文件夹仓库实现
 */
class ChatFolderRepositoryImpl(
    private val chatFolderDao: ChatFolderDao,
    private val chatDao: ChatDao
) : ChatFolderRepository {

    override fun getAllFolders(): Flow<List<ChatFolder>> {
        return chatFolderDao.getAllFlow().map { entities ->
            entities.map { it.toModel() }
        }
    }

    override fun getRootFolders(): Flow<List<ChatFolder>> {
        return chatFolderDao.getRootFoldersFlow().map { entities ->
            entities.map { it.toModel() }
        }
    }

    override suspend fun getChildFolders(parentId: String): List<ChatFolder> {
        return chatFolderDao.getChildFolders(parentId).map { it.toModel() }
    }

    override suspend fun getFolderById(folderId: String): ChatFolder? {
        return chatFolderDao.getById(folderId)?.toModel()
    }

    override suspend fun createFolder(folder: ChatFolder): ChatFolder {
        // 检查名称是否重复
        val exists = chatFolderDao.checkNameExists(folder.name, folder.parentId, folder.id) > 0
        if (exists) {
            throw IllegalArgumentException("文件夹名称已存在")
        }

        val entity = folder.toEntity()
        chatFolderDao.insert(entity)
        return folder
    }

    override suspend fun updateFolder(folder: ChatFolder) {
        chatFolderDao.update(folder.toEntity())
    }

    override suspend fun deleteFolder(folderId: String, deleteChats: Boolean) {
        if (deleteChats) {
            // 删除文件夹中的所有聊天
            val chatIds = chatFolderDao.getChatIdsInFolder(folderId)
            chatIds.forEach { chatId ->
                chatDao.deleteChat(chatId)
            }
        } else {
            // 只移除关联关系
            chatFolderDao.deleteRelationsByFolder(folderId)
        }

        // 递归删除子文件夹
        val children = chatFolderDao.getChildFolders(folderId)
        children.forEach { child ->
            deleteFolder(child.id, deleteChats)
        }

        chatFolderDao.deleteById(folderId)
    }

    override suspend fun moveFolder(folderId: String, newParentId: String?) {
        // 检查是否会造成循环引用
        if (newParentId != null) {
            if (isDescendant(newParentId, folderId)) {
                throw IllegalArgumentException("不能将文件夹移动到其子文件夹中")
            }
        }

        chatFolderDao.moveFolder(folderId, newParentId)
    }

    override suspend fun addChatToFolder(chatId: String, folderId: String) {
        // 先移除该聊天的所有文件夹关联
        chatFolderDao.removeChatFromAllFolders(chatId)

        // 添加新的关联
        val relation = ChatFolderRelationEntity(
            chatId = chatId,
            folderId = folderId
        )
        chatFolderDao.addChatToFolder(relation)
    }

    override suspend fun removeChatFromFolder(chatId: String, folderId: String) {
        chatFolderDao.removeChatFromFolder(chatId, folderId)
    }

    override fun getChatIdsInFolder(folderId: String): Flow<List<String>> {
        return chatFolderDao.getChatIdsInFolderFlow(folderId)
    }

    override suspend fun getFolderIdByChat(chatId: String): String? {
        return chatFolderDao.getFolderIdByChat(chatId)
    }

    override suspend fun getFolderTree(): List<FolderTreeNode> {
        return buildFolderTree(null)
    }

    override suspend fun buildFolderTree(parentId: String?): List<FolderTreeNode> {
        val folders = if (parentId == null) {
            chatFolderDao.getAll().filter { it.parentId == null }
        } else {
            chatFolderDao.getChildFolders(parentId)
        }

        return folders.map { folder ->
            val children = buildFolderTree(folder.id)
            val chatCount = chatFolderDao.getChatCountInFolder(folder.id)
            val totalChatCount = chatCount + children.sumOf { it.totalChatCount }

            FolderTreeNode(
                folder = folder.toModel(),
                children = children,
                chatCount = chatCount,
                totalChatCount = totalChatCount
            )
        }
    }

    override suspend fun getChatCountInFolder(folderId: String): Int {
        return chatFolderDao.getChatCountInFolder(folderId)
    }

    override suspend fun updateFoldersOrder(folderOrders: Map<String, Int>) {
        folderOrders.forEach { (folderId, order) ->
            chatFolderDao.updateOrder(folderId, order)
        }
    }

    override suspend fun groupChatsByTime(chats: List<Chat>): Map<String, List<Chat>> {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()

        val groups = mutableMapOf<String, MutableList<Chat>>()
        groups["今天"] = mutableListOf()
        groups["昨天"] = mutableListOf()
        groups["本周"] = mutableListOf()
        groups["本月"] = mutableListOf()
        groups["更早"] = mutableListOf()

        chats.forEach { chat ->
            calendar.timeInMillis = chat.updatedAt
            val chatDay = calendar.get(Calendar.DAY_OF_YEAR)
            val chatYear = calendar.get(Calendar.YEAR)

            calendar.timeInMillis = now
            val todayDay = calendar.get(Calendar.DAY_OF_YEAR)
            val todayYear = calendar.get(Calendar.YEAR)

            when {
                chatYear == todayYear && chatDay == todayDay -> {
                    groups["今天"]?.add(chat)
                }
                chatYear == todayYear && chatDay == todayDay - 1 -> {
                    groups["昨天"]?.add(chat)
                }
                isThisWeek(chat.updatedAt, now) -> {
                    groups["本周"]?.add(chat)
                }
                isThisMonth(chat.updatedAt, now) -> {
                    groups["本月"]?.add(chat)
                }
                else -> {
                    groups["更早"]?.add(chat)
                }
            }
        }

        return groups.filterValues { it.isNotEmpty() }
    }

    override suspend fun groupChatsByModel(chats: List<Chat>): Map<String, List<Chat>> {
        // 需要从消息中提取模型信息
        val groups = mutableMapOf<String, MutableList<Chat>>()

        chats.forEach { chat ->
            // 获取聊天中最后一条AI消息使用的模型
            val modelName = chat.messages
                .lastOrNull { it.role == MessageRole.ASSISTANT }
                ?.modelName ?: "未知模型"

            groups.getOrPut(modelName) { mutableListOf() }.add(chat)
        }

        return groups
    }

    override suspend fun groupChatsByAssistant(chats: List<Chat>): Map<String, List<Chat>> {
        // TODO: 实现按助手分组
        // 需要访问AssistantDao来获取助手信息
        return emptyMap()
    }

    override suspend fun applySmartGrouping(groupType: SmartGroupType) {
        when (groupType) {
            SmartGroupType.BY_TIME -> {
                // 创建时间分组文件夹
                val timeGroups = listOf("今天", "昨天", "本周", "本月", "更早")
                timeGroups.forEach { groupName ->
                    val folder = ChatFolder(
                        name = groupName,
                        icon = "📅",
                        parentId = null
                    )
                    try {
                        createFolder(folder)
                    } catch (e: Exception) {
                        // 文件夹已存在，忽略
                    }
                }
            }
            SmartGroupType.BY_MODEL -> {
                // 创建按模型分组的文件夹
                // 需要先获取所有使用过的模型
            }
            SmartGroupType.BY_ASSISTANT -> {
                // 创建按助手分组的文件夹
            }
            SmartGroupType.NONE -> {
                // 不分组
            }
        }
    }

    /**
     * 检查targetId是否是folderId的子孙节点
     */
    private suspend fun isDescendant(targetId: String, folderId: String): Boolean {
        var current = chatFolderDao.getById(targetId)
        while (current != null) {
            if (current.id == folderId) {
                return true
            }
            current = current.parentId?.let { chatFolderDao.getById(it) }
        }
        return false
    }

    private fun isThisWeek(timestamp: Long, now: Long): Boolean {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = now
        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        val weekStart = calendar.timeInMillis

        return timestamp >= weekStart && timestamp < now
    }

    private fun isThisMonth(timestamp: Long, now: Long): Boolean {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = now
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentYear = calendar.get(Calendar.YEAR)

        calendar.timeInMillis = timestamp
        val chatMonth = calendar.get(Calendar.MONTH)
        val chatYear = calendar.get(Calendar.YEAR)

        return chatMonth == currentMonth && chatYear == currentYear
    }

    private fun ChatFolderEntity.toModel() = ChatFolder(
        id = id,
        name = name,
        parentId = parentId,
        icon = icon,
        color = color,
        order = sortOrder,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun ChatFolder.toEntity() = ChatFolderEntity(
        id = id,
        name = name,
        parentId = parentId,
        icon = icon,
        color = color,
        sortOrder = order,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
