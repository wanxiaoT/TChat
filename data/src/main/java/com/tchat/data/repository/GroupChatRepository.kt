package com.tchat.data.repository

import com.tchat.data.model.*
import kotlinx.coroutines.flow.Flow

/**
 * 群聊仓库接口
 */
interface GroupChatRepository {
    /**
     * 获取所有群聊
     */
    fun getAllGroups(): Flow<List<GroupChat>>

    /**
     * 根据ID获取群聊
     */
    suspend fun getGroupById(groupId: String): GroupChat?

    /**
     * 根据ID获取群聊（Flow）
     */
    fun getGroupByIdFlow(groupId: String): Flow<GroupChat?>

    /**
     * 创建群聊
     */
    suspend fun createGroup(group: GroupChat): GroupChat

    /**
     * 更新群聊
     */
    suspend fun updateGroup(group: GroupChat)

    /**
     * 删除群聊
     */
    suspend fun deleteGroup(groupId: String)

    /**
     * 添加成员到群聊
     */
    suspend fun addMember(groupId: String, assistantId: String, config: GroupMemberConfig? = null)

    /**
     * 批量添加成员
     */
    suspend fun addMembers(groupId: String, assistantIds: List<String>)

    /**
     * 从群聊移除成员
     */
    suspend fun removeMember(groupId: String, assistantId: String)

    /**
     * 获取群聊成员
     */
    suspend fun getMembers(groupId: String): List<GroupMemberConfig>

    /**
     * 获取群聊成员（Flow）
     */
    fun getMembersFlow(groupId: String): Flow<List<GroupMemberConfig>>

    /**
     * 获取启用的成员
     */
    suspend fun getEnabledMembers(groupId: String): List<GroupMemberConfig>

    /**
     * 更新成员配置
     */
    suspend fun updateMemberConfig(groupId: String, assistantId: String, config: GroupMemberConfig)

    /**
     * 切换成员启用状态
     */
    suspend fun toggleMemberEnabled(groupId: String, assistantId: String, enabled: Boolean)

    /**
     * 更新成员话语权
     */
    suspend fun updateMemberTalkativeness(groupId: String, assistantId: String, talkativeness: Float)

    /**
     * 更新成员优先级
     */
    suspend fun updateMemberPriority(groupId: String, assistantId: String, priority: Int)

    /**
     * 根据激活策略选择下一个发言的助手
     */
    suspend fun selectNextSpeaker(
        groupId: String,
        lastSpeakerId: String?,
        userMessage: String?
    ): String?

    /**
     * 获取群聊统计信息
     */
    suspend fun getGroupStats(groupId: String): GroupChatStats

    /**
     * 更新群聊的最后活跃时间
     */
    suspend fun updateLastActiveTime(groupId: String)

    /**
     * 检查助手是否是群聊成员
     */
    suspend fun isMember(groupId: String, assistantId: String): Boolean

    /**
     * 获取助手所在的所有群聊
     */
    suspend fun getGroupsByAssistant(assistantId: String): List<GroupChat>
}
