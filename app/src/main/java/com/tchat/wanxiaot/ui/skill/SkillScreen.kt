package com.tchat.wanxiaot.ui.skill

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.tchat.data.model.Skill

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
    val skills by viewModel.skills.collectAsState()
    val context = LocalContext.current

    // 导入结果状态
    var importResult by remember { mutableStateOf<String?>(null) }
    var showImportResultDialog by remember { mutableStateOf(false) }

    // 文件选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val content = inputStream?.bufferedReader()?.readText() ?: ""
                inputStream?.close()

                val result = viewModel.importSkill(content)
                importResult = result
                showImportResultDialog = true
            } catch (e: Exception) {
                importResult = "导入失败: ${e.message}"
                showImportResultDialog = true
            }
        }
    }

    // 导入结果对话框
    if (showImportResultDialog) {
        AlertDialog(
            onDismissRequest = { showImportResultDialog = false },
            title = { Text("导入结果") },
            text = { Text(importResult ?: "") },
            confirmButton = {
                TextButton(onClick = { showImportResultDialog = false }) {
                    Text("确定")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = { Text("Skills") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        // 导入按钮
                        IconButton(onClick = {
                            filePickerLauncher.launch(arrayOf("*/*"))
                        }) {
                            Icon(Icons.Default.FileOpen, contentDescription = "导入 Skill")
                        }
                        // 添加按钮
                        IconButton(onClick = onCreateSkill) {
                            Icon(Icons.Default.Add, contentDescription = "添加 Skill")
                        }
                    }
                )
            }
        }
    ) { padding ->
        if (skills.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "暂无 Skills",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onCreateSkill) {
                            Text("创建 Skill")
                        }
                        TextButton(onClick = {
                            filePickerLauncher.launch(arrayOf("*/*"))
                        }) {
                            Text("导入 Skill")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = "内置",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
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
                    Text(
                        text = "${skill.tools.size} 个工具",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
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
