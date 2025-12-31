package com.tchat.feature.chat.markdown

/**
 * Markdown内容块类型
 *
 * 借鉴cherry-studio的设计：将内容分割为不同类型的块，
 * 普通markdown和特殊视图（如mermaid）分别处理
 */
sealed class ContentBlock {
    /** 普通Markdown内容 */
    data class Markdown(val content: String) : ContentBlock()

    /** Mermaid图表代码块 */
    data class Mermaid(val code: String) : ContentBlock()
}

/**
 * 内容解析器
 *
 * 将Markdown文本分割为普通内容和特殊代码块（如Mermaid）
 */
object ContentParser {

    // 支持的特殊视图类型（可扩展）
    private val SPECIAL_LANGUAGES = setOf("mermaid")

    // 代码块正则：匹配 ```language ... ```
    private val CODE_BLOCK_REGEX = Regex(
        """```(\w*)\s*\n([\s\S]*?)```""",
        RegexOption.MULTILINE
    )

    /**
     * 解析Markdown内容，分割为不同类型的内容块
     */
    fun parse(markdown: String): List<ContentBlock> {
        val blocks = mutableListOf<ContentBlock>()
        var lastEnd = 0

        CODE_BLOCK_REGEX.findAll(markdown).forEach { match ->
            val language = match.groupValues[1].lowercase()
            val code = match.groupValues[2].trimEnd()

            // 添加代码块之前的普通markdown内容
            if (match.range.first > lastEnd) {
                val textBefore = markdown.substring(lastEnd, match.range.first)
                if (textBefore.isNotBlank()) {
                    blocks.add(ContentBlock.Markdown(textBefore))
                }
            }

            // 根据语言类型添加对应的内容块
            when {
                language == "mermaid" -> {
                    blocks.add(ContentBlock.Mermaid(code))
                }
                else -> {
                    // 非特殊语言的代码块保留为markdown
                    blocks.add(ContentBlock.Markdown(match.value))
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
