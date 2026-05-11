package com.tchat.wanxiaot.ui.skill

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tchat.data.model.Skill
import com.tchat.wanxiaot.ui.components.AppPageScaffold
import com.tchat.wanxiaot.ui.components.SettingsGroupCard
import com.tchat.wanxiaot.ui.components.SettingsSurface

/**
 * 技能详情/编辑页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillDetailScreen(
    skill: Skill?,
    onSave: (Skill) -> Unit,
    onDelete: ((String) -> Unit)? = null,
    onBack: () -> Unit,
    showTopBar: Boolean = true
) {
    val isEditing = skill != null
    val isBuiltIn = skill?.isBuiltIn == true

    // 编辑状态
    var name by remember(skill) { mutableStateOf(skill?.name ?: "") }
    var displayName by remember(skill) { mutableStateOf(skill?.displayName ?: "") }
    var description by remember(skill) { mutableStateOf(skill?.description ?: "") }
    var content by remember(skill) { mutableStateOf(skill?.content ?: "") }
    var triggerKeywords by remember(skill) {
        mutableStateOf(skill?.triggerKeywords?.joinToString(", ") ?: "")
    }
    var priority by remember(skill) { mutableStateOf(skill?.priority?.toString() ?: "0") }
    var enabled by remember(skill) { mutableStateOf(skill?.enabled ?: true) }

    // 验证
    val isValid = name.isNotBlank() && displayName.isNotBlank() && description.isNotBlank()

    // 删除确认对话框
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog && skill != null && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除 Skill") },
            text = { Text("确定要删除「${skill.displayName}」吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(skill.id)
                        showDeleteDialog = false
                        onBack()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    AppPageScaffold(
        title = when {
            isBuiltIn -> "查看 Skill"
            isEditing -> "编辑 Skill"
            else -> "新建 Skill"
        },
        eyebrow = "Skill Editor",
        subtitle = "编辑技能描述、触发规则与注入内容",
        showTopBar = showTopBar,
        onBack = if (showTopBar) onBack else null,
        actions = {
            if (isEditing && !isBuiltIn && onDelete != null) {
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            if (!isBuiltIn) {
                TextButton(
                    onClick = {
                        val keywords = triggerKeywords
                            .split(",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }

                        val newSkill = (skill ?: Skill(
                            name = name,
                            displayName = displayName,
                            description = description,
                            content = content
                        )).copy(
                            name = name,
                            displayName = displayName,
                            description = description,
                            content = content,
                            triggerKeywords = keywords,
                            priority = priority.toIntOrNull() ?: 0,
                            enabled = enabled,
                            updatedAt = System.currentTimeMillis()
                        )
                        onSave(newSkill)
                        onBack()
                    },
                    enabled = isValid
                ) {
                    Text("保存")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            SettingsGroupCard(
                title = "基本信息",
                description = "定义技能身份、触发场景和关键词。"
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("技能标识符 *") },
                    supportingText = { Text("唯一标识，建议使用英文和连字符") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBuiltIn,
                    singleLine = true
                )

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("显示名称 *") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBuiltIn,
                    singleLine = true
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("触发描述 *") },
                    supportingText = { Text("描述何时触发此技能，包含触发关键词") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBuiltIn,
                    minLines = 2,
                    maxLines = 4
                )

                OutlinedTextField(
                    value = triggerKeywords,
                    onValueChange = { triggerKeywords = it },
                    label = { Text("触发关键词") },
                    supportingText = { Text("用逗号分隔多个关键词，如：写代码, 编程, debug") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBuiltIn,
                    singleLine = true
                )
            }

            SettingsGroupCard(
                title = "技能内容",
                description = "触发后注入系统提示的指令文本。"
            ) {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("技能指令") },
                    supportingText = { Text("触发时注入到系统提示的内容") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBuiltIn,
                    minLines = 8,
                    maxLines = 20
                )
            }

            SettingsGroupCard(
                title = "执行策略",
                description = "控制优先级与启用状态。"
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = priority,
                        onValueChange = { priority = it.filter { c -> c.isDigit() || c == '-' } },
                        label = { Text("优先级") },
                        supportingText = { Text("数字越大优先级越高") },
                        modifier = Modifier.weight(1f),
                        enabled = !isBuiltIn,
                        singleLine = true
                    )

                    SettingsSurface(
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "启用状态",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Switch(
                                checked = enabled,
                                onCheckedChange = { newEnabled ->
                                    enabled = newEnabled
                                    if (isBuiltIn) {
                                        onSave(skill!!.copy(enabled = newEnabled))
                                    }
                                }
                            )
                        }
                    }
                }
            }

            if (isBuiltIn) {
                SettingsSurface {
                    Text(
                        text = "内置 Skill 不可编辑，但可以复制后修改。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
