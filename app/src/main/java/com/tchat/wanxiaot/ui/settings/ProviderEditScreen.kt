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
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tchat.wanxiaot.settings.AIProviderType
import com.tchat.wanxiaot.settings.ApiKeyEntry
import com.tchat.wanxiaot.settings.ApiKeyStatus
import com.tchat.wanxiaot.settings.KeySelectionStrategy
import com.tchat.wanxiaot.settings.ModelCustomParams
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

    // 多 Key 管理
    var apiKeys by remember { mutableStateOf(provider?.apiKeys ?: emptyList()) }
    var multiKeyEnabled by remember { mutableStateOf(provider?.multiKeyEnabled ?: false) }
    var keySelectionStrategy by remember { mutableStateOf(provider?.keySelectionStrategy ?: KeySelectionStrategy.ROUND_ROBIN) }
    var roundRobinIndex by remember { mutableStateOf(provider?.roundRobinIndex ?: 0) }
    var maxFailuresBeforeDisable by remember { mutableStateOf(provider?.maxFailuresBeforeDisable ?: 3) }
    var autoRecoveryMinutes by remember { mutableStateOf(provider?.autoRecoveryMinutes ?: 5) }

    // 多 Key UI 状态
    var showAddKeyDialog by remember { mutableStateOf(false) }
    var editingKey by remember { mutableStateOf<ApiKeyEntry?>(null) }
    var deletingKey by remember { mutableStateOf<ApiKeyEntry?>(null) }
    var strategyExpanded by remember { mutableStateOf(false) }
    var customModel by remember { mutableStateOf("") }

    var fetchedModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var showModelPicker by remember { mutableStateOf(false) }

    var typeExpanded by remember { mutableStateOf(false) }
    var isFetchingModels by remember { mutableStateOf(false) }
    var fetchError by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showQRDialog by remember { mutableStateOf(false) }
    var showModelParamsDialog by remember { mutableStateOf<String?>(null) }
    var modelCustomParams by remember { mutableStateOf(provider?.modelCustomParams ?: emptyMap()) }

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

    // ==================== 多 Key 管理对话框 ====================

    if (showAddKeyDialog) {
        var keysText by remember { mutableStateOf("") }
        var nameText by remember { mutableStateOf("") }
        var enabled by remember { mutableStateOf(true) }
        var priority by remember { mutableStateOf(5f) }
        var errorText by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showAddKeyDialog = false },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            title = { Text("添加 API Key") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = keysText,
                        onValueChange = { keysText = it; errorText = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Key（可多条）") },
                        placeholder = { Text("sk-xxxx...") },
                        supportingText = { Text("可用空格/换行/逗号分隔，一次添加多条") },
                        minLines = 2,
                        maxLines = 4
                    )

                    OutlinedTextField(
                        value = nameText,
                        onValueChange = { nameText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("名称（可选）") },
                        placeholder = { Text("如：备用 Key 1") },
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Switch(checked = enabled, onCheckedChange = { enabled = it })
                            Text("启用")
                        }

                        Text("优先级: ${priority.toInt()}")
                    }

                    Slider(
                        value = priority,
                        onValueChange = { priority = it },
                        valueRange = 1f..10f,
                        steps = 8
                    )

                    if (errorText != null) {
                        Text(
                            text = errorText!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = splitApiKeys(keysText)
                        if (parsed.isEmpty()) {
                            errorText = "请输入至少一个 Key"
                            return@Button
                        }

                        val existing = apiKeys.map { it.key.trim() }.toSet()
                        val newKeys = parsed.filter { it.trim() !in existing }
                        if (newKeys.isEmpty()) {
                            errorText = "这些 Key 已存在"
                            return@Button
                        }

                        val effectiveName = nameText.trim()
                        val prio = priority.toInt().coerceIn(1, 10)
                        val newEntries = newKeys.map { keyValue ->
                            ApiKeyEntry(
                                key = keyValue.trim(),
                                name = if (parsed.size == 1) effectiveName else "",
                                isEnabled = enabled,
                                priority = prio
                            )
                        }

                        apiKeys = apiKeys + newEntries
                        multiKeyEnabled = true
                        showAddKeyDialog = false
                    }
                ) {
                    Text("添加")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddKeyDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    editingKey?.let { keyToEdit ->
        var keyText by remember(keyToEdit.id) { mutableStateOf(keyToEdit.key) }
        var nameText by remember(keyToEdit.id) { mutableStateOf(keyToEdit.name) }
        var enabled by remember(keyToEdit.id) { mutableStateOf(keyToEdit.isEnabled) }
        var priority by remember(keyToEdit.id) { mutableStateOf(keyToEdit.priority.toFloat()) }
        var errorText by remember(keyToEdit.id) { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { editingKey = null },
            icon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
            title = { Text("编辑 API Key") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = keyText,
                        onValueChange = { keyText = it; errorText = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Key") },
                        placeholder = { Text("sk-xxxx...") },
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = nameText,
                        onValueChange = { nameText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("名称（可选）") },
                        placeholder = { Text("如：主 Key") },
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Switch(checked = enabled, onCheckedChange = { enabled = it })
                            Text("启用")
                        }

                        Text("优先级: ${priority.toInt()}")
                    }

                    Slider(
                        value = priority,
                        onValueChange = { priority = it },
                        valueRange = 1f..10f,
                        steps = 8
                    )

                    Text(
                        text = "状态: ${keyToEdit.status.displayLabel()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (errorText != null) {
                        Text(
                            text = errorText!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmedKey = keyText.trim()
                        if (trimmedKey.isBlank()) {
                            errorText = "Key 不能为空"
                            return@Button
                        }

                        val hasDuplicate = apiKeys.any { it.id != keyToEdit.id && it.key.trim() == trimmedKey }
                        if (hasDuplicate) {
                            errorText = "该 Key 已存在"
                            return@Button
                        }

                        val changed = trimmedKey != keyToEdit.key
                        val prio = priority.toInt().coerceIn(1, 10)
                        val updated = keyToEdit.copy(
                            key = trimmedKey,
                            name = nameText.trim(),
                            isEnabled = enabled,
                            priority = prio,
                            requestCount = if (changed) 0 else keyToEdit.requestCount,
                            successCount = if (changed) 0 else keyToEdit.successCount,
                            failureCount = if (changed) 0 else keyToEdit.failureCount,
                            lastUsedAt = if (changed) 0 else keyToEdit.lastUsedAt,
                            lastError = if (changed) null else keyToEdit.lastError,
                            status = if (changed) ApiKeyStatus.ACTIVE else keyToEdit.status,
                            statusChangedAt = if (changed) 0 else keyToEdit.statusChangedAt
                        )

                        apiKeys = apiKeys.map { if (it.id == keyToEdit.id) updated else it }
                        editingKey = null
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { editingKey = null }) {
                    Text("取消")
                }
            }
        )
    }

    deletingKey?.let { keyToDelete ->
        AlertDialog(
            onDismissRequest = { deletingKey = null },
            icon = {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("删除 API Key") },
            text = { Text("确定要删除 \"${keyToDelete.getDisplayName()}\" 吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        apiKeys = apiKeys.filterNot { it.id == keyToDelete.id }
                        deletingKey = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { deletingKey = null }) {
                    Text("取消")
                }
            }
        )
    }

    // 模型参数配置对话框
    showModelParamsDialog?.let { modelName ->
        val currentParams = modelCustomParams[modelName] ?: ModelCustomParams(modelName = modelName)
        ModelParamsDialog(
            modelName = modelName,
            params = currentParams,
            onDismiss = { showModelParamsDialog = null },
            onSave = { newParams ->
                modelCustomParams = if (newParams.hasAnyValue()) {
                    modelCustomParams + (modelName to newParams)
                } else {
                    modelCustomParams - modelName
                }
                showModelParamsDialog = null
            }
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
                        availableModels = savedModels,
                        modelCustomParams = modelCustomParams,
                        // 多 Key 管理
                        apiKeys = apiKeys,
                        multiKeyEnabled = multiKeyEnabled,
                        keySelectionStrategy = keySelectionStrategy,
                        roundRobinIndex = roundRobinIndex,
                        maxFailuresBeforeDisable = maxFailuresBeforeDisable,
                        autoRecoveryMinutes = autoRecoveryMinutes
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
                        supportingText = {
                            Text(
                                if (multiKeyEnabled && apiKeys.isNotEmpty()) {
                                    "已启用多 Key，聊天将使用下方 Key 列表（此处作为备用）"
                                } else {
                                    "需要保密，请妥善保管"
                                }
                            )
                        },
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

            // 多 Key 管理卡片
            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "多 Key 管理",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "启用多 Key",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "同一服务商配置多个 Key，自动轮询与故障切换",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = multiKeyEnabled,
                            onCheckedChange = { enabled ->
                                multiKeyEnabled = enabled
                                if (enabled && apiKeys.isEmpty()) {
                                    val migrated = splitApiKeys(apiKey)
                                    if (migrated.isNotEmpty()) {
                                        apiKeys = migrated.map { key -> ApiKeyEntry(key = key) }
                                    }
                                }
                            }
                        )
                    }

                    if (multiKeyEnabled) {
                        // 策略选择
                        ExposedDropdownMenuBox(
                            expanded = strategyExpanded,
                            onExpandedChange = { strategyExpanded = !strategyExpanded }
                        ) {
                            OutlinedTextField(
                                value = keySelectionStrategy.displayLabel(),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("选择策略") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = strategyExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            )
                            ExposedDropdownMenu(
                                expanded = strategyExpanded,
                                onDismissRequest = { strategyExpanded = false }
                            ) {
                                KeySelectionStrategy.entries.forEach { strategy ->
                                    DropdownMenuItem(
                                        text = { Text(strategy.displayLabel()) },
                                        onClick = {
                                            keySelectionStrategy = strategy
                                            strategyExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "失败阈值: $maxFailuresBeforeDisable（达到后标记为错误）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = maxFailuresBeforeDisable.toFloat(),
                                onValueChange = { maxFailuresBeforeDisable = it.toInt().coerceIn(1, 10) },
                                valueRange = 1f..10f,
                                steps = 8
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "自动恢复: $autoRecoveryMinutes 分钟（错误 Key 冷却后重新启用）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = autoRecoveryMinutes.toFloat(),
                                onValueChange = { autoRecoveryMinutes = it.toInt().coerceIn(1, 30) },
                                valueRange = 1f..30f,
                                steps = 28
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val availableCount = apiKeys.count { it.isEnabled && it.status == ApiKeyStatus.ACTIVE }
                            Text(
                                text = "可用 Key：$availableCount / ${apiKeys.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(onClick = { showAddKeyDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("添加 Key")
                            }
                        }

                        if (apiKeys.isEmpty()) {
                            Text(
                                text = "暂无 Key，请点击“添加 Key”",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                apiKeys.forEach { key ->
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = MaterialTheme.shapes.medium,
                                        color = MaterialTheme.colorScheme.surfaceContainerLow
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = key.getDisplayName(),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${key.getMaskedKey()} · 优先级 ${key.priority} · ${key.status.displayLabel()}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Switch(
                                                    checked = key.isEnabled,
                                                    onCheckedChange = { enabled ->
                                                        apiKeys = apiKeys.map {
                                                            if (it.id == key.id) it.copy(isEnabled = enabled) else it
                                                        }
                                                    }
                                                )
                                                IconButton(onClick = { editingKey = key }) {
                                                    Icon(Icons.Outlined.Edit, contentDescription = "编辑")
                                                }
                                                IconButton(onClick = { deletingKey = key }) {
                                                    Icon(
                                                        Icons.Outlined.Delete,
                                                        contentDescription = "删除",
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
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
                    val modelFetchKey = remember(apiKey, multiKeyEnabled, apiKeys) {
                        if (multiKeyEnabled && apiKeys.isNotEmpty()) {
                            apiKeys.firstOrNull { it.isEnabled }?.key ?: apiKey
                        } else {
                            apiKey
                        }
                    }.trim()

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
                                            apiKey = modelFetchKey,
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
                            enabled = !isFetchingModels && modelFetchKey.isNotBlank() && endpoint.isNotBlank()
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
                                            // 显示已配置标记
                                            if (modelCustomParams.containsKey(model)) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                                    shape = MaterialTheme.shapes.extraSmall
                                                ) {
                                                    Text(
                                                        text = "已配置",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        // 配置按钮
                                        IconButton(
                                            onClick = { showModelParamsDialog = model },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Outlined.Settings,
                                                contentDescription = "配置参数",
                                                modifier = Modifier.size(18.dp),
                                                tint = if (modelCustomParams.containsKey(model))
                                                    MaterialTheme.colorScheme.primary
                                                else
                                                    MaterialTheme.colorScheme.onSurfaceVariant
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
    val normalizedEndpoint = endpoint.trim().trimEnd('/')

    val url = when (providerType) {
        AIProviderType.OPENAI -> "$normalizedEndpoint/models"
        AIProviderType.ANTHROPIC -> {
            // Anthropic models API 使用 /v1/models，并需要 x-api-key + anthropic-version
            val baseUrl = if (normalizedEndpoint.endsWith("/v1")) normalizedEndpoint else "$normalizedEndpoint/v1"
            "$baseUrl/models"
        }
        AIProviderType.GEMINI -> "$normalizedEndpoint/models?key=$apiKey"
    }

    val requestBuilder = Request.Builder().url(url)

    when (providerType) {
        AIProviderType.OPENAI -> {
            if (apiKey.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $apiKey")
            }
        }
        AIProviderType.ANTHROPIC -> {
            if (apiKey.isNotBlank()) {
                requestBuilder.addHeader("x-api-key", apiKey)
            }
            requestBuilder.addHeader("anthropic-version", "2023-06-01")
        }
        AIProviderType.GEMINI -> {
            // Gemini 使用 URL query 的 key；某些代理也支持 header，但这里保持最小集合。
        }
    }

    val response = httpClient.newCall(requestBuilder.build()).execute()

    if (!response.isSuccessful) {
        throw Exception("HTTP ${response.code}: ${response.message}")
    }

    val responseBody = response.body?.string() ?: throw Exception("空响应")

    val jsonObject = JSONObject(responseBody)
    val dataArray = jsonObject.optJSONArray("data") ?: jsonObject.optJSONArray("models")
        ?: throw Exception("无效的响应格式：未找到 data/models 字段")

    val models = LinkedHashSet<String>()
    for (i in 0 until dataArray.length()) {
        val modelObj = dataArray.optJSONObject(i) ?: continue

        // OpenAI: id；Gemini: name；兼容部分代理: model
        val rawId = sequenceOf(
            modelObj.optString("id", ""),
            modelObj.optString("name", ""),
            modelObj.optString("model", "")
        ).firstOrNull { it.isNotBlank() }.orEmpty()

        val modelId = rawId
            .trim()
            .removePrefix("models/")
            .removePrefix("/models/")

        if (modelId.isBlank()) continue

        if (modelId.contains("embedding", ignoreCase = true) ||
            modelId.contains("whisper", ignoreCase = true) ||
            modelId.contains("tts", ignoreCase = true) ||
            modelId.contains("dall-e", ignoreCase = true)
        ) {
            continue
        }

        models.add(modelId)
    }

    if (models.isEmpty()) {
        throw Exception("未找到可用模型")
    }

    models.toList()
}

/**
 * 模型参数配置对话框
 */
@Composable
private fun ModelParamsDialog(
    modelName: String,
    params: ModelCustomParams,
    onDismiss: () -> Unit,
    onSave: (ModelCustomParams) -> Unit
) {
    var temperatureEnabled by remember { mutableStateOf(params.temperature != null) }
    var temperature by remember { mutableFloatStateOf(params.temperature ?: 0.7f) }

    var topPEnabled by remember { mutableStateOf(params.topP != null) }
    var topP by remember { mutableFloatStateOf(params.topP ?: 0.9f) }

    var topKEnabled by remember { mutableStateOf(params.topK != null) }
    var topK by remember { mutableStateOf((params.topK ?: 50).toString()) }

    var presencePenaltyEnabled by remember { mutableStateOf(params.presencePenalty != null) }
    var presencePenalty by remember { mutableFloatStateOf(params.presencePenalty ?: 0f) }

    var frequencyPenaltyEnabled by remember { mutableStateOf(params.frequencyPenalty != null) }
    var frequencyPenalty by remember { mutableFloatStateOf(params.frequencyPenalty ?: 0f) }

    var repetitionPenaltyEnabled by remember { mutableStateOf(params.repetitionPenalty != null) }
    var repetitionPenalty by remember { mutableFloatStateOf(params.repetitionPenalty ?: 1f) }

    var maxTokensEnabled by remember { mutableStateOf(params.maxTokens != null) }
    var maxTokens by remember { mutableStateOf((params.maxTokens ?: 4096).toString()) }

    var extraParams by remember { mutableStateOf(params.extraParams) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("模型参数配置")
                Text(
                    text = modelName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 450.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Temperature
                ParamSliderItem(
                    label = "Temperature",
                    enabled = temperatureEnabled,
                    onEnabledChange = { temperatureEnabled = it },
                    value = temperature,
                    onValueChange = { temperature = it },
                    valueRange = 0f..2f,
                    valueDisplay = "%.2f".format(temperature)
                )

                // Top-P
                ParamSliderItem(
                    label = "Top-P",
                    enabled = topPEnabled,
                    onEnabledChange = { topPEnabled = it },
                    value = topP,
                    onValueChange = { topP = it },
                    valueRange = 0f..1f,
                    valueDisplay = "%.2f".format(topP)
                )

                // Top-K
                ParamInputItem(
                    label = "Top-K",
                    enabled = topKEnabled,
                    onEnabledChange = { topKEnabled = it },
                    value = topK,
                    onValueChange = { topK = it },
                    placeholder = "50"
                )

                // Presence Penalty
                ParamSliderItem(
                    label = "Presence Penalty",
                    enabled = presencePenaltyEnabled,
                    onEnabledChange = { presencePenaltyEnabled = it },
                    value = presencePenalty,
                    onValueChange = { presencePenalty = it },
                    valueRange = -2f..2f,
                    valueDisplay = "%.2f".format(presencePenalty)
                )

                // Frequency Penalty
                ParamSliderItem(
                    label = "Frequency Penalty",
                    enabled = frequencyPenaltyEnabled,
                    onEnabledChange = { frequencyPenaltyEnabled = it },
                    value = frequencyPenalty,
                    onValueChange = { frequencyPenalty = it },
                    valueRange = -2f..2f,
                    valueDisplay = "%.2f".format(frequencyPenalty)
                )

                // Repetition Penalty
                ParamSliderItem(
                    label = "Repetition Penalty",
                    enabled = repetitionPenaltyEnabled,
                    onEnabledChange = { repetitionPenaltyEnabled = it },
                    value = repetitionPenalty,
                    onValueChange = { repetitionPenalty = it },
                    valueRange = 0f..2f,
                    valueDisplay = "%.2f".format(repetitionPenalty)
                )

                // Max Tokens
                ParamInputItem(
                    label = "Max Tokens",
                    enabled = maxTokensEnabled,
                    onEnabledChange = { maxTokensEnabled = it },
                    value = maxTokens,
                    onValueChange = { maxTokens = it },
                    placeholder = "4096"
                )

                HorizontalDivider()

                // Extra JSON Params
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "自定义 JSON 参数",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    OutlinedTextField(
                        value = extraParams,
                        onValueChange = { extraParams = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("{\"stop\": [\"<|end|>\"]}") },
                        supportingText = { Text("直接合并到请求体，覆盖同名参数") },
                        minLines = 2,
                        maxLines = 4
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newParams = ModelCustomParams(
                        modelName = modelName,
                        temperature = if (temperatureEnabled) temperature else null,
                        topP = if (topPEnabled) topP else null,
                        topK = if (topKEnabled) topK.toIntOrNull() else null,
                        presencePenalty = if (presencePenaltyEnabled) presencePenalty else null,
                        frequencyPenalty = if (frequencyPenaltyEnabled) frequencyPenalty else null,
                        repetitionPenalty = if (repetitionPenaltyEnabled) repetitionPenalty else null,
                        maxTokens = if (maxTokensEnabled) maxTokens.toIntOrNull() else null,
                        extraParams = extraParams.ifBlank { "{}" }
                    )
                    onSave(newParams)
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 滑块参数项
 */
@Composable
private fun ParamSliderItem(
    label: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    valueDisplay: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = valueDisplay,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 输入框参数项
 */
@Composable
private fun ParamInputItem(
    label: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Checkbox(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            placeholder = { Text(placeholder) },
            modifier = Modifier.width(100.dp),
            singleLine = true
        )
    }
}

private val API_KEY_SPLIT_REGEX = "[\\s,]+".toRegex()

private fun splitApiKeys(raw: String): List<String> {
    return raw
        .split(API_KEY_SPLIT_REGEX)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
}

private fun ApiKeyStatus.displayLabel(): String {
    return when (this) {
        ApiKeyStatus.ACTIVE -> "正常"
        ApiKeyStatus.DISABLED -> "禁用"
        ApiKeyStatus.ERROR -> "错误"
        ApiKeyStatus.RATE_LIMITED -> "限流"
    }
}

private fun KeySelectionStrategy.displayLabel(): String {
    return when (this) {
        KeySelectionStrategy.ROUND_ROBIN -> "轮询"
        KeySelectionStrategy.PRIORITY -> "优先级"
        KeySelectionStrategy.RANDOM -> "随机"
        KeySelectionStrategy.LEAST_USED -> "最少使用"
    }
}
