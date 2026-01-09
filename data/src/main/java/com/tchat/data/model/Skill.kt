package com.tchat.data.model

import java.util.UUID

/**
 * 技能数据模型
 *
 * 技能是一种可以根据用户输入自动触发的功能模块，
 * 触发后会将技能内容注入到系统提示中，并可以提供额外的工具供 AI 调用。
 */
data class Skill(
    /** 唯一标识符 */
    val id: String = UUID.randomUUID().toString(),
    /** 技能标识符（英文，用于引用） */
    val name: String,
    /** 显示名称 */
    val displayName: String,
    /** 触发描述（描述何时触发此技能，包含触发短语） */
    val description: String,
    /** 技能指令内容（触发时注入到系统提示） */
    val content: String,
    /** 触发关键词列表 */
    val triggerKeywords: List<String> = emptyList(),
    /** 优先级（数字越大优先级越高，多个技能匹配时使用） */
    val priority: Int = 0,
    /** 是否启用 */
    val enabled: Boolean = false,
    /** 是否为内置技能 */
    val isBuiltIn: Boolean = false,
    /** 技能定义的工具列表 */
    val tools: List<SkillToolDefinition> = emptyList(),
    /** 创建时间 */
    val createdAt: Long = System.currentTimeMillis(),
    /** 更新时间 */
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * 创建一个简单的技能（无工具）
         */
        fun createSimple(
            name: String,
            displayName: String,
            description: String,
            content: String,
            triggerKeywords: List<String>,
            priority: Int = 0
        ): Skill = Skill(
            name = name,
            displayName = displayName,
            description = description,
            content = content,
            triggerKeywords = triggerKeywords,
            priority = priority
        )
    }
}

/**
 * 匹配的技能结果
 */
data class MatchedSkill(
    /** 匹配的技能 */
    val skill: Skill,
    /** 匹配分数 */
    val score: Float
)
