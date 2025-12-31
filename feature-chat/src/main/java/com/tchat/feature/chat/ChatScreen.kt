package com.tchat.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.tchat.data.model.Message
import com.tchat.data.model.MessageRole

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    chatId: String?,
    modifier: Modifier = Modifier,
    availableModels: List<String> = emptyList(),
    currentModel: String = "",
    onModelSelected: (String) -> Unit = {},
    providerIcon: ImageVector? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val actualChatId by viewModel.actualChatId.collectAsState()
    var inputText by remember { mutableStateOf("") }

    // 监听 chatId 和 viewModel 的变化，确保切换聊天或重建 ViewModel 时都能重新加载
    LaunchedEffect(chatId, viewModel) {
        inputText = "" // 切换聊天时清空输入框，防止残留文本发送到错误聊天
        viewModel.loadChat(chatId)
    }

    Column(modifier = modifier.fillMaxSize()) {
        when (val state = uiState) {
            is ChatUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator()
                }
            }
            is ChatUiState.Success -> {
                MessageList(
                    messages = state.messages,
                    modifier = Modifier.weight(1f),
                    providerIcon = providerIcon,
                    onRegenerate = { userMessageId, aiMessageId ->
                        viewModel.regenerateMessage(userMessageId, aiMessageId)
                    },
                    onSelectVariant = { messageId, variantIndex ->
                        viewModel.selectVariant(messageId, variantIndex)
                    }
                )

                // 输入区域（工具栏 + 输入框）
                Column {
                    // 工具栏
                    if (availableModels.isNotEmpty()) {
                        InputToolbar(
                            availableModels = availableModels,
                            currentModel = currentModel,
                            onModelSelected = onModelSelected
                        )
                    }

                    // 输入框
                    MessageInput(
                        text = inputText,
                        onTextChange = { inputText = it },
                        onSend = {
                            if (inputText.isNotBlank()) {
                                viewModel.sendMessage(actualChatId ?: chatId, inputText)
                                inputText = ""
                            }
                        }
                    )
                }
            }
            is ChatUiState.Error -> {
                Text(text = "Error: ${state.message}")
            }
        }
    }
}

/**
 * 输入框上方的工具栏
 */
@Composable
private fun InputToolbar(
    availableModels: List<String>,
    currentModel: String,
    onModelSelected: (String) -> Unit
) {
    var modelMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 模型选择器
            Box {
                Surface(
                    onClick = { modelMenuExpanded = true },
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.small,
                    tonalElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // 模型图标（使用首字母作为简单图标）
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                text = getModelIcon(currentModel),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }

                        Text(
                            text = getModelDisplayName(currentModel),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                DropdownMenu(
                    expanded = modelMenuExpanded,
                    onDismissRequest = { modelMenuExpanded = false }
                ) {
                    availableModels.forEach { model ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        color = if (model == currentModel)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.surfaceContainerHighest,
                                        shape = MaterialTheme.shapes.extraSmall
                                    ) {
                                        Text(
                                            text = getModelIcon(model),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (model == currentModel)
                                                MaterialTheme.colorScheme.onPrimary
                                            else
                                                MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = model,
                                        color = if (model == currentModel)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            },
                            onClick = {
                                onModelSelected(model)
                                modelMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 获取模型图标（简化显示）
 */
private fun getModelIcon(model: String): String {
    return when {
        model.contains("gpt", ignoreCase = true) -> "GPT"
        model.contains("claude", ignoreCase = true) -> "C"
        model.contains("gemini", ignoreCase = true) -> "G"
        model.contains("o1", ignoreCase = true) -> "o1"
        else -> model.take(2).uppercase()
    }
}

/**
 * 获取模型简短显示名称
 */
private fun getModelDisplayName(model: String): String {
    return when {
        model.length <= 20 -> model
        else -> model.take(18) + "..."
    }
}

