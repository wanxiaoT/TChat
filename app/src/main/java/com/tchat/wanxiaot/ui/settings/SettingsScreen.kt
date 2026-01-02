package com.tchat.wanxiaot.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.BookOpen
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ScrollText
import com.composables.icons.lucide.Settings2
import com.tchat.data.database.AppDatabase
import com.tchat.data.repository.impl.AssistantRepositoryImpl
import com.tchat.data.repository.impl.KnowledgeRepositoryImpl
import com.tchat.data.service.KnowledgeService
import com.tchat.wanxiaot.settings.SettingsManager
import com.tchat.wanxiaot.ui.assistant.AssistantDetailScreen
import com.tchat.wanxiaot.ui.assistant.AssistantDetailViewModel
import com.tchat.wanxiaot.ui.assistant.AssistantScreen
import com.tchat.wanxiaot.ui.assistant.AssistantViewModel
import com.tchat.wanxiaot.ui.knowledge.KnowledgeDetailScreen
import com.tchat.wanxiaot.ui.knowledge.KnowledgeScreen
import com.tchat.wanxiaot.ui.knowledge.KnowledgeViewModel
import com.tchat.wanxiaot.ui.mcp.McpScreen
import com.tchat.wanxiaot.ui.mcp.McpViewModel
import com.tchat.data.repository.impl.McpServerRepositoryImpl

// 平板模式的最小宽度阈值
private val TABLET_MIN_WIDTH = 840.dp
// 平板模式下左侧列表的宽度
private val TABLET_LIST_WIDTH = 360.dp

/**
 * 设置子页面类型
 */
private sealed class SettingsSubPage {
    data object MAIN : SettingsSubPage()
    data object PROVIDERS : SettingsSubPage()
    data object ABOUT : SettingsSubPage()
    data object LOGCAT : SettingsSubPage()
    data object ASSISTANTS : SettingsSubPage()
    data class ASSISTANT_DETAIL(val id: String) : SettingsSubPage()
    data object KNOWLEDGE : SettingsSubPage()
    data class KNOWLEDGE_DETAIL(val id: String) : SettingsSubPage()
    data object MCP : SettingsSubPage()
    data object USAGE_STATS : SettingsSubPage()
}

