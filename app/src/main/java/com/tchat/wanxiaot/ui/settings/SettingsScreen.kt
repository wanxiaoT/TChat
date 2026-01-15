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
import androidx.compose.material3.*
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ChartColumn
import com.composables.icons.lucide.Bot
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Cloud
import com.composables.icons.lucide.CloudUpload
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Network
import com.composables.icons.lucide.Regex
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Server
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Telescope
import com.composables.icons.lucide.Volume2
import com.composables.icons.lucide.X
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.BookOpen
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.Globe
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ScrollText
import com.composables.icons.lucide.Settings2
import com.composables.icons.lucide.Users
import com.tchat.data.database.AppDatabase
import com.tchat.data.repository.impl.AssistantRepositoryImpl
import com.tchat.data.repository.impl.KnowledgeRepositoryImpl
import com.tchat.data.service.KnowledgeService
import com.tchat.data.tts.TtsService
import com.tchat.wanxiaot.settings.SettingsManager
import com.tchat.wanxiaot.i18n.Language
import com.tchat.wanxiaot.i18n.strings
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
import com.tchat.wanxiaot.ui.deepresearch.DeepResearchScreen
import com.tchat.wanxiaot.ui.deepresearch.DeepResearchViewModel
import com.tchat.data.deepresearch.repository.DeepResearchHistoryRepository
import com.tchat.data.repository.impl.GroupChatRepositoryImpl
import com.tchat.wanxiaot.ui.groupchat.GroupChatListScreen
import com.tchat.wanxiaot.ui.groupchat.CreateGroupChatScreen
import com.tchat.data.repository.impl.SkillRepositoryImpl
import com.tchat.wanxiaot.ui.skill.SkillScreen
import com.tchat.wanxiaot.ui.skill.SkillDetailScreen
import com.tchat.wanxiaot.ui.skill.SkillViewModel
import kotlinx.coroutines.launch

// 平板模式的最小宽度阈值
private val TABLET_MIN_WIDTH = 840.dp
// 平板模式下左侧列表的宽度
private val TABLET_LIST_WIDTH = 360.dp

/**
 * 设置项图标类型
 */
private sealed class SettingsIcon {
    data class Material(val icon: androidx.compose.ui.graphics.vector.ImageVector) : SettingsIcon()
    data class Lucide(val icon: androidx.compose.ui.graphics.vector.ImageVector) : SettingsIcon()
}

/**
 * 设置项数据模型 - 手机和平板共享
 */
private data class SettingsItemData(
    val id: String,
    val group: String,
    val title: String,
    val subtitle: String,
    val icon: SettingsIcon,
    val targetPage: SettingsSubPage
)

/**
 * 获取所有设置项列表 - 单一数据源
 * 修改此列表会同时影响手机和平板模式
 */
