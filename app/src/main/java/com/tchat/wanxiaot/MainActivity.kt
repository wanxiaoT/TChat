package com.tchat.wanxiaot

import android.os.Bundle
import android.util.Log
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tchat.data.MessageSender
import com.tchat.data.database.AppDatabase
import com.tchat.data.database.entity.AssistantEntity
import com.tchat.data.model.Assistant
import com.tchat.data.model.GroupChat
import com.tchat.data.model.GroupMessageMetadata
import com.tchat.data.model.LocalToolOption
import com.tchat.data.repository.ChatConfig
import com.tchat.data.repository.impl.ChatRepositoryImpl
import com.tchat.data.repository.impl.KnowledgeRepositoryImpl
import com.tchat.data.repository.impl.GroupChatRepositoryImpl
import com.tchat.data.service.KnowledgeService
import com.tchat.data.tool.KnowledgeSearchTool
import com.tchat.data.tool.LocalTools
import com.tchat.data.tool.Tool
import com.tchat.data.mcp.McpToolService
import com.tchat.data.repository.impl.McpServerRepositoryImpl
import java.io.File
import com.tchat.data.tts.TtsService
import com.tchat.data.tts.TtsEngineType as DataTtsEngineType
import com.tchat.feature.chat.ChatScreen
import com.tchat.feature.chat.ChatViewModel
import com.tchat.feature.chat.GroupChatScreen
import com.tchat.feature.chat.GroupChatViewModel
import com.tchat.network.provider.AIProviderFactory
import com.tchat.network.provider.EmbeddingProviderFactory
import com.tchat.wanxiaot.settings.AIProviderType
import com.tchat.wanxiaot.settings.SettingsManager
import com.tchat.wanxiaot.settings.TtsEngineType as AppTtsEngineType
import com.tchat.data.util.RegexRuleData
import com.tchat.wanxiaot.ui.DrawerContent
import com.tchat.wanxiaot.ui.settings.SettingsScreen
import com.tchat.wanxiaot.ui.deepresearch.DeepResearchScreen
import com.tchat.wanxiaot.ui.deepresearch.DeepResearchViewModel
import com.tchat.wanxiaot.ui.theme.TChatTheme
import com.tchat.wanxiaot.util.MultiKeyAIProvider
import com.tchat.wanxiaot.i18n.Language
import com.tchat.wanxiaot.i18n.strings
import com.tchat.wanxiaot.i18n.StringsZhCN
import com.tchat.wanxiaot.i18n.StringsZhTW
import com.tchat.wanxiaot.i18n.StringsEn
import com.tchat.data.deepresearch.DeepResearchManager
import com.tchat.data.deepresearch.ResearchState
import com.tchat.data.deepresearch.model.DeepResearchConfig
import com.tchat.data.deepresearch.repository.DeepResearchHistoryRepository
import com.tchat.data.deepresearch.repository.DeepResearchRepositoryFactory
import com.tchat.data.deepresearch.service.WebSearchServiceFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 默认助手ID - 固定值，确保只创建一次
const val DEFAULT_ASSISTANT_ID = "default_assistant"
private const val MAIN_ACTIVITY_TAG = "MainActivity"

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
    private lateinit var ttsService: TtsService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settingsManager = SettingsManager(this)
        val database = AppDatabase.getInstance(this)

        // 初始化 MessageSender 单例
        messageSender = MessageSender.getInstance(applicationScope)

        // 初始化 TTS 服务
        ttsService = TtsService.getInstance(this)

        // 同步 TTS 设置
        applicationScope.launch {
            settingsManager.settings.collect { settings ->
                val ttsSettings = settings.ttsSettings
                // 更新 MessageSender 的 TTS 设置
                messageSender.updateTtsSettings(
                    enabled = ttsSettings.enabled,
                    autoSpeak = ttsSettings.autoSpeak
                )
                // 更新 TtsService 的设置
                ttsService.updateSettings(
                    rate = ttsSettings.speechRate,
                    pitchValue = ttsSettings.pitch,
                    locale = java.util.Locale.forLanguageTag(ttsSettings.language)
                )
                // 同步引擎/配置（避免仅在设置页生效）
                ttsService.setEngine(ttsSettings.enginePackage)
                ttsService.setEngineType(
                    when (ttsSettings.engineType) {
                        AppTtsEngineType.SYSTEM -> DataTtsEngineType.SYSTEM
                        AppTtsEngineType.DOUBAO -> DataTtsEngineType.DOUBAO
                    }
                )
                ttsService.updateDoubaoConfig(
                    appId = ttsSettings.doubaoAppId,
                    accessToken = ttsSettings.doubaoAccessToken,
                    cluster = ttsSettings.doubaoCluster,
                    voiceType = ttsSettings.doubaoVoiceType
                )
            }
        }

        // 确保默认助手存在
        applicationScope.launch(Dispatchers.IO) {
            ensureDefaultAssistantExists(database, settingsManager)
        }

        setContent {
            val settings by settingsManager.settings.collectAsStateWithLifecycle()
            val language = Language.fromCode(settings.language)

            TChatTheme(
                language = language,
                dynamicColor = false
            ) {
                MainScreen(
                    settingsManager = settingsManager,
                    database = database,
                    messageSender = messageSender
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 释放 TTS 资源
        if (isFinishing) {
            ttsService.shutdown()
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
        val loadedSettings = settingsManager.awaitLoadedSettings()

        if (existingAssistant == null) {
            // 根据当前语言获取字符串
            val currentLanguage = Language.fromCode(loadedSettings.language)
            val actualLanguage = Language.getActualLanguage(currentLanguage)
            val localStrings = when (actualLanguage) {
                Language.ZH_CN -> StringsZhCN
                Language.ZH_TW -> StringsZhTW
                Language.EN -> StringsEn
                Language.SYSTEM -> StringsZhCN // 不会到达这里
            }

            // 创建默认助手
            val now = System.currentTimeMillis()
            val defaultAssistant = AssistantEntity(
                id = DEFAULT_ASSISTANT_ID,
                name = localStrings.assistantsDefault,
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
        }

        // 如果当前没有选中的助手，则选中默认助手
        if (loadedSettings.currentAssistantId.isEmpty()) {
            settingsManager.setCurrentAssistant(DEFAULT_ASSISTANT_ID)
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
    val settings by settingsManager.settings.collectAsStateWithLifecycle()
    val currentProvider = settings.getCurrentProvider()
    val context = LocalContext.current
    val chatMediaDir = remember(context) { File(context.filesDir, "chat_media") }

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
    var allChatList by remember { mutableStateOf(emptyList<com.tchat.data.model.Chat>()) }
    var groupChatList by remember { mutableStateOf(emptyList<GroupChat>()) }
    var repository by remember { mutableStateOf<ChatRepositoryImpl?>(null) }
    var currentNavState by remember { mutableStateOf(MainNavState.CHAT) }
    var isDrawerRequested by remember { mutableStateOf(false) }
    val chatList = remember(allChatList, groupChatList) {
        val activeGroupChatIds = groupChatList
            .mapNotNull { it.activeChatId }
            .toSet()
        allChatList.filterNot { chat -> chat.id in activeGroupChatIds }
    }

    // 当前助手
    var currentAssistant by remember { mutableStateOf<Assistant?>(null) }

    // 启用的工具集合（运行时状态，每次启动都重置为助手配置）
    var enabledTools by remember { mutableStateOf<Set<LocalToolOption>>(emptySet()) }

    // 深度研究状态
    val deepResearchSession by DeepResearchManager.currentSession.collectAsStateWithLifecycle()
    val isDeepResearching = deepResearchSession?.state is ResearchState.Researching ||
            deepResearchSession?.state is ResearchState.GeneratingReport

    // 加载当前助手
    LaunchedEffect(settings.currentAssistantId) {
        val resolvedAssistant = if (settings.currentAssistantId.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                assistantDao.getAssistantById(settings.currentAssistantId)?.let(::entityToAssistant)
            }
        } else {
            null
        }
        currentAssistant = resolvedAssistant
        enabledTools = resolvedAssistant?.localTools?.toSet() ?: emptySet()
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

                val newRepo = ChatRepositoryImpl(
                    aiProvider = aiProvider,
                    chatDao = chatDao,
                    messageDao = messageDao,
                    providerType = mappedType,
                    mediaDir = chatMediaDir
                )
                repository = newRepo
                // 初始化 MessageSender 的 repository
                messageSender.init(newRepo)
            } else {
                repository = null
            }
        } catch (e: Exception) {
            Log.e(MAIN_ACTIVITY_TAG, "Failed to create provider client", e)
            repository = null
        }
    }

    // 监听聊天列表（只更新列表，不自动选择聊天）
    LaunchedEffect(repository) {
        repository?.let { repo ->
            repo.getAllChats().collect { chats ->
                allChatList = chats
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
                modifier = Modifier.width(296.dp),
                drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerContentColor = MaterialTheme.colorScheme.onSurface
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
                        currentChatId = groupChatList.find { it.id == groupChatId }?.activeChatId
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
            targetValue = if (isDrawerRequested) 0.12f else 0f,
            label = "scrim"
        )

        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        title = {
                            Column {
                                Text(
                                    text = "当前会话",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                                )
                                Text(
                                    text = when {
                                        currentGroupChatId != null -> {
                                            groupChatList.find { it.id == currentGroupChatId }?.name ?: "群聊"
                                        }
                                        else -> currentAssistant?.name ?: "AI 聊天"
                                    },
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${currentProvider?.name ?: "未配置"} > ${settings.getActiveModel()}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        navigationIcon = {
                            Surface(
                                modifier = Modifier.padding(start = 8.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.26f)
                                ),
                                tonalElevation = 0.dp,
                                shadowElevation = 0.dp
                            ) {
                                IconButton(onClick = {
                                    isDrawerRequested = true
                                    scope.launch { drawerState.open() }
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = strings.chatMenu
                                    )
                                }
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
                            val actualGroupChatId by groupChatViewModel.actualChatId.collectAsStateWithLifecycle()

                            // 加载助手列表
                            val groupChat = groupChatList.find { it.id == currentGroupChatId }
                            var groupAssistants by remember(currentGroupChatId) {
                                mutableStateOf<List<Assistant>>(emptyList())
                            }
                            LaunchedEffect(groupChat?.memberIds) {
                                val memberIds = groupChat?.memberIds.orEmpty()
                                groupAssistants = if (memberIds.isEmpty()) {
                                    emptyList()
                                } else {
                                    withContext(Dispatchers.IO) {
                                        memberIds.mapNotNull { memberId ->
                                            assistantDao.getAssistantById(memberId)?.let(::entityToAssistant)
                                        }
                                    }
                                }
                            }

                            LaunchedEffect(actualGroupChatId, currentGroupChatId) {
                                if (currentGroupChatId != null &&
                                    actualGroupChatId != null &&
                                    currentChatId != actualGroupChatId
                                ) {
                                    currentChatId = actualGroupChatId
                                }
                            }

                            // 创建本地工具实例
                            val context = LocalContext.current
                            val localTools = remember(context) {
                                LocalTools(context)
                            }
                            var groupEnabledTools by remember(currentGroupChatId) {
                                mutableStateOf<Set<LocalToolOption>>(emptySet())
                            }
                            var groupAssistantConfigs by remember(currentGroupChatId) {
                                mutableStateOf<Map<String, ChatConfig>>(emptyMap())
                            }

                            LaunchedEffect(
                                groupChat?.id,
                                groupChat?.name,
                                groupAssistants,
                                settings.regexRules,
                                settings.currentProviderId,
                                settings.tokenRecordingStatus,
                                settings.getActiveModel()
                            ) {
                                groupAssistantConfigs = groupAssistants.associate { assistant ->
                                    val knowledgeTools = assistant.knowledgeBaseId?.let { kbId ->
                                        listOf(
                                            KnowledgeSearchTool.create(
                                                knowledgeService = knowledgeService,
                                                repository = knowledgeRepository,
                                                getEmbeddingProvider = { knowledgeBaseId ->
                                                    getEmbeddingProviderForKnowledgeBase(
                                                        knowledgeBaseId = knowledgeBaseId,
                                                        knowledgeRepository = knowledgeRepository,
                                                        settingsManager = settingsManager
                                                    )
                                                },
                                                knowledgeBaseId = kbId
                                            )
                                        )
                                    } ?: emptyList()

                                    val mcpTools = runCatching {
                                        if (assistant.mcpServerIds.isNotEmpty()) {
                                            mcpToolService.getToolsForServers(assistant.mcpServerIds)
                                        } else {
                                            emptyList()
                                        }
                                    }.getOrDefault(emptyList())

                                    val assistantRegexRules = settings.regexRules
                                        .filter { rule ->
                                            rule.id in assistant.enabledRegexRuleIds && rule.isEnabled
                                        }
                                        .sortedBy { it.order }
                                        .map { rule ->
                                            RegexRuleData(
                                                pattern = rule.pattern,
                                                replacement = rule.replacement,
                                                isEnabled = true,
                                                order = rule.order
                                            )
                                        }

                                    assistant.id to ChatConfig(
                                        systemPrompt = buildGroupAssistantSystemPrompt(
                                            groupName = groupChat?.name,
                                            assistant = assistant
                                        ),
                                        tools = (
                                            localTools.getToolsForOptions(assistant.localTools) +
                                                knowledgeTools +
                                                mcpTools
                                            ).distinctBy { it.name },
                                        temperature = assistant.temperature,
                                        topP = assistant.topP,
                                        maxTokens = assistant.maxTokens,
                                        modelName = settings.getActiveModel(),
                                        providerId = settings.currentProviderId,
                                        shouldRecordTokens = settings.tokenRecordingStatus ==
                                            com.tchat.wanxiaot.settings.TokenRecordingStatus.ENABLED,
                                        regexRules = assistantRegexRules,
                                        enabledSkillIds = assistant.enabledSkillIds,
                                        groupMetadata = groupChat?.let { group ->
                                            GroupMessageMetadata(
                                                groupId = group.id,
                                                assistantId = assistant.id,
                                                assistantName = assistant.name,
                                                activationStrategy = group.activationStrategy
                                            )
                                        }
                                    )
                                }
                            }

                            LaunchedEffect(groupAssistantConfigs, groupChatViewModel) {
                                groupChatViewModel.setAssistantConfigs(groupAssistantConfigs)
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
                                enabledTools = groupEnabledTools,
                                onToolToggle = { tool, enabled ->
                                    groupEnabledTools = if (enabled) {
                                        groupEnabledTools + tool
                                    } else {
                                        groupEnabledTools - tool
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
                                            settingsManager = settingsManager
                                        )
                                    } else {
                                        currentNavState = MainNavState.DEEP_RESEARCH
                                    }
                                },
                                isDeepResearching = isDeepResearching,
                                chatToolbarSettings = settings.chatToolbarSettings,
                                // i18n strings
                                inputHint = strings.chatInputHint,
                                sendContentDescription = strings.chatSendMessage,
                                toolsText = strings.chatTools,
                                toolsWithCountFormat = strings.chatToolsWithCount,
                                deepResearchText = strings.chatDeepResearch,
                                deepResearchRunningText = strings.chatDeepResearchRunning,
                                deepResearchInProgressText = strings.chatDeepResearchInProgress,
                                speakingAssistantLabel = strings.groupChatSpeakingAssistant,
                                selectAssistantHint = strings.groupChatSelectAssistant,
                                pleaseSelectAssistantFirst = strings.groupChatPleaseSelectAssistant
                            )
                        }
                        // 单聊模式
                        repository != null -> {
                            val viewModel = remember(repository) {
                                ChatViewModel(repository!!, messageSender)
                            }

                            // 监听actualChatId变化，同步到currentChatId
                            val actualChatId by viewModel.actualChatId.collectAsStateWithLifecycle()
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
                                } ?: emptyList()
                            }

                            // 计算 MCP 工具（当 currentAssistant 变化时重新计算）
                            var mcpTools by remember { mutableStateOf<List<Tool>>(emptyList()) }
                            LaunchedEffect(currentAssistant?.mcpServerIds) {
                                val serverIds = currentAssistant?.mcpServerIds ?: emptyList()
                                if (serverIds.isNotEmpty()) {
                                    mcpTools = mcpToolService.getToolsForServers(serverIds)
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
                                            settingsManager = settingsManager
                                        )
                                    } else {
                                        // 没有输入，打开深度研究页面
                                        currentNavState = MainNavState.DEEP_RESEARCH
                                    }
                                },
                                isDeepResearching = isDeepResearching,
                                // 打野助手
                                onJungleHelperClick = {
                                    if (!com.tchat.wanxiaot.junglehelper.JungleHelperManager.hasOverlayPermission(context)) {
                                        com.tchat.wanxiaot.junglehelper.JungleHelperManager.requestOverlayPermission(context)
                                    } else {
                                        com.tchat.wanxiaot.junglehelper.JungleHelperManager.toggle(context)
                                    }
                                },
                                chatToolbarSettings = settings.chatToolbarSettings,
                                // i18n strings
                                inputHint = strings.chatInputHint,
                                sendContentDescription = strings.chatSendMessage,
                                toolsText = strings.chatTools,
                                toolsWithCountFormat = strings.chatToolsWithCount,
                                deepResearchText = strings.chatDeepResearch,
                                deepResearchRunningText = strings.chatDeepResearchRunning,
                                deepResearchInProgressText = strings.chatDeepResearchInProgress
                            )
                        }
                        repository == null -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(strings.chatNoProvider)
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { currentNavState = MainNavState.SETTINGS }) {
                                    Text(strings.chatOpenSettings)
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
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = scrimAlpha))
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

    val skillIds: List<String> = try {
        val jsonArray = org.json.JSONArray(entity.enabledSkillIds)
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
        enabledSkillIds = skillIds,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt
    )
}

