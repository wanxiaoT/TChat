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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.composables.icons.lucide.Bot
import com.composables.icons.lucide.BookOpen
import com.composables.icons.lucide.Lucide
import com.tchat.data.database.entity.KnowledgeBaseEntity
import com.tchat.data.model.Assistant
import com.tchat.data.model.LocalToolOption
import com.tchat.data.model.LocalToolOption.Companion.description
import com.tchat.data.model.LocalToolOption.Companion.displayName
import com.tchat.data.model.McpServer
import com.tchat.wanxiaot.settings.RegexRule
import com.tchat.wanxiaot.ui.components.AppEmptyState
import com.tchat.wanxiaot.ui.components.AppPageScaffold
import com.tchat.wanxiaot.ui.components.AppPill
import com.tchat.wanxiaot.ui.components.SettingsGroupCard
import com.tchat.wanxiaot.ui.components.SettingsSurface
import kotlinx.coroutines.launch
import java.util.Locale
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
    val scope = rememberCoroutineScope()
    val assistant by viewModel.assistant.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val knowledgeBases by viewModel.knowledgeBases.collectAsStateWithLifecycle()
    val mcpServers by viewModel.mcpServers.collectAsStateWithLifecycle()
    val regexRules by viewModel.regexRules.collectAsStateWithLifecycle()

    val tabs = listOf("基本设置", "提示词", "本地工具", "MCP工具", "知识库", "正则规则")
    val pagerState = rememberPagerState { tabs.size }

    AppPageScaffold(
        title = assistant?.name?.ifEmpty { "未命名助手" } ?: "助手详情",
        eyebrow = "Assistant Profile",
        subtitle = assistant?.let {
            buildString {
                append(if (it.systemPrompt.isBlank()) "未设置提示词" else "已配置提示词")
                if (it.localTools.isNotEmpty()) append(" · ${it.localTools.size} 个本地工具")
                if (it.mcpServerIds.isNotEmpty()) append(" · ${it.mcpServerIds.size} 个 MCP")
            }
        } ?: "加载中...",
        showTopBar = showTopBar,
        onBack = onBack
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
                AppEmptyState(
                    icon = Lucide.Bot,
                    title = "助手不存在",
                    description = "这个助手可能已经被删除，返回列表重新选择即可。",
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

                SettingsSurface {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SecondaryScrollableTabRow(
                            selectedTabIndex = pagerState.currentPage,
                            modifier = Modifier.fillMaxWidth(),
                            edgePadding = 14.dp,
                            divider = {}
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
                            regexRules = regexRules,
                            onUpdate = { viewModel.updateAssistant(it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantTabColumn(
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 16.dp)
            .verticalScroll(rememberScrollState())
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content
    )
}

@Composable
private fun AssistantSettingRow(
    title: String,
    description: String? = null,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        trailing()
    }
}

@Composable
private fun AssistantSelectableRow(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    SettingsSurface {
        androidx.compose.material3.Surface(
            onClick = onClick,
            color = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
            } else {
                androidx.compose.ui.graphics.Color.Transparent
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selected,
                    onClick = null
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.74f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
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
    AssistantTabColumn {
        SettingsGroupCard(
            modifier = Modifier.padding(horizontal = 16.dp),
            title = "基础信息",
            description = "明确这个助手的名称与输出边界，方便在列表中快速区分。"
        ) {
            OutlinedTextField(
                value = assistant.name,
                onValueChange = { onUpdate(assistant.copy(name = it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("请输入助手名称") },
                singleLine = true
            )
        }

        SettingsGroupCard(
            modifier = Modifier.padding(horizontal = 16.dp),
            title = "模型参数",
            description = "这些设置只影响当前助手，不会覆盖全局服务商默认值。"
        ) {
            AssistantSettingRow(
                title = "温度",
                description = "控制回复的发散程度。"
            ) {
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
                AppPill(text = "当前值 ${String.format(Locale.ROOT, "%.1f", temp)}")
            }

            HorizontalDivider()

            AssistantSettingRow(
                title = "Top-p",
                description = "用于约束采样范围。"
            ) {
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
                AppPill(text = "当前值 ${String.format(Locale.ROOT, "%.1f", topP)}")
            }

            HorizontalDivider()

            Text(
                text = "上下文消息数量",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            AssistantSelectableRow(
                title = "不限制",
                description = "默认保留全部上下文。",
                selected = assistant.contextMessageSize <= 0,
                onClick = { onUpdate(assistant.copy(contextMessageSize = 0)) }
            )

            AssistantSelectableRow(
                title = "限制数量",
                description = "只保留最近若干条消息，降低上下文长度。",
                selected = assistant.contextMessageSize > 0,
                onClick = {
                    if (assistant.contextMessageSize <= 0) {
                        onUpdate(assistant.copy(contextMessageSize = 64))
                    }
                }
            )

            if (assistant.contextMessageSize > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Slider(
                        value = assistant.contextMessageSize.toFloat().coerceIn(1f, 200f),
                        onValueChange = { onUpdate(assistant.copy(contextMessageSize = it.roundToInt())) },
                        valueRange = 1f..200f,
                        modifier = Modifier.fillMaxWidth()
                    )

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
                            modifier = Modifier.width(92.dp),
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

            HorizontalDivider()

            AssistantSettingRow(
                title = "流式输出",
                description = "实时显示 AI 回复生成过程。"
            ) {
                Switch(
                    checked = assistant.streamOutput,
                    onCheckedChange = { onUpdate(assistant.copy(streamOutput = it)) }
                )
            }

            HorizontalDivider()

            OutlinedTextField(
                value = assistant.maxTokens?.toString() ?: "",
                onValueChange = { text ->
                    val tokens = text.toIntOrNull()?.takeIf { it > 0 }
                    onUpdate(assistant.copy(maxTokens = tokens))
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("最大输出 Token") },
                placeholder = { Text("不限制") },
                singleLine = true
            )
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
    AssistantTabColumn {
        SettingsGroupCard(
            modifier = Modifier.padding(horizontal = 16.dp),
            title = "系统提示词",
            description = "把角色、语气、边界和输出格式写清楚，能显著降低后续跑偏。"
        ) {
            OutlinedTextField(
                value = assistant.systemPrompt,
                onValueChange = { onUpdate(assistant.copy(systemPrompt = it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                placeholder = { Text("你是一个有帮助的 AI 助手...") }
            )
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

    AssistantTabColumn {
        SettingsGroupCard(
            modifier = Modifier.padding(horizontal = 16.dp),
            title = "本地工具",
            description = "启用后，助手可以直接调用对应能力。涉及文件访问的工具需要额外授权。"
        ) {
            allTools.forEachIndexed { index, tool ->
                val isEnabled = assistant.localTools.contains(tool)
                val isFileSystem = tool is LocalToolOption.FileSystem

                AssistantSettingRow(
                    title = tool.displayName(),
                    description = tool.description()
                ) {
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

                if (isFileSystem) {
                    SettingsSurface {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (hasStoragePermission) Icons.Default.Check else Icons.Default.Warning,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = if (hasStoragePermission) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    }
                                )
                                Column {
                                    Text(
                                        text = if (hasStoragePermission) "文件访问权限已授权" else "文件访问权限未授权",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "未授权时无法启用文件系统相关工具。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (!hasStoragePermission) {
                                Button(
                                    onClick = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                            val intent = createManageStorageIntent(context)
                                            if (intent != null) {
                                                settingsLauncher.launch(intent)
                                            }
                                        } else {
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

                if (index != allTools.lastIndex) {
                    HorizontalDivider()
                }
            }

            if (assistant.localTools.isNotEmpty()) {
                AppPill(text = "已启用 ${assistant.localTools.size} 个本地工具")
            }
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

    AssistantTabColumn {
        if (mcpServers.isEmpty()) {
            AppEmptyState(
                icon = Icons.Default.Warning,
                title = "暂无 MCP 服务器",
                description = "先在设置里添加 MCP 服务，再为当前助手选择需要开放的工具能力。",
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        } else {
            SettingsGroupCard(
                modifier = Modifier.padding(horizontal = 16.dp),
                title = "MCP 工具",
                description = "只开启当前助手真正需要的 MCP 服务，避免工具噪声过高。"
            ) {
                mcpServers.forEachIndexed { index, server ->
                    val isEnabled = assistant.mcpServerIds.contains(server.id)

                    AssistantSettingRow(
                        title = server.name,
                        description = server.description.ifBlank { "未填写服务描述" }
                    ) {
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

                    if (index != mcpServers.lastIndex) {
                        HorizontalDivider()
                    }
                }

                if (assistant.mcpServerIds.isNotEmpty()) {
                    AppPill(text = "已启用 ${assistant.mcpServerIds.size} 个 MCP 服务器")
                }
            }
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

    AssistantTabColumn {
        SettingsGroupCard(
            modifier = Modifier.padding(horizontal = 16.dp),
            title = "知识库配置",
            description = "绑定后，助手会基于当前知识库检索内容。Embedding 服务与对话模型完全独立。"
        ) {
            if (knowledgeBases.isEmpty()) {
                AppEmptyState(
                    icon = Lucide.BookOpen,
                    title = "暂无知识库",
                    description = "先在设置中创建知识库，再把它绑定到这个助手上。"
                )
            } else {
                KnowledgeBaseOption(
                    title = "不使用知识库",
                    description = "仅使用模型自身能力回答",
                    isSelected = assistant.knowledgeBaseId == null,
                    onClick = {
                        onUpdate(assistant.copy(knowledgeBaseId = null))
                        Toast.makeText(context, "已取消知识库绑定", Toast.LENGTH_SHORT).show()
                    }
                )

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

        if (assistant.knowledgeBaseId != null) {
            val boundBase = knowledgeBases.find { it.id == assistant.knowledgeBaseId }
            SettingsGroupCard(
                modifier = Modifier.padding(horizontal = 16.dp),
                title = "当前绑定状态"
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "已绑定知识库",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (boundBase != null) {
                    Text(
                        text = boundBase.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
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
    AssistantSelectableRow(
        title = title,
        description = description,
        selected = isSelected,
        onClick = onClick
    )
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

    AssistantTabColumn {
        if (regexRules.isEmpty()) {
            AppEmptyState(
                icon = Icons.Default.Warning,
                title = "暂无正则规则",
                description = "先在设置中添加规则，再为当前助手选择需要启用的清洗策略。",
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        } else {
            SettingsGroupCard(
                modifier = Modifier.padding(horizontal = 16.dp),
                title = "正则规则",
                description = "用于在流式输出时实时清理回复内容，只保留当前助手真正需要的规则。"
            ) {
                regexRules.forEachIndexed { index, rule ->
                    val isEnabled = assistant.enabledRegexRuleIds.contains(rule.id)

                    AssistantSettingRow(
                        title = rule.name.ifEmpty { "未命名规则" },
                        description = buildString {
                            append("模式: ${rule.pattern}")
                            if (rule.description.isNotBlank()) {
                                append(" · ${rule.description}")
                            }
                        }
                    ) {
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

                    if (index != regexRules.lastIndex) {
                        HorizontalDivider()
                    }
                }

                if (assistant.enabledRegexRuleIds.isNotEmpty()) {
                    AppPill(text = "已启用 ${assistant.enabledRegexRuleIds.size} 个正则规则")
                }
            }
        }
    }
}
