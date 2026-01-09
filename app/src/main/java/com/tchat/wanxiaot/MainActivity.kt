package com.tchat.wanxiaot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tchat.data.MessageSender
import com.tchat.data.database.AppDatabase
import com.tchat.data.database.entity.AssistantEntity
import com.tchat.data.model.Assistant
import com.tchat.data.model.GroupChat
import com.tchat.data.model.LocalToolOption
import com.tchat.data.repository.impl.ChatRepositoryImpl
import com.tchat.data.repository.impl.KnowledgeRepositoryImpl
import com.tchat.data.repository.impl.GroupChatRepositoryImpl
import com.tchat.data.service.KnowledgeService
import com.tchat.data.tool.KnowledgeSearchTool
import com.tchat.data.tool.LocalTools
import com.tchat.data.tool.Tool
import com.tchat.data.mcp.McpToolService
import com.tchat.data.repository.impl.McpServerRepositoryImpl
import com.tchat.feature.chat.ChatScreen
import com.tchat.feature.chat.ChatViewModel
import com.tchat.feature.chat.GroupChatScreen
import com.tchat.feature.chat.GroupChatViewModel
import com.tchat.network.provider.AIProviderFactory
import com.tchat.network.provider.EmbeddingProviderFactory
import com.tchat.wanxiaot.settings.AIProviderType
import com.tchat.wanxiaot.settings.SettingsManager
import com.tchat.data.util.RegexRuleData
import com.tchat.wanxiaot.ui.DrawerContent
import com.tchat.wanxiaot.ui.settings.SettingsScreen
import com.tchat.wanxiaot.ui.deepresearch.DeepResearchScreen
import com.tchat.wanxiaot.ui.deepresearch.DeepResearchViewModel
import com.tchat.wanxiaot.ui.theme.TChatTheme
import com.tchat.wanxiaot.util.MultiKeyAIProvider
import com.tchat.data.deepresearch.DeepResearchManager
import com.tchat.data.deepresearch.ResearchState
import com.tchat.data.deepresearch.model.DeepResearchConfig
import com.tchat.data.deepresearch.repository.DeepResearchHistoryRepository
import com.tchat.data.deepresearch.repository.DeepResearchRepositoryFactory
import com.tchat.data.deepresearch.service.WebSearchServiceFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// 默认助手ID - 固定值，确保只创建一次
const val DEFAULT_ASSISTANT_ID = "default_assistant"

// 导航状态
private enum class MainNavState {
    CHAT,
    SETTINGS,
    DEEP_RESEARCH
}

class MainActivity : ComponentActivity() {
    // Application 级别的 CoroutineScope，不会因为 Activity 重建而取消
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var messageSender: MessageSender

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settingsManager = SettingsManager(this)
        val database = AppDatabase.getInstance(this)

        // 初始化 MessageSender 单例
        messageSender = MessageSender.getInstance(applicationScope)

        // 确保默认助手存在
        applicationScope.launch(Dispatchers.IO) {
            ensureDefaultAssistantExists(database, settingsManager)
        }

