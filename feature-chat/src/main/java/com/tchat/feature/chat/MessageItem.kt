package com.tchat.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Bot
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.X
import com.composables.icons.lucide.Wrench
import com.tchat.data.model.Message
import com.tchat.data.model.MessageRole
import com.tchat.data.model.ToolResultData
import com.tchat.feature.chat.markdown.MarkdownText

@Composable
fun MessageItem(
    message: Message,
    modifier: Modifier = Modifier,
    providerIcon: ImageVector? = null,
    modelName: String = "",
    nextMessage: Message? = null,  // 下一条消息（用于找到对应的 AI 回复）
    onRegenerate: ((userMessageId: String, aiMessageId: String) -> Unit)? = null,
    onSelectVariant: ((messageId: String, variantIndex: Int) -> Unit)? = null
) {
    val isUser = message.role == MessageRole.USER

    if (isUser) {
        // 用户消息：右对齐气泡样式
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.End
        ) {
            Surface(
                modifier = Modifier.widthIn(max = 280.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = message.content,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            // 重新生成按钮（仅当下一条是 AI 消息且不在流式加载时显示）
            if (nextMessage != null &&
                nextMessage.role == MessageRole.ASSISTANT &&
                !nextMessage.isStreaming &&
                onRegenerate != null
            ) {
                Spacer(modifier = Modifier.height(4.dp))
                IconButton(
                    onClick = { onRegenerate(message.id, nextMessage.id) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Lucide.RefreshCw,
                        contentDescription = "重新生成",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    } else {
        // AI 消息：头像和模型名称在上方，内容在下方
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // 头像和模型名称行
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // AI 提供商图标
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = providerIcon ?: Lucide.Bot,
                            contentDescription = "AI Provider",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // 模型名称
                if (modelName.isNotEmpty()) {
                    Text(
                        text = modelName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 消息内容
            Column {
                // 工具执行结果（如果有）
                if (!message.toolResults.isNullOrEmpty()) {
                    ToolResultsSection(toolResults = message.toolResults!!)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (message.content.isNotEmpty()) {
                    MarkdownText(
                        markdown = message.content,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 流式加载指示器或统计信息和变体选择器
                if (message.isStreaming) {
                    Spacer(modifier = Modifier.height(8.dp))
                    StreamingIndicator()
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 变体选择器（当有多个变体时显示）
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

                        // 统计信息
                        if (message.inputTokens > 0 || message.outputTokens > 0) {
                            StatisticsInfo(
                                inputTokens = message.inputTokens,
                                outputTokens = message.outputTokens,
                                tokensPerSecond = message.tokensPerSecond,
                                firstTokenLatency = message.firstTokenLatency
                            )
                        }
                    }
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
private fun ToolResultsSection(toolResults: List<ToolResultData>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
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
private fun ToolCallCard(result: ToolResultData) {
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
        shape = MaterialTheme.shapes.medium,
        color = if (result.isError)
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
        else
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // 工具调用标题行
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // 状态图标
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (result.isError)
                        MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                    else
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    modifier = Modifier.size(28.dp)
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
                
                // 工具名称和简要信息
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = result.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (result.isError)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.primary
                    )
                    
                    // 参数摘要（如果有）
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
                
                // 执行时间
                if (result.executionTimeMs > 0) {
                    Text(
                        text = "${result.executionTimeMs}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // 展开/收起图标
                Icon(
                    imageVector = if (expanded) Lucide.ChevronUp else Lucide.ChevronDown,
                    contentDescription = if (expanded) "收起" else "查看详情",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // 展开后显示详细信息
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 输入参数
                    if (result.arguments.isNotEmpty() && result.arguments != "{}") {
                        ToolDetailSection(
                            title = "输入参数",
                            content = formatJson(result.arguments),
                            isInput = true
                        )
                    }
                    
                    // 输出结果
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
        verticalArrangement = Arrangement.spacedBy(4.dp)
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
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        IconButton(
            onClick = onPrevious,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Lucide.ChevronLeft,
                contentDescription = "上一个",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }

        Text(
            text = "${currentIndex + 1}/$totalCount",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        IconButton(
            onClick = onNext,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Lucide.ChevronRight,
                contentDescription = "下一个",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
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
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 上行 Token
        if (inputTokens > 0) {
            Text(
                text = "↑$inputTokens",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 下行 Token
        if (outputTokens > 0) {
            Text(
                text = "↓$outputTokens",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // TPS (Tokens Per Second)
        if (tokensPerSecond > 0) {
            Text(
                text = "%.1f TPS".format(tokensPerSecond),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 首字延时
        if (firstTokenLatency > 0) {
            Text(
                text = "${firstTokenLatency}ms",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
