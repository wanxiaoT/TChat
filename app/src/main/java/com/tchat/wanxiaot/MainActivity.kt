package com.tchat.wanxiaot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.unit.dp
import com.tchat.data.repository.impl.ChatRepositoryImpl
import com.tchat.feature.chat.ChatScreen
import com.tchat.feature.chat.ChatViewModel
import com.tchat.network.provider.AIProviderFactory
import com.tchat.wanxiaot.settings.AIProviderType
import com.tchat.wanxiaot.settings.SettingsManager
import com.tchat.wanxiaot.ui.DrawerContent
import com.tchat.wanxiaot.ui.settings.SettingsScreen
import com.tchat.wanxiaot.ui.theme.TChatTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settingsManager = SettingsManager(this)

        setContent {
            TChatTheme {
                MainScreen(settingsManager = settingsManager)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(settingsManager: SettingsManager) {
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val settings by settingsManager.settings.collectAsState()
    val currentProvider = settings.getCurrentProvider()

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

    // 初始化 Repository
    LaunchedEffect(currentProvider) {
        try {
            if (currentProvider != null && currentProvider.apiKey.isNotBlank()) {
                println("=== 当前服务商 ===")
                println("Name: ${currentProvider.name}")
                println("Type: ${currentProvider.providerType.displayName}")
                println("API Key: ${currentProvider.apiKey.take(10)}...")
                println("Endpoint: ${currentProvider.endpoint}")
                println("Model: ${currentProvider.selectedModel}")
                println("==================")

                val selectedModel = currentProvider.selectedModel.ifEmpty {
                    currentProvider.availableModels.firstOrNull() ?: ""
                }

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
                repository = ChatRepositoryImpl(aiProvider)
            } else {
                println("No valid provider configured")
                repository = null
            }
        } catch (e: Exception) {
            println("Error creating provider: ${e.message}")
            e.printStackTrace()
        }
    }

    // 监听聊天列表
    LaunchedEffect(repository) {
        repository?.let { repo ->
            repo.getAllChats().collect { chats ->
                chatList = chats
                if (currentChatId == null && chats.isEmpty()) {
                    val result = repo.createChat("新对话")
                    if (result is com.tchat.core.util.Result.Success) {
                        currentChatId = result.data.id
                    }
                }
            }
        }
    }

    // 如果显示设置页面
    if (showSettingsPage) {
        SettingsScreen(
            settingsManager = settingsManager,
            onBack = { showSettingsPage = false }
        )
        return
    }

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
                    onChatSelected = { chatId ->
                        currentChatId = chatId
                        scope.launch { drawerState.close() }
                    },
                    onNewChat = {
                        scope.launch {
                            repository?.let { repo ->
                                val result = repo.createChat("新对话 ${chatList.size + 1}")
                                if (result is com.tchat.core.util.Result.Success) {
                                    currentChatId = result.data.id
                                }
                            }
                            drawerState.close()
                        }
                    },
                    onDeleteChat = { chatId ->
                        scope.launch {
                            repository?.deleteChat(chatId)
                            if (currentChatId == chatId) {
                                currentChatId = chatList.firstOrNull { it.id != chatId }?.id
                            }
                        }
                    },
                    onSettingsClick = {
                        scope.launch { drawerState.close() }
                        showSettingsPage = true
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
                        currentChatId != null && repository != null -> {
                            val viewModel = remember(repository) { ChatViewModel(repository!!) }
                            ChatScreen(
                                viewModel = viewModel,
                                chatId = currentChatId!!,
                                modifier = Modifier.fillMaxSize()
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
                        else -> {
                            Text("正在初始化...")
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
