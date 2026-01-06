package com.tchat.wanxiaot.ui.settings

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.composables.icons.lucide.*
import com.tchat.wanxiaot.settings.ProviderConfig
import com.tchat.wanxiaot.settings.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * 导出/导入设置页面（增强版）
 * 包含完整的文件选择器和业务逻辑连接
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportImportScreenEnhanced(
    settingsManager: SettingsManager,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel = remember(context, settingsManager) {
        ExportImportViewModel(context, settingsManager)
    }

    val uiState by viewModel.uiState.collectAsState()
    val providers by viewModel.providers.collectAsState()
    val selectedProviderIds by viewModel.selectedProviderIds.collectAsState()

    // 状态管理
    var showProviderSelection by remember { mutableStateOf(false) }
    var showQRCodeDialog by remember { mutableStateOf<Bitmap?>(null) }
    var currentExportType by remember { mutableStateOf<ExportType?>(null) }
    var encryptionPassword by remember { mutableStateOf("") }
    var useEncryption by remember { mutableStateOf(false) }
    var showQRScanner by remember { mutableStateOf(false) }
    var selectedProviderId by remember { mutableStateOf<String?>(null) }
    var selectedKnowledgeBaseId by remember { mutableStateOf<String?>(null) }
    var showProviderSingleSelection by remember { mutableStateOf(false) }
    var pendingExportAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // 文件选择器 - 导出（保存文件）
    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            when (currentExportType) {
                ExportType.PROVIDERS -> {
                    val file = uriToFile(context, it, "providers_export.json")
                    viewModel.exportProvidersToFile(
                        selectedProviderIds.toList(),
                        file,
                        useEncryption,
                        encryptionPassword.takeIf { useEncryption }
                    )
                }
                ExportType.MODELS -> {
                    selectedProviderId?.let { providerId ->
                        val file = uriToFile(context, it, "models_export.json")
                        viewModel.exportModelsToFile(
                            providerId,
                            file,
                            useEncryption,
                            encryptionPassword.takeIf { useEncryption }
                        )
                    }
                }
                ExportType.API_CONFIG -> {
                    selectedProviderId?.let { providerId ->
                        val file = uriToFile(context, it, "api_config_export.json")
                        viewModel.exportApiConfigToFile(
                            providerId,
                            file,
                            true, // 强制加密
                            encryptionPassword
                        )
                    }
                }
                ExportType.KNOWLEDGE_BASE -> {
                    selectedKnowledgeBaseId?.let { kbId ->
                        val file = uriToFile(context, it, "knowledge_base_export.json")
                        viewModel.exportKnowledgeBaseToFile(kbId, file)
                    }
                }
                null -> {}
            }
        }
    }

    // 文件选择器 - 导入（打开文件）
    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            when (currentExportType) {
                ExportType.PROVIDERS -> {
                    val file = uriToFile(context, it, "providers_import.json")
                    viewModel.importProvidersFromFile(file, encryptionPassword.takeIf { it.isNotEmpty() })
                }
                ExportType.MODELS -> {
                    selectedProviderId?.let { providerId ->
                        val file = uriToFile(context, it, "models_import.json")
                        viewModel.importModelsFromFile(file, providerId, encryptionPassword.takeIf { it.isNotEmpty() })
                    }
                }
                ExportType.API_CONFIG -> {
                    val file = uriToFile(context, it, "api_config_import.json")
                    viewModel.importApiConfigFromFile(file, encryptionPassword)
                }
                ExportType.KNOWLEDGE_BASE -> {
                    val file = uriToFile(context, it, "knowledge_base_import.json")
                    viewModel.importKnowledgeBaseFromFile(file)
                }
                null -> {}
            }
        }
    }

    // 多文件选择器（用于批量导入）
    val multiImportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            // TODO: 实现批量导入逻辑
            uris.forEachIndexed { index, uri ->
                val file = uriToFile(context, uri, "import_$index.json")
                viewModel.importProvidersFromFile(file, encryptionPassword.takeIf { it.isNotEmpty() })
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("导出/导入") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = {
            val snackbarHostState = remember { SnackbarHostState() }
            SnackbarHost(hostState = snackbarHostState)

            // 显示成功/错误消息
            LaunchedEffect(uiState) {
                when (val state = uiState) {
                    is ExportImportUiState.Success -> {
                        snackbarHostState.showSnackbar(
                            message = state.message,
                            duration = SnackbarDuration.Short
                        )
                        viewModel.clearUiState()
                    }
                    is ExportImportUiState.Error -> {
                        snackbarHostState.showSnackbar(
                            message = state.message,
                            duration = SnackbarDuration.Long,
                            actionLabel = "确定"
                        )
                        viewModel.clearUiState()
                    }
                    else -> {}
                }
            }
        },
        modifier = modifier
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 供应商配置
                item {
                    ExportImportSectionConnected(
                        title = "供应商配置",
                        description = "导出或导入AI供应商配置（包括模型列表和自定义参数）",
                        icon = Lucide.Server,
                        onExportFile = {
                            currentExportType = ExportType.PROVIDERS
                            showProviderSelection = true
                        },
                        onExportQRCode = {
                            currentExportType = ExportType.PROVIDERS
                            if (selectedProviderIds.isNotEmpty()) {
                                viewModel.exportProvidersToQRCode(
                                    selectedProviderIds.toList(),
                                    useEncryption,
                                    encryptionPassword.takeIf { useEncryption }
                                ) { qrCode ->
                                    showQRCodeDialog = qrCode
                                }
                            }
                        },
                        onImportFile = {
                            currentExportType = ExportType.PROVIDERS
                            importFileLauncher.launch(arrayOf("application/json", "*/*"))
                        },
                        onImportQRCode = {
                            currentExportType = ExportType.PROVIDERS
                            showQRScanner = true
                        }
                    )
                }

                // 模型列表
                item {
                    ExportImportSectionConnected(
                        title = "模型列表",
                        description = "导出或导入单个供应商的模型列表",
                        icon = Lucide.List,
                        onExportFile = {
                            currentExportType = ExportType.MODELS
                            showProviderSingleSelection = true
                            pendingExportAction = {
                                selectedProviderId?.let { id ->
                                    exportFileLauncher.launch("models_${System.currentTimeMillis()}.json")
                                }
                            }
                        },
                        onExportQRCode = {
                            currentExportType = ExportType.MODELS
                            showProviderSingleSelection = true
                            pendingExportAction = {
                                selectedProviderId?.let { id ->
                                    viewModel.exportModelsToQRCode(
                                        id,
                                        useEncryption,
                                        encryptionPassword.takeIf { useEncryption }
                                    ) { qrCode ->
                                        showQRCodeDialog = qrCode
                                    }
                                }
                            }
                        },
                        onImportFile = {
                            currentExportType = ExportType.MODELS
                            showProviderSingleSelection = true
                            pendingExportAction = {
                                selectedProviderId?.let { id ->
                                    importFileLauncher.launch(arrayOf("application/json", "*/*"))
                                }
                            }
                        },
                        onImportQRCode = {
                            currentExportType = ExportType.MODELS
                            showQRScanner = true
                        }
                    )
                }

                // API配置（含密钥）
                item {
                    ExportImportSectionConnected(
                        title = "API配置",
                        description = "导出或导入API配置（包含API密钥，强烈建议加密）",
                        icon = Lucide.Key,
                        onExportFile = {
                            currentExportType = ExportType.API_CONFIG
                            useEncryption = true // API配置强制加密
                            showProviderSingleSelection = true
                            pendingExportAction = {
                                selectedProviderId?.let { id ->
                                    exportFileLauncher.launch("api_config_${System.currentTimeMillis()}.json")
                                }
                            }
                        },
                        onExportQRCode = {
                            currentExportType = ExportType.API_CONFIG
                            useEncryption = true
                            showProviderSingleSelection = true
                            pendingExportAction = {
                                selectedProviderId?.let { id ->
                                    viewModel.exportApiConfigToQRCode(
                                        id,
                                        true,
                                        encryptionPassword
                                    ) { qrCode ->
                                        showQRCodeDialog = qrCode
                                    }
                                }
                            }
                        },
                        onImportFile = {
                            currentExportType = ExportType.API_CONFIG
                            importFileLauncher.launch(arrayOf("application/json", "*/*"))
                        },
                        onImportQRCode = {
                            currentExportType = ExportType.API_CONFIG
                            showQRScanner = true
                        },
                        requiresEncryption = true
                    )
                }

                // 知识库
                item {
                    ExportImportSectionConnected(
                        title = "知识库",
                        description = "导出或导入知识库（包含原始文件、向量数据和配置）",
                        icon = Lucide.Database,
                        onExportFile = {
                            currentExportType = ExportType.KNOWLEDGE_BASE
                            // TODO: 需要知识库列表选择对话框
                            // showKnowledgeBaseSelection = true
                        },
                        onImportFile = {
                            currentExportType = ExportType.KNOWLEDGE_BASE
                            importFileLauncher.launch(arrayOf("application/json", "*/*"))
                        },
                        supportsQRCode = false
                    )
                }
            }

            // Loading overlay
            if (uiState is ExportImportUiState.Loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Card {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator()
                            Text((uiState as ExportImportUiState.Loading).message)
                        }
                    }
                }
            }
        }

        // 供应商选择对话框
        if (showProviderSelection) {
            ProviderSelectionDialog(
                providers = providers,
                selectedProviders = selectedProviderIds,
                onProviderToggle = { viewModel.toggleProviderSelection(it) },
                onDismiss = { showProviderSelection = false },
                onConfirm = {
                    showProviderSelection = false
                    exportFileLauncher.launch("providers_${System.currentTimeMillis()}.json")
                }
            )
        }

        // 二维码显示对话框
        if (showQRCodeDialog != null) {
            QRCodeDisplayDialog(
                qrCodeBitmap = showQRCodeDialog!!,
                title = "导出二维码",
                onDismiss = { showQRCodeDialog = null },
                onShare = {
                    // TODO: 实现分享功能
                    // 可以使用Android的分享Intent
                }
            )
        }

        // 供应商单选对话框
        if (showProviderSingleSelection) {
            ProviderSingleSelectionDialog(
                providers = providers,
                onDismiss = {
                    showProviderSingleSelection = false
                    pendingExportAction = null
                },
                onConfirm = { providerId ->
                    selectedProviderId = providerId
                    showProviderSingleSelection = false
                    pendingExportAction?.invoke()
                    pendingExportAction = null
                }
            )
        }

        // 二维码扫描器
        if (showQRScanner) {
            val scannerScope = rememberCoroutineScope()

            com.tchat.wanxiaot.ui.components.QRCodeScannerForImport(
                onBack = { showQRScanner = false },
                onDataScanned = { exportData ->
                    showQRScanner = false
                    // ExportData包含了已经解析好的数据，直接使用viewModel的导入功能
                    // 这里需要临时保存到文件，然后使用文件导入
                    scannerScope.launch {
                        try {
                            val tempFile = File(context.cacheDir, "qr_import_${System.currentTimeMillis()}.json")
                            tempFile.writeText(exportData.toJson())

                            when (currentExportType) {
                                ExportType.PROVIDERS -> {
                                    viewModel.importProvidersFromFile(
                                        tempFile,
                                        encryptionPassword.takeIf { it.isNotEmpty() }
                                    )
                                }
                                ExportType.MODELS -> {
                                    selectedProviderId?.let { providerId ->
                                        viewModel.importModelsFromFile(
                                            tempFile,
                                            providerId,
                                            encryptionPassword.takeIf { it.isNotEmpty() }
                                        )
                                    }
                                }
                                ExportType.API_CONFIG -> {
                                    viewModel.importApiConfigFromFile(tempFile, encryptionPassword)
                                }
                                else -> {}
                            }

                            tempFile.delete()
                        } catch (e: Exception) {
                            // 错误会通过ViewModel的uiState传递
                        }
                    }
                },
                password = encryptionPassword.takeIf { it.isNotEmpty() }
            )
        }
    }
}

