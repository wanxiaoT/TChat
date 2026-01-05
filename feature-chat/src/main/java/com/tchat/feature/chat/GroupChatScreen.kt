package com.tchat.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Psychology
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
import com.tchat.data.model.GroupActivationStrategy
import com.tchat.data.model.Assistant
import com.tchat.data.tool.Tool
import com.tchat.data.util.RegexRuleData
import kotlinx.coroutines.launch

/**
 * 群聊对话界面
 * 支持多个助手在同一个对话中协作
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatScreen(
    viewModel: GroupChatViewModel,
    groupChatId: String,
    chatId: String?,
    modifier: Modifier = Modifier,
    availableModels: List<String> = emptyList(),
    currentModel: String = "",
    onModelSelected: (String) -> Unit = {},
    providerIcon: ImageVector? = null,
    // 本地工具支持
    enabledTools: Set<LocalToolOption> = emptySet(),
    onToolToggle: (LocalToolOption, Boolean) -> Unit = { _, _ -> },
    getToolsForOptions: (List<LocalToolOption>) -> List<Tool> = { emptyList() },
    extraTools: List<Tool> = emptyList(),
    systemPrompt: String? = null,
    regexRules: List<RegexRuleData> = emptyList(),
    // 群聊成员列表
    assistants: List<Assistant> = emptyList(),
    // 深度研究支持
    onDeepResearch: ((String?) -> Unit)? = null,
    isDeepResearching: Boolean = false
) {
    val uiState by viewModel.uiState.collectAsState()
    val actualChatId by viewModel.actualChatId.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val currentGroupChat by viewModel.currentGroupChat.collectAsState()
    val groupMembers by viewModel.groupMembers.collectAsState()
    val currentSpeakerId by viewModel.currentSpeakerId.collectAsState()

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

    // 计算当前启用的工具列表
    val currentTools = remember(enabledTools, getToolsForOptions, extraTools) {
        getToolsForOptions(enabledTools.toList()) + extraTools
    }

    // 当工具状态变化时更新ViewModel的配置
    LaunchedEffect(enabledTools, currentTools, systemPrompt, currentModel, regexRules) {
        val hasTools = currentTools.isNotEmpty()
        val hasSystemPrompt = !systemPrompt.isNullOrEmpty()

        if (hasTools || hasSystemPrompt) {
            viewModel.setTools(currentTools, systemPrompt, currentModel, regexRules)
        } else {
            viewModel.setTools(emptyList(), null, currentModel, regexRules)
        }
    }

    // 监听 groupChatId 和 chatId 的变化
    LaunchedEffect(groupChatId, chatId, viewModel) {
        inputText = ""
        viewModel.loadGroupChat(groupChatId, chatId)
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
                    // 消息列表
                    MessageList(
                        messages = state.messages,
                        modifier = Modifier.weight(1f),
                        providerIcon = providerIcon,
                        modelName = currentModel,
                        onRegenerate = { userMessageId, aiMessageId ->
                            viewModel.regenerateMessage(userMessageId, aiMessageId)
                        },
                        onSelectVariant = { messageId, variantIndex ->
                            viewModel.selectVariant(messageId, variantIndex)
                        }
                    )

                    // 输入区域（工具栏 + 助手选择器 + 输入框）
                    Column {
                        // 深度研究进度提示
                        AnimatedVisibility(visible = isDeepResearching) {
                            DeepResearchIndicator()
                        }

                        // 助手选择器（手动模式）
                        if (currentGroupChat?.activationStrategy == GroupActivationStrategy.MANUAL) {
                            AssistantSelector(
                                assistants = assistants.filter { assistant ->
                                    groupMembers.any { it.assistantId == assistant.id }
                                },
                                currentSpeakerId = currentSpeakerId,
                                onAssistantSelected = { assistantId ->
                                    viewModel.selectAssistant(assistantId)
                                }
                            )
                        }

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
                                },
                                onDeepResearch = onDeepResearch?.let { callback ->
                                    {
                                        val query = inputText.takeIf { it.isNotBlank() }
                                        callback(query)
                                        if (query != null) {
                                            inputText = ""
                                        }
                                    }
                                },
                                isDeepResearching = isDeepResearching
                            )
                        }

                        // 输入框
                        MessageInput(
                            text = inputText,
                            onTextChange = { inputText = it },
                            onSend = {
                                if (inputText.isNotBlank()) {
                                    // 如果是手动模式且没有选择助手，提示用户
                                    if (currentGroupChat?.activationStrategy == GroupActivationStrategy.MANUAL
                                        && currentSpeakerId == null) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("请先选择助手")
                                        }
                                        return@MessageInput
                                    }

                                    viewModel.sendMessage(
                                        chatId = actualChatId ?: chatId,
                                        content = inputText,
                                        selectedAssistantId = currentSpeakerId
                                    )
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
 * 助手选择器（用于手动模式）
 */
@Composable
private fun AssistantSelector(
    assistants: List<Assistant>,
    currentSpeakerId: String?,
    onAssistantSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val currentAssistant = assistants.find { it.id == currentSpeakerId }

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
            Text(
                text = "发言助手:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Box {
                Surface(
                    onClick = { expanded = true },
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small,
                    tonalElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = currentAssistant?.name ?: "选择助手",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    assistants.forEach { assistant ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = assistant.name,
                                    color = if (assistant.id == currentSpeakerId) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            },
                            onClick = {
                                onAssistantSelected(assistant.id)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 深度研究进度指示器
 */
@Composable
private fun DeepResearchIndicator() {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Icon(
                imageVector = Icons.Default.Psychology,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "深度研究进行中...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
