package com.tchat.wanxiaot.ui.folder

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import com.tchat.data.model.ChatFolder
import com.tchat.data.model.FolderTreeNode
import com.tchat.wanxiaot.ui.components.AppEmptyState
import com.tchat.wanxiaot.ui.components.AppHeroCard
import com.tchat.wanxiaot.ui.components.AppPageScaffold
import com.tchat.wanxiaot.ui.components.AppPill
import com.tchat.wanxiaot.ui.components.AppSectionCard
import com.tchat.wanxiaot.ui.components.AppSectionSurface

/**
 * 聊天文件夹管理页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatFolderManagementScreen(
    folderTree: List<FolderTreeNode>,
    onBackClick: () -> Unit,
    onCreateFolder: (parentId: String?) -> Unit,
    onEditFolder: (ChatFolder) -> Unit,
    onDeleteFolder: (ChatFolder) -> Unit,
    onMoveFolder: (ChatFolder, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedParentId by remember { mutableStateOf<String?>(null) }

    AppPageScaffold(
        title = "文件夹管理",
        eyebrow = "Organization",
        subtitle = "整理聊天目录与层级结构",
        onBack = onBackClick,
        modifier = modifier,
        actions = {
            IconButton(onClick = {
                selectedParentId = null
                showCreateDialog = true
            }) {
                Icon(Lucide.FolderPlus, contentDescription = "创建文件夹")
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
            item {
                AppHeroCard(
                    title = "聊天目录",
                    description = "创建、嵌套和维护聊天文件夹，保持对话内容有清晰归档。",
                    eyebrow = "Folder Tree",
                    icon = Lucide.FolderTree
                ) {
                    AppPill(text = "${folderTree.size} 个根目录")
                }
            }

            if (folderTree.isEmpty()) {
                item {
                    AppEmptyState(
                        title = "还没有文件夹",
                        description = "创建第一个文件夹后，可以继续建立子文件夹层级。",
                        icon = Lucide.FolderOpen,
                        action = {
                            Button(onClick = {
                                selectedParentId = null
                                showCreateDialog = true
                            }) {
                                Icon(Lucide.Plus, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("创建第一个文件夹")
                            }
                        }
                    )
                }
            } else {
                items(folderTree) { node ->
                    FolderTreeItem(
                        node = node,
                        level = 0,
                        onEdit = onEditFolder,
                        onDelete = onDeleteFolder,
                        onCreateSubfolder = { folder ->
                            selectedParentId = folder.id
                            showCreateDialog = true
                        }
                    )
                }
            }
        }
    }

    // 创建/编辑文件夹对话框
    if (showCreateDialog) {
        CreateFolderDialog(
            parentId = selectedParentId,
            onDismiss = { showCreateDialog = false },
            onConfirm = { parentId ->
                onCreateFolder(parentId)
                showCreateDialog = false
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderTreeItem(
    node: FolderTreeNode,
    level: Int,
    onEdit: (ChatFolder) -> Unit,
    onDelete: (ChatFolder) -> Unit,
    onCreateSubfolder: (ChatFolder) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(level == 0) }
    var showMenu by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        // 文件夹项
        AppSectionSurface(
            modifier = Modifier.padding(start = (level * 24).dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { if (node.children.isNotEmpty()) expanded = !expanded },
                        onLongClick = { showMenu = true }
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 展开/折叠图标
                if (node.children.isNotEmpty()) {
                    IconButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (expanded) Lucide.ChevronDown else Lucide.ChevronRight,
                            contentDescription = if (expanded) "折叠" else "展开",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(24.dp))
                }

                // 文件夹图标
                Icon(
                    imageVector = if (node.folder.icon != null) {
                        // TODO: 解析emoji或自定义图标
                        Lucide.Folder
                    } else {
                        Lucide.Folder
                    },
                    contentDescription = null,
                    tint = node.folder.color?.let {
                        // TODO: 解析颜色
                        MaterialTheme.colorScheme.primary
                    } ?: MaterialTheme.colorScheme.primary
                )

                // 文件夹名称和统计
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = node.folder.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append("${node.chatCount} 个聊天")
                            if (node.children.isNotEmpty()) {
                                append(" · ${node.children.size} 个子文件夹")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 更多操作按钮
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "更多操作"
                    )
                }

                // 菜单
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("编辑") },
                        onClick = {
                            onEdit(node.folder)
                            showMenu = false
                        },
                        leadingIcon = { Icon(Lucide.Pencil, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("创建子文件夹") },
                        onClick = {
                            onCreateSubfolder(node.folder)
                            showMenu = false
                        },
                        leadingIcon = { Icon(Lucide.FolderPlus, contentDescription = null) }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("删除") },
                        onClick = {
                            onDelete(node.folder)
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

        // 子文件夹（递归渲染）
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                node.children.forEach { childNode ->
                    FolderTreeItem(
                        node = childNode,
                        level = level + 1,
                        onEdit = onEdit,
                        onDelete = onDelete,
                        onCreateSubfolder = onCreateSubfolder
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateFolderDialog(
    parentId: String?,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit
) {
    var folderName by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf("📁") }
    var selectedColor by remember { mutableStateOf("#2196F3") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Lucide.FolderPlus, contentDescription = null) },
        title = { Text(if (parentId == null) "创建文件夹" else "创建子文件夹") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("文件夹名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 图标选择器（简化版）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("图标：", style = MaterialTheme.typography.bodyMedium)
                    listOf("📁", "📂", "🗂️", "📋", "📊", "💼", "🎯", "⭐").forEach { emoji ->
                        FilterChip(
                            selected = selectedIcon == emoji,
                            onClick = { selectedIcon = emoji },
                            label = { Text(emoji) }
                        )
                    }
                }

                // 颜色选择器（简化版）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("颜色：", style = MaterialTheme.typography.bodyMedium)
                    // TODO: 添加颜色选择器
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(parentId) },
                enabled = folderName.isNotBlank()
            ) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 智能分组设置卡片
 */
@Composable
fun SmartGroupingCard(
    enabled: Boolean,
    groupType: String,
    onToggle: (Boolean) -> Unit,
    onGroupTypeChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    AppSectionCard(
        modifier = modifier,
        title = "智能分组",
        description = "自动按时间、模型或助手将聊天归档到文件夹。"
    ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "智能分组",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "自动将聊天分组到文件夹",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle
                )
            }

            if (enabled) {
                HorizontalDivider()

                Text(
                    text = "分组方式：",
                    style = MaterialTheme.typography.bodyMedium
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "BY_TIME" to "按时间分组",
                        "BY_MODEL" to "按模型分组",
                        "BY_ASSISTANT" to "按助手分组"
                    ).forEach { (type, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = groupType == type,
                                onClick = { onGroupTypeChange(type) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            }
        }
}
