package com.tchat.wanxiaot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tchat.data.repository.impl.ChatRepositoryImpl
import com.tchat.feature.chat.ChatScreen
import com.tchat.feature.chat.ChatViewModel
import com.tchat.network.provider.AIProviderFactory
import com.tchat.wanxiaot.settings.SettingsManager
import com.tchat.wanxiaot.ui.DrawerContent
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

    var currentChatId by remember { mutableStateOf<String?>(null) }
    var chatList by remember { mutableStateOf(emptyList<com.tchat.data.model.Chat>()) }
    var repository by remember { mutableStateOf<ChatRepositoryImpl?>(null) }
    var isLoading by remember { mutableStateOf(false) }  // 改为 false，不需要初始加载
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showSettingsPage by remember { mutableStateOf(false) }  // 新增：设置页面状态

    // 初始化 Repository
    LaunchedEffect(settings.apiKey, settings.endpoint, settings.provider, settings.selectedModel) {
        try {
            // 打印当前设置（调试用）
            println("=== 当前设置 ===")
            println("Provider: ${settings.provider.displayName}")
            println("API Key: ${settings.apiKey.take(10)}...")
            println("Endpoint: ${settings.endpoint}")
            println("Selected Model: ${settings.selectedModel}")
            println("Available Models: ${settings.availableModels}")
            println("================")

            // 只有在 API Key 不为空时才创建 Provider
            if (settings.apiKey.isNotBlank()) {
                val selectedModel = settings.selectedModel.ifEmpty {
                    settings.availableModels.firstOrNull() ?: ""
                }

                println("Creating Provider with model: $selectedModel")

                val providerConfig = AIProviderFactory.ProviderConfig(
                    type = when (settings.provider) {
                        com.tchat.wanxiaot.settings.AIProvider.OPENAI -> AIProviderFactory.ProviderType.OPENAI
                        com.tchat.wanxiaot.settings.AIProvider.ANTHROPIC -> AIProviderFactory.ProviderType.ANTHROPIC
                        com.tchat.wanxiaot.settings.AIProvider.GEMINI -> AIProviderFactory.ProviderType.GEMINI
                    },
                    apiKey = settings.apiKey,
                    baseUrl = settings.endpoint,
                    model = selectedModel
                )
                val aiProvider = AIProviderFactory.create(providerConfig)
                repository = ChatRepositoryImpl(aiProvider)
            } else {
                println("API Key is blank, not creating provider")
            }
        } catch (e: Exception) {
            // 如果创建失败，不更新 repository
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
                    // 创建初始聊天
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
        SettingsPage(
            settingsManager = settingsManager,
            onBack = { showSettingsPage = false }
        )
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = false,  // 禁用左滑手势
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(280.dp)  // 设置侧边栏宽度
            ) {
                DrawerContent(
                    chats = chatList,
                    currentChatId = currentChatId,
                    settings = settings,
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
                        showSettingsPage = true  // 打开设置页面
                    }
                )
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text("AI 聊天") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
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
                            Text("请先配置 API 设置")
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
    }
}

