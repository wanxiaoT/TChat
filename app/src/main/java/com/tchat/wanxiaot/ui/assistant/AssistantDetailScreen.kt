package com.tchat.wanxiaot.ui.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.composables.icons.lucide.BookOpen
import com.composables.icons.lucide.Lucide
import com.tchat.data.database.entity.KnowledgeBaseEntity
import com.tchat.data.model.Assistant
import com.tchat.data.model.LocalToolOption
import com.tchat.data.model.LocalToolOption.Companion.description
import com.tchat.data.model.LocalToolOption.Companion.displayName
import com.tchat.data.model.McpServer
import com.tchat.wanxiaot.settings.RegexRule
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 助手详情页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantDetailScreen(
    viewModel: AssistantDetailViewModel,
    onBack: () -> Unit,
    showTopBar: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val assistant by viewModel.assistant.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val knowledgeBases by viewModel.knowledgeBases.collectAsState()
    val mcpServers by viewModel.mcpServers.collectAsState()

    val tabs = listOf("基本设置", "提示词", "本地工具", "MCP工具", "知识库", "正则规则")
    val pagerState = rememberPagerState { tabs.size }

    Scaffold(
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = {
                        Text(
                            text = assistant?.name?.ifEmpty { "未命名助手" } ?: "加载中...",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
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
        } else if (assistant == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("助手不存在")
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                SecondaryScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    modifier = Modifier.fillMaxWidth(),
                    edgePadding = 16.dp,
                ) {
                    tabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = index == pagerState.currentPage,
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(index) }
                            },
                            text = { Text(tab) }
                        )
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) { page ->
                    when (page) {
                        0 -> BasicSettingsTab(
                            assistant = assistant!!,
                            onUpdate = { viewModel.updateAssistant(it) }
                        )
                        1 -> PromptSettingsTab(
                            assistant = assistant!!,
                            onUpdate = { viewModel.updateAssistant(it) }
                        )
                        2 -> LocalToolsTab(
                            assistant = assistant!!,
                            onUpdate = { viewModel.updateAssistant(it) }
                        )
                        3 -> McpToolsTab(
                            assistant = assistant!!,
                            mcpServers = mcpServers,
                            onUpdate = { viewModel.updateAssistant(it) }
                        )
                        4 -> KnowledgeBaseTab(
                            assistant = assistant!!,
                            knowledgeBases = knowledgeBases,
                            onUpdate = { viewModel.updateAssistant(it) }
                        )
                        5 -> RegexRulesTab(
                            assistant = assistant!!,
                            regexRules = viewModel.regexRules.collectAsState().value,
                            onUpdate = { viewModel.updateAssistant(it) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 基本设置标签页
 */
@Composable
private fun BasicSettingsTab(
    assistant: Assistant,
    onUpdate: (Assistant) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 名称
        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "名称",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = assistant.name,
                    onValueChange = { onUpdate(assistant.copy(name = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("请输入助手名称") },
                    singleLine = true
                )
            }
        }

        // 模型参数
        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "模型参数",
                    style = MaterialTheme.typography.labelLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 温度
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("温度")
                    Switch(
                        checked = assistant.temperature != null,
                        onCheckedChange = { enabled ->
                            onUpdate(assistant.copy(temperature = if (enabled) 1.0f else null))
                        }
                    )
                }
                if (assistant.temperature != null) {
                    val temp = assistant.temperature!!
                    Slider(
                        value = temp,
                        onValueChange = { onUpdate(assistant.copy(temperature = it)) },
                        valueRange = 0f..2f,
                        steps = 19,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "当前值: ${String.format("%.1f", temp)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Top-p
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Top-p")
                    Switch(
                        checked = assistant.topP != null,
                        onCheckedChange = { enabled ->
                            onUpdate(assistant.copy(topP = if (enabled) 1.0f else null))
                        }
                    )
                }
                if (assistant.topP != null) {
                    val topP = assistant.topP!!
                    Slider(
                        value = topP,
                        onValueChange = { onUpdate(assistant.copy(topP = it)) },
                        valueRange = 0f..1f,
                        steps = 9,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "当前值: ${String.format("%.1f", topP)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // 上下文消息数量
                Text("上下文消息数量")
                Spacer(modifier = Modifier.height(8.dp))

                // 不限制选项 - Material You 风格可点击行
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = assistant.contextMessageSize <= 0,
                            onClick = { onUpdate(assistant.copy(contextMessageSize = 0)) },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = assistant.contextMessageSize <= 0,
                        onClick = null // 由 Row 的 selectable 处理点击
                    )
                    Text(
                        text = "不限制（默认）",
                        modifier = Modifier.padding(start = 12.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                // 限制选项 - Material You 风格可点击行
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = assistant.contextMessageSize > 0,
                            onClick = {
                                if (assistant.contextMessageSize <= 0) {
                                    onUpdate(assistant.copy(contextMessageSize = 64))
                                }
                            },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = assistant.contextMessageSize > 0,
                        onClick = null // 由 Row 的 selectable 处理点击
                    )
                    Text(
                        text = "限制数量",
                        modifier = Modifier.padding(start = 12.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                // 限制数量时显示调节控件
                if (assistant.contextMessageSize > 0) {
                    Column(
                        modifier = Modifier.padding(start = 40.dp, top = 8.dp)
                    ) {
                        // 滑块
                        Slider(
                            value = assistant.contextMessageSize.toFloat().coerceIn(1f, 200f),
                            onValueChange = { onUpdate(assistant.copy(contextMessageSize = it.roundToInt())) },
                            valueRange = 1f..200f,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // 手动输入 - 下划线样式输入框
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "保留最近",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            TextField(
                                value = assistant.contextMessageSize.toString(),
                                onValueChange = { text ->
                                    val value = text.toIntOrNull()?.coerceAtLeast(1) ?: 1
                                    onUpdate(assistant.copy(contextMessageSize = value))
                                },
                                modifier = Modifier.width(80.dp),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    textAlign = TextAlign.Center
                                ),
                                colors = TextFieldDefaults.colors(
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
                                    focusedIndicatorColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Text(
                                text = "条消息",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // 流式输出
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("流式输出")
                        Text(
                            text = "实时显示AI回复内容",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = assistant.streamOutput,
                        onCheckedChange = { onUpdate(assistant.copy(streamOutput = it)) }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // 最大Token
                Text("最大输出Token")
                OutlinedTextField(
                    value = assistant.maxTokens?.toString() ?: "",
                    onValueChange = { text ->
                        val tokens = text.toIntOrNull()?.takeIf { it > 0 }
                        onUpdate(assistant.copy(maxTokens = tokens))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("不限制") },
                    singleLine = true
                )
            }
        }
    }
}

/**
 * 提示词设置标签页
 */
@Composable
private fun PromptSettingsTab(
    assistant: Assistant,
    onUpdate: (Assistant) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "系统提示词",
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = "设置AI助手的角色和行为",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = assistant.systemPrompt,
                    onValueChange = { onUpdate(assistant.copy(systemPrompt = it)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    placeholder = { Text("你是一个有帮助的AI助手...") }
                )
            }
        }
    }
}

/**
 * 本地工具设置标签页
 */
@Composable
private fun LocalToolsTab(
    assistant: Assistant,
    onUpdate: (Assistant) -> Unit
) {
    val context = LocalContext.current
    val allTools = LocalToolOption.allOptions()
    
    // 检查存储权限
    var hasStoragePermission by remember { 
        mutableStateOf(checkStoragePermission(context)) 
    }
    
    // 权限请求 launcher（Android 10 及以下）
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        hasStoragePermission = allGranted
        if (allGranted) {
            Toast.makeText(context, "存储权限已授权", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "需要存储权限才能使用文件系统工具", Toast.LENGTH_SHORT).show()
        }
    }
    
    // 设置页面 launcher（Android 11+）
    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasStoragePermission = checkStoragePermission(context)
        if (hasStoragePermission) {
            Toast.makeText(context, "存储权限已授权", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "启用本地工具后，AI可以执行相应操作",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        allTools.forEach { tool ->
            val isEnabled = assistant.localTools.contains(tool)
            val isFileSystem = tool is LocalToolOption.FileSystem

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tool.displayName(),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = tool.description(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { enabled ->
                                val newTools = if (enabled) {
                                    assistant.localTools + tool
                                } else {
                                    assistant.localTools - tool
                                }
                                onUpdate(assistant.copy(localTools = newTools))
                                Toast.makeText(
                                    context,
                                    if (enabled) "已启用${tool.displayName()}" else "已禁用${tool.displayName()}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            enabled = !isFileSystem || hasStoragePermission
                        )
                    }
                    
                    // 文件系统工具的权限状态和授权按钮
                    if (isFileSystem) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 权限状态
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (hasStoragePermission) 
                                        Icons.Default.Check 
                                    else 
                                        Icons.Default.Warning,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = if (hasStoragePermission)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.error
                                )
                                Column {
                                    Text(
                                        text = if (hasStoragePermission) "已授权" else "需要授权",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (hasStoragePermission)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = "文件访问权限",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            
                            // 授权按钮
                            if (!hasStoragePermission) {
                                Button(
                                    onClick = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                            // Android 11+ 跳转到设置页面
                                            val intent = createManageStorageIntent(context)
                                            if (intent != null) {
                                                settingsLauncher.launch(intent)
                                            }
                                        } else {
                                            // Android 10 及以下使用权限请求
                                            val permissions = getRequiredPermissions()
                                            if (permissions.isNotEmpty()) {
                                                permissionLauncher.launch(permissions)
                                            }
                                        }
                                    }
                                ) {
                                    Text("授权")
                                }
                            }
                        }
                    }
                }
            }
        }

        if (assistant.localTools.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "已启用 ${assistant.localTools.size} 个本地工具",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * MCP 工具设置标签页
 */
@Composable
private fun McpToolsTab(
    assistant: Assistant,
    mcpServers: List<McpServer>,
    onUpdate: (Assistant) -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "选择要启用的 MCP 服务器，AI 可以调用其提供的工具",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (mcpServers.isEmpty()) {
            // 空状态
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "暂无 MCP 服务器",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "请先在设置中添加 MCP 服务器",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            mcpServers.forEach { server ->
                val isEnabled = assistant.mcpServerIds.contains(server.id)

                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = server.name,
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (server.description.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = server.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { enabled ->
                                val newIds = if (enabled) {
                                    assistant.mcpServerIds + server.id
                                } else {
                                    assistant.mcpServerIds - server.id
                                }
                                onUpdate(assistant.copy(mcpServerIds = newIds))
                                Toast.makeText(
                                    context,
                                    if (enabled) "已启用 ${server.name}" else "已禁用 ${server.name}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            enabled = server.enabled
                        )
                    }
                }
            }
        }

        if (assistant.mcpServerIds.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "已启用 ${assistant.mcpServerIds.size} 个 MCP 服务器",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// ========== 权限帮助函数 ==========

/**
 * 检查是否有存储权限
 */
private fun checkStoragePermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        val readPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            readPermission
        } else {
            val writePermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            readPermission && writePermission
        }
    }
}

/**
 * 获取需要请求的权限列表
 */
private fun getRequiredPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        emptyArray()
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    } else {
        arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }
}

/**
 * 创建跳转到"所有文件访问权限"设置页面的 Intent
 */
private fun createManageStorageIntent(context: Context): Intent? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    } else {
        null
    }
}

/**
 * 知识库配置标签页
 */
@Composable
private fun KnowledgeBaseTab(
    assistant: Assistant,
    knowledgeBases: List<KnowledgeBaseEntity>,
    onUpdate: (Assistant) -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 说明文字
        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Lucide.BookOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "知识库配置",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "为助手绑定知识库后，AI 可以在对话中自动检索相关内容。知识库使用自己配置的 Embedding 服务商，与对话模型提供商独立。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 知识库选择
        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "选择知识库",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (knowledgeBases.isEmpty()) {
                    // 没有知识库时的空状态
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Lucide.BookOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "暂无知识库",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "请先在设置中创建知识库",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                } else {
                    // 不使用知识库选项
                    KnowledgeBaseOption(
                        title = "不使用知识库",
                        description = "仅使用模型自身能力回答",
                        isSelected = assistant.knowledgeBaseId == null,
                        onClick = {
                            onUpdate(assistant.copy(knowledgeBaseId = null))
                            Toast.makeText(context, "已取消知识库绑定", Toast.LENGTH_SHORT).show()
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // 知识库列表
                    knowledgeBases.forEach { base ->
                        KnowledgeBaseOption(
                            title = base.name,
                            description = base.description ?: "使用 ${base.embeddingModelId} 模型",
                            isSelected = assistant.knowledgeBaseId == base.id,
                            onClick = {
                                onUpdate(assistant.copy(knowledgeBaseId = base.id))
                                Toast.makeText(context, "已绑定「${base.name}」", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }

        // 当前绑定状态
        if (assistant.knowledgeBaseId != null) {
            val boundBase = knowledgeBases.find { it.id == assistant.knowledgeBaseId }
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "已绑定知识库",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (boundBase != null) {
                        Text(
                            text = boundBase.name,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Embedding 模型: ${boundBase.embeddingModelId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "知识库不存在（可能已被删除）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

/**
 * 知识库选项卡片
 */
@Composable
private fun KnowledgeBaseOption(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSelected)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 正则规则配置标签页
 */
@Composable
private fun RegexRulesTab(
    assistant: Assistant,
    regexRules: List<RegexRule>,
    onUpdate: (Assistant) -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "选择要启用的正则规则，用于在流式输出时实时清理 AI 回复内容",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (regexRules.isEmpty()) {
            // 空状态
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "暂无正则规则",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "请先在设置中添加正则规则",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            regexRules.forEach { rule ->
                val isEnabled = assistant.enabledRegexRuleIds.contains(rule.id)

                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = rule.name.ifEmpty { "未命名规则" },
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "模式: ${rule.pattern}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (rule.description.isNotBlank()) {
                                Text(
                                    text = rule.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { enabled ->
                                val newIds = if (enabled) {
                                    assistant.enabledRegexRuleIds + rule.id
                                } else {
                                    assistant.enabledRegexRuleIds - rule.id
                                }
                                onUpdate(assistant.copy(enabledRegexRuleIds = newIds))
                                Toast.makeText(
                                    context,
                                    if (enabled) "已启用「${rule.name}」" else "已禁用「${rule.name}」",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }
                }
            }
        }

        if (assistant.enabledRegexRuleIds.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "已启用 ${assistant.enabledRegexRuleIds.size} 个正则规则",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
