package com.tchat.feature.chat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Bot
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Share2
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Volume2
import com.composables.icons.lucide.X
import com.tchat.data.model.Message
import com.tchat.data.model.MessagePart
import com.tchat.data.model.MessageRole
import com.tchat.feature.chat.markdown.MarkdownText

@Composable
fun MessageItem(
    message: Message,
    modifier: Modifier = Modifier,
    providerIcon: ImageVector? = null,
    modelName: String = "",
    previousMessage: Message? = null,  // 上一条消息（用于找到对应的用户消息）
    onRegenerate: ((userMessageId: String, aiMessageId: String) -> Unit)? = null,
    onSelectVariant: ((messageId: String, variantIndex: Int) -> Unit)? = null,
    onCopy: ((content: String) -> Unit)? = null,
    onSpeak: ((content: String) -> Unit)? = null,
    onShare: ((content: String) -> Unit)? = null,
    onDelete: ((messageId: String) -> Unit)? = null
) {
    val isUser = message.role == MessageRole.USER
    val colorScheme = MaterialTheme.colorScheme
    val textContent = remember(message.parts, message.selectedVariantIndex, message.variants) {
        message.getCurrentContent().ifBlank { message.getTextContent() }
    }
    val imageParts = remember(message.parts) { message.parts.filterIsInstance<MessagePart.Image>() }
    val videoParts = remember(message.parts) { message.parts.filterIsInstance<MessagePart.Video>() }
    val displayedModelName = remember(message.modelName, modelName) {
        message.modelName?.takeIf { it.isNotBlank() } ?: modelName
    }
    val speakerName = remember(message.groupMetadata) {
        message.groupMetadata?.assistantName?.takeIf { it.isNotBlank() }
    }
    val assistantCardColor = colorScheme.surface.copy(alpha = 0.82f)
    val assistantBorderColor = colorScheme.outlineVariant.copy(alpha = 0.34f)

    if (isUser) {
        val bubbleShape = RoundedCornerShape(
            topStart = 21.dp,
            topEnd = 21.dp,
            bottomStart = 21.dp,
            bottomEnd = 7.dp
        )
        val bubbleStart = colorScheme.primary
        val bubbleEnd = lerp(colorScheme.primary, colorScheme.tertiary, 0.38f)

        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.End
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 328.dp)
                    .clip(bubbleShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(bubbleStart, bubbleEnd)
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.18f),
                        shape = bubbleShape
                    )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (imageParts.isNotEmpty() || videoParts.isNotEmpty()) {
                        MessageMediaSection(
                            imageParts = imageParts,
                            videoParts = videoParts,
                            isUser = true
                        )
                    }

                    if (textContent.isNotBlank()) {
                        SelectionContainer {
                            Text(
                                text = textContent,
                                color = colorScheme.onPrimary,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            AssistantMessageHeader(
                providerIcon = providerIcon,
                modelName = displayedModelName,
                speakerName = speakerName
            )

            Surface(
                shape = RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomStart = 20.dp,
                    bottomEnd = 8.dp
                ),
                color = assistantCardColor,
                border = BorderStroke(1.dp, assistantBorderColor),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 13.dp, vertical = 11.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    if (imageParts.isNotEmpty() || videoParts.isNotEmpty()) {
                        MessageMediaSection(
                            imageParts = imageParts,
                            videoParts = videoParts,
                            isUser = false
                        )
                    }

                    val toolResults = message.getToolResults()
                    if (toolResults.isNotEmpty()) {
                        ToolResultsSection(toolResults = toolResults)
                    }

                    if (textContent.isNotEmpty()) {
                        MarkdownText(
                            markdown = textContent,
                            modifier = Modifier.fillMaxWidth(),
                            selectable = true
                        )
                    }
                }
            }

            if (message.isStreaming) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MetaChip {
                        StreamingIndicator()
                        Spacer(modifier = Modifier.size(2.dp))
                        Text(
                            text = "生成中",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }

                    if (previousMessage != null &&
                        previousMessage.role == MessageRole.USER &&
                        onRegenerate != null
                    ) {
                        BubbleActionButton(
                            onClick = { onRegenerate(previousMessage.id, message.id) },
                            icon = Lucide.RefreshCw,
                            contentDescription = "重新生成"
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val variantCount = message.variantCount()
                    if (variantCount > 1 && onSelectVariant != null) {
                        VariantSelector(
                            currentIndex = message.selectedVariantIndex,
                            totalCount = variantCount,
                            onPrevious = {
                                val newIndex = (message.selectedVariantIndex - 1 + variantCount) % variantCount
                                onSelectVariant(message.id, newIndex)
                            },
                            onNext = {
                                val newIndex = (message.selectedVariantIndex + 1) % variantCount
                                onSelectVariant(message.id, newIndex)
                            }
                        )
                    }

                    if (message.inputTokens > 0 || message.outputTokens > 0) {
                        StatisticsInfo(
                            inputTokens = message.inputTokens,
                            outputTokens = message.outputTokens,
                            tokensPerSecond = message.tokensPerSecond,
                            firstTokenLatency = message.firstTokenLatency
                        )
                    }
                }

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onCopy != null) {
                        BubbleActionButton(
                            onClick = { onCopy(textContent) },
                            icon = Lucide.Copy,
                            contentDescription = "复制"
                        )
                    }

                    if (onSpeak != null) {
                        BubbleActionButton(
                            onClick = { onSpeak(textContent) },
                            icon = Lucide.Volume2,
                            contentDescription = "朗读"
                        )
                    }

                    if (onShare != null) {
                        BubbleActionButton(
                            onClick = { onShare(textContent) },
                            icon = Lucide.Share2,
                            contentDescription = "分享"
                        )
                    }

                    if (previousMessage != null &&
                        previousMessage.role == MessageRole.USER &&
                        onRegenerate != null
                    ) {
                        BubbleActionButton(
                            onClick = { onRegenerate(previousMessage.id, message.id) },
                            icon = Lucide.RefreshCw,
                            contentDescription = "重新生成"
                        )
                    }

                    if (onDelete != null) {
                        BubbleActionButton(
                            onClick = { onDelete(message.id) },
                            icon = Lucide.Trash2,
                            contentDescription = "删除",
                            tint = colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantMessageHeader(
    providerIcon: ImageVector?,
    modelName: String,
    speakerName: String? = null
) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = colorScheme.surface.copy(alpha = 0.78f),
            border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.38f)),
            modifier = Modifier.size(30.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = providerIcon ?: Lucide.Bot,
                    contentDescription = "AI Provider",
                    tint = colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = if (speakerName != null) "群聊成员" else "AI 助手",
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
            )
            Text(
                text = speakerName ?: if (modelName.isNotBlank()) modelName else "智能回复",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (speakerName != null && modelName.isNotBlank()) {
                Text(
                    text = modelName,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MetaChip(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.56f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            content = content
        )
    }
}

@Composable
private fun BubbleActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.56f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f)
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

@Composable
private fun MessageMediaSection(
    imageParts: List<MessagePart.Image>,
    videoParts: List<MessagePart.Video>,
    isUser: Boolean
) {
    val colorScheme = MaterialTheme.colorScheme
    val mediaShape = RoundedCornerShape(18.dp)
    val mediaBorder = colorScheme.outlineVariant.copy(alpha = 0.36f)
    val targetImageSizePx = with(LocalDensity.current) { 360.dp.roundToPx() }
    val fallbackColor = if (isUser) {
        colorScheme.secondaryContainer.copy(alpha = 0.72f)
    } else {
        colorScheme.surfaceColorAtElevation(3.dp)
    }
    val fallbackTextColor = if (isUser) {
        colorScheme.onSecondaryContainer
    } else {
        colorScheme.onSurfaceVariant
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        imageParts.forEach { img ->
            val bitmap = rememberScaledBitmap(img.filePath, targetImageSizePx)

            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = img.fileName ?: "image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .clip(mediaShape)
                        .border(1.dp, mediaBorder, mediaShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    shape = mediaShape,
                    color = fallbackColor,
                    border = BorderStroke(1.dp, mediaBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "图片不可用：${img.fileName ?: img.filePath}",
                        style = MaterialTheme.typography.bodySmall,
                        color = fallbackTextColor,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        videoParts.forEach { vid ->
            Surface(
                shape = mediaShape,
                color = fallbackColor,
                border = BorderStroke(1.dp, mediaBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = if (isUser) 0.22f else 0.78f),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Lucide.ChevronRight,
                                contentDescription = null,
                                tint = fallbackTextColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = vid.fileName ?: "视频",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isUser) fallbackTextColor else colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * 工具执行结果区域
 * 每个工具调用显示为独立的可展开卡片
 */
@Composable
private fun ToolResultsSection(toolResults: List<MessagePart.ToolResult>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        toolResults.forEach { result ->
            ToolCallCard(result = result)
        }
    }
}

/**
 * 单个工具调用卡片
 * 显示工具名称、参数、执行时间和结果
 * 点击可展开/收起查看详细信息
 */
@Composable
private fun ToolCallCard(result: MessagePart.ToolResult) {
    var expanded by remember { mutableStateOf(false) }

    // 格式化参数显示（安全处理空字符串）
    val formattedArgs = remember(result.arguments) {
        val args = result.arguments.trim()
        if (args.isEmpty() || args == "{}") {
            null
        } else {
            try {
                val json = org.json.JSONObject(args)
                if (json.length() == 0) {
                    null
                } else {
                    json.keys().asSequence().map { key ->
                        val value = json.opt(key)?.toString()?.take(50) ?: ""
                        "$key: $value"
                    }.joinToString(", ")
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    Surface(
        onClick = { expanded = !expanded },
        shape = RoundedCornerShape(16.dp),
        color = if (result.isError)
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.62f)
        else
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f)
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (result.isError)
                        MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                    else
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    modifier = Modifier.size(30.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = if (result.isError) Lucide.X else Lucide.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (result.isError)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = result.toolName,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (result.isError)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.primary
                    )

                    if (formattedArgs != null) {
                        Text(
                            text = formattedArgs,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }

                if (result.executionTimeMs > 0) {
                    MetaChip {
                        Text(
                            text = "${result.executionTimeMs}ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Icon(
                    imageVector = if (expanded) Lucide.ChevronUp else Lucide.ChevronDown,
                    contentDescription = if (expanded) "收起" else "查看详情",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (result.arguments.isNotEmpty() && result.arguments != "{}") {
                        ToolDetailSection(
                            title = "输入参数",
                            content = formatJson(result.arguments),
                            isInput = true
                        )
                    }

                    ToolDetailSection(
                        title = if (result.isError) "错误信息" else "执行结果",
                        content = formatJson(result.result),
                        isError = result.isError
                    )
                }
            }
        }
    }
}

/**
 * 工具详情区块
 */
@Composable
private fun ToolDetailSection(
    title: String,
    content: String,
    isInput: Boolean = false,
    isError: Boolean = false
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = when {
                isError -> MaterialTheme.colorScheme.error
                isInput -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.primary
            }
        )

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = content,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(10.dp)
            )
        }
    }
}

/**
 * 格式化 JSON 字符串，使其更易读
 * 安全处理空字符串和无效输入
 */
private fun formatJson(jsonStr: String): String {
    val trimmed = jsonStr.trim()
    if (trimmed.isEmpty() || trimmed == "{}") {
        return "(无参数)"
    }
    
    return try {
        when {
            trimmed.startsWith("{") -> org.json.JSONObject(trimmed).toString(2)
            trimmed.startsWith("[") -> org.json.JSONArray(trimmed).toString(2)
            else -> jsonStr
        }
    } catch (e: Exception) {
        // 如果解析失败，返回原始字符串
        jsonStr.ifEmpty { "(解析失败)" }
    }
}

/**
 * 变体选择器
 */
@Composable
private fun VariantSelector(
    currentIndex: Int,
    totalCount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    MetaChip {
        IconButton(
            onClick = onPrevious,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Lucide.ChevronLeft,
                contentDescription = "上一个",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
        }

        Text(
            text = "版本 ${currentIndex + 1}/$totalCount",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        IconButton(
            onClick = onNext,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Lucide.ChevronRight,
                contentDescription = "下一个",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/**
 * 流式加载指示器 - Material You 风格
 */
@Composable
private fun StreamingIndicator() {
    CircularProgressIndicator(
        modifier = Modifier.size(16.dp),
        strokeWidth = 2.dp,
        color = MaterialTheme.colorScheme.primary
    )
}

/**
 * 统计信息显示
 */
@Composable
private fun StatisticsInfo(
    inputTokens: Int,
    outputTokens: Int,
    tokensPerSecond: Double,
    firstTokenLatency: Long
) {
    MetaChip {
        if (inputTokens > 0) {
            Text(
                text = "↑$inputTokens",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (outputTokens > 0) {
            Text(
                text = "↓$outputTokens",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (tokensPerSecond > 0) {
            Text(
                text = "%.1f TPS".format(tokensPerSecond),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (firstTokenLatency > 0) {
            Text(
                text = "${firstTokenLatency}ms",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