/**
 * 设置页面 - 主页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(
    settingsManager: SettingsManager,
    onBack: () -> Unit
) {
    val currentSettings by settingsManager.settings.collectAsState()
    var selectedProviderToEdit by remember { mutableStateOf<com.tchat.wanxiaot.settings.AIProvider?>(null) }

    // 如果选择了要编辑的提供商，显示提供商配置页面
    if (selectedProviderToEdit != null) {
        ProviderConfigPage(
            provider = selectedProviderToEdit!!,
            currentSettings = currentSettings,
            settingsManager = settingsManager,
            onBack = { selectedProviderToEdit = null }
        )
        return
    }

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
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 当前使用的提供商
            Text("当前服务商", style = MaterialTheme.typography.titleMedium)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = currentSettings.provider.displayName,
                        style = MaterialTheme.typography.titleLarge
                    )
                    if (currentSettings.selectedModel.isNotEmpty()) {
                        Text(
                            text = "模型: ${currentSettings.selectedModel}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (currentSettings.apiKey.isNotEmpty()) {
                        Text(
                            text = "API Key: ${currentSettings.apiKey.take(10)}...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 提供商列表
            Text("选择服务商", style = MaterialTheme.typography.titleMedium)

            com.tchat.wanxiaot.settings.AIProvider.entries.forEach { provider ->
                val isCurrentProvider = provider == currentSettings.provider
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedProviderToEdit = provider },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCurrentProvider)
                            MaterialTheme.colorScheme.secondaryContainer
                        else
                            MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = provider.displayName,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = provider.defaultEndpoint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isCurrentProvider) {
                            Text(
                                text = "当前使用",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 提供商配置页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderConfigPage(
    provider: com.tchat.wanxiaot.settings.AIProvider,
    currentSettings: com.tchat.wanxiaot.settings.AppSettings,
    settingsManager: SettingsManager,
    onBack: () -> Unit
) {
    // 如果是当前提供商，加载已保存的设置；否则使用默认值
    val isCurrentProvider = provider == currentSettings.provider

    var apiKey by remember {
        mutableStateOf(if (isCurrentProvider) currentSettings.apiKey else "")
    }
    var endpoint by remember {
        mutableStateOf(if (isCurrentProvider) currentSettings.endpoint else provider.defaultEndpoint)
    }
    var availableModels by remember {
        mutableStateOf(
            if (isCurrentProvider && currentSettings.availableModels.isNotEmpty())
                currentSettings.availableModels
            else
                provider.defaultModels
        )
    }
    var selectedModel by remember {
        mutableStateOf(
            if (isCurrentProvider && currentSettings.selectedModel.isNotEmpty())
                currentSettings.selectedModel
            else
                availableModels.firstOrNull() ?: ""
        )
    }
    var customModel by remember { mutableStateOf("") }

    var modelExpanded by remember { mutableStateOf(false) }
    var isFetchingModels by remember { mutableStateOf(false) }
    var fetchError by remember { mutableStateOf<String?>(null) }
    var saveSuccess by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val httpClient = remember { okhttp3.OkHttpClient() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(provider.displayName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // API Key 输入
            Text("API Key", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it; saveSuccess = false },
                placeholder = { Text("sk-xxxxxxxxxxxxxxxxxxxxxxxx") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // API 端点输入
            Text("API 端点", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = endpoint,
                onValueChange = { endpoint = it; saveSuccess = false },
                placeholder = { Text(provider.defaultEndpoint) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // 拉取模型按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "模型列表 (${availableModels.size})",
                    style = MaterialTheme.typography.titleMedium
                )

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            isFetchingModels = true
                            fetchError = null
                            try {
                                val models = fetchModelsFromApi(
                                    httpClient = httpClient,
                                    endpoint = endpoint,
                                    apiKey = apiKey,
                                    provider = provider
                                )
                                availableModels = models
                                if (models.isNotEmpty() && selectedModel.isEmpty()) {
                                    selectedModel = models.first()
                                }
                            } catch (e: Exception) {
                                fetchError = e.message ?: "拉取失败"
                            }
                            isFetchingModels = false
                        }
                    },
                    enabled = !isFetchingModels && apiKey.isNotBlank() && endpoint.isNotBlank()
                ) {
                    if (isFetchingModels) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("拉取模型")
                }
            }

            // 错误提示
            if (fetchError != null) {
                Text(
                    text = fetchError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // 模型选择下拉框
            if (availableModels.isNotEmpty()) {
                Text("选择模型", style = MaterialTheme.typography.titleMedium)
                ExposedDropdownMenuBox(
                    expanded = modelExpanded,
                    onExpandedChange = { modelExpanded = !modelExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedModel,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = modelExpanded,
                        onDismissRequest = { modelExpanded = false }
                    ) {
                        availableModels.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model) },
                                onClick = {
                                    selectedModel = model
                                    modelExpanded = false
                                    saveSuccess = false
                                }
                            )
                        }
                    }
                }
            }

            // 手动输入模型
            Text("或手动输入模型", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = customModel,
                    onValueChange = { customModel = it },
                    placeholder = { Text("输入模型名称，如 gpt-4o") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Button(
                    onClick = {
                        if (customModel.isNotBlank()) {
                            if (!availableModels.contains(customModel)) {
                                availableModels = availableModels + customModel
                            }
                            selectedModel = customModel
                            customModel = ""
                            saveSuccess = false
                        }
                    },
                    enabled = customModel.isNotBlank()
                ) {
                    Text("添加")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 保存并使用按钮
            Button(
                onClick = {
                    val newSettings = com.tchat.wanxiaot.settings.AppSettings(
                        provider = provider,
                        apiKey = apiKey.trim(),
                        endpoint = endpoint.trim(),
                        selectedModel = selectedModel,
                        availableModels = availableModels
                    )
                    settingsManager.updateSettings(newSettings)
                    saveSuccess = true
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = apiKey.isNotBlank() && endpoint.isNotBlank()
            ) {
                Text("保存并使用此服务商")
            }

            // 保存成功提示
            if (saveSuccess) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        text = "✓ 设置已保存，当前使用 ${provider.displayName}",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

/**
 * 从 API 拉取模型列表
 */
private suspend fun fetchModelsFromApi(
    httpClient: okhttp3.OkHttpClient,
    endpoint: String,
    apiKey: String,
    provider: com.tchat.wanxiaot.settings.AIProvider
): List<String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    val url = when (provider) {
        com.tchat.wanxiaot.settings.AIProvider.GEMINI -> "$endpoint/models?key=$apiKey"
        else -> "${endpoint.trimEnd('/')}/models"
    }

    val requestBuilder = okhttp3.Request.Builder().url(url)

    when (provider) {
        com.tchat.wanxiaot.settings.AIProvider.OPENAI,
        com.tchat.wanxiaot.settings.AIProvider.ANTHROPIC -> {
            if (apiKey.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $apiKey")
            }
        }
        else -> {}
    }

    val response = httpClient.newCall(requestBuilder.build()).execute()

    if (!response.isSuccessful) {
        throw Exception("HTTP ${response.code}: ${response.message}")
    }

    val responseBody = response.body?.string() ?: throw Exception("空响应")

    val jsonObject = org.json.JSONObject(responseBody)
    val dataArray = jsonObject.optJSONArray("data")
        ?: jsonObject.optJSONArray("models")
        ?: throw Exception("无效的响应格式")

    val models = mutableListOf<String>()
    for (i in 0 until dataArray.length()) {
        val modelObj = dataArray.getJSONObject(i)
        val modelId = modelObj.optString("id") ?: modelObj.optString("model") ?: continue

        if (modelId.contains("embedding", ignoreCase = true) ||
            modelId.contains("whisper", ignoreCase = true) ||
            modelId.contains("tts", ignoreCase = true) ||
            modelId.contains("dall-e", ignoreCase = true)) {
            continue
        }

        models.add(modelId)
    }

    if (models.isEmpty()) {
        throw Exception("未找到可用模型")
    }

    models
}
