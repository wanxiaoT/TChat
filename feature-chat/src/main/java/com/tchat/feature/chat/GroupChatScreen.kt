package com.tchat.feature.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Bot
import com.composables.icons.lucide.BrainCircuit
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Wrench
import com.tchat.data.model.LocalToolOption
import com.tchat.data.model.GroupActivationStrategy
import com.tchat.data.model.Assistant
import com.tchat.data.model.ChatToolbarSettings
import com.tchat.data.tool.Tool
import com.tchat.data.util.RegexRuleData
import kotlinx.coroutines.launch
import java.util.Locale

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
    // 提供商ID（用于按提供商统计token）
    providerId: String? = null,
    // 是否记录token统计
    shouldRecordTokens: Boolean = true,
    // 群聊成员列表
    assistants: List<Assistant> = emptyList(),
    // 深度研究支持
    onDeepResearch: ((String?) -> Unit)? = null,
    isDeepResearching: Boolean = false,
    // 聊天工具栏显示/顺序设置
    chatToolbarSettings: ChatToolbarSettings = ChatToolbarSettings(),
    // i18n strings
    inputHint: String = "输入消息...",
    sendContentDescription: String = "发送",
    toolsText: String = "工具",
    toolsWithCountFormat: String = "工具 (%d)",
    deepResearchText: String = "深度研究",
    deepResearchRunningText: String = "研究中",
    deepResearchInProgressText: String = "深度研究进行中...",
    speakingAssistantLabel: String = "发言助手:",
    selectAssistantHint: String = "选择助手",
    pleaseSelectAssistantFirst: String = "请先选择助手"
) {
    val uiState by viewModel.uiState.collectAsState()
    val actualChatId by viewModel.actualChatId.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val currentGroupChat by viewModel.currentGroupChat.collectAsState()
    val groupMembers by viewModel.groupMembers.collectAsState()
    val currentSpeakerId by viewModel.currentSpeakerId.collectAsState()

    var inputText by remember { mutableStateOf("") }

    // Context for clipboard, share, TTS
    val context = LocalContext.current

    // Snackbar 状态
    val snackbarHostState = remember { SnackbarHostState() }

    // Coroutine scope
    val scope = rememberCoroutineScope()

    // TTS 初始化
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsReady by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        tts = TextToSpeech(context) { status ->
            isTtsReady = status == TextToSpeech.SUCCESS
            if (isTtsReady) {
                tts?.language = Locale.getDefault()
            }
        }
        onDispose {
            tts?.stop()
            tts?.shutdown()
        }
    }

    // 复制到剪贴板
    val onCopy: (String) -> Unit = { content ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("message", content)
        clipboard.setPrimaryClip(clip)
        scope.launch {
            snackbarHostState.showSnackbar(
                message = "已复制到剪贴板",
                duration = SnackbarDuration.Short
            )
        }
    }

    // 朗读
    val onSpeak: (String) -> Unit = { content ->
        if (isTtsReady) {
            tts?.speak(content, TextToSpeech.QUEUE_FLUSH, null, null)
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = "语音引擎未就绪",
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    // 分享
    val onShare: (String) -> Unit = { content ->
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, content)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, null)
        context.startActivity(shareIntent)
    }

    // 删除消息
    val onDelete: (String) -> Unit = { messageId ->
        viewModel.deleteMessage(messageId)
    }

    val onRegenerateMessage: (String, String) -> Unit = { userMessageId, aiMessageId ->
        viewModel.regenerateMessage(userMessageId, aiMessageId)
    }

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
    var showToolSheet by remember { mutableStateOf(false) }
    val toolSheetState = rememberModalBottomSheetState()

    // 计算当前启用的工具列表
    val currentTools = remember(enabledTools, getToolsForOptions, extraTools) {
        getToolsForOptions(enabledTools.toList()) + extraTools
    }

    // 当工具状态变化时更新ViewModel的配置
    LaunchedEffect(enabledTools, currentTools, systemPrompt, currentModel, regexRules, providerId, shouldRecordTokens) {
        val hasTools = currentTools.isNotEmpty()
        val hasSystemPrompt = !systemPrompt.isNullOrEmpty()

        if (hasTools || hasSystemPrompt) {
            viewModel.setTools(currentTools, systemPrompt, currentModel, providerId, shouldRecordTokens, regexRules)
        } else {
            viewModel.setTools(emptyList(), null, currentModel, providerId, shouldRecordTokens, regexRules)
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
                            onRegenerateMessage(userMessageId, aiMessageId)
                        },
                        onSelectVariant = { messageId, variantIndex ->
                            viewModel.selectVariant(messageId, variantIndex)
                        },
                        onCopy = onCopy,
                        onSpeak = onSpeak,
                        onShare = onShare,
                        onDelete = onDelete
                    )

                    // 输入区域（工具栏 + 助手选择器 + 输入框）
                    Column {
                        // 深度研究进度提示
                        AnimatedVisibility(visible = isDeepResearching) {
                            GroupDeepResearchIndicator(
                                text = deepResearchInProgressText
                            )
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
                                },
                                speakingAssistantLabel = speakingAssistantLabel,
                                selectAssistantHint = selectAssistantHint
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
                                isDeepResearching = isDeepResearching,
                                toolbarSettings = chatToolbarSettings,
                                toolsText = toolsText,
                                toolsWithCountFormat = toolsWithCountFormat,
                                deepResearchText = deepResearchText,
                                deepResearchRunningText = deepResearchRunningText
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
                                            snackbarHostState.showSnackbar(pleaseSelectAssistantFirst)
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
                            },
                            inputHint = inputHint,
                            sendContentDescription = sendContentDescription
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
    onAssistantSelected: (String) -> Unit,
    speakingAssistantLabel: String = "发言助手:",
    selectAssistantHint: String = "选择助手"
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
                text = speakingAssistantLabel,
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
                            text = currentAssistant?.name ?: selectAssistantHint,
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
private fun GroupDeepResearchIndicator(
    text: String = "深度研究进行中..."
) {
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
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
