package com.tchat.wanxiaot.ui.deepresearch

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Bot
import com.composables.icons.lucide.BrainCircuit
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Sparkles
import com.tchat.data.deepresearch.NodeStatus
import com.tchat.data.deepresearch.ResearchNode
import com.tchat.data.deepresearch.ResearchState
import com.tchat.data.deepresearch.model.DeepResearchConfig
import com.tchat.data.deepresearch.model.Learning
import com.tchat.data.deepresearch.repository.DeepResearchHistory
import com.tchat.data.deepresearch.service.WebSearchProvider
import com.tchat.wanxiaot.settings.DeepResearchSettings
import com.tchat.wanxiaot.settings.ProviderConfig
import com.tchat.wanxiaot.ui.ProviderSelectionSheet
import com.tchat.feature.chat.markdown.MarkdownText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 深度研究主界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeepResearchScreen(
    viewModel: DeepResearchViewModel,
    onBack: () -> Unit,
    showTopBar: Boolean = true,
    // 服务商和模型选择
    providers: List<ProviderConfig> = emptyList(),
    currentProviderId: String = "",
    currentProviderName: String = "",
    availableModels: List<String> = emptyList(),
    currentModel: String = "",
    onProviderSelected: (String) -> Unit = {},
    onModelSelected: (String) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val nodes by viewModel.nodes.collectAsState()
    val learnings by viewModel.learnings.collectAsState()
    val report by viewModel.report.collectAsState()
    val config by viewModel.config.collectAsState()
    val deepResearchSettings by viewModel.deepResearchSettings.collectAsState()
    val historyList by viewModel.historyList.collectAsState()

    var query by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showProviderSelection by remember { mutableStateOf(false) }
    var configError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("深度研究") },
                navigationIcon = {
                    if (showTopBar) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showHistory = true }) {
                        Icon(Icons.Default.History, contentDescription = "历史记录")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 输入区域
            InputSection(
                query = query,
                onQueryChange = { query = it },
                isResearching = state is ResearchState.Researching || state is ResearchState.GeneratingReport,
                onStartResearch = {
                    if (query.isNotBlank()) {
                        val error = viewModel.getConfigError()
                        if (error != null) {
                            configError = error
                        } else {
                            configError = null
                            viewModel.startResearch(query)
                        }
                    }
                },
                onCancel = { viewModel.cancelResearch() }
            )

            // 服务商和模型选择工具栏
            if (providers.isNotEmpty()) {
                DeepResearchToolbar(
                    currentProviderName = currentProviderName,
                    availableModels = availableModels,
                    currentModel = currentModel,
                    onProviderClick = { showProviderSelection = true },
                    onModelSelected = onModelSelected
                )
            }

            // 配置错误提示
            configError?.let { error ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { showSettings = true }) {
                            Text("去配置")
                        }
                    }
                }
            }

            // 状态指示器
            StateIndicator(state = state)

            // 内容区域
            when (state) {
                is ResearchState.Idle -> {
                    IdleContent()
                }
                is ResearchState.Researching, is ResearchState.GeneratingReport -> {
                    ResearchProgress(
                        nodes = nodes,
                        report = report,
                        isGeneratingReport = state is ResearchState.GeneratingReport
                    )
                }
                is ResearchState.Complete -> {
                    val completeState = state as ResearchState.Complete
                    ResearchResult(
                        learnings = completeState.learnings,
                        report = completeState.report
                    )
                }
                is ResearchState.Error -> {
                    val errorState = state as ResearchState.Error
                    ErrorContent(message = errorState.message)
                }
            }
        }
    }

    // 设置对话框
    if (showSettings) {
        SettingsDialog(
            settings = deepResearchSettings,
            onSettingsChange = { viewModel.updateDeepResearchSettings(it) },
            onDismiss = { showSettings = false }
        )
    }

    // 历史记录对话框
    if (showHistory) {
        HistoryDialog(
            historyList = historyList,
            onHistoryClick = { history ->
                viewModel.loadFromHistory(history)
                showHistory = false
            },
            onDeleteHistory = { history ->
                viewModel.deleteHistory(history.id)
            },
            onDeleteAllHistory = {
                viewModel.deleteAllHistory()
            },
            onDismiss = { showHistory = false }
        )
    }

    // 服务商选择对话框
    if (showProviderSelection) {
        ProviderSelectionSheet(
            providers = providers,
            currentProviderId = currentProviderId,
            onProviderSelected = { providerId ->
                onProviderSelected(providerId)
                showProviderSelection = false
            },
            onDismiss = { showProviderSelection = false }
        )
    }
}

