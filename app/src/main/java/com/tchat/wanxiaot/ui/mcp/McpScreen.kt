package com.tchat.wanxiaot.ui.mcp

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tchat.data.model.McpServer
import com.tchat.data.model.McpServerType
import com.tchat.wanxiaot.ui.components.AppEmptyState
import com.tchat.wanxiaot.ui.components.AppPageScaffold
import com.tchat.wanxiaot.ui.components.AppPill
import com.tchat.wanxiaot.ui.components.SettingsGroupCard
import com.tchat.wanxiaot.ui.components.SettingsSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpScreen(
    viewModel: McpViewModel,
    onBack: () -> Unit,
    showTopBar: Boolean = true
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val testResult by viewModel.testResult.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingServer by remember { mutableStateOf<McpServer?>(null) }

    LaunchedEffect(testResult) {
        when (val result = testResult) {
            is McpViewModel.TestResult.Success -> {
                Toast.makeText(
                    context,
                    "连接成功！发现 ${result.tools.size} 个工具",
                    Toast.LENGTH_SHORT
                ).show()
                viewModel.clearTestResult()
            }
            is McpViewModel.TestResult.Error -> {
                Toast.makeText(context, "连接失败: ${result.message}", Toast.LENGTH_LONG).show()
                viewModel.clearTestResult()
            }
            null -> {}
        }
    }

    AppPageScaffold(
        title = "MCP 服务器",
        eyebrow = "MCP Network",
        subtitle = if (servers.isEmpty()) "连接外部工具能力" else "已配置 ${servers.size} 个 MCP 服务",
        showTopBar = showTopBar,
        onBack = onBack,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("添加服务器") }
            )
        }
    ) { innerPadding ->
        if (servers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    AppEmptyState(
                        icon = Icons.Default.Cloud,
                        title = "暂无 MCP 服务器",
                        description = "点击右下角添加一个 MCP 服务，然后再为助手按需开放对应能力。"
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

                items(servers, key = { it.id }) { server ->
                    McpServerCard(
                        server = server,
                        isLoading = isLoading,
                        onEdit = { editingServer = server },
                        onDelete = { viewModel.deleteServer(server.id) },
                        onTest = { viewModel.testConnection(server) },
                        onToggleEnabled = {
                            viewModel.updateServer(server.copy(enabled = !server.enabled))
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(84.dp))
                }
            }
        }
    }

    if (showAddDialog) {
        McpServerDialog(
            server = null,
            onDismiss = { showAddDialog = false },
            onSave = { server ->
                viewModel.addServer(server)
                showAddDialog = false
            }
        )
    }

    editingServer?.let { server ->
        McpServerDialog(
            server = server,
            onDismiss = { editingServer = null },
            onSave = { updated ->
                viewModel.updateServer(updated)
                editingServer = null
            }
        )
    }
}

@Composable
private fun McpServerCard(
    server: McpServer,
    isLoading: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTest: () -> Unit,
    onToggleEnabled: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    SettingsSurface {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = server.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (server.description.isNotEmpty()) {
                        Text(
                            text = server.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Switch(
                    checked = server.enabled,
                    onCheckedChange = { onToggleEnabled() }
                )
            }

            Text(
                text = server.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppPill(
                    text = if (server.enabled) "已启用" else "已停用",
                    containerColor = if (server.enabled) {
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    contentColor = if (server.enabled) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                AppPill(
                    text = if (server.type == McpServerType.SSE) "SSE" else "Streamable HTTP",
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = onTest,
                    label = { Text(if (isLoading) "测试中..." else "测试连接") },
                    leadingIcon = {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                        }
                    },
                    enabled = !isLoading
                )
                AssistChip(
                    onClick = onEdit,
                    label = { Text("编辑") },
                    leadingIcon = { Icon(Icons.Default.Edit, null, Modifier.size(16.dp)) }
                )
                AssistChip(
                    onClick = { showDeleteDialog = true },
                    label = { Text("删除") },
                    leadingIcon = { Icon(Icons.Default.Delete, null, Modifier.size(16.dp)) }
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除服务器") },
            text = { Text("确定要删除「${server.name}」吗？") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteDialog = false
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun McpServerDialog(
    server: McpServer?,
    onDismiss: () -> Unit,
    onSave: (McpServer) -> Unit
) {
    var name by remember { mutableStateOf(server?.name ?: "") }
    var description by remember { mutableStateOf(server?.description ?: "") }
    var url by remember { mutableStateOf(server?.url ?: "") }
    var type by remember { mutableStateOf(server?.type ?: McpServerType.SSE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (server == null) "添加 MCP 服务器" else "编辑 MCP 服务器") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("服务器 URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("http://localhost:3000/sse") }
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("描述（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                SettingsGroupCard(title = "传输类型") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = type == McpServerType.SSE,
                            onClick = { type = McpServerType.SSE },
                            label = { Text("SSE") }
                        )
                        FilterChip(
                            selected = type == McpServerType.STREAMABLE_HTTP,
                            onClick = { type = McpServerType.STREAMABLE_HTTP },
                            label = { Text("Streamable HTTP") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val newServer = server?.copy(
                        name = name,
                        description = description,
                        url = url,
                        type = type,
                        updatedAt = System.currentTimeMillis()
                    ) ?: McpServer(
                        name = name,
                        description = description,
                        url = url,
                        type = type
                    )
                    onSave(newServer)
                },
                enabled = name.isNotBlank() && url.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
