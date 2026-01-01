package com.tchat.feature.chat

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Bot
import com.composables.icons.lucide.BrainCircuit
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Wrench
import com.tchat.data.model.LocalToolOption
import com.tchat.data.tool.Tool
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    chatId: String?,
    modifier: Modifier = Modifier,
    availableModels: List<String> = emptyList(),
    currentModel: String = "",
    onModelSelected: (String) -> Unit = {},
    providerIcon: ImageVector? = null,
    // 本地工具支持 - 改为 LocalToolOption 集合
    enabledTools: Set<LocalToolOption> = emptySet(),
    onToolToggle: (LocalToolOption, Boolean) -> Unit = { _, _ -> },
    // 工具实例 - 从外部传入
    getToolsForOptions: (List<LocalToolOption>) -> List<Tool> = { emptyList() },
    // 系统提示
    systemPrompt: String? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val actualChatId by viewModel.actualChatId.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    var inputText by remember { mutableStateOf("") }

    // Snackbar 状态
    val snackbarHostState = remember { SnackbarHostState() }

    // 显示错误信息
    LaunchedEffect(errorMessage) {
        errorMessage?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Long
            )
            viewModel.clearError()
        }
    }

    // 工具选择抽屉状态
    val scope = rememberCoroutineScope()
    var showToolSheet by remember { mutableStateOf(false) }
    val toolSheetState = rememberModalBottomSheetState()

    // 计算当前启用的工具列表 - 每次 enabledTools 变化时重新计算
    val currentTools = remember(enabledTools, getToolsForOptions) {
        if (enabledTools.isEmpty()) {
            emptyList()
        } else {
            getToolsForOptions(enabledTools.toList())
        }
    }

    // 当工具状态变化时更新ViewModel的配置
    LaunchedEffect(enabledTools, currentTools, systemPrompt) {
        // 只要有工具或系统提示，就设置配置
        val hasTools = currentTools.isNotEmpty()
        val hasSystemPrompt = !systemPrompt.isNullOrEmpty()
        
        if (hasTools || hasSystemPrompt) {
            viewModel.setTools(currentTools, systemPrompt)
        } else {
            // 即使没有工具和系统提示，也设置一个空配置，确保消息可以正常发送
            viewModel.setTools(emptyList(), null)
        }
    }

    // 监听 chatId 和 viewModel 的变化，确保切换聊天或重建 ViewModel 时都能重新加载
    LaunchedEffect(chatId, viewModel) {
        inputText = "" // 切换聊天时清空输入框，防止残留文本发送到错误聊天
        viewModel.loadChat(chatId)
    }

    // 工具选择抽屉
    if (showToolSheet) {
        ToolSelectorSheet(
            sheetState = toolSheetState,
            enabledTools = enabledTools,
            onToolToggle = onToolToggle,
            onDismiss = { 
                scope.launch { 
                    toolSheetState.hide()
                    showToolSheet = false 
                }
            }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                                onModelSelected = onModelSelected,
                                enabledToolsCount = enabledTools.size,
                                onToolsClick = {
                                    showToolSheet = true
                                    scope.launch { toolSheetState.show() }
                                }
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

        // Snackbar 显示在底部
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/**
 * 输入框上方的工具栏
 */
@Composable
private fun InputToolbar(
    availableModels: List<String>,
    currentModel: String,
    onModelSelected: (String) -> Unit,
    enabledToolsCount: Int = 0,
    onToolsClick: () -> Unit = {}
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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 模型选择器
            if (availableModels.isNotEmpty()) {
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 使用 Lucide Icon 显示模型类型
                            Icon(
                                imageVector = getModelLucideIcon(currentModel),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )

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
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // 使用 Lucide Icon 显示模型类型
                                        Icon(
                                            imageVector = getModelLucideIcon(model),
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = if (model == currentModel)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                        )
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

            Spacer(modifier = Modifier.weight(1f))

            // 工具按钮 - 点击打开抽屉
            Surface(
                onClick = onToolsClick,
                color = if (enabledToolsCount > 0)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.small,
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Lucide.Wrench,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (enabledToolsCount > 0)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (enabledToolsCount > 0) "工具 ($enabledToolsCount)" else "工具",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (enabledToolsCount > 0)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 根据模型名称获取对应的 Lucide 图标
 */
private fun getModelLucideIcon(model: String): ImageVector {
    return when {
        // OpenAI 模型
        model.contains("gpt", ignoreCase = true) -> Lucide.Sparkles
        model.contains("o1", ignoreCase = true) -> Lucide.Sparkles
        model.contains("o3", ignoreCase = true) -> Lucide.Sparkles
        model.contains("davinci", ignoreCase = true) -> Lucide.Sparkles
        
        // Claude 模型
        model.contains("claude", ignoreCase = true) -> Lucide.Bot
        
        // Gemini 模型
        model.contains("gemini", ignoreCase = true) -> Lucide.BrainCircuit
        
        // 其他模型使用通用图标
        else -> Lucide.Bot
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