        setContent {
            TChatTheme {
                MainScreen(
                    settingsManager = settingsManager,
                    database = database,
                    messageSender = messageSender
                )
            }
        }
    }

    /**
     * 确保默认助手存在
     */
    private suspend fun ensureDefaultAssistantExists(
        database: AppDatabase,
        settingsManager: SettingsManager
    ) {
        val assistantDao = database.assistantDao()
        val existingAssistant = assistantDao.getAssistantById(DEFAULT_ASSISTANT_ID)

        if (existingAssistant == null) {
            // 创建默认助手
            val now = System.currentTimeMillis()
            val defaultAssistant = AssistantEntity(
                id = DEFAULT_ASSISTANT_ID,
                name = "默认助手",
                avatar = null,
                systemPrompt = "你是一个有帮助的AI助手。",
                temperature = null,
                topP = null,
                maxTokens = null,
                contextMessageSize = 64,
                streamOutput = true,
                localTools = "[]", // 空的工具列表
                createdAt = now,
                updatedAt = now
            )
            assistantDao.insertAssistant(defaultAssistant)
            println("Created default assistant")
        }

        // 如果当前没有选中的助手，则选中默认助手
        if (settingsManager.settings.first().currentAssistantId.isEmpty()) {
            settingsManager.setCurrentAssistant(DEFAULT_ASSISTANT_ID)
            println("Set default assistant as current")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    settingsManager: SettingsManager,
    database: AppDatabase,
    messageSender: MessageSender
) {
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val settings by settingsManager.settings.collectAsState()
    val currentProvider = settings.getCurrentProvider()

    val chatDao = database.chatDao()
    val messageDao = database.messageDao()
    val assistantDao = database.assistantDao()

    // 知识库相关
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

    // 群聊相关
    val groupChatRepository = remember(database) {
        GroupChatRepositoryImpl(
            database.groupChatDao(),
            database.assistantDao(),
            database.messageDao()
        )
    }

    // MCP 相关
    val mcpRepository = remember(database) {
        McpServerRepositoryImpl(database.mcpServerDao())
    }
    val mcpToolService = remember(mcpRepository) {
        McpToolService(mcpRepository)
    }

    // null表示新对话（懒创建模式）
    var currentChatId by remember { mutableStateOf<String?>(null) }
    var currentGroupChatId by remember { mutableStateOf<String?>(null) }
    var chatList by remember { mutableStateOf(emptyList<com.tchat.data.model.Chat>()) }
    var groupChatList by remember { mutableStateOf(emptyList<GroupChat>()) }
    var repository by remember { mutableStateOf<ChatRepositoryImpl?>(null) }
    var currentNavState by remember { mutableStateOf(MainNavState.CHAT) }
    var isDrawerRequested by remember { mutableStateOf(false) }

    // 当前助手
    var currentAssistant by remember { mutableStateOf<Assistant?>(null) }

    // 启用的工具集合（运行时状态，每次启动都重置为助手配置）
    var enabledTools by remember { mutableStateOf<Set<LocalToolOption>>(emptySet()) }

    // 深度研究状态
    val deepResearchSession by DeepResearchManager.currentSession.collectAsState()
    val isDeepResearching = deepResearchSession?.state is ResearchState.Researching ||
            deepResearchSession?.state is ResearchState.GeneratingReport

    // 加载当前助手
    LaunchedEffect(settings.currentAssistantId) {
        if (settings.currentAssistantId.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                val entity = assistantDao.getAssistantById(settings.currentAssistantId)
                entity?.let {
                    currentAssistant = entityToAssistant(it)
                    // 初始化启用的工具为助手配置的工具
                    enabledTools = currentAssistant?.localTools?.toSet() ?: emptySet()
                }
            }
        }
    }

    // 同步 isDrawerRequested 和 drawerState - 监听 targetValue 以便滑动关闭时也能重置
    LaunchedEffect(drawerState.targetValue) {
        if (drawerState.targetValue == DrawerValue.Closed) {
            isDrawerRequested = false
        }
    }

    // 初始化 Repository（当服务商或模型改变时重新创建）
    val activeModel = settings.getActiveModel()
    val providerTypeKey = currentProvider?.providerType
    val endpointKey = currentProvider?.endpoint
    val apiKeyKey = currentProvider?.apiKey
    val multiKeyEnabledKey = currentProvider?.multiKeyEnabled
    val apiKeysCountKey = currentProvider?.apiKeys?.size ?: 0

    LaunchedEffect(
        settings.currentProviderId,
        activeModel,
        providerTypeKey,
        endpointKey,
        apiKeyKey,
        multiKeyEnabledKey,
        apiKeysCountKey
    ) {
        try {
            val hasCredential = currentProvider != null && (
                currentProvider.apiKey.isNotBlank() ||
                    (currentProvider.multiKeyEnabled && currentProvider.apiKeys.isNotEmpty())
                )

            if (currentProvider != null && hasCredential) {
                println("=== 当前服务商 ===")
                println("Name: ${currentProvider.name}")
                println("Type: ${currentProvider.providerType.displayName}")
                if (currentProvider.multiKeyEnabled && currentProvider.apiKeys.isNotEmpty()) {
                    val enabledCount = currentProvider.apiKeys.count { it.isEnabled && it.status == com.tchat.wanxiaot.settings.ApiKeyStatus.ACTIVE }
                    println("API Keys: ${currentProvider.apiKeys.size} (enabled: $enabledCount)")
                } else {
                    println("API Key: ${currentProvider.apiKey.take(10)}...")
                }
                println("Endpoint: ${currentProvider.endpoint}")
                println("Model: $activeModel")
                println("==================")

                val selectedModel = activeModel

                val mappedType = when (currentProvider.providerType) {
                    AIProviderType.OPENAI -> AIProviderFactory.ProviderType.OPENAI
                    AIProviderType.ANTHROPIC -> AIProviderFactory.ProviderType.ANTHROPIC
                    AIProviderType.GEMINI -> AIProviderFactory.ProviderType.GEMINI
                }

                val aiProvider = if (currentProvider.multiKeyEnabled && currentProvider.apiKeys.isNotEmpty()) {
                    MultiKeyAIProvider(
                        settingsManager = settingsManager,
                        providerId = currentProvider.id,
                        providerType = mappedType,
                        baseUrl = currentProvider.endpoint,
                        model = selectedModel
                    )
                } else {
                    AIProviderFactory.create(
                        AIProviderFactory.ProviderConfig(
                            type = mappedType,
                            apiKey = currentProvider.apiKey,
                            baseUrl = currentProvider.endpoint,
                            model = selectedModel
                        )
                    )
                }

                val newRepo = ChatRepositoryImpl(aiProvider, chatDao, messageDao)
                repository = newRepo
                // 初始化 MessageSender 的 repository
                messageSender.init(newRepo)
            } else {
                println("No valid provider configured")
                repository = null
            }
        } catch (e: Exception) {
            println("Error creating provider: ${e.message}")
            e.printStackTrace()
        }
    }

    // 监听聊天列表（只更新列表，不自动选择聊天）
    LaunchedEffect(repository) {
        repository?.let { repo ->
            repo.getAllChats().collect { chats ->
                chatList = chats
            }
        }
    }

    // 监听群聊列表
    LaunchedEffect(groupChatRepository) {
        groupChatRepository.getAllGroups().collect { groups ->
            groupChatList = groups
        }
    }

    // 主页面切换动画
    AnimatedContent(
        targetState = currentNavState,
        transitionSpec = {
            val animationDuration = 200
            if (targetState != MainNavState.CHAT) {
                // 进入其他页面：从右边滑入，主页面向左滑出
                slideInHorizontally(
                    animationSpec = tween(animationDuration),
                    initialOffsetX = { it }
                ) togetherWith slideOutHorizontally(
                    animationSpec = tween(animationDuration),
                    targetOffsetX = { -it }
                )
            } else {
                // 返回主页面：从左边滑入，其他页面向右滑出
                slideInHorizontally(
                    animationSpec = tween(animationDuration),
                    initialOffsetX = { -it }
                ) togetherWith slideOutHorizontally(
                    animationSpec = tween(animationDuration),
                    targetOffsetX = { it }
                )
            }
        },
        label = "main_nav_transition"
    ) { navState ->
        when (navState) {
            MainNavState.SETTINGS -> {
                SettingsScreen(
                    settingsManager = settingsManager,
                    database = database,
                    onBack = { currentNavState = MainNavState.CHAT }
                )
            }
            MainNavState.DEEP_RESEARCH -> {
                val historyRepository = remember(database) {
                    DeepResearchHistoryRepository(database.deepResearchHistoryDao())
                }
                val deepResearchViewModel = remember(settingsManager, historyRepository) {
                    DeepResearchViewModel(settingsManager, historyRepository)
                }
                DeepResearchScreen(
                    viewModel = deepResearchViewModel,
                    onBack = { currentNavState = MainNavState.CHAT },
                    // 服务商和模型选择
                    providers = settings.providers,
                    currentProviderId = settings.currentProviderId,
                    currentProviderName = currentProvider?.name ?: "未配置",
                    availableModels = currentProvider?.availableModels ?: emptyList(),
                    currentModel = settings.getActiveModel(),
                    onProviderSelected = { providerId ->
                        settingsManager.setCurrentProvider(providerId)
                    },
                    onModelSelected = { model ->
                        settingsManager.setCurrentModel(model)
                    }
                )
            }
            MainNavState.CHAT -> {

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        scrimColor = Color.Transparent, // 禁用默认遮罩，使用自定义遮罩
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(280.dp)
            ) {
                DrawerContent(
                    chats = chatList,
                    groupChats = groupChatList,
                    currentChatId = currentChatId,
                    currentGroupChatId = currentGroupChatId,
                    currentProviderName = currentProvider?.name ?: "未配置",
                    currentProviderId = settings.currentProviderId,
                    providers = settings.providers,
                    onChatSelected = { chatId ->
                        currentChatId = chatId
                        currentGroupChatId = null // 切换到单聊模式
                        scope.launch { drawerState.close() }
                    },
                    onGroupChatSelected = { groupChatId ->
                        currentGroupChatId = groupChatId
                        currentChatId = null // 切换到群聊模式
                        scope.launch { drawerState.close() }
                    },
                    onNewChat = {
                        // 新对话：设置为null进入懒创建模式
                        currentChatId = null
                        currentGroupChatId = null
                        scope.launch { drawerState.close() }
                    },
                    onDeleteChat = { chatId ->
                        scope.launch {
                            repository?.deleteChat(chatId)
                            if (currentChatId == chatId) {
                                // 删除当前聊天后，进入新对话模式
                                currentChatId = null
                            }
                        }
                    },
                    onSettingsClick = {
                        scope.launch { drawerState.close() }
                        currentNavState = MainNavState.SETTINGS
                    },
                    onProviderSelected = { providerId ->
                        settingsManager.setCurrentProvider(providerId)
                    }
                )
            }
        }
    ) {
        // 自定义遮罩层 - 使用本地状态立即响应
        val scrimAlpha by animateFloatAsState(
            targetValue = if (isDrawerRequested) 0.32f else 0f,
            label = "scrim"
        )

        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = when {
                                        currentGroupChatId != null -> {
                                            groupChatList.find { it.id == currentGroupChatId }?.name ?: "群聊"
                                        }
                                        else -> currentAssistant?.name ?: "AI 聊天"
                                    }
                                )
                                Text(
                                    text = "${currentProvider?.name ?: "未配置"} > ${settings.getActiveModel()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                isDrawerRequested = true
                                scope.launch { drawerState.open() }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "菜单"
                                )
                            }
                        }
                    )
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        // 群聊模式
                        currentGroupChatId != null && repository != null -> {
                            val groupChatViewModel = remember(repository, groupChatRepository, currentGroupChatId) {
                                GroupChatViewModel(repository!!, groupChatRepository, messageSender)
                            }

                            // 加载助手列表
                            val groupChat = groupChatList.find { it.id == currentGroupChatId }
                            val groupAssistants = remember(groupChat?.memberIds, assistantDao) {
                                val memberIds = groupChat?.memberIds ?: emptyList()
                                if (memberIds.isEmpty()) {
                                    emptyList()
                                } else {
                                    // 同步加载助手信息
                                    memberIds.mapNotNull { memberId ->
                                        kotlinx.coroutines.runBlocking {
                                            assistantDao.getAssistantById(memberId)?.let { entityToAssistant(it) }
                                        }
                                    }
                                }
                            }

                            // 创建本地工具实例
                            val context = LocalContext.current
                            val localTools = remember(context) {
                                LocalTools(context)
                            }

                            GroupChatScreen(
                                viewModel = groupChatViewModel,
                                groupChatId = currentGroupChatId!!,
                                chatId = currentChatId,
                                modifier = Modifier.fillMaxSize(),
                                availableModels = currentProvider?.availableModels ?: emptyList(),
                                currentModel = settings.getActiveModel(),
                                onModelSelected = { model ->
                                    settingsManager.setCurrentModel(model)
                                },
                                providerIcon = currentProvider?.providerType?.icon?.invoke(),
                                enabledTools = enabledTools,
                                onToolToggle = { tool, enabled ->
                                    enabledTools = if (enabled) {
                                        enabledTools + tool
                                    } else {
                                        enabledTools - tool
                                    }
                                },
                                getToolsForOptions = { options ->
                                    localTools.getToolsForOptions(options)
                                },
                                extraTools = emptyList(),
                                systemPrompt = null,
                                regexRules = emptyList(),
                                providerId = settings.currentProviderId,
                                shouldRecordTokens = settings.tokenRecordingStatus == com.tchat.wanxiaot.settings.TokenRecordingStatus.ENABLED,
                                assistants = groupAssistants,
                                onDeepResearch = { query ->
                                    if (isDeepResearching) {
                                        // 深度研究进行中，直接导航到深度研究页面查看进度
                                        currentNavState = MainNavState.DEEP_RESEARCH
                                    } else if (query != null) {
                                        startDeepResearch(
                                            query = query,
                                            settingsManager = settingsManager,
                                            viewModel = ChatViewModel(repository!!, messageSender),
                                            chatId = currentChatId
                                        )
                                    } else {
                                        currentNavState = MainNavState.DEEP_RESEARCH
                                    }
                                },
                                isDeepResearching = isDeepResearching
                            )
                        }
                        // 单聊模式
                        repository != null -> {
                            val viewModel = remember(repository) {
                                ChatViewModel(repository!!, messageSender)
                            }

                            // 监听actualChatId变化，同步到currentChatId
                            val actualChatId by viewModel.actualChatId.collectAsState()
                            LaunchedEffect(actualChatId) {
                                if (actualChatId != null && currentChatId == null) {
                                    // 懒创建完成，更新currentChatId
                                    currentChatId = actualChatId
                                }
                            }

                            // 创建本地工具实例
                            val context = LocalContext.current
                            val localTools = remember(context) {
                                LocalTools(context)
                            }

                            // 计算知识库搜索工具（当 currentAssistant 变化时重新计算）
                            val knowledgeTools = remember(currentAssistant?.knowledgeBaseId, knowledgeService, knowledgeRepository) {
                                currentAssistant?.knowledgeBaseId?.let { kbId ->
                                    println("=== 知识库工具已启用 ===")
                                    println("知识库ID: $kbId")
                                    println("助手: ${currentAssistant?.name}")
                                    listOf(
                                        KnowledgeSearchTool.create(
                                            knowledgeService = knowledgeService,
                                            repository = knowledgeRepository,
                                            getEmbeddingProvider = { knowledgeBaseId ->
                                                // 从知识库配置获取 Embedding Provider
                                                getEmbeddingProviderForKnowledgeBase(
                                                    knowledgeBaseId = knowledgeBaseId,
                                                    knowledgeRepository = knowledgeRepository,
                                                    settingsManager = settingsManager
                                                )
                                            },
                                            knowledgeBaseId = kbId
                                        )
                                    )
                                } ?: run {
                                    println("=== 未绑定知识库 ===")
                                    emptyList()
                                }
                            }

                            // 计算 MCP 工具（当 currentAssistant 变化时重新计算）
                            var mcpTools by remember { mutableStateOf<List<Tool>>(emptyList()) }
                            LaunchedEffect(currentAssistant?.mcpServerIds) {
                                val serverIds = currentAssistant?.mcpServerIds ?: emptyList()
                                if (serverIds.isNotEmpty()) {
                                    println("=== MCP 工具加载中 ===")
                                    println("服务器IDs: $serverIds")
                                    mcpTools = mcpToolService.getToolsForServers(serverIds)
                                    println("已加载 ${mcpTools.size} 个 MCP 工具")
                                } else {
                                    mcpTools = emptyList()
                                }
                            }

                            // 合并所有额外工具
                            val allExtraTools = remember(knowledgeTools, mcpTools) {
                                knowledgeTools + mcpTools
                            }

                            // 计算启用的正则规则
                            val enabledRegexRules = remember(currentAssistant?.enabledRegexRuleIds, settings.regexRules) {
                                val enabledIds = currentAssistant?.enabledRegexRuleIds ?: emptyList()
                                if (enabledIds.isEmpty()) {
                                    emptyList()
                                } else {
                                    settings.regexRules
                                        .filter { it.id in enabledIds && it.isEnabled }
                                        .sortedBy { it.order }
                                        .map { rule ->
                                            RegexRuleData(
                                                pattern = rule.pattern,
                                                replacement = rule.replacement,
                                                isEnabled = true,
                                                order = rule.order
                                            )
                                        }
                                }
                            }

                            ChatScreen(
                                viewModel = viewModel,
                                chatId = currentChatId,
                                modifier = Modifier.fillMaxSize(),
                                // 使用当前服务商的模型列表
                                availableModels = currentProvider?.availableModels ?: emptyList(),
                                currentModel = settings.getActiveModel(),
                                onModelSelected = { model ->
                                    settingsManager.setCurrentModel(model)
                                },
                                providerIcon = currentProvider?.providerType?.icon?.invoke(),
                                // 本地工具支持
                                enabledTools = enabledTools,
                                onToolToggle = { tool, enabled ->
                                    enabledTools = if (enabled) {
                                        enabledTools + tool
                                    } else {
                                        enabledTools - tool
                                    }
                                },
                                getToolsForOptions = { options ->
                                    localTools.getToolsForOptions(options)
                                },
                                // 知识库搜索工具作为额外工具传递
                                extraTools = allExtraTools,
                                systemPrompt = currentAssistant?.systemPrompt,
                                // 正则规则
                                regexRules = enabledRegexRules,
                                // 提供商ID和token记录设置
                                providerId = settings.currentProviderId,
                                shouldRecordTokens = settings.tokenRecordingStatus == com.tchat.wanxiaot.settings.TokenRecordingStatus.ENABLED,
                                // 深度研究支持
                                onDeepResearch = { query ->
                                    if (isDeepResearching) {
                                        // 深度研究进行中，直接导航到深度研究页面查看进度
                                        currentNavState = MainNavState.DEEP_RESEARCH
                                    } else if (query != null) {
                                        // 有输入内容，直接开始研究
                                        startDeepResearch(
                                            query = query,
                                            settingsManager = settingsManager,
                                            viewModel = viewModel,
                                            chatId = currentChatId
                                        )
                                    } else {
                                        // 没有输入，打开深度研究页面
                                        currentNavState = MainNavState.DEEP_RESEARCH
                                    }
                                },
                                isDeepResearching = isDeepResearching
                            )
                        }
                        repository == null -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("请先添加服务商配置")
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { currentNavState = MainNavState.SETTINGS }) {
                                    Text("打开设置")
                                }
                            }
                        }
                    }
                }
            }

            // 自定义遮罩 - 使用本地状态立即响应
            if (isDrawerRequested) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = scrimAlpha))
                        .pointerInput(Unit) {
                            detectTapGestures {
                                isDrawerRequested = false
                                scope.launch { drawerState.close() }
                            }
                        }
                )
            }
        }
    }
        }
        }
    }
}

