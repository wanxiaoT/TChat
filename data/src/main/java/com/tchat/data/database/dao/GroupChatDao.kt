package com.tchat.data.database.dao

import androidx.room.*
import com.tchat.data.database.entity.GroupChatEntity
import com.tchat.data.database.entity.GroupMemberEntity
import kotlinx.coroutines.flow.Flow

/**
 * 群聊DAO
 */
@Dao
interface GroupChatDao {
    // ============= 群聊操作 =============

    /**
     * 插入群聊
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(group: GroupChatEntity)

    /**
     * 更新群聊
     */
    @Update
    suspend fun update(group: GroupChatEntity)

    /**
     * 删除群聊
     */
    @Delete
    suspend fun delete(group: GroupChatEntity)

    /**
     * 根据ID删除群聊
     */
    @Query("DELETE FROM group_chats WHERE id = :groupId")
    suspend fun deleteById(groupId: String)

    /**
     * 获取所有群聊
     */
    @Query("SELECT * FROM group_chats ORDER BY updatedAt DESC")
    fun getAllFlow(): Flow<List<GroupChatEntity>>

    /**
     * 获取所有群聊（一次性）
     */
    @Query("SELECT * FROM group_chats ORDER BY updatedAt DESC")
    suspend fun getAll(): List<GroupChatEntity>

    /**
     * 根据ID获取群聊
     */
    @Query("SELECT * FROM group_chats WHERE id = :groupId")
    suspend fun getById(groupId: String): GroupChatEntity?

    /**
     * 根据ID获取群聊（Flow）
     */
    @Query("SELECT * FROM group_chats WHERE id = :groupId")
    fun getByIdFlow(groupId: String): Flow<GroupChatEntity?>

    /**
     * 检查群聊名称是否存在
     */
    @Query("SELECT COUNT(*) FROM group_chats WHERE name = :name AND id != :excludeId")
    suspend fun checkNameExists(name: String, excludeId: String = ""): Int

    /**
     * 更新群聊的最后更新时间
     */
    @Query("UPDATE group_chats SET updatedAt = :updatedAt WHERE id = :groupId")
    suspend fun updateLastActiveTime(groupId: String, updatedAt: Long = System.currentTimeMillis())

    // ============= 群聊成员操作 =============

    /**
     * 添加成员到群聊
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addMember(member: GroupMemberEntity)

    /**
     * 批量添加成员
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addMembers(members: List<GroupMemberEntity>)

    /**
     * 更新成员配置
     */
    @Update
    suspend fun updateMember(member: GroupMemberEntity)

    /**
     * 从群聊移除成员
     */
    @Query("DELETE FROM group_members WHERE groupId = :groupId AND assistantId = :assistantId")
    suspend fun removeMember(groupId: String, assistantId: String)

    /**
     * 删除群聊的所有成员
     */
    @Query("DELETE FROM group_members WHERE groupId = :groupId")
    suspend fun deleteAllMembers(groupId: String)

    /**
     * 获取群聊的所有成员
     */
    @Query("SELECT * FROM group_members WHERE groupId = :groupId ORDER BY priority DESC, joinedAt ASC")
    suspend fun getMembers(groupId: String): List<GroupMemberEntity>

    /**
     * 获取群聊的所有成员（Flow）
     */
    @Query("SELECT * FROM group_members WHERE groupId = :groupId ORDER BY priority DESC, joinedAt ASC")
    fun getMembersFlow(groupId: String): Flow<List<GroupMemberEntity>>

    /**
     * 获取启用的成员
     */
    @Query("SELECT * FROM group_members WHERE groupId = :groupId AND enabled = 1 ORDER BY priority DESC, joinedAt ASC")
    suspend fun getEnabledMembers(groupId: String): List<GroupMemberEntity>

    /**
     * 获取群聊的成员数量
     */
    @Query("SELECT COUNT(*) FROM group_members WHERE groupId = :groupId")
    suspend fun getMemberCount(groupId: String): Int

    /**
     * 检查助手是否是群聊成员
     */
    @Query("SELECT COUNT(*) FROM group_members WHERE groupId = :groupId AND assistantId = :assistantId")
    suspend fun isMember(groupId: String, assistantId: String): Int

    /**
     * 更新成员的话语权
     */
    @Query("UPDATE group_members SET talkativeness = :talkativeness WHERE groupId = :groupId AND assistantId = :assistantId")
    suspend fun updateTalkativeness(groupId: String, assistantId: String, talkativeness: Float)

    /**
     * 更新成员的优先级
     */
    @Query("UPDATE group_members SET priority = :priority WHERE groupId = :groupId AND assistantId = :assistantId")
    suspend fun updatePriority(groupId: String, assistantId: String, priority: Int)

    /**
     * 切换成员的启用状态
     */
    @Query("UPDATE group_members SET enabled = :enabled WHERE groupId = :groupId AND assistantId = :assistantId")
    suspend fun toggleMemberEnabled(groupId: String, assistantId: String, enabled: Boolean)

    /**
     * 获取助手所在的所有群聊ID
     */
    @Query("SELECT groupId FROM group_members WHERE assistantId = :assistantId")
    suspend fun getGroupIdsByAssistant(assistantId: String): List<String>
}
