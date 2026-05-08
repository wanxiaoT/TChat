package com.tchat.wanxiaot.ui.skill

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tchat.data.model.Skill
import com.tchat.wanxiaot.ui.components.AppEmptyState
import com.tchat.wanxiaot.ui.components.AppHeroCard
import com.tchat.wanxiaot.ui.components.AppPageScaffold
import com.tchat.wanxiaot.ui.components.AppPill
import com.tchat.wanxiaot.ui.components.AppSectionSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Skills 管理页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillScreen(
    viewModel: SkillViewModel,
    onBack: () -> Unit,
    onSkillClick: (String) -> Unit,
    onCreateSkill: () -> Unit,
    showTopBar: Boolean = true
) {
    val skills by viewModel.skills.collectAsStateWithLifecycle()
    val importResult by viewModel.importResult.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 文件选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    val content = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader ->
                            reader.readText()
                        } ?: throw IllegalArgumentException("无法读取所选文件")
                    }
                    viewModel.importSkill(content)
                } catch (e: Exception) {
                    viewModel.reportImportFailure("导入失败: ${e.message}")
                }
            }
        }
    }

    // 导入结果对话框
    if (importResult != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearImportResult() },
            title = { Text("导入结果") },
            text = { Text(importResult ?: "") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearImportResult() }) {
                    Text("确定")
                }
            }
        )
    }

    AppPageScaffold(
        title = "Skills",
        eyebrow = "Automation",
        subtitle = "管理内置与自定义技能",
        showTopBar = showTopBar,
        onBack = if (showTopBar) onBack else null,
        actions = {
            IconButton(onClick = { filePickerLauncher.launch(arrayOf("*/*")) }) {
                Icon(Icons.Default.FileOpen, contentDescription = "导入 Skill")
            }
            IconButton(onClick = onCreateSkill) {
                Icon(Icons.Default.Add, contentDescription = "添加 Skill")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                AppHeroCard(
                    title = "技能库",
                    description = "在这里管理系统能力扩展，包括导入、复制、启用和创建自定义 Skills。",
                    eyebrow = "Skill System"
                ) {
                    AppPill(text = "${skills.size} 个技能")
                }
            }

            if (skills.isEmpty()) {
                item {
                    AppEmptyState(
                        title = "暂无 Skills",
                        description = "可以创建一个新技能，或者从文件中导入已有 Skill。",
                        icon = Icons.Default.Add,
                        action = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = onCreateSkill) {
                                    Text("创建 Skill")
                                }
                                TextButton(onClick = { filePickerLauncher.launch(arrayOf("*/*")) }) {
                                    Text("导入 Skill")
                                }
                            }
                        }
                    )
                }
            } else {
                items(skills, key = { it.id }) { skill ->
                    SkillCard(
                        skill = skill,
                        onClick = { onSkillClick(skill.id) },
                        onToggleEnabled = { viewModel.toggleSkillEnabled(skill) },
                        onCopy = { viewModel.copySkill(skill) },
                        onDelete = { viewModel.deleteSkill(skill.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SkillCard(
    skill: Skill,
    onClick: () -> Unit,
    onToggleEnabled: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    AppSectionSurface(
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = skill.displayName,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (skill.isBuiltIn) {
                        Spacer(Modifier.width(8.dp))
                        AppPill(text = "内置")
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = skill.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (skill.triggerKeywords.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "关键词: ${skill.triggerKeywords.take(3).joinToString(", ")}${if (skill.triggerKeywords.size > 3) "..." else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (skill.tools.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    AppPill(text = "${skill.tools.size} 个工具")
                }
            }

            Switch(
                checked = skill.enabled,
                onCheckedChange = { onToggleEnabled() }
            )

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "更多")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("复制") },
                        onClick = {
                            onCopy()
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                        }
                    )
                    if (!skill.isBuiltIn) {
                        DropdownMenuItem(
                            text = { Text("删除") },
                            onClick = {
                                onDelete()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
