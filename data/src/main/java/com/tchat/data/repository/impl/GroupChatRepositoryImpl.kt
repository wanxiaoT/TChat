package com.tchat.data.repository.impl

import com.tchat.data.database.dao.AssistantDao
import com.tchat.data.database.dao.GroupChatDao
import com.tchat.data.database.dao.MessageDao
import com.tchat.data.database.entity.GroupChatEntity
import com.tchat.data.database.entity.GroupMemberEntity
import com.tchat.data.model.*
import com.tchat.data.repository.GroupChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.UUID
import kotlin.random.Random

/**
 * 群聊仓库实现
 */
class GroupChatRepositoryImpl(
    private val groupChatDao: GroupChatDao,
    private val assistantDao: AssistantDao,
    private val messageDao: MessageDao
) : GroupChatRepository {

    override fun getAllGroups(): Flow<List<GroupChat>> {
        return combine(
            groupChatDao.getAllFlow(),
            groupChatDao.getAllMembersFlow()
        ) { groupEntities, memberEntities ->
            val memberIdsByGroupId = memberEntities
                .groupBy { it.groupId }
                .mapValues { (_, members) -> members.map { it.assistantId } }

            groupEntities.map { entity ->
                entity.toModel(memberIds = memberIdsByGroupId[entity.id].orEmpty())
            }
        }
    }

    override suspend fun getGroupById(groupId: String): GroupChat? {
        val entity = groupChatDao.getById(groupId) ?: return null
        val members = groupChatDao.getMembers(groupId)
        return entity.toModel(memberIds = members.map { it.assistantId })
    }

    override fun getGroupByIdFlow(groupId: String): Flow<GroupChat?> {
        return combine(
            groupChatDao.getByIdFlow(groupId),
            groupChatDao.getMembersFlow(groupId)
        ) { groupEntity, memberEntities ->
            groupEntity?.toModel(memberIds = memberEntities.map { it.assistantId })
        }
    }

    override suspend fun createGroup(group: GroupChat): GroupChat {
        val resolvedGroup = if (group.id.isBlank()) {
            group.copy(id = UUID.randomUUID().toString())
        } else {
            group
        }

        // 检查名称是否重复
        val exists = groupChatDao.checkNameExists(resolvedGroup.name, resolvedGroup.id) > 0
        if (exists) {
            throw IllegalArgumentException("群聊名称已存在")
        }

        val entity = resolvedGroup.toEntity()
        groupChatDao.insert(entity)

        // 添加成员
        if (resolvedGroup.memberIds.isNotEmpty()) {
            val members = resolvedGroup.memberIds.mapIndexed { index, assistantId ->
                GroupMemberEntity(
                    groupId = resolvedGroup.id,
                    assistantId = assistantId,
                    priority = resolvedGroup.memberIds.size - index
                )
            }
            groupChatDao.addMembers(members)
        }

        return resolvedGroup
    }

    override suspend fun updateGroup(group: GroupChat) {
        groupChatDao.update(group.toEntity())
    }

    override suspend fun deleteGroup(groupId: String) {
        // 先删除所有成员
        groupChatDao.deleteAllMembers(groupId)
        // 删除群聊
        groupChatDao.deleteById(groupId)
    }

    override suspend fun addMember(groupId: String, assistantId: String, config: GroupMemberConfig?) {
        val member = GroupMemberEntity(
            groupId = groupId,
            assistantId = assistantId,
            enabled = config?.enabled ?: true,
            priority = config?.priority ?: 0,
            talkativeness = config?.talkativeness ?: 0.5f
        )
        groupChatDao.addMember(member)
    }

    override suspend fun addMembers(groupId: String, assistantIds: List<String>) {
        val members = assistantIds.mapIndexed { index, assistantId ->
            GroupMemberEntity(
                groupId = groupId,
                assistantId = assistantId,
                priority = assistantIds.size - index
            )
        }
        groupChatDao.addMembers(members)
    }

    override suspend fun removeMember(groupId: String, assistantId: String) {
        groupChatDao.removeMember(groupId, assistantId)
    }

    override suspend fun getMembers(groupId: String): List<GroupMemberConfig> {
        return groupChatDao.getMembers(groupId).map { it.toModel() }
    }

    override fun getMembersFlow(groupId: String): Flow<List<GroupMemberConfig>> {
        return groupChatDao.getMembersFlow(groupId).map { entities ->
            entities.map { it.toModel() }
        }
    }

    override suspend fun getEnabledMembers(groupId: String): List<GroupMemberConfig> {
        return groupChatDao.getEnabledMembers(groupId).map { it.toModel() }
    }

    override suspend fun updateMemberConfig(groupId: String, assistantId: String, config: GroupMemberConfig) {
        val member = GroupMemberEntity(
            groupId = groupId,
            assistantId = assistantId,
            enabled = config.enabled,
            priority = config.priority,
            talkativeness = config.talkativeness,
            joinedAt = config.joinedAt
        )
        groupChatDao.updateMember(member)
    }

    override suspend fun toggleMemberEnabled(groupId: String, assistantId: String, enabled: Boolean) {
        groupChatDao.toggleMemberEnabled(groupId, assistantId, enabled)
    }

    override suspend fun updateMemberTalkativeness(groupId: String, assistantId: String, talkativeness: Float) {
        groupChatDao.updateTalkativeness(groupId, assistantId, talkativeness)
    }

    override suspend fun updateMemberPriority(groupId: String, assistantId: String, priority: Int) {
        groupChatDao.updatePriority(groupId, assistantId, priority)
    }

    override suspend fun selectNextSpeaker(
        groupId: String,
        lastSpeakerId: String?,
        userMessage: String?
    ): String? {
        val group = getGroupById(groupId) ?: return null
        val enabledMembers = getEnabledMembers(groupId)

        if (enabledMembers.isEmpty()) {
            return null
        }

        return when (GroupActivationStrategy.valueOf(group.activationStrategy.name)) {
            GroupActivationStrategy.LIST -> selectByList(enabledMembers, lastSpeakerId)
            GroupActivationStrategy.POOLED -> selectByPool(enabledMembers)
            GroupActivationStrategy.NATURAL -> selectByNatural(enabledMembers, userMessage)
            GroupActivationStrategy.MANUAL -> null // 手动模式，返回null让用户选择
        }
    }

    /**
     * 列表模式：按优先级轮流发言
     */
    private fun selectByList(members: List<GroupMemberConfig>, lastSpeakerId: String?): String? {
        if (lastSpeakerId == null) {
            // 选择优先级最高的
            return members.maxByOrNull { it.priority }?.assistantId
        }

        // 找到上一个发言者的索引
        val lastIndex = members.indexOfFirst { it.assistantId == lastSpeakerId }
        if (lastIndex == -1) {
            return members.firstOrNull()?.assistantId
        }

        // 选择下一个
        val nextIndex = (lastIndex + 1) % members.size
        return members[nextIndex].assistantId
    }

    /**
     * 池化模式：随机选择
     */
    private fun selectByPool(members: List<GroupMemberConfig>): String? {
        return members.randomOrNull()?.assistantId
    }

    /**
     * 自然模式：基于话语权加权随机选择
     */
    private fun selectByNatural(members: List<GroupMemberConfig>, userMessage: String?): String? {
        // 计算总话语权
        val totalTalkativeness = members.sumOf { it.talkativeness.toDouble() }

        if (totalTalkativeness <= 0) {
            return members.randomOrNull()?.assistantId
        }

        // 加权随机选择
        val randomValue = Random.nextDouble(0.0, totalTalkativeness)
        var accumulated = 0.0

        for (member in members) {
            accumulated += member.talkativeness
            if (randomValue <= accumulated) {
                return member.assistantId
            }
        }

        return members.lastOrNull()?.assistantId
    }

    override suspend fun getGroupStats(groupId: String): GroupChatStats {
        // TODO: 实现统计信息收集
        // 需要统计消息数量、每个助手的发言次数等
        return GroupChatStats(
            groupId = groupId,
            totalMessages = 0,
            messagesByAssistant = emptyMap()
        )
    }

    override suspend fun updateLastActiveTime(groupId: String) {
        groupChatDao.updateLastActiveTime(groupId)
    }

    override suspend fun isMember(groupId: String, assistantId: String): Boolean {
        return groupChatDao.isMember(groupId, assistantId) > 0
    }

    override suspend fun getGroupsByAssistant(assistantId: String): List<GroupChat> {
        val groupIds = groupChatDao.getGroupIdsByAssistant(assistantId)
        return groupIds.mapNotNull { getGroupById(it) }
    }

    private fun GroupChatEntity.toModel(): GroupChat {
        return toModel(memberIds = emptyList())
    }

    private fun GroupChatEntity.toModel(memberIds: List<String>): GroupChat = GroupChat(
        id = id,
        name = name,
        avatar = avatar,
        description = description,
        memberIds = memberIds,
        activationStrategy = GroupActivationStrategy.valueOf(activationStrategy),
        generationMode = GroupGenerationMode.valueOf(generationMode),
        autoModeEnabled = autoModeEnabled,
        autoModeDelay = autoModeDelay,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun GroupChat.toEntity() = GroupChatEntity(
        id = id,
        name = name,
        avatar = avatar,
        description = description,
        activationStrategy = activationStrategy.name,
        generationMode = generationMode.name,
        autoModeEnabled = autoModeEnabled,
        autoModeDelay = autoModeDelay,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun GroupMemberEntity.toModel() = GroupMemberConfig(
        groupId = groupId,
        assistantId = assistantId,
        enabled = enabled,
        priority = priority,
        talkativeness = talkativeness,
        joinedAt = joinedAt
    )
}
