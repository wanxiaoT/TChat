package com.tchat.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 技能数据库实体
 */
@Entity(tableName = "skills")
data class SkillEntity(
    /** 唯一标识符 */
    @PrimaryKey
    val id: String,
    /** 技能标识符（英文） */
    val name: String,
    /** 显示名称 */
    val displayName: String,
    /** 触发描述 */
    val description: String,
    /** 技能指令内容 */
    val content: String,
    /** 触发关键词（JSON 数组） */
    val triggerKeywords: String = "[]",
    /** 优先级 */
    val priority: Int = 0,
    /** 是否启用 */
    val enabled: Boolean = true,
    /** 是否为内置技能 */
    val isBuiltIn: Boolean = false,
    /** 技能工具列表（JSON 数组） */
    val toolsJson: String = "[]",
    /** 创建时间 */
    val createdAt: Long,
    /** 更新时间 */
    val updatedAt: Long
)
