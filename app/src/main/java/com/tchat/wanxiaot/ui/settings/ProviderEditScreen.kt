package com.tchat.wanxiaot.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tchat.wanxiaot.settings.AIProviderType
import com.tchat.wanxiaot.settings.ProviderConfig
import com.tchat.wanxiaot.settings.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.UUID

/**
 * 服务商编辑页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderEditScreen(
    provider: ProviderConfig?,
    settingsManager: SettingsManager,
    onBack: () -> Unit,
    onSave: (ProviderConfig) -> Unit,
    onDelete: (() -> Unit)?
) {
    val isNew = provider == null

    var name by remember { mutableStateOf(provider?.name ?: "") }
    var providerType by remember { mutableStateOf(provider?.providerType ?: AIProviderType.OPENAI) }
    var apiKey by remember { mutableStateOf(provider?.apiKey ?: "") }
    var endpoint by remember { mutableStateOf(provider?.endpoint ?: providerType.defaultEndpoint) }
    var availableModels by remember {
        mutableStateOf(provider?.availableModels?.ifEmpty { providerType.defaultModels }
            ?: providerType.defaultModels)
    }
    var selectedModel by remember {
        mutableStateOf(provider?.selectedModel ?: availableModels.firstOrNull() ?: "")
    }
    var customModel by remember { mutableStateOf("") }

    var typeExpanded by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }
    var isFetchingModels by remember { mutableStateOf(false) }
    var fetchError by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val httpClient = remember { OkHttpClient() }

    // 删除确认对话框
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("删除服务商") },
            text = { Text("确定要删除 \"${name.ifEmpty { "未命名" }}\" 吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete?.invoke()
                        showDeleteDialog = false
                        onBack()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                FilledTonalButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "添加服务商" else "编辑服务商") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    if (!isNew && onDelete != null) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.error
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 名称
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("名称") },
                placeholder = { Text("例如：我的 OpenAI") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )

            // 服务商类型
            ExposedDropdownMenuBox(
                expanded = typeExpanded,
                onExpandedChange = { typeExpanded = !typeExpanded }
            ) {
                OutlinedTextField(
                    value = providerType.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("服务商类型") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    shape = MaterialTheme.shapes.medium
                )
                ExposedDropdownMenu(
                    expanded = typeExpanded,
                    onDismissRequest = { typeExpanded = false }
                ) {
                    AIProviderType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.displayName) },
                            onClick = {
                                providerType = type
                                endpoint = type.defaultEndpoint
                                availableModels = type.defaultModels
                                selectedModel = type.defaultModels.firstOrNull() ?: ""
                                typeExpanded = false
                            }
                        )
                    }
                }
            }

            // API Key
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key") },
                placeholder = { Text("sk-xxxxxxxxxxxxxxxxxxxxxxxx") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )

            // API 端点
            OutlinedTextField(
                value = endpoint,
                onValueChange = { endpoint = it },
                label = { Text("API 端点") },
                placeholder = { Text(providerType.defaultEndpoint) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )

            // 拉取模型按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "模型列表 (${availableModels.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            isFetchingModels = true
                            fetchError = null
                            try {
                                val models = fetchModelsFromApi(
                                    httpClient = httpClient,
                                    endpoint = endpoint,
                                    apiKey = apiKey,
                                    providerType = providerType
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
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("拉取模型")
                }
            }

            if (fetchError != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = fetchError!!,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // 模型选择
            if (availableModels.isNotEmpty()) {
                ExposedDropdownMenuBox(
                    expanded = modelExpanded,
                    onExpandedChange = { modelExpanded = !modelExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedModel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("选择模型") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        shape = MaterialTheme.shapes.medium
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
                                }
                            )
                        }
                    }
                }
            }

            // 手动输入模型
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = customModel,
                    onValueChange = { customModel = it },
                    label = { Text("手动输入模型") },
                    placeholder = { Text("输入模型名称") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )
                FilledTonalButton(
                    onClick = {
                        if (customModel.isNotBlank()) {
                            if (!availableModels.contains(customModel)) {
                                availableModels = availableModels + customModel
                            }
                            selectedModel = customModel
                            customModel = ""
                        }
                    },
                    enabled = customModel.isNotBlank()
                ) {
                    Text("添加")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 保存按钮
            Button(
                onClick = {
                    val newProvider = ProviderConfig(
                        id = provider?.id ?: UUID.randomUUID().toString(),
                        name = name.trim().ifEmpty { providerType.displayName },
                        providerType = providerType,
                        apiKey = apiKey.trim(),
                        endpoint = endpoint.trim(),
                        selectedModel = selectedModel,
                        availableModels = availableModels
                    )
                    onSave(newProvider)
                    // 如果是新添加的，设为当前使用
                    if (isNew) {
                        settingsManager.setCurrentProvider(newProvider.id)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = apiKey.isNotBlank() && endpoint.isNotBlank(),
                shape = MaterialTheme.shapes.large
            ) {
                Text(
                    if (isNew) "添加并使用" else "保存",
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            // 设为当前使用按钮（仅编辑时显示）
            if (!isNew) {
                OutlinedButton(
                    onClick = {
                        settingsManager.setCurrentProvider(provider!!.id)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large
                ) {
                    Text(
                        "设为当前使用",
                        modifier = Modifier.padding(vertical = 4.dp)
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
    httpClient: OkHttpClient,
    endpoint: String,
    apiKey: String,
    providerType: AIProviderType
): List<String> = withContext(Dispatchers.IO) {
    val url = when (providerType) {
        AIProviderType.GEMINI -> "$endpoint/models?key=$apiKey"
        else -> "${endpoint.trimEnd('/')}/models"
    }

    val requestBuilder = Request.Builder().url(url)

    when (providerType) {
        AIProviderType.OPENAI,
        AIProviderType.ANTHROPIC -> {
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

    val jsonObject = JSONObject(responseBody)
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