private fun buildGroupAssistantSystemPrompt(groupName: String?, assistant: Assistant): String {
    val assistantPrompt = assistant.systemPrompt.trim()
    val groupContext = buildString {
        if (!groupName.isNullOrBlank()) {
            append("你正在参与群聊“")
            append(groupName)
            append("”，当前发言身份是助手“")
            append(assistant.name)
            append("”。请严格保持该助手的人设、职责和语气。")
        } else {
            append("你当前发言身份是助手“")
            append(assistant.name)
            append("”。请严格保持该助手的人设、职责和语气。")
        }
    }

    return if (assistantPrompt.isBlank()) {
        groupContext
    } else {
        "$groupContext\n\n$assistantPrompt"
    }
}

/**
 * 根据知识库配置获取对应的 Embedding Provider
 * 知识库使用自己配置的 Embedding 服务商，与对话模型提供商独立
 */
private suspend fun getEmbeddingProviderForKnowledgeBase(
    knowledgeBaseId: String,
    knowledgeRepository: KnowledgeRepositoryImpl,
    settingsManager: SettingsManager
): com.tchat.network.provider.EmbeddingProvider? {
    return try {
        val base = knowledgeRepository.getBaseById(knowledgeBaseId) ?: return null
        
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
        Log.e(MAIN_ACTIVITY_TAG, "Failed to create embedding provider for knowledge base", e)
        null
    }
}

/**
 * 启动深度研究并将结果发送到聊天
 */
private fun startDeepResearch(
    query: String,
    settingsManager: SettingsManager
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