@Composable
private fun getAllSettingsItems(): List<SettingsItemData> = listOf(
    // 通用分组
    SettingsItemData(
        id = "assistants",
        group = strings.settingsGeneral,
        title = strings.settingsAssistants,
        subtitle = strings.settingsAssistantsDesc,
        icon = SettingsIcon.Lucide(Lucide.Bot),
        targetPage = SettingsSubPage.ASSISTANTS
    ),
    SettingsItemData(
        id = "group_chat",
        group = strings.settingsGeneral,
        title = strings.settingsGroupChat,
        subtitle = strings.settingsGroupChatDesc,
        icon = SettingsIcon.Lucide(Lucide.Users),
        targetPage = SettingsSubPage.GROUP_CHAT
    ),
    SettingsItemData(
        id = "providers",
        group = strings.settingsGeneral,
        title = strings.settingsProviders,
        subtitle = strings.settingsProvidersDesc,
        icon = SettingsIcon.Lucide(Lucide.Server),
        targetPage = SettingsSubPage.PROVIDERS
    ),
    SettingsItemData(
        id = "knowledge",
        group = strings.settingsGeneral,
        title = strings.settingsKnowledge,
        subtitle = strings.settingsKnowledgeDesc,
        icon = SettingsIcon.Lucide(Lucide.BookOpen),
        targetPage = SettingsSubPage.KNOWLEDGE
    ),
    SettingsItemData(
        id = "mcp",
        group = strings.settingsGeneral,
        title = strings.settingsMcp,
        subtitle = strings.settingsMcpDesc,
        icon = SettingsIcon.Lucide(Lucide.Cloud),
        targetPage = SettingsSubPage.MCP
    ),
    SettingsItemData(
        id = "deep_research",
        group = strings.settingsGeneral,
        title = strings.settingsDeepResearch,
        subtitle = strings.settingsDeepResearchDesc,
        icon = SettingsIcon.Lucide(Lucide.Telescope),
        targetPage = SettingsSubPage.DEEP_RESEARCH
    ),
    SettingsItemData(
        id = "regex_rules",
        group = strings.settingsGeneral,
        title = strings.settingsRegex,
        subtitle = strings.settingsRegexDesc,
        icon = SettingsIcon.Lucide(Lucide.Regex),
        targetPage = SettingsSubPage.REGEX_RULES
    ),
    SettingsItemData(
        id = "skills",
        group = strings.settingsGeneral,
        title = strings.settingsSkills,
        subtitle = strings.settingsSkillsDesc,
        icon = SettingsIcon.Lucide(Lucide.Sparkles),
        targetPage = SettingsSubPage.SKILLS
    ),
    SettingsItemData(
        id = "tts",
        group = strings.settingsGeneral,
        title = strings.settingsTts,
        subtitle = strings.settingsTtsDesc,
        icon = SettingsIcon.Lucide(Lucide.Volume2),
        targetPage = SettingsSubPage.TTS
    ),
    SettingsItemData(
        id = "ocr",
        group = strings.settingsGeneral,
        title = strings.settingsOcr,
        subtitle = strings.settingsOcrDesc,
        icon = SettingsIcon.Lucide(Lucide.Search),
        targetPage = SettingsSubPage.OCR
    ),
    // 其他分组
    SettingsItemData(
        id = "usage_stats",
        group = strings.settingsOther,
        title = strings.settingsUsageStats,
        subtitle = strings.settingsUsageStatsDesc,
        icon = SettingsIcon.Lucide(Lucide.ChartColumn),
        targetPage = SettingsSubPage.USAGE_STATS
    ),
    SettingsItemData(
        id = "export_import",
        group = strings.settingsOther,
        title = strings.settingsExportImport,
        subtitle = strings.settingsExportImportDesc,
        icon = SettingsIcon.Lucide(Lucide.Download),
        targetPage = SettingsSubPage.EXPORT_IMPORT
    ),
    SettingsItemData(
        id = "r2_settings",
        group = strings.settingsOther,
        title = strings.settingsCloudBackup,
        subtitle = strings.settingsCloudBackupDesc,
        icon = SettingsIcon.Lucide(Lucide.CloudUpload),
        targetPage = SettingsSubPage.R2_SETTINGS
    ),
    SettingsItemData(
        id = "logcat",
        group = strings.settingsOther,
        title = strings.settingsLogcat,
        subtitle = strings.settingsLogcatDesc,
        icon = SettingsIcon.Lucide(Lucide.ScrollText),
        targetPage = SettingsSubPage.LOGCAT
    ),
    SettingsItemData(
        id = "network_log",
        group = strings.settingsOther,
        title = strings.settingsNetworkLog,
        subtitle = strings.settingsNetworkLogDesc,
        icon = SettingsIcon.Lucide(Lucide.Network),
        targetPage = SettingsSubPage.NETWORK_LOG
    ),
    SettingsItemData(
        id = "about",
        group = strings.settingsOther,
        title = strings.settingsAbout,
        subtitle = strings.settingsAboutDesc,
        icon = SettingsIcon.Lucide(Lucide.Info),
        targetPage = SettingsSubPage.ABOUT
    ),
    SettingsItemData(
        id = "language",
        group = strings.settingsOther,
        title = strings.settingsLanguage,
        subtitle = strings.settingsLanguageDesc,
        icon = SettingsIcon.Lucide(Lucide.Globe),
        targetPage = SettingsSubPage.LANGUAGE
    )
)

/**
 * 检查设置项是否被选中
 */