/**
 * 设置页面 - 设置菜单
 * 支持平板双栏布局和手机单栏布局
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    database: AppDatabase,
    onBack: () -> Unit
) {
    var currentSubPage by remember { mutableStateOf<SettingsSubPage>(SettingsSubPage.MAIN) }

    // 创建助手Repository
    val assistantRepository = remember(database) {
        AssistantRepositoryImpl(database.assistantDao())
    }

    // 创建知识库Repository和Service
    val knowledgeRepository = remember(database) {
        KnowledgeRepositoryImpl(
            database.knowledgeBaseDao(),
            database.knowledgeItemDao(),
            database.knowledgeChunkDao()
        )
    }
    val knowledgeService = remember(knowledgeRepository) {
        KnowledgeService(knowledgeRepository)
    }

    // 创建MCP Repository
    val mcpRepository = remember(database) {
        McpServerRepositoryImpl(database.mcpServerDao())
    }

    // 使用 BoxWithConstraints 检测屏幕宽度
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTabletMode = maxWidth >= TABLET_MIN_WIDTH

        if (isTabletMode) {
            // 平板模式：左右分栏布局
            TabletSettingsLayout(
                currentSubPage = currentSubPage,
                onSubPageChange = { currentSubPage = it },
                onBack = onBack,
                settingsManager = settingsManager,
                database = database,
                assistantRepository = assistantRepository,
                knowledgeRepository = knowledgeRepository,
                knowledgeService = knowledgeService,
                mcpRepository = mcpRepository
            )
        } else {
            // 手机模式：单栏布局（保持原有逻辑）
            PhoneSettingsLayout(
                currentSubPage = currentSubPage,
                onSubPageChange = { currentSubPage = it },
                onBack = onBack,
                settingsManager = settingsManager,
                database = database,
                assistantRepository = assistantRepository,
                knowledgeRepository = knowledgeRepository,
                knowledgeService = knowledgeService,
                mcpRepository = mcpRepository
            )
        }
    }
}

/**
 * 平板模式设置布局 - 左右分栏
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabletSettingsLayout(
    currentSubPage: SettingsSubPage,
    onSubPageChange: (SettingsSubPage) -> Unit,
    onBack: () -> Unit,
    settingsManager: SettingsManager,
    database: AppDatabase,
    assistantRepository: AssistantRepositoryImpl,
    knowledgeRepository: KnowledgeRepositoryImpl,
    knowledgeService: KnowledgeService,
    mcpRepository: McpServerRepositoryImpl
) {
    // 处理系统返回键
    BackHandler {
        when (currentSubPage) {
            is SettingsSubPage.MAIN -> onBack()
            is SettingsSubPage.ASSISTANT_DETAIL -> onSubPageChange(SettingsSubPage.ASSISTANTS)
            is SettingsSubPage.KNOWLEDGE_DETAIL -> onSubPageChange(SettingsSubPage.KNOWLEDGE)
            else -> onSubPageChange(SettingsSubPage.MAIN)
        }
    }

    // 搜索状态
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showSearchBar = !showSearchBar }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "搜索"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 左侧：设置列表
            Surface(
                modifier = Modifier
                    .width(TABLET_LIST_WIDTH)
                    .fillMaxHeight(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 搜索栏
                    AnimatedVisibility(visible = showSearchBar) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            placeholder = { Text("搜索设置...") },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null)
                            },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium
                        )
                    }

                    SettingsListContent(
                        currentSubPage = currentSubPage,
                        searchQuery = searchQuery,
                        onProvidersClick = { onSubPageChange(SettingsSubPage.PROVIDERS) },
                        onAboutClick = { onSubPageChange(SettingsSubPage.ABOUT) },
                        onLogcatClick = { onSubPageChange(SettingsSubPage.LOGCAT) },
                        onAssistantsClick = { onSubPageChange(SettingsSubPage.ASSISTANTS) },
                        onKnowledgeClick = { onSubPageChange(SettingsSubPage.KNOWLEDGE) },
                        onMcpClick = { onSubPageChange(SettingsSubPage.MCP) },
                        onUsageStatsClick = { onSubPageChange(SettingsSubPage.USAGE_STATS) }
                    )
                }
            }

            // 分隔线
            HorizontalDivider(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // 右侧：详情内容
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                AnimatedContent(
                    targetState = currentSubPage,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(150))
                    },
                    label = "tablet_detail_transition"
                ) { page ->
                    when (page) {
                        is SettingsSubPage.MAIN -> {
                            // 主页面显示欢迎提示
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Lucide.Settings2,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "请从左侧选择设置项",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        is SettingsSubPage.PROVIDERS -> {
                            ProvidersScreen(
                                settingsManager = settingsManager,
                                onBack = { onSubPageChange(SettingsSubPage.MAIN) },
                                showTopBar = false
                            )
                        }
                        is SettingsSubPage.ABOUT -> {
                            AboutScreen(
                                onBack = { onSubPageChange(SettingsSubPage.MAIN) },
                                showTopBar = false
                            )
                        }
                        is SettingsSubPage.LOGCAT -> {
                            LogcatScreen(
                                onBack = { onSubPageChange(SettingsSubPage.MAIN) },
                                showTopBar = false
                            )
                        }
                        is SettingsSubPage.ASSISTANTS -> {
                            val viewModel = remember(assistantRepository) {
                                AssistantViewModel(assistantRepository)
                            }
                            AssistantScreen(
                                viewModel = viewModel,
                                onBack = { onSubPageChange(SettingsSubPage.MAIN) },
                                onAssistantClick = { id -> onSubPageChange(SettingsSubPage.ASSISTANT_DETAIL(id)) },
                                showTopBar = false
                            )
                        }
                        is SettingsSubPage.ASSISTANT_DETAIL -> {
                            val viewModel = remember(assistantRepository, knowledgeRepository, mcpRepository, page.id) {
                                AssistantDetailViewModel(assistantRepository, knowledgeRepository, mcpRepository, page.id)
                            }
                            AssistantDetailScreen(
                                viewModel = viewModel,
                                onBack = { onSubPageChange(SettingsSubPage.ASSISTANTS) },
                                showTopBar = false
                            )
                        }
                        is SettingsSubPage.KNOWLEDGE -> {
                            val viewModel = remember(knowledgeRepository, knowledgeService, settingsManager) {
                                KnowledgeViewModel(knowledgeRepository, knowledgeService, settingsManager)
                            }
                            KnowledgeScreen(
                                viewModel = viewModel,
                                onBack = { onSubPageChange(SettingsSubPage.MAIN) },
                                onBaseClick = { id -> onSubPageChange(SettingsSubPage.KNOWLEDGE_DETAIL(id)) },
                                showTopBar = false
                            )
                        }
                        is SettingsSubPage.KNOWLEDGE_DETAIL -> {
                            val viewModel = remember(knowledgeRepository, knowledgeService, settingsManager) {
                                KnowledgeViewModel(knowledgeRepository, knowledgeService, settingsManager)
                            }
                            KnowledgeDetailScreen(
                                baseId = page.id,
                                viewModel = viewModel,
                                onBack = { onSubPageChange(SettingsSubPage.KNOWLEDGE) },
                                showTopBar = false
                            )
                        }
                        is SettingsSubPage.MCP -> {
                            val viewModel = remember(mcpRepository) {
                                McpViewModel(mcpRepository)
                            }
                            McpScreen(
                                viewModel = viewModel,
                                onBack = { onSubPageChange(SettingsSubPage.MAIN) },
                                showTopBar = false
                            )
                        }
                        is SettingsSubPage.USAGE_STATS -> {
                            UsageStatsScreen(
                                messageDao = database.messageDao(),
                                onBack = { onSubPageChange(SettingsSubPage.MAIN) },
                                showTopBar = false
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 手机模式设置布局 - 单栏
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneSettingsLayout(
    currentSubPage: SettingsSubPage,
    onSubPageChange: (SettingsSubPage) -> Unit,
    onBack: () -> Unit,
    settingsManager: SettingsManager,
    database: AppDatabase,
    assistantRepository: AssistantRepositoryImpl,
    knowledgeRepository: KnowledgeRepositoryImpl,
    knowledgeService: KnowledgeService,
    mcpRepository: McpServerRepositoryImpl
) {
    // 处理系统返回键
    BackHandler {
        when (currentSubPage) {
            is SettingsSubPage.MAIN -> onBack()
            is SettingsSubPage.ASSISTANT_DETAIL -> onSubPageChange(SettingsSubPage.ASSISTANTS)
            is SettingsSubPage.KNOWLEDGE_DETAIL -> onSubPageChange(SettingsSubPage.KNOWLEDGE)
            else -> onSubPageChange(SettingsSubPage.MAIN)
        }
    }

    // 定义页面层级用于判断导航方向
    fun SettingsSubPage.level(): Int = when (this) {
        is SettingsSubPage.MAIN -> 0
        is SettingsSubPage.ASSISTANT_DETAIL, is SettingsSubPage.KNOWLEDGE_DETAIL -> 2
        else -> 1
    }

    AnimatedContent(
        targetState = currentSubPage,
        transitionSpec = {
            val animationDuration = 100
            val isNavigatingBack = targetState.level() < initialState.level()
            if (isNavigatingBack) {
                // 返回上级页面：从左边滑入，旧页面向右滑出
                slideInHorizontally(
                    animationSpec = tween(animationDuration),
                    initialOffsetX = { -it }
                ) togetherWith slideOutHorizontally(
                    animationSpec = tween(animationDuration),
                    targetOffsetX = { it }
                )
            } else {
                // 进入子页面：从右边滑入，旧页面向左滑出
                slideInHorizontally(
                    animationSpec = tween(animationDuration),
                    initialOffsetX = { it }
                ) togetherWith slideOutHorizontally(
                    animationSpec = tween(animationDuration),
                    targetOffsetX = { -it }
                )
            }
        },
        label = "settings_page_transition"
    ) { page ->
        when (page) {
            is SettingsSubPage.MAIN -> {
                SettingsMainContent(
                    onBack = onBack,
                    onProvidersClick = { onSubPageChange(SettingsSubPage.PROVIDERS) },
                    onAboutClick = { onSubPageChange(SettingsSubPage.ABOUT) },
                    onLogcatClick = { onSubPageChange(SettingsSubPage.LOGCAT) },
                    onAssistantsClick = { onSubPageChange(SettingsSubPage.ASSISTANTS) },
                    onKnowledgeClick = { onSubPageChange(SettingsSubPage.KNOWLEDGE) },
                    onMcpClick = { onSubPageChange(SettingsSubPage.MCP) },
                    onUsageStatsClick = { onSubPageChange(SettingsSubPage.USAGE_STATS) }
                )
            }
            is SettingsSubPage.PROVIDERS -> {
                ProvidersScreen(
                    settingsManager = settingsManager,
                    onBack = { onSubPageChange(SettingsSubPage.MAIN) }
                )
            }
            is SettingsSubPage.ABOUT -> {
                AboutScreen(onBack = { onSubPageChange(SettingsSubPage.MAIN) })
            }
            is SettingsSubPage.LOGCAT -> {
                LogcatScreen(onBack = { onSubPageChange(SettingsSubPage.MAIN) })
            }
            is SettingsSubPage.ASSISTANTS -> {
                val viewModel = remember(assistantRepository) {
                    AssistantViewModel(assistantRepository)
                }
                AssistantScreen(
                    viewModel = viewModel,
                    onBack = { onSubPageChange(SettingsSubPage.MAIN) },
                    onAssistantClick = { id -> onSubPageChange(SettingsSubPage.ASSISTANT_DETAIL(id)) }
                )
            }
            is SettingsSubPage.ASSISTANT_DETAIL -> {
                val viewModel = remember(assistantRepository, knowledgeRepository, mcpRepository, page.id) {
                    AssistantDetailViewModel(assistantRepository, knowledgeRepository, mcpRepository, page.id)
                }
                AssistantDetailScreen(
                    viewModel = viewModel,
                    onBack = { onSubPageChange(SettingsSubPage.ASSISTANTS) }
                )
            }
            is SettingsSubPage.KNOWLEDGE -> {
                val viewModel = remember(knowledgeRepository, knowledgeService, settingsManager) {
                    KnowledgeViewModel(knowledgeRepository, knowledgeService, settingsManager)
                }
                KnowledgeScreen(
                    viewModel = viewModel,
                    onBack = { onSubPageChange(SettingsSubPage.MAIN) },
                    onBaseClick = { id -> onSubPageChange(SettingsSubPage.KNOWLEDGE_DETAIL(id)) }
                )
            }
            is SettingsSubPage.KNOWLEDGE_DETAIL -> {
                val viewModel = remember(knowledgeRepository, knowledgeService, settingsManager) {
                    KnowledgeViewModel(knowledgeRepository, knowledgeService, settingsManager)
                }
                KnowledgeDetailScreen(
                    baseId = page.id,
                    viewModel = viewModel,
                    onBack = { onSubPageChange(SettingsSubPage.KNOWLEDGE) }
                )
            }
            is SettingsSubPage.MCP -> {
                val viewModel = remember(mcpRepository) {
                    McpViewModel(mcpRepository)
                }
                McpScreen(
                    viewModel = viewModel,
                    onBack = { onSubPageChange(SettingsSubPage.MAIN) }
                )
            }
            is SettingsSubPage.USAGE_STATS -> {
                UsageStatsScreen(
                    messageDao = database.messageDao(),
                    onBack = { onSubPageChange(SettingsSubPage.MAIN) }
                )
            }
        }
    }
}

/**
 * 设置主页面内容 - 卡片式设计
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsMainContent(
    onBack: () -> Unit,
    onProvidersClick: () -> Unit,
    onAboutClick: () -> Unit,
    onLogcatClick: () -> Unit,
    onAssistantsClick: () -> Unit,
    onKnowledgeClick: () -> Unit,
    onMcpClick: () -> Unit,
    onUsageStatsClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
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
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 通用设置分组标题
            Text(
                text = "通用",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )

            // 助手设置卡片
            OutlinedCard(
                onClick = onAssistantsClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "助手",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "管理AI助手和本地工具",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 服务商设置卡片
            OutlinedCard(
                onClick = onProvidersClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "服务商",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "管理 AI 服务商配置",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 知识库设置卡片
            OutlinedCard(
                onClick = onKnowledgeClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Lucide.BookOpen,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "知识库",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "管理RAG知识库和向量检索",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // MCP 服务器设置卡片
            OutlinedCard(
                onClick = onMcpClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Cloud,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "MCP 服务器",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "管理 MCP 工具服务器连接",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 其他分组标题
            Text(
                text = "其他",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )

            // 使用统计卡片
            OutlinedCard(
                onClick = onUsageStatsClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.BarChart,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "使用统计",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "查看 Token 和模型调用统计",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 日志查看卡片
            OutlinedCard(
                onClick = onLogcatClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Lucide.ScrollText,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "日志查看",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "查看应用运行日志",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 关于卡片
            OutlinedCard(
                onClick = onAboutClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "关于",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "版本信息与开发者",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 平板模式下的设置列表内容（带选中状态）
 */
