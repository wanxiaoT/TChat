package com.tchat.wanxiaot.ui.mcp

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tchat.data.model.McpServer
import com.tchat.data.model.McpServerType

/**
 * MCP 服务器管理页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpScreen(
    viewModel: McpViewModel,
    onBack: () -> Unit,
    showTopBar: Boolean = true
) {
    val context = LocalContext.current
    val servers by viewModel.servers.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val testResult by viewModel.testResult.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingServer by remember { mutableStateOf<McpServer?>(null) }

    // 处理测试结果
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

    Scaffold(
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = { Text("MCP 服务器") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, "添加服务器")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        if (servers.isEmpty()) {
            EmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
            }
        }
    }

    // 添加对话框
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

    // 编辑对话框
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

/**
 * 空状态
 */
@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Cloud,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "暂无 MCP 服务器",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "点击右下角按钮添加服务器",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

/**
 * MCP 服务器卡片
 */
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

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = server.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (server.description.isNotEmpty()) {
                        Text(
                            text = server.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Switch(
                    checked = server.enabled,
                    onCheckedChange = { onToggleEnabled() }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = server.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

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

/**
 * MCP 服务器编辑对话框
 */
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

                Text("传输类型", style = MaterialTheme.typography.labelMedium)
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
