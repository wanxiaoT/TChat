package com.tchat.feature.chat.markdown

/**
 * Markdown内容块类型
 *
 * 借鉴cherry-studio的设计：将内容分割为不同类型的块，
 * 普通markdown和特殊视图（如mermaid、thinking）分别处理
 */
sealed class ContentBlock {
    /** 普通Markdown内容 */
    data class Markdown(val content: String) : ContentBlock()

    /** Mermaid图表代码块 */
    data class Mermaid(val code: String) : ContentBlock()

    /** Thinking思考过程块 */
    data class Thinking(val content: String) : ContentBlock()
}

/**
 * 内容解析器
 *
 * 将Markdown文本分割为普通内容和特殊代码块（如Mermaid、Thinking）
 */
object ContentParser {

    // 代码块正则：匹配 ```language ... ```
    private val CODE_BLOCK_REGEX = Regex(
        """```(\w*)\s*\n([\s\S]*?)```""",
        RegexOption.MULTILINE
    )

    // Thinking标签正则：匹配 <thinking>...</thinking> 或 <think>...</think>
    private val THINKING_TAG_REGEX = Regex(
        """<think(?:ing)?>\s*([\s\S]*?)\s*</think(?:ing)?>""",
        setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)
    )

    /**
     * 解析Markdown内容，分割为不同类型的内容块
     */
    fun parse(markdown: String): List<ContentBlock> {
        val blocks = mutableListOf<ContentBlock>()

        // 先移除thinking标签，记录位置
        val thinkingMatches = THINKING_TAG_REGEX.findAll(markdown).toList()
        val codeBlockMatches = CODE_BLOCK_REGEX.findAll(markdown).toList()

        // 合并所有匹配项并按位置排序
        data class Match(val range: IntRange, val type: String, val content: String)
        val allMatches = mutableListOf<Match>()

        thinkingMatches.forEach { matchResult ->
            allMatches.add(Match(matchResult.range, "thinking", matchResult.groupValues[1]))
        }

        codeBlockMatches.forEach { matchResult ->
            val language = matchResult.groupValues[1].lowercase()
            val code = matchResult.groupValues[2].trimEnd()
            allMatches.add(Match(matchResult.range, language, code))
        }

        // 按位置排序
        allMatches.sortBy { it.range.first }

        var lastEnd = 0
        allMatches.forEach { match ->
            // 添加匹配项之前的普通markdown内容
            if (match.range.first > lastEnd) {
                val textBefore = markdown.substring(lastEnd, match.range.first)
                if (textBefore.isNotBlank()) {
                    blocks.add(ContentBlock.Markdown(textBefore))
                }
            }

            // 根据类型添加对应的内容块
            when (match.type) {
                "thinking" -> {
                    blocks.add(ContentBlock.Thinking(match.content))
                }
                "mermaid" -> {
                    blocks.add(ContentBlock.Mermaid(match.content))
                }
                else -> {
                    // 非特殊语言的代码块保留为markdown
                    val originalMatch = codeBlockMatches.find { it.range == match.range }
                    if (originalMatch != null) {
                        blocks.add(ContentBlock.Markdown(originalMatch.value))
                    }
                }
            }

            lastEnd = match.range.last + 1
        }

        // 添加最后一段普通内容
        if (lastEnd < markdown.length) {
            val remaining = markdown.substring(lastEnd)
            if (remaining.isNotBlank()) {
                blocks.add(ContentBlock.Markdown(remaining))
            }
        }

        // 如果没有任何块，整个内容作为markdown
        if (blocks.isEmpty() && markdown.isNotBlank()) {
            blocks.add(ContentBlock.Markdown(markdown))
        }

        return blocks
    }
}
