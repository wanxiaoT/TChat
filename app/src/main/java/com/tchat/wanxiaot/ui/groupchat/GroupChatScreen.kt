package com.tchat.wanxiaot.ui.groupchat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.*
import com.tchat.data.model.*
import com.tchat.wanxiaot.ui.components.AppEmptyState
import com.tchat.wanxiaot.ui.components.AppHeroCard
import com.tchat.wanxiaot.ui.components.AppIconTile
import com.tchat.wanxiaot.ui.components.AppPageScaffold
import com.tchat.wanxiaot.ui.components.AppPill
import com.tchat.wanxiaot.ui.components.AppSectionCard
import com.tchat.wanxiaot.ui.components.AppSectionSurface

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
    AppPageScaffold(
        title = "助手群聊",
        onBack = onBackClick,
        modifier = modifier,
        actions = {
            IconButton(onClick = onCreateGroup) {
                Icon(Lucide.Plus, contentDescription = "创建群聊")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (groups.isEmpty()) {
                item {
                    AppEmptyState(
                        title = "还没有群聊",
                        description = "创建群聊后，多个助手就可以在同一会话中协同工作。",
                        icon = Lucide.Users,
                        action = {
                            Button(onClick = onCreateGroup) {
                                Icon(Lucide.Plus, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("创建群聊")
                            }
                        }
                    )
                }
            } else {
                items(groups, key = { it.id }) { group ->
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

    AppSectionSurface(
        modifier = modifier
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 群聊图标
            Box(modifier = Modifier.padding(top = 2.dp)) {
                AppIconTile(icon = Lucide.Users)
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
                    AppPill(text = "${group.memberIds.size} 个助手")
                    AppPill(
                        text = when (group.activationStrategy) {
                            GroupActivationStrategy.NATURAL -> "自然"
                            GroupActivationStrategy.LIST -> "轮流"
                            GroupActivationStrategy.MANUAL -> "手动"
                            GroupActivationStrategy.POOLED -> "随机"
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

    AppPageScaffold(
        title = if (editingGroup == null) "创建群聊" else "编辑群聊",
        eyebrow = "Collaboration Setup",
        subtitle = "配置成员、回复策略与自动模式",
        onBack = onBackClick,
        modifier = modifier,
        actions = {
            TextButton(
                onClick = {
                    val memberIds = selectedAssistants.toList()
                    val group = if (editingGroup != null) {
                        editingGroup.copy(
                            name = groupName,
                            description = groupDescription.takeIf { it.isNotBlank() },
                            memberIds = memberIds,
                            activationStrategy = activationStrategy,
                            generationMode = generationMode,
                            autoModeEnabled = autoModeEnabled,
                            autoModeDelay = autoModeDelay.toIntOrNull() ?: 5,
                            updatedAt = System.currentTimeMillis()
                        )
                    } else {
                        GroupChat(
                            name = groupName,
                            description = groupDescription.takeIf { it.isNotBlank() },
                            memberIds = memberIds,
                            activationStrategy = activationStrategy,
                            generationMode = generationMode,
                            autoModeEnabled = autoModeEnabled,
                            autoModeDelay = autoModeDelay.toIntOrNull() ?: 5
                        )
                    }
                    onSave(group, memberIds)
                },
                enabled = groupName.isNotBlank() && selectedAssistants.size >= 2
            ) {
                Text("保存")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                AppHeroCard(
                    title = if (editingGroup == null) "新建协作群聊" else groupName.ifBlank { "编辑协作群聊" },
                    description = "至少选择两个助手，才能建立可协同响应的群聊。",
                    eyebrow = "Group Definition",
                    icon = Lucide.Users
                ) {
                    AppPill(text = "${selectedAssistants.size} 个助手")
                }
            }

            item {
                AppSectionCard(
                    title = "基本信息",
                    description = "设置群聊名称和说明。"
                ) {
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

            item {
                AppSectionCard(
                    title = "选择助手",
                    description = "至少需要 2 个助手参与协作。"
                ) {
                    availableAssistants.forEach { assistant ->
                        AppSectionSurface {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
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

            item {
                AppSectionCard(
                    title = "激活策略",
                    description = "决定哪个助手响应用户消息。"
                ) {
                    listOf(
                        GroupActivationStrategy.MANUAL to Pair("手动选择", "每次由用户选择哪个助手回复"),
                        GroupActivationStrategy.LIST to Pair("轮流发言", "按成员顺序依次回复"),
                        GroupActivationStrategy.POOLED to Pair("随机选择", "随机选择一个助手回复"),
                        GroupActivationStrategy.NATURAL to Pair("自然对话", "基于话语权智能选择")
                    ).forEach { (strategy, info) ->
                        AppSectionSurface {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
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

            item {
                AppSectionCard(
                    title = "高级设置",
                    description = "控制自动连聊和节奏。"
                ) {
                    AppSectionSurface {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
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
}