private fun isSettingsItemSelected(itemId: String, currentSubPage: SettingsSubPage): Boolean {
    return when (itemId) {
        "assistants" -> currentSubPage is SettingsSubPage.ASSISTANTS || currentSubPage is SettingsSubPage.ASSISTANT_DETAIL
        "group_chat" -> currentSubPage is SettingsSubPage.GROUP_CHAT || currentSubPage is SettingsSubPage.CREATE_GROUP_CHAT || currentSubPage is SettingsSubPage.EDIT_GROUP_CHAT
        "providers" -> currentSubPage is SettingsSubPage.PROVIDERS
        "knowledge" -> currentSubPage is SettingsSubPage.KNOWLEDGE || currentSubPage is SettingsSubPage.KNOWLEDGE_DETAIL
        "mcp" -> currentSubPage is SettingsSubPage.MCP
        "deep_research" -> currentSubPage is SettingsSubPage.DEEP_RESEARCH
        "regex_rules" -> currentSubPage is SettingsSubPage.REGEX_RULES
        "skills" -> currentSubPage is SettingsSubPage.SKILLS || currentSubPage is SettingsSubPage.SKILL_DETAIL
        "tts" -> currentSubPage is SettingsSubPage.TTS
        "ocr" -> currentSubPage is SettingsSubPage.OCR
        "usage_stats" -> currentSubPage is SettingsSubPage.USAGE_STATS
        "export_import" -> currentSubPage is SettingsSubPage.EXPORT_IMPORT
        "r2_settings" -> currentSubPage is SettingsSubPage.R2_SETTINGS
        "logcat" -> currentSubPage is SettingsSubPage.LOGCAT
        "network_log" -> currentSubPage is SettingsSubPage.NETWORK_LOG
        "about" -> currentSubPage is SettingsSubPage.ABOUT
        "language" -> currentSubPage is SettingsSubPage.LANGUAGE
        else -> false
    }
}

/**
 * 设置子页面类型
 */
private sealed class SettingsSubPage {
    data object MAIN : SettingsSubPage()
    data object PROVIDERS : SettingsSubPage()
    data object ABOUT : SettingsSubPage()
    data object LOGCAT : SettingsSubPage()
    data object NETWORK_LOG : SettingsSubPage()
    data object ASSISTANTS : SettingsSubPage()
    data class ASSISTANT_DETAIL(val id: String) : SettingsSubPage()
    data object GROUP_CHAT : SettingsSubPage()
    data object CREATE_GROUP_CHAT : SettingsSubPage()
    data class EDIT_GROUP_CHAT(val id: String) : SettingsSubPage()
    data object KNOWLEDGE : SettingsSubPage()
    data class KNOWLEDGE_DETAIL(val id: String) : SettingsSubPage()
    data object MCP : SettingsSubPage()
    data object USAGE_STATS : SettingsSubPage()
    data object DEEP_RESEARCH : SettingsSubPage()
    data object REGEX_RULES : SettingsSubPage()
    data object EXPORT_IMPORT : SettingsSubPage()
    data object SKILLS : SettingsSubPage()
    data class SKILL_DETAIL(val id: String?) : SettingsSubPage()
    data object TTS : SettingsSubPage()
    data object OCR : SettingsSubPage()
    data object R2_SETTINGS : SettingsSubPage()
    data object LANGUAGE : SettingsSubPage()
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

    // 创建群聊Repository
    val groupChatRepository = remember(database) {
        GroupChatRepositoryImpl(
            database.groupChatDao(),
            database.assistantDao(),
            database.messageDao()
        )
    }