@Composable
private fun InputSection(
    query: String,
    onQueryChange: (String) -> Unit,
    isResearching: Boolean,
    onStartResearch: () -> Unit,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            label = { Text("输入研究问题") },
            placeholder = { Text("例如：2024年人工智能的最新进展") },
            enabled = !isResearching,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onStartResearch() }),
            maxLines = 3,
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        if (isResearching) {
            FilledTonalButton(
                onClick = onCancel,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("停止")
            }
        } else {
            Button(onClick = onStartResearch, enabled = query.isNotBlank()) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("研究")
            }
        }
    }
}

/**
 * 深度研究工具栏 - 服务商和模型选择
 */
@Composable
private fun DeepResearchToolbar(
    currentProviderName: String,
    availableModels: List<String>,
    currentModel: String,
    onProviderClick: () -> Unit,
    onModelSelected: (String) -> Unit
) {
    var modelMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 服务商选择器
            Surface(
                onClick = onProviderClick,
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.small,
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = currentProviderName.ifEmpty { "选择服务商" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 模型选择器
            if (availableModels.isNotEmpty()) {
                Box {
                    Surface(
                        onClick = { modelMenuExpanded = true },
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.small,
                        tonalElevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 使用 Lucide Icon 显示模型类型
                            Icon(
                                imageVector = getModelLucideIcon(currentModel),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = getModelDisplayName(currentModel),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = modelMenuExpanded,
                        onDismissRequest = { modelMenuExpanded = false }
                    ) {
                        availableModels.forEach { model ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // 使用 Lucide Icon 显示模型类型
                                        Icon(
                                            imageVector = getModelLucideIcon(model),
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = if (model == currentModel)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = model,
                                            color = if (model == currentModel)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                },
                                onClick = {
                                    onModelSelected(model)
                                    modelMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

/**
 * 根据模型名称获取对应的 Lucide 图标
 */
private fun getModelLucideIcon(model: String): ImageVector {
    return when {
        // OpenAI 模型
        model.contains("gpt", ignoreCase = true) -> Lucide.Sparkles
        model.contains("o1", ignoreCase = true) -> Lucide.Sparkles
        model.contains("o3", ignoreCase = true) -> Lucide.Sparkles
        model.contains("davinci", ignoreCase = true) -> Lucide.Sparkles
        
        // Claude 模型
        model.contains("claude", ignoreCase = true) -> Lucide.Bot
        
        // Gemini 模型
        model.contains("gemini", ignoreCase = true) -> Lucide.BrainCircuit
        
        // 其他模型使用通用图标
        else -> Lucide.Bot
    }
}

/**
 * 获取模型简短显示名称
 */
private fun getModelDisplayName(model: String): String {
    return when {
        model.length <= 20 -> model
        else -> model.take(18) + "..."
    }
}

@Composable
private fun StateIndicator(state: ResearchState) {
    val (text, color) = when (state) {
        is ResearchState.Idle -> "" to Color.Transparent
        is ResearchState.Researching -> "正在研究..." to MaterialTheme.colorScheme.primary
        is ResearchState.GeneratingReport -> "正在生成报告..." to MaterialTheme.colorScheme.tertiary
        is ResearchState.Complete -> "研究完成" to MaterialTheme.colorScheme.primary
        is ResearchState.Error -> "发生错误" to MaterialTheme.colorScheme.error
    }

    AnimatedVisibility(
        visible = text.isNotEmpty(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state is ResearchState.Researching || state is ResearchState.GeneratingReport) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = color
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = color
            )
        }
    }
}

@Composable
private fun IdleContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Psychology,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "输入问题开始深度研究",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "AI 将通过多轮搜索和分析，为您生成详细的研究报告",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun ResearchProgress(
    nodes: Map<String, ResearchNode>,
    report: String,
    isGeneratingReport: Boolean
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState)
    ) {
        // 节点列表
        Text(
            text = "研究进度",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        nodes.values.sortedBy { it.id }.forEach { node ->
            NodeItem(node = node)
        }

        // 报告预览
        if (report.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "研究报告",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    MarkdownText(markdown = report)
                    if (isGeneratingReport) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun NodeItem(node: ResearchNode) {
    val statusColor = when (node.status) {
        NodeStatus.PENDING -> MaterialTheme.colorScheme.outline
        NodeStatus.GENERATING_QUERY -> MaterialTheme.colorScheme.tertiary
        NodeStatus.SEARCHING -> MaterialTheme.colorScheme.primary
        NodeStatus.PROCESSING -> MaterialTheme.colorScheme.secondary
        NodeStatus.COMPLETE -> MaterialTheme.colorScheme.primary
        NodeStatus.ERROR -> MaterialTheme.colorScheme.error
    }

    val statusIcon = when (node.status) {
        NodeStatus.PENDING -> Icons.Default.Schedule
        NodeStatus.GENERATING_QUERY -> Icons.Default.AutoAwesome
        NodeStatus.SEARCHING -> Icons.Default.Search
        NodeStatus.PROCESSING -> Icons.Default.Psychology
        NodeStatus.COMPLETE -> Icons.Default.CheckCircle
        NodeStatus.ERROR -> Icons.Default.Error
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 状态图标
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                if (node.status == NodeStatus.SEARCHING ||
                    node.status == NodeStatus.PROCESSING ||
                    node.status == NodeStatus.GENERATING_QUERY) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = statusColor
                    )
                } else {
                    Icon(
                        statusIcon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = statusColor
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 节点信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = node.query ?: "正在生成查询...",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (node.learnings.isNotEmpty()) {
                    Text(
                        text = "发现 ${node.learnings.size} 条信息",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                node.errorMessage?.let { errorMsg ->
                    Text(
                        text = errorMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun ResearchResult(
    learnings: List<Learning>,
    report: String?
) {
    var selectedTab by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Tab 切换
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("研究报告") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("来源 (${learnings.size})") }
            )
        }

        when (selectedTab) {
            0 -> {
                // 报告内容
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (report.isNullOrEmpty()) {
                        Text(
                            text = "报告生成中...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        MarkdownText(markdown = report)
                    }
                }
            }
            1 -> {
                // 来源列表
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(learnings) { learning ->
                        LearningItem(learning = learning)
                    }
                }
            }
        }
    }
}

@Composable
private fun LearningItem(learning: Learning) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = learning.title ?: learning.url,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = learning.learning,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = learning.url,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ErrorContent(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "研究失败",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDialog(
    settings: DeepResearchSettings,
    onSettingsChange: (DeepResearchSettings) -> Unit,
    onDismiss: () -> Unit
) {
    // 搜索设置
    var webSearchProvider by remember { mutableStateOf(settings.webSearchProvider) }
    var webSearchApiKey by remember { mutableStateOf(settings.webSearchApiKey) }
    var webSearchApiBase by remember { mutableStateOf(settings.webSearchApiBase ?: "") }
    var tavilyAdvancedSearch by remember { mutableStateOf(settings.tavilyAdvancedSearch) }

    // AI 设置
    var useCustomAI by remember { mutableStateOf(settings.aiApiKey.isNotBlank()) }
    var aiProviderType by remember { mutableStateOf(settings.aiProviderType) }
    var aiApiKey by remember { mutableStateOf(settings.aiApiKey) }
    var aiApiBase by remember { mutableStateOf(settings.aiApiBase) }
    var aiModel by remember { mutableStateOf(settings.aiModel) }

    // 研究参数
    var breadth by remember { mutableStateOf(settings.breadth.toString()) }
    var maxDepth by remember { mutableStateOf(settings.maxDepth.toString()) }
    var language by remember { mutableStateOf(settings.language) }
    var searchLanguage by remember { mutableStateOf(settings.searchLanguage) }

    // 密码可见性
    var showWebSearchApiKey by remember { mutableStateOf(false) }
    var showAiApiKey by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("深度研究设置") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ===== 搜索 API 设置 =====
                Text(
                    text = "搜索 API 设置",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                // 搜索提供商选择
                var providerExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = providerExpanded,
                    onExpandedChange = { providerExpanded = it }
                ) {
                    OutlinedTextField(
                        value = webSearchProvider.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("搜索提供商") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                    )
                    ExposedDropdownMenu(
                        expanded = providerExpanded,
                        onDismissRequest = { providerExpanded = false }
                    ) {
                        WebSearchProvider.entries.forEach { provider ->
                            DropdownMenuItem(
                                text = { Text(provider.displayName) },
                                onClick = {
                                    webSearchProvider = provider
                                    providerExpanded = false
                                }
                            )
                        }
                    }
                }

                // 搜索 API Key
                OutlinedTextField(
                    value = webSearchApiKey,
                    onValueChange = { webSearchApiKey = it },
                    label = { Text("${webSearchProvider.displayName} API Key") },
                    supportingText = {
                        Text(
                            when (webSearchProvider) {
                                WebSearchProvider.TAVILY -> "从 tavily.com 获取"
                                WebSearchProvider.FIRECRAWL -> "从 firecrawl.dev 获取"
                            }
                        )
                    },
                    visualTransformation = if (showWebSearchApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showWebSearchApiKey = !showWebSearchApiKey }) {
                            Icon(
                                if (showWebSearchApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Tavily 高级搜索选项
                if (webSearchProvider == WebSearchProvider.TAVILY) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("高级搜索", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "更精确但消耗更多配额",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = tavilyAdvancedSearch,
                            onCheckedChange = { tavilyAdvancedSearch = it }
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // ===== AI 设置 =====
                Text(
                    text = "AI 设置",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("使用独立 AI 配置", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "关闭则使用默认服务商",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = useCustomAI,
                        onCheckedChange = { useCustomAI = it }
                    )
                }

                // 自定义 AI 配置（仅在启用时显示）
                AnimatedVisibility(visible = useCustomAI) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // AI 提供商类型
                        var aiTypeExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = aiTypeExpanded,
                            onExpandedChange = { aiTypeExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = aiProviderType.replaceFirstChar { it.uppercase() },
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("AI 提供商") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = aiTypeExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                            )
                            ExposedDropdownMenu(
                                expanded = aiTypeExpanded,
                                onDismissRequest = { aiTypeExpanded = false }
                            ) {
                                listOf("openai", "anthropic", "gemini").forEach { type ->
                                    DropdownMenuItem(
                                        text = { Text(type.replaceFirstChar { it.uppercase() }) },
                                        onClick = {
                                            aiProviderType = type
                                            aiTypeExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // AI API Key
                        OutlinedTextField(
                            value = aiApiKey,
                            onValueChange = { aiApiKey = it },
                            label = { Text("AI API Key") },
                            visualTransformation = if (showAiApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showAiApiKey = !showAiApiKey }) {
                                    Icon(
                                        if (showAiApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        // AI API Base URL
                        OutlinedTextField(
                            value = aiApiBase,
                            onValueChange = { aiApiBase = it },
                            label = { Text("API Base URL (可选)") },
                            placeholder = { Text("留空使用默认") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        // AI Model
                        OutlinedTextField(
                            value = aiModel,
                            onValueChange = { aiModel = it },
                            label = { Text("模型名称") },
                            placeholder = { Text("如 gpt-4o, claude-3-5-sonnet-20241022") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // ===== 研究参数 =====
                Text(
                    text = "研究参数",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                // 搜索广度
                OutlinedTextField(
                    value = breadth,
                    onValueChange = { breadth = it },
                    label = { Text("搜索广度") },
                    supportingText = { Text("每层查询数量 (1-10)") },
                    modifier = Modifier.fillMaxWidth()
                )

                // 搜索深度
                OutlinedTextField(
                    value = maxDepth,
                    onValueChange = { maxDepth = it },
                    label = { Text("搜索深度") },
                    supportingText = { Text("递归层数 (1-5)") },
                    modifier = Modifier.fillMaxWidth()
                )

                // 输出语言
                var langExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = langExpanded,
                    onExpandedChange = { langExpanded = it }
                ) {
                    OutlinedTextField(
                        value = if (language == "zh") "中文" else "English",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("输出语言") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = langExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                    )
                    ExposedDropdownMenu(
                        expanded = langExpanded,
                        onDismissRequest = { langExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("中文") },
                            onClick = { language = "zh"; langExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("English") },
                            onClick = { language = "en"; langExpanded = false }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSettingsChange(settings.copy(
                    webSearchProvider = webSearchProvider,
                    webSearchApiKey = webSearchApiKey,
                    webSearchApiBase = webSearchApiBase.ifBlank { null },
                    tavilyAdvancedSearch = tavilyAdvancedSearch,
                    aiProviderType = if (useCustomAI) aiProviderType else "",
                    aiApiKey = if (useCustomAI) aiApiKey else "",
                    aiApiBase = if (useCustomAI) aiApiBase else "",
                    aiModel = if (useCustomAI) aiModel else "",
                    breadth = breadth.toIntOrNull()?.coerceIn(1, 10) ?: 3,
                    maxDepth = maxDepth.toIntOrNull()?.coerceIn(1, 5) ?: 2,
                    language = language,
                    searchLanguage = searchLanguage
                ))
                onDismiss()
            }) {
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

/**
 * 历史记录对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryDialog(
    historyList: List<DeepResearchHistory>,
    onHistoryClick: (DeepResearchHistory) -> Unit,
    onDeleteHistory: (DeepResearchHistory) -> Unit,
    onDeleteAllHistory: () -> Unit,
    onDismiss: () -> Unit
) {
    var showDeleteAllConfirm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("历史记录")
                if (historyList.isNotEmpty()) {
                    IconButton(onClick = { showDeleteAllConfirm = true }) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = "清空全部",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        text = {
            if (historyList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "暂无历史记录",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(historyList, key = { it.id }) { history ->
                        HistoryItem(
                            history = history,
                            onClick = { onHistoryClick(history) },
                            onDelete = { onDeleteHistory(history) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )

    // 确认删除全部对话框
    if (showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            title = { Text("确认清空") },
            text = { Text("确定要删除所有历史记录吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteAllHistory()
                        showDeleteAllConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除全部")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 历史记录项
 */
@Composable
private fun HistoryItem(
    history: DeepResearchHistory,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = history.query,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dateFormat.format(Date(history.endTime)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${history.learnings.size} 条来源",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
