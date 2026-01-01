package com.tchat.wanxiaot.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tchat.wanxiaot.settings.AIProviderType
import com.tchat.wanxiaot.settings.ProviderConfig
import com.tchat.wanxiaot.settings.SettingsManager
import com.tchat.wanxiaot.ui.components.QRCodeDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.UUID

/**
 * 服务商编辑页面 - Material You 设计
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
    var savedModels by remember { mutableStateOf(provider?.availableModels ?: emptyList()) }
    var selectedModel by remember {
        mutableStateOf(provider?.selectedModel ?: savedModels.firstOrNull() ?: "")
    }
    var customModel by remember { mutableStateOf("") }

    var fetchedModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var showModelPicker by remember { mutableStateOf(false) }

    var typeExpanded by remember { mutableStateOf(false) }
    var isFetchingModels by remember { mutableStateOf(false) }
    var fetchError by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showQRDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val httpClient = remember { OkHttpClient() }

    BackHandler { onBack() }

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
            text = { Text("确定要删除 \"${name.ifEmpty { "未命名" }}\" 吗？此操作不可撤销。") },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete?.invoke()
                        showDeleteDialog = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 二维码分享对话框
    if (showQRDialog && provider != null) {
        QRCodeDialog(
            provider = provider,
            onDismiss = { showQRDialog = false }
        )
    }

    // 模型选择器弹窗
    if (showModelPicker && fetchedModels.isNotEmpty()) {
        var selectedToAdd by remember { mutableStateOf(setOf<String>()) }
        
        AlertDialog(
            onDismissRequest = { showModelPicker = false },
            title = { Text("选择要添加的模型") },
            text = {
                Column(modifier = Modifier.heightIn(max = 400.dp)) {
                    Text(
                        text = "共 ${fetchedModels.size} 个可用模型",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    androidx.compose.foundation.lazy.LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(fetchedModels.size) { index ->
                            val model = fetchedModels[index]
                            val isAlreadySaved = savedModels.contains(model)
                            val isSelected = selectedToAdd.contains(model)
                            
                            Surface(
                                onClick = {
                                    if (!isAlreadySaved) {
                                        selectedToAdd = if (isSelected) {
                                            selectedToAdd - model
                                        } else {
                                            selectedToAdd + model
                                        }
                                    }
                                },
                                color = when {
                                    isAlreadySaved -> MaterialTheme.colorScheme.surfaceContainerHighest
                                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                                    else -> MaterialTheme.colorScheme.surface
                                },
                                shape = MaterialTheme.shapes.small,
                                enabled = !isAlreadySaved
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = model,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = when {
                                            isAlreadySaved -> MaterialTheme.colorScheme.onSurfaceVariant
                                            isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                            else -> MaterialTheme.colorScheme.onSurface
                                        },
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isAlreadySaved) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    } else if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        savedModels = savedModels + selectedToAdd.toList()
                        if (selectedModel.isEmpty() && savedModels.isNotEmpty()) {
                            selectedModel = savedModels.first()
                        }
                        showModelPicker = false
                    },
                    enabled = selectedToAdd.isNotEmpty()
                ) {
                    Text("添加 ${selectedToAdd.size} 个")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showModelPicker = false }) {
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (!isNew) {
                        IconButton(onClick = { showQRDialog = true }) {
                            Icon(Icons.Outlined.Share, contentDescription = "分享")
                        }
                        if (onDelete != null) {
                            IconButton(onClick = { showDeleteDialog = true }) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    val newProvider = ProviderConfig(
                        id = provider?.id ?: UUID.randomUUID().toString(),
                        name = name.trim().ifEmpty { providerType.displayName },
                        providerType = providerType,
                        apiKey = apiKey.trim(),
                        endpoint = endpoint.trim(),
                        selectedModel = selectedModel,
                        availableModels = savedModels
                    )
                    onSave(newProvider)
                    if (isNew) {
                        settingsManager.setCurrentProvider(newProvider.id)
                    }
                    onBack()
                },
                icon = { Icon(Icons.Default.Check, contentDescription = null) },
                text = { Text(if (isNew) "添加" else "保存") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                expanded = true
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 基本信息卡片
            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "基本信息",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("名称") },
                        placeholder = { Text("例如：我的 OpenAI") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    ExposedDropdownMenuBox(
                        expanded = typeExpanded,
                        onExpandedChange = { typeExpanded = !typeExpanded }
                    ) {
                        OutlinedTextField(
                            value = providerType.displayName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("服务商类型") },
                            leadingIcon = {
                                Icon(
                                    imageVector = providerType.icon(),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = typeExpanded,
                            onDismissRequest = { typeExpanded = false }
                        ) {
                            AIProviderType.entries.forEach { type ->
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(
                                            imageVector = type.icon(),
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = if (type == providerType)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    text = {
                                        Text(
                                            text = type.displayName,
                                            color = if (type == providerType)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        providerType = type
                                        endpoint = type.defaultEndpoint
                                        savedModels = type.defaultModels
                                        selectedModel = type.defaultModels.firstOrNull() ?: ""
                                        typeExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // API 配置卡片
            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "API 配置",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key") },
                        placeholder = { Text("sk-xxxxxxxxxxxxxxxxxxxxxxxx") },
                        supportingText = { Text("需要保密，请妥善保管") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = endpoint,
                        onValueChange = { endpoint = it },
                        label = { Text("API 端点") },
                        placeholder = { Text(providerType.defaultEndpoint) },
                        supportingText = { Text("留空使用默认端点") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            // 模型配置卡片
            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "模型配置",
                            style = MaterialTheme.typography.titleMedium,
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
                                        fetchedModels = models
                                        showModelPicker = true
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

                    Text(
                        text = "已保存 ${savedModels.size} 个模型",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // 已保存的模型列表
                    if (savedModels.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            savedModels.forEach { model ->
                                Surface(
                                    onClick = { selectedModel = model },
                                    color = if (model == selectedModel)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surfaceContainerLow,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            if (model == selectedModel) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Text(
                                                text = model,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (model == selectedModel)
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                else
                                                    MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                savedModels = savedModels - model
                                                if (selectedModel == model) {
                                                    selectedModel = savedModels.firstOrNull() ?: ""
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Outlined.Delete,
                                                contentDescription = "删除",
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "暂无保存的模型，请拉取或手动添加",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // 手动添加模型
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customModel,
                            onValueChange = { customModel = it },
                            label = { Text("手动添加") },
                            placeholder = { Text("模型名称") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        FilledIconButton(
                            onClick = {
                                if (customModel.isNotBlank()) {
                                    if (!savedModels.contains(customModel)) {
                                        savedModels = savedModels + customModel
                                    }
                                    selectedModel = customModel
                                    customModel = ""
                                }
                            },
                            enabled = customModel.isNotBlank()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "添加")
                        }
                    }
                }
            }

            // 底部留空给 FAB
            Spacer(modifier = Modifier.height(80.dp))
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
