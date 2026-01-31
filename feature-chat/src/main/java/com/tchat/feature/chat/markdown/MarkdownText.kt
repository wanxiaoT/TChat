package com.tchat.feature.chat.markdown

import android.graphics.Typeface
import android.widget.TextView
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin

/**
 * Markdown 渲染组件 - 使用 Markwon 库
 *
 * 借鉴cherry-studio的设计思路：
 * - 检测特殊代码块（如mermaid），使用专门组件渲染
 * - 增量更新：检测内容是否真正变化，避免不必要的重新渲染
 * - 支持主题适配
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    onLinkClick: ((String) -> Unit)? = null,
    selectable: Boolean = false
) {
    val context = LocalContext.current
    val isDarkTheme = isSystemInDarkTheme()

    // 获取Material主题颜色
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    val codeBackgroundColor = if (isDarkTheme) {
        0xFF1E1E1E.toInt()
    } else {
        0xFFF5F5F5.toInt()
    }
    val codeTextColor = if (isDarkTheme) {
        0xFFD4D4D4.toInt()
    } else {
        0xFF000000.toInt()
    }
    val blockQuoteColor = MaterialTheme.colorScheme.primary.toArgb()

    // LaTeX 公式文字大小
    val textSize = 48f

    // 创建Markwon实例（缓存）
    val markwon = remember(isDarkTheme, textColor, linkColor) {
        Markwon.builder(context)
            .usePlugin(MarkwonInlineParserPlugin.create())
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(HtmlPlugin.create())
            .usePlugin(JLatexMathPlugin.create(textSize) { builder ->
                builder.inlinesEnabled(true)
                builder.theme()
                    .textColor(textColor)
            })
            .usePlugin(object : io.noties.markwon.AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder
                        .linkColor(linkColor)
                        .codeTextColor(codeTextColor)
                        .codeBackgroundColor(codeBackgroundColor)
                        .codeBlockTextColor(codeTextColor)
                        .codeBlockBackgroundColor(codeBackgroundColor)
                        .codeTypeface(Typeface.MONOSPACE)
                        .codeBlockTypeface(Typeface.MONOSPACE)
                        .blockQuoteColor(blockQuoteColor)
                        .headingBreakHeight(0)
                }
            })
            .build()
    }

    // 解析内容块（检测mermaid等特殊代码块）
    val contentBlocks = remember(markdown) {
        ContentParser.parse(markdown)
    }

    // 检查是否包含特殊块（Mermaid 或 Thinking）
    val hasSpecialBlocks = remember(contentBlocks) {
        contentBlocks.any { it is ContentBlock.Mermaid || it is ContentBlock.Thinking }
    }

    if (hasSpecialBlocks) {
        // 包含特殊块时使用Column布局
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            contentBlocks.forEachIndexed { index, block ->
                key(index, block::class) {
                    when (block) {
                        is ContentBlock.Markdown -> {
                            MarkdownBlock(
                                content = block.content,
                                markwon = markwon,
                                textColor = textColor,
                                selectable = selectable
                            )
                        }
                        is ContentBlock.Mermaid -> {
                            MermaidView(code = block.code)
                        }
                        is ContentBlock.Thinking -> {
                            ThinkingView(content = block.content)
                        }
                    }
                }
            }
        }
    } else {
        // 纯Markdown时直接渲染，避免Column布局开销
        SimpleMarkdownBlock(
            content = markdown,
            markwon = markwon,
            textColor = textColor,
            selectable = selectable,
            modifier = modifier
        )
    }
}

/**
 * 简单Markdown渲染（无特殊块）
 * 直接更新TextView，最小化重组
 */
@Composable
private fun SimpleMarkdownBlock(
    content: String,
    markwon: Markwon,
    textColor: Int,
    selectable: Boolean,
    modifier: Modifier = Modifier
) {
    // 记住上一次的内容长度，用于增量检测
    var lastContent by remember { mutableStateOf("") }
    var textViewInstance by remember { mutableStateOf<TextView?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                setTextColor(textColor)
                textSize = 16f
                setLineSpacing(0f, 1.2f)
                movementMethod = android.text.method.LinkMovementMethod.getInstance()
                setTextIsSelectable(selectable)
                textViewInstance = this
            }
        },
        update = { textView ->
            if (textView.isTextSelectable != selectable) {
                textView.setTextIsSelectable(selectable)
            }
            // 只在内容真正变化时更新
            if (content != lastContent) {
                textView.setTextColor(textColor)
                val spanned = markwon.toMarkdown(content)
                markwon.setParsedMarkdown(textView, spanned)
                lastContent = content
            }
        }
    )
}

/**
 * Markdown内容块渲染
 */
@Composable
private fun MarkdownBlock(
    content: String,
    markwon: Markwon,
    textColor: Int,
    selectable: Boolean
) {
    var lastContent by remember { mutableStateOf("") }

    AndroidView(
        factory = { ctx ->
            TextView(ctx).apply {
                setTextColor(textColor)
                textSize = 16f
                setLineSpacing(0f, 1.2f)
                movementMethod = android.text.method.LinkMovementMethod.getInstance()
                setTextIsSelectable(selectable)
            }
        },
        update = { textView ->
            if (textView.isTextSelectable != selectable) {
                textView.setTextIsSelectable(selectable)
            }
            if (content != lastContent) {
                textView.setTextColor(textColor)
                val spanned = markwon.toMarkdown(content)
                markwon.setParsedMarkdown(textView, spanned)
                lastContent = content
            }
        }
    )
}