    // 创建技能Repository
    val skillRepository = remember(database) {
        SkillRepositoryImpl(database.skillDao())
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
                mcpRepository = mcpRepository,
                groupChatRepository = groupChatRepository,
                skillRepository = skillRepository
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
                mcpRepository = mcpRepository,
                groupChatRepository = groupChatRepository,
                skillRepository = skillRepository
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
    mcpRepository: McpServerRepositoryImpl,
    groupChatRepository: GroupChatRepositoryImpl,
    skillRepository: SkillRepositoryImpl
) {
    // 搜索状态
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    // 处理系统返回键
    BackHandler {
        when {
            showSearchBar -> {
                showSearchBar = false
                searchQuery = ""
            }
            currentSubPage is SettingsSubPage.MAIN -> onBack()
            currentSubPage is SettingsSubPage.ASSISTANT_DETAIL -> onSubPageChange(SettingsSubPage.ASSISTANTS)
            currentSubPage is SettingsSubPage.KNOWLEDGE_DETAIL -> onSubPageChange(SettingsSubPage.KNOWLEDGE)
            currentSubPage is SettingsSubPage.CREATE_GROUP_CHAT -> onSubPageChange(SettingsSubPage.GROUP_CHAT)
            currentSubPage is SettingsSubPage.EDIT_GROUP_CHAT -> onSubPageChange(SettingsSubPage.GROUP_CHAT)
            currentSubPage is SettingsSubPage.SKILL_DETAIL -> onSubPageChange(SettingsSubPage.SKILLS)
            else -> onSubPageChange(SettingsSubPage.MAIN)
        }
    }

    // 搜索框展开时自动获取焦点
    LaunchedEffect(showSearchBar) {
        if (showSearchBar) {
            focusRequester.requestFocus()
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // 左侧：设置列表（带自己的 TopAppBar）
        Scaffold(
            modifier = Modifier
                .width(TABLET_LIST_WIDTH)
                .fillMaxHeight(),
            topBar = {
                TopAppBar(
                    title = {
                        if (showSearchBar) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester),
                                placeholder = { Text(strings.settingsSearchHint) },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                )
                            )
                        } else {
                            Text(strings.settingsTitle)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (showSearchBar) {
                                showSearchBar = false
                                searchQuery = ""
                            } else {
                                onBack()
                            }
                        }) {
                            Icon(
                                imageVector = if (showSearchBar) Lucide.X else Lucide.ArrowLeft,
                                contentDescription = if (showSearchBar) strings.close else strings.back
                            )
                        }
                    },
                    actions = {
                        if (!showSearchBar) {
                            IconButton(onClick = { showSearchBar = true }) {
                                Icon(
                                    imageVector = Lucide.Search,
                                    contentDescription = strings.search
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.surface
        ) { innerPadding ->
            SettingsListContent(
                modifier = Modifier.padding(innerPadding),
                currentSubPage = currentSubPage,
                searchQuery = searchQuery,
                onSubPageChange = onSubPageChange
            )
        }

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
                                    text = strings.settingsSelectHint,
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
                    is SettingsSubPage.NETWORK_LOG -> {
                        NetworkLogScreen(
                            onBack = { onSubPageChange(SettingsSubPage.MAIN) }
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
                        val viewModel = remember(assistantRepository, knowledgeRepository, mcpRepository, settingsManager, page.id) {
                            AssistantDetailViewModel(assistantRepository, knowledgeRepository, mcpRepository, page.id, settingsManager)
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
                        val settings by settingsManager.settings.collectAsState()
                        UsageStatsScreen(
                            messageDao = database.messageDao(),
                            onBack = { onSubPageChange(SettingsSubPage.MAIN) },
                            showTopBar = false,
                            providers = settings.providers,
                            tokenRecordingStatus = settings.tokenRecordingStatus,
                            onTokenRecordingStatusChange = { status ->
                                settingsManager.updateTokenRecordingStatus(status)
                            },
                            onClearTokenStats = { }
                        )
                    }
                    is SettingsSubPage.DEEP_RESEARCH -> {
                        val historyRepository = remember(database) {
                            DeepResearchHistoryRepository(database.deepResearchHistoryDao())
                        }
                        val viewModel = remember(settingsManager, historyRepository) {
                            DeepResearchViewModel(settingsManager, historyRepository)
                        }
                        DeepResearchScreen(
                            viewModel = viewModel,
                            onBack = { onSubPageChange(SettingsSubPage.MAIN) },
                            showTopBar = false
                        )
                    }
                    is SettingsSubPage.REGEX_RULES -> {
                        RegexRulesScreen(
                            settingsManager = settingsManager,
                            onBack = { onSubPageChange(SettingsSubPage.MAIN) },
                            showTopBar = false
                        )
                    }
                    is SettingsSubPage.EXPORT_IMPORT -> {
                        ExportImportScreenEnhanced(
                            settingsManager = settingsManager,
                            onBackClick = { onSubPageChange(SettingsSubPage.MAIN) }
                        )
                    }
                    is SettingsSubPage.GROUP_CHAT -> {
                        val groups by groupChatRepository.getAllGroups().collectAsState(initial = emptyList())
                        GroupChatListScreen(
                            groups = groups,
                            onBackClick = { onSubPageChange(SettingsSubPage.MAIN) },
                            onGroupClick = { /* TODO: 进入群聊对话 */ },
                            onCreateGroup = { onSubPageChange(SettingsSubPage.CREATE_GROUP_CHAT) },
                            onEditGroup = { group -> onSubPageChange(SettingsSubPage.EDIT_GROUP_CHAT(group.id)) },
                            onDeleteGroup = { group ->
                                kotlinx.coroutines.MainScope().launch {
                                    groupChatRepository.deleteGroup(group.id)
                                }
                            }
                        )
                    }
                    is SettingsSubPage.CREATE_GROUP_CHAT -> {
                        val assistants by assistantRepository.getAllAssistants().collectAsState(initial = emptyList())
                        CreateGroupChatScreen(
                            availableAssistants = assistants,
                            onBackClick = { onSubPageChange(SettingsSubPage.GROUP_CHAT) },
                            onSave = { group, memberIds ->
                                kotlinx.coroutines.MainScope().launch {
                                    groupChatRepository.createGroup(group)
                                    onSubPageChange(SettingsSubPage.GROUP_CHAT)
                                }
                            }
                        )
                    }
                    is SettingsSubPage.EDIT_GROUP_CHAT -> {
                        val assistants by assistantRepository.getAllAssistants().collectAsState(initial = emptyList())
                        val editingGroup by groupChatRepository.getGroupByIdFlow(page.id).collectAsState(initial = null)
                        editingGroup?.let { group ->
                            CreateGroupChatScreen(
                                availableAssistants = assistants,
                                onBackClick = { onSubPageChange(SettingsSubPage.GROUP_CHAT) },
                                onSave = { updatedGroup, memberIds ->
                                    kotlinx.coroutines.MainScope().launch {
                                        // 更新群聊基本信息
                                        groupChatRepository.updateGroup(updatedGroup)

                                        // 更新成员列表：先删除所有成员，再添加新成员
                                        val oldMembers = groupChatRepository.getMembers(updatedGroup.id)
                                        oldMembers.forEach { member ->
                                            groupChatRepository.removeMember(updatedGroup.id, member.assistantId)
                                        }
                                        groupChatRepository.addMembers(updatedGroup.id, memberIds)

                                        onSubPageChange(SettingsSubPage.GROUP_CHAT)
                                    }
                                },
                                editingGroup = group
                            )
                        }
                    }
                    is SettingsSubPage.SKILLS -> {
                        val viewModel = remember(skillRepository) {
                            SkillViewModel(skillRepository)
                        }
                        SkillScreen(
                            viewModel = viewModel,
                            onBack = { onSubPageChange(SettingsSubPage.MAIN) },
                            onSkillClick = { id -> onSubPageChange(SettingsSubPage.SKILL_DETAIL(id)) },
                            onCreateSkill = { onSubPageChange(SettingsSubPage.SKILL_DETAIL(null)) },
                            showTopBar = true
                        )
                    }
                    is SettingsSubPage.SKILL_DETAIL -> {
                        val viewModel = remember(skillRepository) {
                            SkillViewModel(skillRepository)
                        }
                        val skills by viewModel.skills.collectAsState()
                        val skill = page.id?.let { id -> skills.find { it.id == id } }
                        SkillDetailScreen(
                            skill = skill,
                            onSave = { newSkill ->
                                if (page.id == null) {
                                    viewModel.createSkill(newSkill)
                                } else {
                                    viewModel.updateSkill(newSkill)
                                }
                            },
                            onDelete = { id -> viewModel.deleteSkill(id) },
                            onBack = { onSubPageChange(SettingsSubPage.SKILLS) },
                            showTopBar = true
                        )
                    }
                    is SettingsSubPage.TTS -> {
                        val settings by settingsManager.settings.collectAsState()
                        val ttsService = remember { TtsService.getInstanceOrNull() }
                        TtsSettingsScreen(
                            ttsSettings = settings.ttsSettings,
                            ttsService = ttsService,
                            onSettingsChange = { newTtsSettings ->
                                settingsManager.updateSettings(settings.copy(ttsSettings = newTtsSettings))
                                // 同步更新 TtsService 设置
                                ttsService?.updateSettings(
                                    rate = newTtsSettings.speechRate,
                                    pitchValue = newTtsSettings.pitch,
                                    locale = java.util.Locale.forLanguageTag(newTtsSettings.language)
                                )
                            },
                            onBack = { onSubPageChange(SettingsSubPage.MAIN) },
                            showTopBar = false
                        )
                    }
                    is SettingsSubPage.OCR -> {
                        OcrSettingsScreen(
                            settingsManager = settingsManager,
                            onBack = { onSubPageChange(SettingsSubPage.MAIN) },
                            showTopBar = false
                        )
                    }
                    is SettingsSubPage.R2_SETTINGS -> {
                        R2SettingsScreen(
                            settingsManager = settingsManager,
                            onBack = { onSubPageChange(SettingsSubPage.MAIN) }
                        )
                    }
                    is SettingsSubPage.LANGUAGE -> {
                        val settings by settingsManager.settings.collectAsState()
                        LanguageScreen(
                            currentLanguage = Language.fromCode(settings.language),
                            onLanguageSelected = { language ->
                                settingsManager.setLanguage(language.code)
                            },
                            onBack = { onSubPageChange(SettingsSubPage.MAIN) }
                        )
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
    mcpRepository: McpServerRepositoryImpl,
    groupChatRepository: GroupChatRepositoryImpl,
    skillRepository: SkillRepositoryImpl
) {
    // 处理系统返回键
    BackHandler {
        when (currentSubPage) {
            is SettingsSubPage.MAIN -> onBack()
            is SettingsSubPage.ASSISTANT_DETAIL -> onSubPageChange(SettingsSubPage.ASSISTANTS)
            is SettingsSubPage.KNOWLEDGE_DETAIL -> onSubPageChange(SettingsSubPage.KNOWLEDGE)
            is SettingsSubPage.CREATE_GROUP_CHAT -> onSubPageChange(SettingsSubPage.GROUP_CHAT)
            is SettingsSubPage.EDIT_GROUP_CHAT -> onSubPageChange(SettingsSubPage.GROUP_CHAT)
            is SettingsSubPage.SKILL_DETAIL -> onSubPageChange(SettingsSubPage.SKILLS)
            else -> onSubPageChange(SettingsSubPage.MAIN)
        }
    }

    // 定义页面层级用于判断导航方向
    fun SettingsSubPage.level(): Int = when (this) {
        is SettingsSubPage.MAIN -> 0
        is SettingsSubPage.ASSISTANT_DETAIL, is SettingsSubPage.KNOWLEDGE_DETAIL,
        is SettingsSubPage.CREATE_GROUP_CHAT, is SettingsSubPage.EDIT_GROUP_CHAT,
        is SettingsSubPage.SKILL_DETAIL -> 2
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
                    onSubPageChange = onSubPageChange
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
            is SettingsSubPage.NETWORK_LOG -> {
                NetworkLogScreen(onBack = { onSubPageChange(SettingsSubPage.MAIN) })
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
                val viewModel = remember(assistantRepository, knowledgeRepository, mcpRepository, settingsManager, page.id) {
                    AssistantDetailViewModel(assistantRepository, knowledgeRepository, mcpRepository, page.id, settingsManager)
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
                val settings by settingsManager.settings.collectAsState()
                UsageStatsScreen(
                    messageDao = database.messageDao(),
                    onBack = { onSubPageChange(SettingsSubPage.MAIN) },
                    providers = settings.providers,
                    tokenRecordingStatus = settings.tokenRecordingStatus,
                    onTokenRecordingStatusChange = { status ->
                        settingsManager.updateTokenRecordingStatus(status)
                    },
                    onClearTokenStats = { }
                )
            }
            is SettingsSubPage.DEEP_RESEARCH -> {
                val historyRepository = remember(database) {
                    DeepResearchHistoryRepository(database.deepResearchHistoryDao())
                }
                val viewModel = remember(settingsManager, historyRepository) {
                    DeepResearchViewModel(settingsManager, historyRepository)
                }
                DeepResearchScreen(
                    viewModel = viewModel,
                    onBack = { onSubPageChange(SettingsSubPage.MAIN) }
                )
            }
            is SettingsSubPage.REGEX_RULES -> {
                RegexRulesScreen(
                    settingsManager = settingsManager,
                    onBack = { onSubPageChange(SettingsSubPage.MAIN) }
                )
            }
            is SettingsSubPage.EXPORT_IMPORT -> {
                ExportImportScreenEnhanced(
                    settingsManager = settingsManager,
                    onBackClick = { onSubPageChange(SettingsSubPage.MAIN) },
                    showTopBar = false
                )
            }
            is SettingsSubPage.GROUP_CHAT -> {
                val groups by groupChatRepository.getAllGroups().collectAsState(initial = emptyList())
                GroupChatListScreen(
                    groups = groups,
                    onBackClick = { onSubPageChange(SettingsSubPage.MAIN) },
                    onGroupClick = { /* TODO: 进入群聊对话 */ },
                    onCreateGroup = { onSubPageChange(SettingsSubPage.CREATE_GROUP_CHAT) },
                    onEditGroup = { group -> onSubPageChange(SettingsSubPage.EDIT_GROUP_CHAT(group.id)) },
                    onDeleteGroup = { group ->
                        kotlinx.coroutines.MainScope().launch {
                            groupChatRepository.deleteGroup(group.id)
                        }
                    }
                )
            }
            is SettingsSubPage.CREATE_GROUP_CHAT -> {
                val assistants by assistantRepository.getAllAssistants().collectAsState(initial = emptyList())
                CreateGroupChatScreen(
                    availableAssistants = assistants,
                    onBackClick = { onSubPageChange(SettingsSubPage.GROUP_CHAT) },
                    onSave = { group, memberIds ->
                        kotlinx.coroutines.MainScope().launch {
                            groupChatRepository.createGroup(group)
                            onSubPageChange(SettingsSubPage.GROUP_CHAT)
                        }
                    }
                )
            }
            is SettingsSubPage.EDIT_GROUP_CHAT -> {
                val assistants by assistantRepository.getAllAssistants().collectAsState(initial = emptyList())
                val editingGroup by groupChatRepository.getGroupByIdFlow(page.id).collectAsState(initial = null)
                editingGroup?.let { group ->
                    CreateGroupChatScreen(
                        availableAssistants = assistants,
                        onBackClick = { onSubPageChange(SettingsSubPage.GROUP_CHAT) },
                        onSave = { updatedGroup, memberIds ->
                            kotlinx.coroutines.MainScope().launch {
                                // 更新群聊基本信息
                                groupChatRepository.updateGroup(updatedGroup)

                                // 更新成员列表：先删除所有成员，再添加新成员
                                val oldMembers = groupChatRepository.getMembers(updatedGroup.id)
                                oldMembers.forEach { member ->
                                    groupChatRepository.removeMember(updatedGroup.id, member.assistantId)
                                }
                                groupChatRepository.addMembers(updatedGroup.id, memberIds)

                                onSubPageChange(SettingsSubPage.GROUP_CHAT)
                            }
                        },
                        editingGroup = group
                    )
                }
            }
            is SettingsSubPage.SKILLS -> {
                val viewModel = remember(skillRepository) {
                    SkillViewModel(skillRepository)
                }
                SkillScreen(
                    viewModel = viewModel,
                    onBack = { onSubPageChange(SettingsSubPage.MAIN) },
                    onSkillClick = { id -> onSubPageChange(SettingsSubPage.SKILL_DETAIL(id)) },
                    onCreateSkill = { onSubPageChange(SettingsSubPage.SKILL_DETAIL(null)) }
                )
            }
            is SettingsSubPage.SKILL_DETAIL -> {
                val viewModel = remember(skillRepository) {
                    SkillViewModel(skillRepository)
                }
                val skills by viewModel.skills.collectAsState()
                val skill = page.id?.let { id -> skills.find { it.id == id } }
                SkillDetailScreen(
                    skill = skill,
                    onSave = { newSkill ->
                        if (page.id == null) {
                            viewModel.createSkill(newSkill)
                        } else {
                            viewModel.updateSkill(newSkill)
                        }
                    },
                    onDelete = { id -> viewModel.deleteSkill(id) },
                    onBack = { onSubPageChange(SettingsSubPage.SKILLS) }
                )
            }
            is SettingsSubPage.TTS -> {
                val settings by settingsManager.settings.collectAsState()
                val ttsService = remember { TtsService.getInstanceOrNull() }
                TtsSettingsScreen(
                    ttsSettings = settings.ttsSettings,
                    ttsService = ttsService,
                    onSettingsChange = { newTtsSettings ->
                        settingsManager.updateSettings(settings.copy(ttsSettings = newTtsSettings))
                        // 同步更新 TtsService 设置
                        ttsService?.updateSettings(
                            rate = newTtsSettings.speechRate,
                            pitchValue = newTtsSettings.pitch,
                            locale = java.util.Locale.forLanguageTag(newTtsSettings.language)
                        )
                    },
                    onBack = { onSubPageChange(SettingsSubPage.MAIN) }
                )
            }
            is SettingsSubPage.OCR -> {
                OcrSettingsScreen(
                    settingsManager = settingsManager,
                    onBack = { onSubPageChange(SettingsSubPage.MAIN) }
                )
            }
            is SettingsSubPage.R2_SETTINGS -> {
                R2SettingsScreen(
                    settingsManager = settingsManager,
                    onBack = { onSubPageChange(SettingsSubPage.MAIN) }
                )
            }
            is SettingsSubPage.LANGUAGE -> {
                val settings by settingsManager.settings.collectAsState()
                LanguageScreen(
                    currentLanguage = Language.fromCode(settings.language),
                    onLanguageSelected = { language ->
                        settingsManager.setLanguage(language.code)
                    },
                    onBack = { onSubPageChange(SettingsSubPage.MAIN) }
                )
            }
        }
    }
}

/**
 * 设置主页面内容 - 卡片式设计（手机模式）
 * 使用共享数据源 getAllSettingsItems()
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsMainContent(
    onBack: () -> Unit,
    onSubPageChange: (SettingsSubPage) -> Unit
) {
    val allItems = getAllSettingsItems()
    val groupOrder = listOf(strings.settingsGeneral, strings.settingsOther)
    val groupedItems = allItems.groupBy { it.group }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.settingsTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Lucide.ArrowLeft,
                            contentDescription = strings.back
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
            groupOrder.forEach { group ->
                val items = groupedItems[group] ?: return@forEach

                // 分组标题
                Text(
                    text = group,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )

                // 设置项卡片
                items.forEach { item ->
                    SettingsCardItem(
                        item = item,
                        onClick = { onSubPageChange(item.targetPage) }
                    )
                }

                if (group != groupOrder.last()) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

/**
 * 设置卡片项（手机模式使用）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsCardItem(
    item: SettingsItemData,
    onClick: () -> Unit
) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (val icon = item.icon) {
                is SettingsIcon.Material -> Icon(
                    icon.icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                is SettingsIcon.Lucide -> Icon(
                    icon.icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                Lucide.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 平板模式下的设置列表内容（带选中状态）
 * 使用共享数据源 getAllSettingsItems()
 */
@Composable
private fun SettingsListContent(
    modifier: Modifier = Modifier,
    currentSubPage: SettingsSubPage,
    searchQuery: String = "",
    onSubPageChange: (SettingsSubPage) -> Unit
) {
    val allItems = getAllSettingsItems()

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
    val groupOrder = listOf(strings.settingsGeneral, strings.settingsOther)
    val groupedItems = filteredItems.groupBy { it.group }

    Column(
        modifier = modifier
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
                    text = strings.settingsNoResults,
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
                    val isSelected = isSettingsItemSelected(item.id, currentSubPage)
                    SettingsListItemFromData(
                        item = item,
                        isSelected = isSelected,
                        onClick = { onSubPageChange(item.targetPage) }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

/**
 * 设置列表项（平板模式使用，支持选中状态）
 */
@Composable
private fun SettingsListItemFromData(
    item: SettingsItemData,
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
    val iconTint = if (isSelected) contentColor else MaterialTheme.colorScheme.onSurfaceVariant

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
            when (val icon = item.icon) {
                is SettingsIcon.Material -> Icon(
                    icon.icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = iconTint
                )
                is SettingsIcon.Lucide -> Icon(
                    icon.icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = iconTint
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) contentColor.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                Lucide.ChevronRight,
                contentDescription = null,
                tint = if (isSelected) contentColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