/**
 * 将 AssistantEntity 转换为 Assistant
 */
private fun entityToAssistant(entity: AssistantEntity): Assistant {
    val toolOptions = try {
        val jsonArray = org.json.JSONArray(entity.localTools)
        (0 until jsonArray.length()).mapNotNull { i ->
            LocalToolOption.fromId(jsonArray.getString(i))
        }
    } catch (e: Exception) {
        emptyList()
    }

    val mcpIds: List<String> = try {
        val jsonArray = org.json.JSONArray(entity.mcpServerIds)
        (0 until jsonArray.length()).map { i ->
            jsonArray.getString(i)
        }
    } catch (e: Exception) {
        emptyList()
    }

    val regexRuleIds: List<String> = try {
        val jsonArray = org.json.JSONArray(entity.enabledRegexRuleIds)
        (0 until jsonArray.length()).map { i ->
            jsonArray.getString(i)
        }
    } catch (e: Exception) {
        emptyList()
    }

    return Assistant(
        id = entity.id,
        name = entity.name,
        avatar = entity.avatar,
        systemPrompt = entity.systemPrompt,
        temperature = entity.temperature,
        topP = entity.topP,
        maxTokens = entity.maxTokens,
        contextMessageSize = entity.contextMessageSize,
        streamOutput = entity.streamOutput,
        localTools = toolOptions,
        knowledgeBaseId = entity.knowledgeBaseId,
        mcpServerIds = mcpIds,
        enabledRegexRuleIds = regexRuleIds,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt
    )
}