@Composable
private fun SettingsListContent(
    currentSubPage: SettingsSubPage,
    searchQuery: String = "",
    onProvidersClick: () -> Unit,
    onAboutClick: () -> Unit,
    onLogcatClick: () -> Unit,
    onAssistantsClick: () -> Unit,
    onKnowledgeClick: () -> Unit,
    onMcpClick: () -> Unit,
    onUsageStatsClick: () -> Unit
) {
    // 设置项数据（不使用 Composable lambda）
    data class SettingsItemData(
        val id: String,
        val group: String,
        val title: String,
        val subtitle: String,
        val onClick: () -> Unit
    )

    val allItems = listOf(
        SettingsItemData(
            id = "assistants",
            group = "通用",
            title = "助手",
            subtitle = "管理AI助手和本地工具",
            onClick = onAssistantsClick
        ),
        SettingsItemData(
            id = "providers",
            group = "通用",
            title = "服务商",
            subtitle = "管理 AI 服务商配置",
            onClick = onProvidersClick
        ),
        SettingsItemData(
            id = "knowledge",
            group = "通用",
            title = "知识库",
            subtitle = "管理RAG知识库和向量检索",
            onClick = onKnowledgeClick
        ),
        SettingsItemData(
            id = "mcp",
            group = "通用",
            title = "MCP 服务器",
            subtitle = "管理 MCP 工具服务器连接",
            onClick = onMcpClick
        ),
        SettingsItemData(
            id = "usage_stats",
            group = "其他",
            title = "使用统计",
            subtitle = "查看 Token 和模型调用统计",
            onClick = onUsageStatsClick
        ),
        SettingsItemData(
            id = "logcat",
            group = "其他",
            title = "日志查看",
            subtitle = "查看应用运行日志",
            onClick = onLogcatClick
        ),
        SettingsItemData(
            id = "about",
            group = "其他",
            title = "关于",
            subtitle = "版本信息与开发者",
            onClick = onAboutClick
        )
    )

    // 根据搜索词过滤
    val filteredItems = if (searchQuery.isBlank()) {
        allItems
    } else {
        allItems.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.subtitle.contains(searchQuery, ignoreCase = true)
        }
    }

    // 按组分组（保持顺序）
    val groupOrder = listOf("通用", "其他")
    val groupedItems = filteredItems.groupBy { it.group }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (filteredItems.isEmpty()) {
            // 无搜索结果
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "未找到匹配的设置项",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            groupOrder.forEach { group ->
                val items = groupedItems[group] ?: return@forEach

                // 分组标题
                Text(
                    text = group,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )

                items.forEach { item ->
                    val isSelected = when (item.id) {
                        "assistants" -> currentSubPage is SettingsSubPage.ASSISTANTS || currentSubPage is SettingsSubPage.ASSISTANT_DETAIL
                        "providers" -> currentSubPage is SettingsSubPage.PROVIDERS
                        "knowledge" -> currentSubPage is SettingsSubPage.KNOWLEDGE || currentSubPage is SettingsSubPage.KNOWLEDGE_DETAIL
                        "mcp" -> currentSubPage is SettingsSubPage.MCP
                        "usage_stats" -> currentSubPage is SettingsSubPage.USAGE_STATS
                        "logcat" -> currentSubPage is SettingsSubPage.LOGCAT
                        "about" -> currentSubPage is SettingsSubPage.ABOUT
                        else -> false
                    }

                    when (item.id) {
                        "knowledge" -> SettingsListItem(
                            iconContent = {
                                Icon(
                                    Lucide.BookOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                                           else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            title = item.title,
                            subtitle = item.subtitle,
                            isSelected = isSelected,
                            onClick = item.onClick
                        )
                        "logcat" -> SettingsListItem(
                            iconContent = {
                                Icon(
                                    Lucide.ScrollText,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                                           else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            title = item.title,
                            subtitle = item.subtitle,
                            isSelected = isSelected,
                            onClick = item.onClick
                        )
                        else -> {
                            val icon = when (item.id) {
                                "assistants" -> Icons.Default.Person
                                "providers" -> Icons.Default.Settings
                                "mcp" -> Icons.Default.Cloud
                                "usage_stats" -> Icons.Default.BarChart
                                "about" -> Icons.Default.Info
                                else -> Icons.Default.Settings
                            }
                            SettingsListItem(
                                icon = icon,
                                title = item.title,
                                subtitle = item.subtitle,
                                isSelected = isSelected,
                                onClick = item.onClick
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

/**
 * 设置列表项（支持选中状态）
 */
@Composable
private fun SettingsListItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    iconContent: @Composable (() -> Unit)? = null,
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = containerColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (isSelected) contentColor else MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (iconContent != null) {
                iconContent()
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) contentColor.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = if (isSelected) contentColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
