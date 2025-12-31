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
import com.tchat.data.repository.impl.ChatRepositoryImpl
import com.tchat.feature.chat.ChatScreen
import com.tchat.feature.chat.ChatViewModel
import com.tchat.network.provider.AIProviderFactory
import com.tchat.wanxiaot.settings.AIProviderType
import com.tchat.wanxiaot.settings.SettingsManager
import com.tchat.wanxiaot.ui.DrawerContent
import com.tchat.wanxiaot.ui.settings.SettingsScreen
import com.tchat.wanxiaot.ui.theme.TChatTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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

    // null表示新对话（懒创建模式）
    var currentChatId by remember { mutableStateOf<String?>(null) }
    var chatList by remember { mutableStateOf(emptyList<com.tchat.data.model.Chat>()) }
    var repository by remember { mutableStateOf<ChatRepositoryImpl?>(null) }
    var showSettingsPage by remember { mutableStateOf(false) }
    var isDrawerRequested by remember { mutableStateOf(false) }

    // 同步 isDrawerRequested 和 drawerState - 监听 targetValue 以便滑动关闭时也能重置
    LaunchedEffect(drawerState.targetValue) {
        if (drawerState.targetValue == DrawerValue.Closed) {
            isDrawerRequested = false
        }
    }

    // 初始化 Repository（当服务商或模型改变时重新创建）
    val currentModel = settings.currentModel
    LaunchedEffect(currentProvider, currentModel) {
        try {
            if (currentProvider != null && currentProvider.apiKey.isNotBlank()) {
                println("=== 当前服务商 ===")
                println("Name: ${currentProvider.name}")
                println("Type: ${currentProvider.providerType.displayName}")
                println("API Key: ${currentProvider.apiKey.take(10)}...")
                println("Endpoint: ${currentProvider.endpoint}")
                println("Model: ${currentProvider.selectedModel}")
                println("==================")

                val selectedModel = settings.getActiveModel()

                val providerConfig = AIProviderFactory.ProviderConfig(
                    type = when (currentProvider.providerType) {
                        AIProviderType.OPENAI -> AIProviderFactory.ProviderType.OPENAI
                        AIProviderType.ANTHROPIC -> AIProviderFactory.ProviderType.ANTHROPIC
                        AIProviderType.GEMINI -> AIProviderFactory.ProviderType.GEMINI
                    },
                    apiKey = currentProvider.apiKey,
                    baseUrl = currentProvider.endpoint,
                    model = selectedModel
                )
                val aiProvider = AIProviderFactory.create(providerConfig)
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

    // 主页面与设置页面切换动画
    AnimatedContent(
        targetState = showSettingsPage,
        transitionSpec = {
            val animationDuration = 200
            if (targetState) {
                // 进入设置页面：从右边滑入，主页面向左滑出
                slideInHorizontally(
                    animationSpec = tween(animationDuration),
                    initialOffsetX = { it }
                ) togetherWith slideOutHorizontally(
                    animationSpec = tween(animationDuration),
                    targetOffsetX = { -it }
                )
            } else {
                // 返回主页面：从左边滑入，设置页面向右滑出
                slideInHorizontally(
                    animationSpec = tween(animationDuration),
                    initialOffsetX = { -it }
                ) togetherWith slideOutHorizontally(
                    animationSpec = tween(animationDuration),
                    targetOffsetX = { it }
                )
            }
        },
        label = "main_settings_transition"
    ) { isSettingsPage ->
        if (isSettingsPage) {
            SettingsScreen(
                settingsManager = settingsManager,
                onBack = { showSettingsPage = false }
            )
        } else {

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
                    currentChatId = currentChatId,
                    currentProviderName = currentProvider?.name ?: "未配置",
                    currentProviderId = settings.currentProviderId,
                    providers = settings.providers,
                    onChatSelected = { chatId ->
                        currentChatId = chatId
                        scope.launch { drawerState.close() }
                    },
                    onNewChat = {
                        // 新对话：设置为null进入懒创建模式
                        currentChatId = null
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
                        showSettingsPage = true
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
                        title = { Text("AI 聊天") },
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

                            ChatScreen(
                                viewModel = viewModel,
                                chatId = currentChatId,
                                modifier = Modifier.fillMaxSize(),
                                availableModels = currentProvider?.availableModels ?: emptyList(),
                                currentModel = settings.getActiveModel(),
                                onModelSelected = { model ->
                                    settingsManager.setCurrentModel(model)
                                },
                                providerIcon = currentProvider?.providerType?.icon?.invoke()
                            )
                        }
                        repository == null -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("请先添加服务商配置")
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { showSettingsPage = true }) {
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
