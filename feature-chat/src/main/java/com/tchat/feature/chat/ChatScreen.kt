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
import com.composables.icons.lucide.Swords
import com.composables.icons.lucide.Wrench
import com.tchat.data.model.LocalToolOption
import com.tchat.data.model.ChatToolbarItem
import com.tchat.data.model.ChatToolbarSettings
import com.tchat.data.tool.Tool
import com.tchat.data.util.RegexRuleData
import kotlinx.coroutines.launch
import java.util.Locale

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
    // 额外的工具（如知识库搜索工具）
    extraTools: List<Tool> = emptyList(),
    // 系统提示
    systemPrompt: String? = null,
    // 正则规则
    regexRules: List<RegexRuleData> = emptyList(),
    // 提供商ID（用于按提供商统计token）
    providerId: String? = null,
    // 是否记录token统计
    shouldRecordTokens: Boolean = true,
    // 深度研究支持
    onDeepResearch: ((String?) -> Unit)? = null,
    isDeepResearching: Boolean = false,
    // 打野助手支持
    onJungleHelperClick: (() -> Unit)? = null,
    // 聊天工具栏显示/顺序设置
    chatToolbarSettings: ChatToolbarSettings = ChatToolbarSettings(),
    // i18n strings
    inputHint: String = "输入消息...",
    sendContentDescription: String = "发送",
    toolsText: String = "工具",
    toolsWithCountFormat: String = "工具 (%d)",
    deepResearchText: String = "深度研究",
    deepResearchRunningText: String = "研究中",
    deepResearchInProgressText: String = "深度研究进行中..."
) {
    val uiState by viewModel.uiState.collectAsState()
    val actualChatId by viewModel.actualChatId.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
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

    // 计算当前启用的工具列表 - 合并本地工具和额外工具（如知识库工具）
    val currentTools = remember(enabledTools, getToolsForOptions, extraTools) {
        getToolsForOptions(enabledTools.toList()) + extraTools
    }

    // 当工具状态变化时更新ViewModel的配置
    LaunchedEffect(enabledTools, currentTools, systemPrompt, currentModel, regexRules, providerId, shouldRecordTokens) {
        // 只要有工具或系统提示，就设置配置
        val hasTools = currentTools.isNotEmpty()
        val hasSystemPrompt = !systemPrompt.isNullOrEmpty()

        if (hasTools || hasSystemPrompt) {
            viewModel.setTools(currentTools, systemPrompt, currentModel, providerId, shouldRecordTokens, regexRules)
        } else {
            // 即使没有工具和系统提示，也设置一个空配置，确保消息可以正常发送
            viewModel.setTools(emptyList(), null, currentModel, providerId, shouldRecordTokens, regexRules)
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

                    // 输入区域（工具栏 + 输入框）
                    Column {
                        // 深度研究进度提示
                        AnimatedVisibility(visible = isDeepResearching) {
                            DeepResearchIndicator(
                                text = deepResearchInProgressText
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
                                onJungleHelperClick = onJungleHelperClick,
                                onDeepResearch = onDeepResearch?.let { callback ->
                                    {
                                        // 传递输入框内容（可能为空）
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
                                    viewModel.sendMessage(actualChatId ?: chatId, inputText)
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
 * 输入框上方的工具栏
 */
@Composable
internal fun InputToolbar(
    availableModels: List<String>,
    currentModel: String,
    onModelSelected: (String) -> Unit,
    enabledToolsCount: Int = 0,
    onToolsClick: () -> Unit = {},
    // 打野助手支持
    onJungleHelperClick: (() -> Unit)? = null,
    // 深度研究支持
    onDeepResearch: (() -> Unit)? = null,
    isDeepResearching: Boolean = false,
    toolbarSettings: ChatToolbarSettings = ChatToolbarSettings(),
    // i18n strings
    toolsText: String = "工具",
    toolsWithCountFormat: String = "工具 (%d)",
    deepResearchText: String = "深度研究",
    deepResearchRunningText: String = "研究中"
) {
    var modelMenuExpanded by remember { mutableStateOf(false) }
    val normalizedToolbarSettings = remember(toolbarSettings) { toolbarSettings.normalized() }

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
            normalizedToolbarSettings.items.forEach { config ->
                if (!config.visible) return@forEach

                when (config.item) {
                    ChatToolbarItem.MODEL -> {
                        if (availableModels.isNotEmpty()) {
                            Box {
                                Surface(
                                    onClick = { modelMenuExpanded = true },
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = MaterialTheme.shapes.small,
                                    tonalElevation = 0.dp
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = getModelLucideIcon(currentModel),
                                            contentDescription = currentModel.takeIf { it.isNotBlank() },
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.primary
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
                    }

                    ChatToolbarItem.DEEP_RESEARCH -> {
                        if (onDeepResearch != null) {
                            Surface(
                                onClick = { onDeepResearch() },
                                color = if (isDeepResearching)
                                    MaterialTheme.colorScheme.tertiaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = MaterialTheme.shapes.small,
                                tonalElevation = 0.dp
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isDeepResearching) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Psychology,
                                            contentDescription = deepResearchText,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    ChatToolbarItem.TOOLS -> {
                        Surface(
                            onClick = onToolsClick,
                            color = if (enabledToolsCount > 0)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = MaterialTheme.shapes.small,
                            tonalElevation = 0.dp
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                val badgeText = when {
                                    enabledToolsCount <= 0 -> null
                                    enabledToolsCount <= 9 -> enabledToolsCount.toString()
                                    else -> "9+"
                                }

                                if (badgeText != null) {
                                    BadgedBox(
                                        badge = {
                                            Badge {
                                                Text(badgeText)
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Lucide.Wrench,
                                            contentDescription = toolsWithCountFormat.format(enabledToolsCount),
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector = Lucide.Wrench,
                                        contentDescription = toolsText,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    ChatToolbarItem.JUNGLE_HELPER -> {
                        if (onJungleHelperClick != null) {
                            Surface(
                                onClick = onJungleHelperClick,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = MaterialTheme.shapes.small,
                                tonalElevation = 0.dp
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Lucide.Swords,
                                        contentDescription = "打野助手",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 根据模型名称获取对应的 Lucide 图标
 */
internal fun getModelLucideIcon(model: String): ImageVector {
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
internal fun getModelDisplayName(model: String): String {
    return when {
        model.length <= 20 -> model
        else -> model.take(18) + "..."
    }
}

/**
 * 深度研究进度指示器
 */
@Composable
private fun DeepResearchIndicator(
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
