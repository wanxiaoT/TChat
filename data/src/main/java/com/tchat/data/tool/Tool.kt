package com.tchat.data.tool

import org.json.JSONObject

/**
 * 工具定义
 *
 * 设计思路：
 * - 工具是AI可以调用的外部函数
 * - 每个工具有名称、描述、参数定义和执行函数
 * - 使用JSON格式进行参数传递，便于与各种AI API交互
 */
data class Tool(
    /** 工具名称，用于AI识别和调用 */
    val name: String,
    /** 工具描述，帮助AI理解何时使用此工具 */
    val description: String,
    /** 参数Schema，定义工具接受的参数 */
    val parameters: () -> InputSchema? = { null },
    /** 执行函数，接收参数并返回结果 */
    val execute: suspend (JSONObject) -> JSONObject
)

/**
 * 输入参数Schema
 *
 * 使用sealed class支持扩展不同类型的schema
 */
sealed class InputSchema {
    /**
     * 对象类型Schema
     * @param properties 属性定义
     * @param required 必需的属性列表
     */
    data class Obj(
        val properties: Map<String, PropertyDef>,
        val required: List<String>? = null,
    ) : InputSchema()
}

/**
 * 属性定义
 */
data class PropertyDef(
    val type: String,
    val description: String? = null,
    val enum: List<String>? = null
)

/**
 * 工具调用请求
 */
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: JSONObject
)

/**
 * 工具调用结果
 */
data class ToolResult(
    val toolCallId: String,
    val name: String,
    val content: JSONObject,
    val isError: Boolean = false
)
