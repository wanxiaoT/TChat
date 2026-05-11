package com.tchat.feature.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.Bot
import com.composables.icons.lucide.BrainCircuit
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Swords
import com.composables.icons.lucide.Wrench
import com.tchat.data.model.LocalToolOption
import com.tchat.data.model.ChatToolbarItem
import com.tchat.data.model.ChatToolbarSettings
import com.tchat.data.model.Message
import com.tchat.data.model.MessagePart
import com.tchat.data.model.QuickMessage
import com.tchat.data.tool.Tool
import com.tchat.data.tts.TtsService
import com.tchat.data.util.RegexRuleData
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged

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
    // 启用的技能
    enabledSkillIds: List<String> = emptyList(),
    // 发送给模型的最近上下文消息数量，<=0 表示全部上下文
    contextMessageSize: Int = 64,
    // 提供商ID（用于按提供商统计token）
    providerId: String? = null,
    // 是否记录token统计
    shouldRecordTokens: Boolean = true,
    // 深度研究支持
    onDeepResearch: ((String?) -> Unit)? = null,
    isDeepResearching: Boolean = false,
    onOpenChat: (String) -> Unit = {},
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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val actualChatId by viewModel.actualChatId.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    var inputText by remember { mutableStateOf("") }
    var draftMediaParts by remember { mutableStateOf<List<MessagePart>>(emptyList()) }
    var replyTarget by remember { mutableStateOf<Message?>(null) }
    val quickMessages = remember {
        listOf(
            QuickMessage("总结", "请总结上面的内容，并列出关键结论。"),
            QuickMessage("翻译", "请将以下内容翻译成中文，并保留原有格式："),
            QuickMessage("行动项", "请提取行动项，按负责人、截止时间、下一步整理。"),
            QuickMessage("表格", "请用 Markdown 表格整理以上信息。")
        )
    }

    // Context for clipboard, share, TTS
    val context = LocalContext.current

    // Snackbar 状态
    val snackbarHostState = remember { SnackbarHostState() }
    val messageListState = rememberLazyListState()

    // Coroutine scope
    val scope = rememberCoroutineScope()
    var initialScrollChatKey by remember { mutableStateOf<String?>(null) }
    val ttsService = remember(context) { TtsService.getInstance(context) }

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
        if (ttsService.isAvailable()) {
            ttsService.speak(content)
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

    val pickMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val added = mutableListOf<MessagePart>()
            uris.forEach { uri ->
                runCatching {
                    ChatMediaUtils.importMediaPart(context, uri)
                }.onSuccess { part ->
                    added.add(part)
                }.onFailure { e ->
                    snackbarHostState.showSnackbar(
                        message = e.message ?: "导入文件失败",
                        duration = SnackbarDuration.Short
                    )
                }
            }
            if (added.isNotEmpty()) {
                draftMediaParts = (draftMediaParts + added).distinctBy { part ->
                    when (part) {
                        is MessagePart.Image -> "img:${part.filePath}"
                        is MessagePart.Video -> "vid:${part.filePath}"
                        else -> part.toString()
                    }
                }
            }
        }
    }

    // 计算当前启用的工具列表 - 合并本地工具和额外工具（如知识库工具）
    val currentTools = remember(enabledTools, getToolsForOptions, extraTools) {
        getToolsForOptions(enabledTools.toList()) + extraTools
    }

    // 当工具状态变化时更新ViewModel的配置
    LaunchedEffect(
        enabledTools,
        currentTools,
        systemPrompt,
        currentModel,
        regexRules,
        enabledSkillIds,
        contextMessageSize,
        providerId,
        shouldRecordTokens
    ) {
        // 只要有工具或系统提示，就设置配置
        val hasTools = currentTools.isNotEmpty()
        val hasSystemPrompt = !systemPrompt.isNullOrEmpty()

        if (hasTools || hasSystemPrompt) {
            viewModel.setTools(
                tools = currentTools,
                systemPrompt = systemPrompt,
                modelName = currentModel,
                providerId = providerId,
                shouldRecordTokens = shouldRecordTokens,
                regexRules = regexRules,
                enabledSkillIds = enabledSkillIds,
                contextMessageSize = contextMessageSize
            )
        } else {
            // 即使没有工具和系统提示，也设置一个空配置，确保消息可以正常发送
            viewModel.setTools(
                tools = emptyList(),
                systemPrompt = null,
                modelName = currentModel,
                providerId = providerId,
                shouldRecordTokens = shouldRecordTokens,
                regexRules = regexRules,
                enabledSkillIds = enabledSkillIds,
                contextMessageSize = contextMessageSize
            )
        }
    }

    // 监听 chatId 和 viewModel 的变化，确保切换聊天或重建 ViewModel 时都能重新加载
    LaunchedEffect(chatId, viewModel) {
        inputText = "" // 切换聊天时清空输入框，防止残留文本发送到错误聊天
        draftMediaParts = emptyList()
        replyTarget = null
        initialScrollChatKey = null
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

    // Chat 页面手动消费 IME inset，避免不同 OEM 对 resize 的行为不一致
    SoftInputModeEffect(SoftInputAdjustNothing)
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0

    Box(modifier = modifier.fillMaxSize()) {
        ChatBackdrop(modifier = Modifier.matchParentSize())
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.ime.only(WindowInsetsSides.Bottom))
        ) {

            var inputAreaHeightPx by remember { mutableIntStateOf(0) }
            val inputAreaHeight = with(LocalDensity.current) { inputAreaHeightPx.toDp() }

        when (val state = uiState) {
            is ChatUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is ChatUiState.Success -> {
                LaunchedEffect(actualChatId, chatId, state.messages.size, state.streamingMessage?.id) {
                    val chatKey = actualChatId ?: chatId ?: return@LaunchedEffect
                    if (initialScrollChatKey == chatKey) return@LaunchedEffect

                    val targetIndex = state.messages.lastIndex + if (
                        state.streamingMessage != null &&
                        state.messages.none { it.id == state.streamingMessage.id }
                    ) {
                        1
                    } else {
                        0
                    }

                    if (targetIndex >= 0) {
                        messageListState.scrollToItem(targetIndex)
                        initialScrollChatKey = chatKey
                    }
                }

                LaunchedEffect(actualChatId, chatId, state.hasMoreHistory, state.isLoadingHistory) {
                    if (!state.hasMoreHistory) return@LaunchedEffect

                    snapshotFlow { messageListState.firstVisibleItemIndex <= 2 }
                        .distinctUntilChanged()
                        .collect { isNearTop ->
                            if (isNearTop && !state.isLoadingHistory) {
                                viewModel.loadMoreHistory()
                            }
                        }
                }

                MessageList(
                    messages = state.messages,
                    streamingMessage = state.streamingMessage,
                    listState = messageListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        top = 16.dp,
                        end = 12.dp,
                        bottom = 18.dp + inputAreaHeight
                    ),
                    providerIcon = providerIcon,
                    modelName = currentModel,
                    isLoadingHistory = state.isLoadingHistory,
                    onRegenerate = { userMessageId, aiMessageId ->
                        onRegenerateMessage(userMessageId, aiMessageId)
                    },
                    onSelectVariant = { messageId, variantIndex ->
                        viewModel.selectVariant(messageId, variantIndex)
                    },
                    onCopy = onCopy,
                    onSpeak = onSpeak,
                    onShare = onShare,
                    onDelete = onDelete,
                    onToggleBookmark = { message ->
                        viewModel.toggleBookmark(message)
                    },
                    onReply = { message ->
                        replyTarget = message
                    },
                    onCreateBranch = { message ->
                        viewModel.createBranchFromMessage(message.id) { newChatId ->
                            onOpenChat(newChatId)
                        }
                    },
                    onQuoteClick = { quotedMessageId ->
                        val index = state.messages.indexOfFirst { it.id == quotedMessageId }
                        if (index >= 0) {
                            scope.launch { messageListState.animateScrollToItem(index) }
                        }
                    }
                )

                // 输入区域（工具栏 + 输入框），固定在底部并跟随 IME 上移
                ChatComposerDock(
                    imeVisible = imeVisible,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .onSizeChanged { inputAreaHeightPx = it.height }
                ) {
                    // 深度研究进度提示
                    AnimatedVisibility(visible = isDeepResearching) {
                        DeepResearchIndicator(
                            text = deepResearchInProgressText
                        )
                    }

                    // 工具栏
                    if (availableModels.isNotEmpty() && !imeVisible) {
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
                    if (inputText.isBlank() && draftMediaParts.isEmpty()) {
                        QuickMessageRow(
                            quickMessages = quickMessages,
                            onInsert = { quickMessage ->
                                inputText = quickMessage.content
                            }
                        )
                    }

                    MessageInput(
                        text = inputText,
                        onTextChange = { inputText = it },
                        onSend = {
                            if (inputText.isNotBlank() || draftMediaParts.isNotEmpty()) {
                                val reply = replyTarget
                                viewModel.sendMessage(
                                    chatId = actualChatId ?: chatId,
                                    content = inputText,
                                    mediaParts = draftMediaParts,
                                    replyToMessageId = reply?.id,
                                    replyPreview = reply?.toReplyPreview()
                                )
                                inputText = ""
                                draftMediaParts = emptyList()
                                replyTarget = null
                            }
                        },
                        mediaParts = draftMediaParts,
                        onPickMedia = {
                            pickMediaLauncher.launch(arrayOf("image/*", "video/*"))
                        },
                        onRemoveMedia = { part ->
                            draftMediaParts = draftMediaParts.filterNot { it == part }
                        },
                        onGenerateImage = {
                            if (inputText.isNotBlank()) {
                                viewModel.generateImage(actualChatId ?: chatId, inputText)
                                inputText = ""
                                draftMediaParts = emptyList()
                            }
                        },
                        inputHint = inputHint,
                        sendContentDescription = sendContentDescription,
                        replyPreview = replyTarget?.toReplyPreview(),
                        onClearReply = { replyTarget = null }
                    )
                }
            }
            is ChatUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "Error: ${state.message}")
                }
            }
        }

            // Snackbar 显示在底部
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun QuickMessageRow(
    quickMessages: List<QuickMessage>,
    onInsert: (QuickMessage) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        quickMessages.forEach { quickMessage ->
            FilterChip(
                selected = false,
                onClick = { onInsert(quickMessage) },
                label = { Text(quickMessage.title, maxLines = 1) },
                leadingIcon = {
                    Icon(
                        imageVector = Lucide.Sparkles,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                }
            )
        }
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        normalizedToolbarSettings.items.forEach { config ->
            if (!config.visible) return@forEach

            when (config.item) {
                ChatToolbarItem.MODEL -> {
                    if (availableModels.isNotEmpty()) {
                        Box {
                            ToolbarChip(
                                onClick = { modelMenuExpanded = true },
                                label = getModelDisplayName(currentModel.ifBlank { "选择模型" }),
                                accentColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.widthIn(max = 172.dp)
                            ) {
                                Icon(
                                    imageVector = getModelLucideIcon(currentModel),
                                    contentDescription = currentModel.takeIf { it.isNotBlank() },
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
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
                        ToolbarChip(
                            onClick = { onDeepResearch() },
                            label = if (isDeepResearching) deepResearchRunningText else deepResearchText,
                            accentColor = MaterialTheme.colorScheme.tertiary,
                            active = isDeepResearching
                        ) {
                            if (isDeepResearching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Psychology,
                                    contentDescription = deepResearchText,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }
                }

                ChatToolbarItem.TOOLS -> {
                    ToolbarChip(
                        onClick = onToolsClick,
                        label = if (enabledToolsCount > 0) {
                            "$toolsText · $enabledToolsCount"
                        } else {
                            toolsText
                        },
                        accentColor = MaterialTheme.colorScheme.primary,
                        active = enabledToolsCount > 0
                    ) {
                        Icon(
                            imageVector = Lucide.Wrench,
                            contentDescription = if (enabledToolsCount > 0) {
                                toolsWithCountFormat.format(enabledToolsCount)
                            } else {
                                toolsText
                            },
                            modifier = Modifier.size(16.dp),
                            tint = if (enabledToolsCount > 0) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                ChatToolbarItem.JUNGLE_HELPER -> {
                    if (onJungleHelperClick != null) {
                        ToolbarChip(
                            onClick = onJungleHelperClick,
                            label = "打野助手",
                            accentColor = MaterialTheme.colorScheme.secondary
                        ) {
                            Icon(
                                imageVector = Lucide.Swords,
                                contentDescription = "打野助手",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolbarChip(
    onClick: () -> Unit,
    label: String,
    accentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    leadingContent: @Composable RowScope.() -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (active) {
            colorScheme.primaryContainer.copy(alpha = 0.34f)
        } else {
            colorScheme.surface.copy(alpha = 0.68f)
        },
        border = BorderStroke(
            1.dp,
            if (active) accentColor.copy(alpha = 0.24f) else colorScheme.outlineVariant.copy(alpha = 0.48f)
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            leadingContent()
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
        model.length <= 16 -> model
        else -> model.take(14) + "..."
    }
}

private fun Message.toReplyPreview(): String {
    val text = getCurrentContent().ifBlank {
        parts.firstNotNullOfOrNull { part ->
            when (part) {
                is MessagePart.Image -> part.fileName ?: "图片"
                is MessagePart.Video -> part.fileName ?: "视频"
                is MessagePart.ToolCall -> "工具调用：${part.toolName}"
                is MessagePart.ToolResult -> "工具结果：${part.toolName}"
                is MessagePart.Text -> part.content
            }
        }.orEmpty()
    }.replace(Regex("\\s+"), " ").trim()

    return if (text.length <= 120) text else text.take(117).trimEnd() + "..."
}

/**
 * 深度研究进度指示器
 */
@Composable
private fun DeepResearchIndicator(
    text: String = "深度研究进行中..."
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.64f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
        tonalElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
