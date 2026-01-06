// ExportImportScreenEnhanced.kt - 关键补丁代码
// 这些代码片段需要添加到现有文件中

// ============= 1. 在状态管理部分添加（第47-52行之后）=============
var showQRScanner by remember { mutableStateOf(false) }
var selectedProviderId by remember { mutableStateOf<String?>(null) }
var selectedKnowledgeBaseId by remember { mutableStateOf<String?>(null) }
var showProviderSingleSelection by remember { mutableStateOf(false) }
var pendingExportAction by remember { mutableStateOf<(() -> Unit)?>(null) }

// ============= 2. 更新 onImportQRCode 回调（替换所有 TODO 注释）=============

// 供应商配置部分
onImportQRCode = {
    currentExportType = ExportType.PROVIDERS
    showQRScanner = true
}

// 模型列表部分 - 替换ExportImportSectionConnected中的TODO
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

// API配置部分
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

// 知识库部分
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

// ============= 3. 在Scaffold内添加Snackbar支持 =============
val snackbarHostState = remember { SnackbarHostState() }

Scaffold(
    topBar = { ... },
    snackbarHost = { SnackbarHost(snackbarHostState) },
    modifier = modifier
) { ... }

// ============= 4. 添加LaunchedEffect处理消息显示 =============
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

// ============= 5. 在文件末尾添加对话框组件 =============

// 二维码扫描器
if (showQRScanner) {
    com.tchat.wanxiaot.ui.components.QRCodeScannerForImport(
        onBack = { showQRScanner = false },
        onDataScanned = { exportData ->
            showQRScanner = false
            when (currentExportType) {
                ExportType.PROVIDERS -> {
                    val data = com.tchat.wanxiaot.util.ProvidersExportData.fromJson(exportData.data)
                    // 直接使用 ExportImportManager 导入
                    kotlinx.coroutines.MainScope().launch {
                        try {
                            val tempFile = File(context.cacheDir, "temp_import.json")
                            tempFile.writeText(exportData.toJson())
                            viewModel.importProvidersFromFile(
                                tempFile,
                                encryptionPassword.takeIf { it.isNotEmpty() }
                            )
                            tempFile.delete()
                        } catch (e: Exception) {
                            // 错误处理
                        }
                    }
                }
                ExportType.MODELS -> {
                    // 处理模型列表导入
                }
                ExportType.API_CONFIG -> {
                    // 处理API配置导入
                }
                else -> {}
            }
        },
        password = encryptionPassword.takeIf { it.isNotEmpty() }
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

// ============= 6. 添加供应商单选对话框组件 =============
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

// ============= 7. 更新exportFileLauncher中的MODELS和API_CONFIG处理 =============
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

// ============= 8. 更新importFileLauncher中的处理 =============
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