/**
 * 根据知识库配置获取对应的 Embedding Provider
 * 知识库使用自己配置的 Embedding 服务商，与对话模型提供商独立
 */
private fun getEmbeddingProviderForKnowledgeBase(
    knowledgeBaseId: String,
    knowledgeRepository: KnowledgeRepositoryImpl,
    settingsManager: SettingsManager
): com.tchat.network.provider.EmbeddingProvider? {
    return try {
        // 获取知识库配置（同步方式，因为我们在工具执行时需要）
        val base = kotlinx.coroutines.runBlocking {
            knowledgeRepository.getBaseById(knowledgeBaseId)
        } ?: return null
        
        // 获取设置中的服务商配置
        val settings = settingsManager.settings.value
        val providerConfig = settings.providers.find { it.id == base.embeddingProviderId }
            ?: return null
        
        // 根据服务商类型创建 Embedding Provider
        val providerType = when (providerConfig.providerType) {
            AIProviderType.OPENAI -> EmbeddingProviderFactory.EmbeddingProviderType.OPENAI
            AIProviderType.GEMINI -> EmbeddingProviderFactory.EmbeddingProviderType.GEMINI
            else -> return null
        }
        
        EmbeddingProviderFactory.create(
            type = providerType,
            apiKey = providerConfig.apiKey,
            baseUrl = providerConfig.endpoint.ifBlank { null }
        )
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/**
 * 启动深度研究并将结果发送到聊天
 */
private fun startDeepResearch(
    query: String,
    settingsManager: SettingsManager,
    viewModel: ChatViewModel,
    chatId: String?
) {
    val settings = settingsManager.settings.value
    val deepResearchSettings = settings.deepResearchSettings

    // 检查搜索 API Key
    if (deepResearchSettings.webSearchApiKey.isBlank()) {
        return
    }

    // 获取 AI Provider
    val aiProvider = getAIProviderForDeepResearch(settingsManager) ?: return

    // 创建搜索服务
    val webSearchService = WebSearchServiceFactory.create(
        provider = deepResearchSettings.webSearchProvider,
        apiKey = deepResearchSettings.webSearchApiKey,
        baseUrl = deepResearchSettings.webSearchApiBase,
        advancedSearch = deepResearchSettings.tavilyAdvancedSearch,
        searchTopic = deepResearchSettings.tavilySearchTopic
    )

    // 创建 Repository
    val repository = DeepResearchRepositoryFactory.create(aiProvider, webSearchService)

    // 创建配置
    val config = DeepResearchConfig(
        breadth = deepResearchSettings.breadth,
        maxDepth = deepResearchSettings.maxDepth,
        maxSearchResults = deepResearchSettings.maxSearchResults,
        language = deepResearchSettings.language,
        searchLanguage = deepResearchSettings.searchLanguage,
        concurrencyLimit = deepResearchSettings.concurrencyLimit
    )

    // 启动研究
    DeepResearchManager.startResearch(query, repository, config)
}

/**
 * 获取深度研究使用的 AI Provider
 */
private fun getAIProviderForDeepResearch(
    settingsManager: SettingsManager
): com.tchat.network.provider.AIProvider? {
    val settings = settingsManager.settings.value
    val deepResearchSettings = settings.deepResearchSettings

    // 如果深度研究有自己的配置，使用它
    if (deepResearchSettings.aiApiKey.isNotBlank()) {
        return AIProviderFactory.create(
            providerType = deepResearchSettings.aiProviderType,
            apiKey = deepResearchSettings.aiApiKey,
            baseUrl = deepResearchSettings.aiApiBase,
            model = deepResearchSettings.aiModel
        )
    }

    // 否则使用默认服务商
    val defaultProviderId = settings.defaultProviderId
    val providerConfig = settings.providers.find { it.id == defaultProviderId }
        ?: settings.providers.firstOrNull()
        ?: return null

    return AIProviderFactory.create(
        providerType = providerConfig.providerType.name.lowercase(),
        apiKey = providerConfig.apiKey,
        baseUrl = providerConfig.endpoint.ifBlank { null },
        model = providerConfig.selectedModel.ifEmpty {
            providerConfig.availableModels.firstOrNull() ?: ""
        }
    )
}
