package com.tchat.wanxiaot.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.tchat.designsystem.ConversationListItem
import com.tchat.designsystem.Spacing
import com.tchat.designsystem.TChatModalBottomSheet
import com.tchat.data.model.Chat
import com.tchat.data.model.ChatSearchResult
import com.tchat.data.model.GroupChat
import com.tchat.data.model.MessageRole
import com.tchat.wanxiaot.settings.ProviderConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DrawerContent(
    chats: List<Chat>,
    currentChatId: String?,
    currentProviderName: String,
    currentProviderId: String,
    providers: List<ProviderConfig>,
    onChatSelected: (String) -> Unit,
    onNewChat: () -> Unit,
    onDeleteChat: (String) -> Unit,
    onToggleChatPinned: (Chat) -> Unit,
    onSearchMessages: suspend (String) -> List<ChatSearchResult> = { emptyList() },
    onSearchResultSelected: (ChatSearchResult) -> Unit = {},
    bookmarkedMessages: List<ChatSearchResult> = emptyList(),
    onSettingsClick: () -> Unit,
    onProviderSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    groupChats: List<GroupChat> = emptyList(),
    currentGroupChatId: String? = null,
    onGroupChatSelected: (String) -> Unit = {}
) {
    var showProviderDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var messageSearchResults by remember { mutableStateOf<List<ChatSearchResult>>(emptyList()) }
    var isSearchingMessages by remember { mutableStateOf(false) }
    var messageSearchError by remember { mutableStateOf<String?>(null) }
    val currentOnSearchMessages by rememberUpdatedState(onSearchMessages)

    // 根据搜索词过滤聊天记录
    val filteredChats = remember(chats, searchQuery) {
        if (searchQuery.isBlank()) {
            chats
        } else {
            chats.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }
    }

    val filteredGroupChats = remember(groupChats, searchQuery) {
        if (searchQuery.isBlank()) {
            groupChats
        } else {
            groupChats.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    LaunchedEffect(searchQuery) {
        val trimmedQuery = searchQuery.trim()
        if (trimmedQuery.isBlank()) {
            messageSearchResults = emptyList()
            messageSearchError = null
            isSearchingMessages = false
            return@LaunchedEffect
        }

        delay(250)
        isSearchingMessages = true
        messageSearchError = null
        runCatching {
            currentOnSearchMessages(trimmedQuery)
        }.onSuccess { results ->
            messageSearchResults = results
        }.onFailure { error ->
            if (error is CancellationException) throw error
            messageSearchResults = emptyList()
            messageSearchError = error.message ?: "搜索失败"
        }
        isSearchingMessages = false
    }

    // 服务商选择抽屉
    if (showProviderDialog) {
        ProviderSelectionSheet(
            providers = providers,
            currentProviderId = currentProviderId,
            onProviderSelected = { providerId ->
                onProviderSelected(providerId)
                showProviderDialog = false
            },
            onDismiss = { showProviderDialog = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.lg, end = Spacing.sm, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )

                // 搜索输入框
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        Box {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "搜索聊天记录",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                // 清除按钮或新建按钮
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { searchQuery = "" },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "清除搜索",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    FilledTonalIconButton(
                        onClick = onNewChat,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "新建聊天",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            val hasSearchQuery = searchQuery.isNotBlank()

            if (!hasSearchQuery && bookmarkedMessages.isNotEmpty()) {
                item {
                    DrawerSectionLabel(text = "收藏")
                }

                items(bookmarkedMessages, key = { "bookmark_${it.messageId}" }) { result ->
                    MessageSearchResultItem(
                        result = result,
                        onClick = { onSearchResultSelected(result) }
                    )
                }

                item {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f),
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }

            if (filteredGroupChats.isNotEmpty()) {
                item {
                    DrawerSectionLabel(text = "群聊")
                }

                items(filteredGroupChats, key = { it.id }) { groupChat ->
                    GroupChatHistoryItem(
                        groupChat = groupChat,
                        isSelected = groupChat.id == currentGroupChatId,
                        onClick = { onGroupChatSelected(groupChat.id) }
                    )
                }

                item {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f),
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }

            if (filteredChats.isNotEmpty()) {
                item {
                    DrawerSectionLabel(text = "单聊")
                }
            }

            items(filteredChats, key = { it.id }) { chat ->
                        ChatHistoryItem(
                            chat = chat,
                            isSelected = chat.id == currentChatId && currentGroupChatId == null,
                            onClick = { onChatSelected(chat.id) },
                            onDelete = { onDeleteChat(chat.id) },
                            onTogglePinned = { onToggleChatPinned(chat) }
                )
            }

            if (hasSearchQuery) {
                if (messageSearchResults.isNotEmpty()) {
                    item {
                        DrawerSectionLabel(text = "消息内容")
                    }

                    items(messageSearchResults, key = { "message_${it.messageId}" }) { result ->
                        MessageSearchResultItem(
                            result = result,
                            onClick = { onSearchResultSelected(result) }
                        )
                    }
                }

                if (isSearchingMessages) {
                    item {
                        MessageSearchStatus(text = "搜索消息中...")
                    }
                } else if (messageSearchError != null) {
                    item {
                        MessageSearchStatus(text = messageSearchError ?: "搜索失败")
                    }
                } else if (
                    filteredGroupChats.isEmpty() &&
                    filteredChats.isEmpty() &&
                    messageSearchResults.isEmpty()
                ) {
                    item {
                        MessageSearchStatus(text = "没有找到相关聊天或消息")
                    }
                }
            }
        }

        Surface(
            onClick = { showProviderDialog = true },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "当前服务商",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = currentProviderName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    Icons.Outlined.SwapHoriz,
                    contentDescription = "切换服务商",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Surface(
            onClick = onSettingsClick,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)),
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.42f),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "设置",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "管理模型、显示、日志与扩展功能",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
    )
}

/**
 * 服务商选择抽屉 - Material You 风格的底部抽屉
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderSelectionSheet(
    providers: List<ProviderConfig>,
    currentProviderId: String,
    onProviderSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    TChatModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.xxl)
        ) {
            // 标题
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xl, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "选择服务商",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    Icons.Outlined.SwapHoriz,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            if (providers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.xl),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无服务商，请先添加",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    items(providers, key = { it.id }) { provider ->
                        ProviderSelectionItem(
                            provider = provider,
                            isSelected = provider.id == currentProviderId,
                            onClick = { onProviderSelected(provider.id) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 服务商选择项
 */
@Composable
fun ProviderSelectionItem(
    provider: ProviderConfig,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.24f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            1.dp,
            if (isSelected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)
            }
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = provider.name.ifEmpty { provider.providerType.displayName },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = provider.selectedModel.ifEmpty { "未选择模型" },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isSelected) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = "已选中",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}



@Composable
fun GroupChatHistoryItem(
    groupChat: GroupChat,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }

    DrawerConversationCard(
        title = groupChat.name.ifEmpty { "未命名群聊" },
        subtitle = "${groupChat.memberIds.size} 位成员",
        timestamp = dateFormat.format(Date(groupChat.updatedAt)),
        isSelected = isSelected,
        onClick = onClick,
        avatarName = groupChat.name
    )
}

