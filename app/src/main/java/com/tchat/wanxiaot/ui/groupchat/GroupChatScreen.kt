package com.tchat.wanxiaot.ui.groupchat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.*
import com.tchat.data.model.*

/**
 * 群聊列表页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatListScreen(
    groups: List<GroupChat>,
    onBackClick: () -> Unit,
    onGroupClick: (GroupChat) -> Unit,
    onCreateGroup: () -> Unit,
    onEditGroup: (GroupChat) -> Unit,
    onDeleteGroup: (GroupChat) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("助手群聊") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onCreateGroup) {
                        Icon(Lucide.Plus, contentDescription = "创建群聊")
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        if (groups.isEmpty()) {
            // 空状态
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Lucide.Users,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "还没有群聊",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "创建群聊让多个助手协作对话",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onCreateGroup) {
                        Icon(Lucide.Plus, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("创建群聊")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(groups) { group ->
                    GroupChatCard(
                        group = group,
                        onClick = { onGroupClick(group) },
                        onEdit = { onEditGroup(group) },
                        onDelete = { onDeleteGroup(group) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupChatCard(
    group: GroupChat,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    ElevatedCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 群聊图标
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Lucide.Users,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // 群聊信息
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                group.description?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 成员数量
                    AssistChip(
                        onClick = {},
                        label = { Text("${group.memberIds.size} 个助手") },
                        leadingIcon = {
                            Icon(
                                Lucide.User,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )

                    // 激活策略
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                when (group.activationStrategy) {
                                    GroupActivationStrategy.NATURAL -> "自然"
                                    GroupActivationStrategy.LIST -> "轮流"
                                    GroupActivationStrategy.MANUAL -> "手动"
                                    GroupActivationStrategy.POOLED -> "随机"
                                }
                            )
                        }
                    )
                }
            }

            // 更多操作按钮
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "更多操作")
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("编辑") },
                        onClick = {
                            onEdit()
                            showMenu = false
                        },
                        leadingIcon = { Icon(Lucide.Pencil, contentDescription = null) }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("删除") },
                        onClick = {
                            onDelete()
                            showMenu = false
                        },
                        leadingIcon = { Icon(Lucide.Trash2, contentDescription = null) },
                        colors = MenuDefaults.itemColors(
                            textColor = MaterialTheme.colorScheme.error,
                            leadingIconColor = MaterialTheme.colorScheme.error
                        )
                    )
                }
            }
        }
    }
}

/**
 * 创建/编辑群聊页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupChatScreen(
    availableAssistants: List<Assistant>,
    onBackClick: () -> Unit,
    onSave: (GroupChat, List<String>) -> Unit,
    editingGroup: GroupChat? = null,
    modifier: Modifier = Modifier
) {
    var groupName by remember { mutableStateOf(editingGroup?.name ?: "") }
    var groupDescription by remember { mutableStateOf(editingGroup?.description ?: "") }
    var selectedAssistants by remember { mutableStateOf(editingGroup?.memberIds?.toSet() ?: emptySet()) }
    var activationStrategy by remember { mutableStateOf(editingGroup?.activationStrategy ?: GroupActivationStrategy.MANUAL) }
    var generationMode by remember { mutableStateOf(editingGroup?.generationMode ?: GroupGenerationMode.APPEND) }
    var autoModeEnabled by remember { mutableStateOf(editingGroup?.autoModeEnabled ?: false) }
    var autoModeDelay by remember { mutableStateOf(editingGroup?.autoModeDelay?.toString() ?: "5") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editingGroup == null) "创建群聊" else "编辑群聊") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            val group = GroupChat(
                                id = editingGroup?.id ?: "",
                                name = groupName,
                                description = groupDescription.takeIf { it.isNotBlank() },
                                memberIds = selectedAssistants.toList(),
                                activationStrategy = activationStrategy,
                                generationMode = generationMode,
                                autoModeEnabled = autoModeEnabled,
                                autoModeDelay = autoModeDelay.toIntOrNull() ?: 5
                            )
                            onSave(group, selectedAssistants.toList())
                        },
                        enabled = groupName.isNotBlank() && selectedAssistants.size >= 2
                    ) {
                        Text("保存")
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 基本信息
            item {
                ElevatedCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "基本信息",
                            style = MaterialTheme.typography.titleMedium
                        )

                        OutlinedTextField(
                            value = groupName,
                            onValueChange = { groupName = it },
                            label = { Text("群聊名称") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = groupDescription,
                            onValueChange = { groupDescription = it },
                            label = { Text("群聊描述（可选）") },
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // 选择助手
            item {
                ElevatedCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "选择助手（至少2个）",
                            style = MaterialTheme.typography.titleMedium
                        )

                        availableAssistants.forEach { assistant ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = assistant.id in selectedAssistants,
                                    onCheckedChange = { checked ->
                                        selectedAssistants = if (checked) {
                                            selectedAssistants + assistant.id
                                        } else {
                                            selectedAssistants - assistant.id
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = assistant.name,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }

            // 激活策略
            item {
                ElevatedCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "激活策略",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Text(
                            text = "决定哪个助手响应用户消息",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        listOf(
                            GroupActivationStrategy.MANUAL to Pair("手动选择", "每次由用户选择哪个助手回复"),
                            GroupActivationStrategy.LIST to Pair("轮流发言", "按成员顺序依次回复"),
                            GroupActivationStrategy.POOLED to Pair("随机选择", "随机选择一个助手回复"),
                            GroupActivationStrategy.NATURAL to Pair("自然对话", "基于话语权智能选择")
                        ).forEach { (strategy, info) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = activationStrategy == strategy,
                                    onClick = { activationStrategy = strategy }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(info.first)
                                    Text(
                                        text = info.second,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 高级设置
            item {
                ElevatedCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "高级设置",
                            style = MaterialTheme.typography.titleMedium
                        )

                        // 自动模式
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("自动模式")
                                Text(
                                    text = "助手自动连续对话",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = autoModeEnabled,
                                onCheckedChange = { autoModeEnabled = it }
                            )
                        }

                        if (autoModeEnabled) {
                            OutlinedTextField(
                                value = autoModeDelay,
                                onValueChange = { autoModeDelay = it },
                                label = { Text("自动模式延迟（秒）") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}
