package com.tchat.wanxiaot.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tchat.wanxiaot.settings.PresetRegexRules
import com.tchat.wanxiaot.settings.RegexRule
import com.tchat.wanxiaot.settings.SettingsManager

/**
 * 正则表达式规则管理页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegexRulesScreen(
    settingsManager: SettingsManager,
    onBack: () -> Unit,
    showTopBar: Boolean = true
) {
    val settings by settingsManager.settings.collectAsState()
    val userRules = settings.regexRules

    var showEditDialog by remember { mutableStateOf<RegexRule?>(null) }
    var showDeleteDialog by remember { mutableStateOf<RegexRule?>(null) }
    var showAddPresetDialog by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    // 编辑/新建规则对话框
    showEditDialog?.let { rule ->
        RegexRuleEditDialog(
            rule = rule,
            isNew = rule.id.isEmpty(),
            onDismiss = { showEditDialog = null },
            onSave = { newRule ->
                if (rule.id.isEmpty()) {
                    settingsManager.addRegexRule(newRule)
                } else {
                    settingsManager.updateRegexRule(newRule)
                }
                showEditDialog = null
            }
        )
    }

    // 删除确认对话框
    showDeleteDialog?.let { rule ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            icon = {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("删除规则") },
            text = { Text("确定要删除 \"${rule.name}\" 吗？此操作不可撤销。") },
            confirmButton = {
                Button(
                    onClick = {
                        settingsManager.deleteRegexRule(rule.id)
                        showDeleteDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteDialog = null }) {
                    Text("取消")
                }
            }
        )
    }

    // 添加预设规则对话框
    if (showAddPresetDialog) {
        AddPresetRulesDialog(
            existingRuleIds = userRules.map { it.id }.toSet(),
            onDismiss = { showAddPresetDialog = false },
            onAdd = { presetRules ->
                presetRules.forEach { preset ->
                    settingsManager.addRegexRule(preset.copy(id = preset.id))
                }
                showAddPresetDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = { Text("正则表达式规则") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showAddPresetDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "添加预设")
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showEditDialog = RegexRule(id = "") },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("新建规则") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // 说明卡片
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "关于正则规则",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "正则规则用于在流式输出时实时清理 AI 回复内容。规则按顺序执行，可在助手设置中选择启用哪些规则。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 我的规则
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "我的规则",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${userRules.size} 个",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (userRules.isEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "暂无规则",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "点击右上角添加预设规则，或点击下方按钮新建自定义规则",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(userRules, key = { it.id }) { rule ->
                    RegexRuleCard(
                        rule = rule,
                        onToggle = { enabled ->
                            settingsManager.updateRegexRule(rule.copy(isEnabled = enabled))
                        },
                        onEdit = { showEditDialog = rule },
                        onDelete = { showDeleteDialog = rule }
                    )
                }
            }

            // 底部留空给 FAB
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

/**
 * 规则卡片
 */
@Composable
private fun RegexRuleCard(
    rule: RegexRule,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rule.name.ifEmpty { "未命名规则" },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (rule.description.isNotBlank()) {
                        Text(
                            text = rule.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Switch(
                    checked = rule.isEnabled,
                    onCheckedChange = onToggle
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = MaterialTheme.shapes.small
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "模式:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = rule.pattern,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "替换:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = rule.replacement.ifEmpty { "(空)" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onEdit) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("编辑")
                }
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("删除")
                }
            }
        }
    }
}

/**
 * 规则编辑对话框
 */
@Composable
private fun RegexRuleEditDialog(
    rule: RegexRule,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (RegexRule) -> Unit
) {
    var name by remember { mutableStateOf(rule.name) }
    var pattern by remember { mutableStateOf(rule.pattern) }
    var replacement by remember { mutableStateOf(rule.replacement) }
    var description by remember { mutableStateOf(rule.description) }
    var testInput by remember { mutableStateOf("") }
    var testResult by remember { mutableStateOf<String?>(null) }
    var patternError by remember { mutableStateOf<String?>(null) }

    // 验证正则表达式
    LaunchedEffect(pattern) {
        patternError = try {
            if (pattern.isNotBlank()) {
                Regex(pattern)
            }
            null
        } catch (e: Exception) {
            e.message
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "新建规则" else "编辑规则") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 450.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("规则名称") },
                    placeholder = { Text("例如：清除行首空格") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("正则表达式") },
                    placeholder = { Text("例如：^ +") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = patternError != null,
                    supportingText = patternError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    singleLine = true
                )

                OutlinedTextField(
                    value = replacement,
                    onValueChange = { replacement = it },
                    label = { Text("替换为") },
                    placeholder = { Text("留空表示删除匹配内容") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("描述（可选）") },
                    placeholder = { Text("规则的用途说明") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                HorizontalDivider()

                // 测试区域
                Text(
                    text = "测试规则",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = testInput,
                    onValueChange = { testInput = it },
                    label = { Text("测试输入") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )

                FilledTonalButton(
                    onClick = {
                        testResult = try {
                            if (pattern.isBlank()) {
                                "请输入正则表达式"
                            } else {
                                val regex = Regex(pattern, RegexOption.MULTILINE)
                                testInput.replace(regex, replacement)
                            }
                        } catch (e: Exception) {
                            "错误: ${e.message}"
                        }
                    },
                    enabled = pattern.isNotBlank() && testInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("测试")
                }

                testResult?.let { result ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "测试结果:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = result.ifEmpty { "(空)" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newRule = rule.copy(
                        id = if (isNew) java.util.UUID.randomUUID().toString() else rule.id,
                        name = name.trim(),
                        pattern = pattern,
                        replacement = replacement,
                        description = description.trim()
                    )
                    onSave(newRule)
                },
                enabled = pattern.isNotBlank() && patternError == null
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 添加预设规则对话框
 */
@Composable
private fun AddPresetRulesDialog(
    existingRuleIds: Set<String>,
    onDismiss: () -> Unit,
    onAdd: (List<RegexRule>) -> Unit
) {
    var selectedRules by remember { mutableStateOf(setOf<String>()) }
    val presetRules = PresetRegexRules.rules

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加预设规则") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "选择要添加的预设规则",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(presetRules) { preset ->
                        val isAlreadyAdded = existingRuleIds.contains(preset.id)
                        val isSelected = selectedRules.contains(preset.id)

                        Surface(
                            onClick = {
                                if (!isAlreadyAdded) {
                                    selectedRules = if (isSelected) {
                                        selectedRules - preset.id
                                    } else {
                                        selectedRules + preset.id
                                    }
                                }
                            },
                            color = when {
                                isAlreadyAdded -> MaterialTheme.colorScheme.surfaceContainerHighest
                                isSelected -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surface
                            },
                            shape = MaterialTheme.shapes.small,
                            enabled = !isAlreadyAdded
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = preset.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = when {
                                            isAlreadyAdded -> MaterialTheme.colorScheme.onSurfaceVariant
                                            isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                            else -> MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    Text(
                                        text = preset.pattern,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (isAlreadyAdded) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = MaterialTheme.shapes.extraSmall
                                    ) {
                                        Text(
                                            text = "已添加",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                } else if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rulesToAdd = presetRules.filter { selectedRules.contains(it.id) }
                    onAdd(rulesToAdd)
                },
                enabled = selectedRules.isNotEmpty()
            ) {
                Text("添加 ${selectedRules.size} 个")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
