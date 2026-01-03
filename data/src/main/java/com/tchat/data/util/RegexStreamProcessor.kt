package com.tchat.data.util

/**
 * 正则表达式流式处理器
 * 用于在流式输出时实时应用正则替换规则
 *
 * 设计思路：
 * 1. 使用缓冲区处理可能被截断的模式
 * 2. 按完整行处理行级规则（如 ^ +）
 * 3. 流结束时处理剩余缓冲区
 */
class RegexStreamProcessor(
    rules: List<RegexRuleData>
) {
    private val buffer = StringBuilder()
    private val maxBufferSize = 200  // 缓冲区最大大小

    // 预编译正则表达式
    private val compiledRules: List<CompiledRule> = rules
        .filter { it.isEnabled }
        .sortedBy { it.order }
        .mapNotNull { rule ->
            try {
                CompiledRule(
                    regex = Regex(rule.pattern, RegexOption.MULTILINE),
                    replacement = rule.replacement,
                    isLineBased = rule.pattern.startsWith("^") || rule.pattern.endsWith("$")
                )
            } catch (e: Exception) {
                // 忽略无效的正则表达式
                null
            }
        }

    // 非行级规则（可以在任意位置匹配）
    private val nonLineRules = compiledRules.filter { !it.isLineBased }

    // 行级规则（需要完整行才能匹配）
    private val lineRules = compiledRules.filter { it.isLineBased }

    /**
     * 处理流式数据块
     * @param chunk 新的数据块
     * @return 处理后可以输出的文本
     */
    fun processChunk(chunk: String): String {
        if (compiledRules.isEmpty()) {
            return chunk
        }

        buffer.append(chunk)

        // 找到最后一个换行符的位置
        val lastNewline = buffer.lastIndexOf('\n')

        if (lastNewline == -1) {
            // 没有完整行
            if (buffer.length > maxBufferSize) {
                // 缓冲区过大，强制输出
                return flushPartial()
            }
            return ""
        }

        // 提取完整行部分
        val completeLines = buffer.substring(0, lastNewline + 1)
        val remaining = buffer.substring(lastNewline + 1)
        buffer.clear()
        buffer.append(remaining)

        // 对完整行应用所有规则
        var result = completeLines

        // 先应用行级规则
        lineRules.forEach { rule ->
            result = result.replace(rule.regex, rule.replacement)
        }

        // 再应用非行级规则
        nonLineRules.forEach { rule ->
            result = result.replace(rule.regex, rule.replacement)
        }

        return result
    }

    /**
     * 强制输出部分缓冲区（当缓冲区过大时）
     */
    private fun flushPartial(): String {
        // 保留最后一部分以防截断
        val keepSize = 50.coerceAtMost(buffer.length)
        val outputSize = buffer.length - keepSize

        if (outputSize <= 0) {
            return ""
        }

        val output = buffer.substring(0, outputSize)
        val remaining = buffer.substring(outputSize)
        buffer.clear()
        buffer.append(remaining)

        // 只应用非行级规则
        var result = output
        nonLineRules.forEach { rule ->
            result = result.replace(rule.regex, rule.replacement)
        }

        return result
    }

    /**
     * 流结束时处理剩余缓冲区
     * @return 处理后的剩余文本
     */
    fun flush(): String {
        if (buffer.isEmpty()) {
            return ""
        }

        val remaining = buffer.toString()
        buffer.clear()

        // 应用所有规则
        var result = remaining
        compiledRules.forEach { rule ->
            result = result.replace(rule.regex, rule.replacement)
        }

        return result
    }

    /**
     * 重置处理器状态
     */
    fun reset() {
        buffer.clear()
    }

    private data class CompiledRule(
        val regex: Regex,
        val replacement: String,
        val isLineBased: Boolean
    )

    companion object {
        /**
         * 测试单个正则规则
         * @return Pair<是否成功, 结果或错误信息>
         */
        fun testRule(pattern: String, replacement: String, testInput: String): Pair<Boolean, String> {
            return try {
                val regex = Regex(pattern, RegexOption.MULTILINE)
                val result = testInput.replace(regex, replacement)
                Pair(true, result)
            } catch (e: Exception) {
                Pair(false, "正则表达式错误: ${e.message}")
            }
        }

        /**
         * 验证正则表达式是否有效
         */
        fun isValidPattern(pattern: String): Boolean {
            return try {
                Regex(pattern)
                true
            } catch (e: Exception) {
                false
            }
        }
    }
}

/**
 * 正则规则数据
 */
data class RegexRuleData(
    val pattern: String,
    val replacement: String,
    val isEnabled: Boolean = true,
    val order: Int = 0
)