@Composable
fun ChatHistoryItem(
    chat: Chat,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onTogglePinned: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }

    DrawerConversationCard(
        title = chat.title,
        subtitle = if (chat.isPinned) "置顶会话" else "AI 聊天",
        timestamp = dateFormat.format(Date(chat.updatedAt)),
        isSelected = isSelected,
        onClick = onClick,
        avatarName = chat.title,
        onDelete = onDelete,
        onTogglePinned = onTogglePinned,
        trailingContent = if (chat.isPinned) {
            {
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = "已置顶",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            null
        }
    )
}

@Composable
private fun MessageSearchResultItem(
    result: ChatSearchResult,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }
    val roleLabel = when (result.role) {
        MessageRole.USER -> "用户"
        MessageRole.ASSISTANT -> result.groupAssistantName ?: "助手"
        MessageRole.SYSTEM -> "系统"
        MessageRole.TOOL -> "工具"
    }
    val modelLabel = result.modelName?.takeIf { it.isNotBlank() }
    val subtitle = buildString {
        append(roleLabel)
        if (modelLabel != null) {
            append(" · ")
            append(modelLabel)
        }
        append(" · ")
        append(result.snippet)
    }

    DrawerConversationCard(
        title = result.chatTitle.ifEmpty { "未命名聊天" },
        subtitle = subtitle,
        timestamp = dateFormat.format(Date(result.timestamp)),
        isSelected = false,
        onClick = onClick,
        avatarName = result.chatTitle
    )
}

@Composable
private fun MessageSearchStatus(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerConversationCard(
    title: String,
    subtitle: String,
    timestamp: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    avatarName: String,
    onDelete: (() -> Unit)? = null,
    onTogglePinned: (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null
) {
    val density = LocalDensity.current
    var showActionMenu by remember { mutableStateOf(false) }
    val menuOffset = with(density) {
        IntOffset(
            x = -Spacing.lg.roundToPx(),
            y = (-56).dp.roundToPx()
        )
    }
    val longClickAction: (() -> Unit)? = if (onDelete != null) {
        {
            showActionMenu = true
        }
    } else {
        null
    }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> false
                SwipeToDismissBoxValue.StartToEnd -> {
                    if (onTogglePinned != null) {
                        onTogglePinned()
                    }
                    false
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = onTogglePinned != null,
        enableDismissFromEndToStart = false,
        backgroundContent = {
            val value = dismissState.dismissDirection
            val backgroundColor = when (value) {
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.surfaceContainer
                SwipeToDismissBoxValue.Settled -> MaterialTheme.colorScheme.surfaceContainer
            }
            val icon = when (value) {
                SwipeToDismissBoxValue.StartToEnd -> Icons.Default.PushPin
                SwipeToDismissBoxValue.EndToStart -> Icons.Default.PushPin
                SwipeToDismissBoxValue.Settled -> Icons.Default.PushPin
            }
            val alignment = when (value) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterStart
                SwipeToDismissBoxValue.Settled -> Alignment.CenterStart
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.medium)
                    .background(backgroundColor)
                    .padding(horizontal = Spacing.lg),
                contentAlignment = alignment
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    ) {
        Box {
            ConversationListItem(
                name = title,
                timestamp = timestamp,
                preview = subtitle,
                modifier = Modifier.combinedClickable(
                    onClick = onClick,
                    onLongClick = longClickAction,
                    onLongClickLabel = if (onDelete != null) "显示操作菜单" else null
                ),
                isSelected = isSelected,
                avatarUrl = null,
                onClick = null,
                trailing = {
                    trailingContent?.invoke()
                }
            )

            if (showActionMenu && onDelete != null) {
                Popup(
                    alignment = Alignment.TopEnd,
                    offset = menuOffset,
                    onDismissRequest = { showActionMenu = false },
                    properties = PopupProperties(focusable = true)
                ) {
                    Surface(
                        modifier = Modifier.widthIn(min = 144.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "删除",
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                showActionMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}
