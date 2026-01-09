package com.tchat.data.model

/**
 * 技能工具执行类型
 */
enum class SkillToolExecuteType {
    /** HTTP 请求 */
    HTTP_REQUEST,
    /** 模板响应（直接返回模板内容） */
    TEMPLATE_RESPONSE
}

/**
 * 技能工具定义
 *
 * 每个技能可以定义多个工具，AI 可以调用这些工具执行操作
 */
data class SkillToolDefinition(
    /** 工具名称 */
    val name: String,
    /** 工具描述 */
    val description: String,
    /** 参数 Schema（JSON 格式） */
    val parametersJson: String = "{}",
    /** 执行类型 */
    val executeType: SkillToolExecuteType = SkillToolExecuteType.TEMPLATE_RESPONSE,
    /** 执行配置（JSON 格式，根据 executeType 不同有不同结构） */
    val executeConfig: String = "{}"
)
