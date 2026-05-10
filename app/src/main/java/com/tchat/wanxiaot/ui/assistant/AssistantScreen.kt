package com.tchat.wanxiaot.ui.assistant

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tchat.data.model.Assistant
import com.tchat.wanxiaot.ui.components.AppEmptyState
import com.tchat.wanxiaot.ui.components.AppIconTile
import com.tchat.wanxiaot.ui.components.AppPageScaffold
import com.tchat.wanxiaot.ui.components.AppPill
import com.tchat.wanxiaot.ui.components.AppSectionSurface
import com.tchat.wanxiaot.ui.components.AppSheetSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    viewModel: AssistantViewModel,
    onBack: () -> Unit,
    onAssistantClick: (String) -> Unit,
    showTopBar: Boolean = true
) {
    val context = LocalContext.current
    val assistants by viewModel.assistants.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    var showCreateSheet by remember { mutableStateOf(false) }
    var assistantToDelete by remember { mutableStateOf<Assistant?>(null) }

    AppPageScaffold(
        title = "助手",
        eyebrow = "Assistant Library",
        subtitle = if (assistants.isEmpty()) "定义不同角色、工具和知识库组合" else "已配置 ${assistants.size} 位助手",
        showTopBar = showTopBar,
        onBack = onBack,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateSheet = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("新建助手") }
            )
        }
    ) { innerPadding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

                if (assistants.isEmpty()) {
                    item {
                        AppEmptyState(
                            icon = Icons.Default.Person,
                            title = "还没有助手",
                            description = "点击右下角创建第一个助手，给不同任务建立更明确的工作边界。"
                        )
                    }
                } else {
                    items(assistants, key = { it.id }) { assistant ->
                        AssistantItem(
                            assistant = assistant,
                            onClick = { onAssistantClick(assistant.id) },
                            onCopy = {
                                viewModel.copyAssistant(assistant)
                                Toast.makeText(context, "已复制助手", Toast.LENGTH_SHORT).show()
                            },
                            onDelete = { assistantToDelete = assistant }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(84.dp))
                    }
                }
            }
        }
    }

    if (showCreateSheet) {
        CreateAssistantSheet(
            onDismiss = { showCreateSheet = false },
            onCreate = { name ->
                val newAssistant = Assistant(name = name)
                viewModel.createAssistant(newAssistant)
                showCreateSheet = false
                Toast.makeText(context, "已创建助手", Toast.LENGTH_SHORT).show()
            }
        )
    }

    assistantToDelete?.let { assistant ->
        AlertDialog(
            onDismissRequest = { assistantToDelete = null },
            title = { Text("删除助手") },
            text = { Text("确定要删除「${assistant.name.ifEmpty { "未命名助手" }}」吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAssistant(assistant.id)
                        assistantToDelete = null
                        Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { assistantToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun AssistantItem(
    assistant: Assistant,
    onClick: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    AppSectionSurface(
        modifier = Modifier.fillMaxWidth()
    ) {
        androidx.compose.material3.Surface(
            onClick = onClick,
            color = androidx.compose.ui.graphics.Color.Transparent
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIconTile(icon = Icons.Default.Person)

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = assistant.name.ifEmpty { "未命名助手" },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = assistant.systemPrompt.ifBlank { "未设置系统提示词，适合继续补充角色边界与输出风格。" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (assistant.localTools.isNotEmpty()) {
                        AppPill(text = "本地工具 ${assistant.localTools.size}")
                    }
                    if (assistant.mcpServerIds.isNotEmpty()) {
                        AppPill(text = "MCP ${assistant.mcpServerIds.size}")
                    }
                    if (assistant.knowledgeBaseId != null) {
                        AppPill(text = "已绑定知识库")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onCopy) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "复制",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateAssistantSheet(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = androidx.compose.ui.graphics.Color.Transparent
    ) {
        AppSheetSurface(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = "创建新助手",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "先给它一个清晰名称，后续再补提示词、工具和知识库。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("助手名称") },
                placeholder = { Text("例如：产品分析助手") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
                TextButton(
                    onClick = { onCreate(name) },
                    enabled = name.isNotBlank()
                ) {
                    Text("创建")
                }
            }
        }
    }
}