/**
 * 连接了业务逻辑的导出导入模块
 */
@Composable
private fun ExportImportSectionConnected(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onExportFile: () -> Unit,
    onExportQRCode: (() -> Unit)? = null,
    onImportFile: () -> Unit,
    onImportQRCode: (() -> Unit)? = null,
    supportsQRCode: Boolean = true,
    requiresEncryption: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showExportOptions by remember { mutableStateOf(false) }
    var showImportOptions by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 标题和图标
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (requiresEncryption) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Lucide.ShieldAlert,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "包含敏感信息，建议加密导出",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onExportFile,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Lucide.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("导出")
                }

                Button(
                    onClick = onImportFile,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Lucide.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("导入")
                }
            }
        }
    }
}

/**
 * 导出类型枚举
 */
enum class ExportType {
    PROVIDERS,
    MODELS,
    API_CONFIG,
    KNOWLEDGE_BASE
}

/**
 * 将 URI 转换为临时文件
 */
private fun uriToFile(context: Context, uri: Uri, defaultFileName: String): File {
    val inputStream = context.contentResolver.openInputStream(uri)
    val tempFile = File(context.cacheDir, defaultFileName)
    inputStream?.use { input ->
        tempFile.outputStream().use { output ->
            input.copyTo(output)
        }
    }
    return tempFile
}

/**
 * 供应商单选对话框（用于模型列表和API配置）
 */
@Composable
private fun ProviderSingleSelectionDialog(
    providers: List<ProviderConfig>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var selectedId by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Lucide.Server, contentDescription = null) },
        title = { Text("选择供应商") },
        text = {
            LazyColumn {
                items(providers) { provider ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedId = provider.id }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedId == provider.id,
                            onClick = { selectedId = provider.id }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = provider.name,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "${provider.providerType.displayName} · ${provider.availableModels.size} 个模型",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { selectedId?.let { onConfirm(it) } },
                enabled = selectedId != null
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
