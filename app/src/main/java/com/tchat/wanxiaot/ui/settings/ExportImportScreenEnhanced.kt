package com.tchat.wanxiaot.ui.settings

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
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
import androidx.core.content.FileProvider
import com.composables.icons.lucide.*
import com.tchat.data.database.entity.KnowledgeBaseEntity
import com.tchat.wanxiaot.settings.ProviderConfig
import com.tchat.wanxiaot.settings.SettingsManager
import java.io.File
import java.io.FileOutputStream

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
    val knowledgeBases by viewModel.knowledgeBases.collectAsState()

    // 状态管理
    var showProviderSelection by remember { mutableStateOf(false) }
    var showQRCodeDialog by remember { mutableStateOf<Bitmap?>(null) }
    var currentExportType by remember { mutableStateOf<ExportType?>(null) }
    var encryptionPassword by remember { mutableStateOf("") }
    var useEncryption by remember { mutableStateOf(false) }
    var showQRScanner by remember { mutableStateOf(false) }
    var selectedProviderId by remember { mutableStateOf<String?>(null) }
    var selectedKnowledgeBaseId by remember { mutableStateOf<String?>(null) }
    var showKnowledgeBaseSelection by remember { mutableStateOf(false) }
    var showProviderSingleSelection by remember { mutableStateOf(false) }
    var pendingExportAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // 文件选择器 - 数据库备份（保存 zip 文件）
    val backupFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        uri?.let {
            viewModel.backupDatabaseToUri(it)
        }
    }

    // 文件选择器 - 数据库恢复（打开 zip 文件）
    val restoreFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.restoreDatabaseFromUri(it)
        }
    }

    // 文件选择器 - 导出（保存文件）
    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            when (currentExportType) {
                ExportType.PROVIDERS -> {
                    viewModel.exportProvidersToUri(
                        selectedProviderIds.toList(),
                        it,
                        useEncryption,
                        encryptionPassword.takeIf { useEncryption }
                    )
                }
                ExportType.API_CONFIG -> {
                    selectedProviderId?.let { providerId ->
                        viewModel.exportApiConfigToUri(
                            providerId,
                            it,
                            true, // 强制加密
                            encryptionPassword
                        )
                    }
                }
                ExportType.KNOWLEDGE_BASE -> {
                    selectedKnowledgeBaseId?.let { kbId ->
                        viewModel.exportKnowledgeBaseToUri(kbId, it)
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
                // 数据库备份
                item {
                    DatabaseBackupSection(
                        onBackup = {
                            backupFileLauncher.launch(viewModel.generateBackupFileName())
                        },
                        onRestore = {
                            restoreFileLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*"))
                        }
                    )
                }

                // 供应商配置
                item {
                    ExportImportSectionConnected(
                        title = "供应商配置",
                        description = "导出或导入AI供应商配置（包括模型列表和自定义参数）",
                        icon = Lucide.Server,
                        encryptionEnabled = useEncryption,
                        encryptionPassword = encryptionPassword,
                        onEncryptionEnabledChange = { useEncryption = it },
                        onEncryptionPasswordChange = { encryptionPassword = it },
                        onExportFile = {
                            currentExportType = ExportType.PROVIDERS
                            pendingExportAction = null
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
                            } else {
                                pendingExportAction = {
                                    if (selectedProviderIds.isNotEmpty()) {
                                        viewModel.exportProvidersToQRCode(
                                            selectedProviderIds.toList(),
                                            useEncryption,
                                            encryptionPassword.takeIf { useEncryption }
                                        ) { qrCode ->
                                            showQRCodeDialog = qrCode
                                        }
                                    }
                                }
                                showProviderSelection = true
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

                // API配置（含密钥）
                item {
                    ExportImportSectionConnected(
                        title = "API配置",
                        description = "导出或导入API配置（包含API密钥，强烈建议加密）",
                        icon = Lucide.Key,
                        encryptionEnabled = useEncryption,
                        encryptionPassword = encryptionPassword,
                        onEncryptionEnabledChange = { useEncryption = it },
                        onEncryptionPasswordChange = { encryptionPassword = it },
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
                        encryptionEnabled = false,
                        encryptionPassword = "",
                        onEncryptionEnabledChange = {},
                        onEncryptionPasswordChange = { encryptionPassword = it },
                        onExportFile = {
                            currentExportType = ExportType.KNOWLEDGE_BASE
                            showKnowledgeBaseSelection = true
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
                onDismiss = {
                    showProviderSelection = false
                    pendingExportAction = null
                },
                onConfirm = {
                    showProviderSelection = false
                    if (pendingExportAction != null) {
                        pendingExportAction?.invoke()
                        pendingExportAction = null
                    } else {
                        exportFileLauncher.launch("providers_${System.currentTimeMillis()}.json")
                    }
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
                    val bitmap = showQRCodeDialog
                    if (bitmap != null) {
                        try {
                            val shareDir = File(context.cacheDir, "share")
                            if (!shareDir.exists()) shareDir.mkdirs()

                            val file = File(shareDir, "TChat_Export_QRCode.png")
                            FileOutputStream(file).use { output ->
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                            }

                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )

                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/png"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                putExtra(Intent.EXTRA_TEXT, "TChat 导出二维码")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }

                            context.startActivity(Intent.createChooser(shareIntent, "分享二维码"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "分享失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
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

        // 知识库选择对话框
        if (showKnowledgeBaseSelection) {
            KnowledgeBaseSelectionDialog(
                knowledgeBases = knowledgeBases,
                onDismiss = { showKnowledgeBaseSelection = false },
                onConfirm = { baseId ->
                    selectedKnowledgeBaseId = baseId
                    showKnowledgeBaseSelection = false
                    exportFileLauncher.launch("knowledge_base_${System.currentTimeMillis()}.json")
                }
            )
        }

        // 二维码扫描器
        if (showQRScanner) {
            com.tchat.wanxiaot.ui.components.QRCodeScannerForImport(
                onBack = { showQRScanner = false },
                onDataScanned = { exportData ->
                    showQRScanner = false
                    when (currentExportType) {
                        ExportType.PROVIDERS -> {
                            viewModel.importProvidersFromExportData(exportData)
                        }
                        ExportType.API_CONFIG -> {
                            viewModel.importApiConfigFromExportData(exportData)
                        }
                        else -> {}
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
    encryptionEnabled: Boolean,
    encryptionPassword: String,
    onEncryptionEnabledChange: (Boolean) -> Unit,
    onEncryptionPasswordChange: (String) -> Unit,
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
                    onClick = { showExportOptions = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Lucide.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("导出")
                }

                Button(
                    onClick = { showImportOptions = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Lucide.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("导入")
                }
            }
        }
    }

    // 导出选项对话框
    if (showExportOptions) {
        ExportOptionsDialogConnected(
            title = "导出$title",
            supportsQRCode = supportsQRCode,
            requiresEncryption = requiresEncryption,
            initialEncryptionEnabled = if (requiresEncryption) true else encryptionEnabled,
            initialPassword = encryptionPassword,
            onDismiss = { showExportOptions = false },
            onExportFile = { encrypted, password ->
                onEncryptionEnabledChange(encrypted)
                onEncryptionPasswordChange(password)
                onExportFile()
                showExportOptions = false
            },
            onExportQRCode = if (supportsQRCode && onExportQRCode != null) {
                { encrypted, password ->
                    onEncryptionEnabledChange(encrypted)
                    onEncryptionPasswordChange(password)
                    onExportQRCode()
                    showExportOptions = false
                }
            } else {
                null
            }
        )
    }

    // 导入选项对话框
    if (showImportOptions) {
        ImportOptionsDialogConnected(
            title = "导入$title",
            supportsQRCode = supportsQRCode,
            initialPassword = encryptionPassword,
            onDismiss = { showImportOptions = false },
            onImportFile = { password ->
                onEncryptionPasswordChange(password)
                onImportFile()
                showImportOptions = false
            },
            onImportQRCode = if (supportsQRCode && onImportQRCode != null) {
                { password ->
                    onEncryptionPasswordChange(password)
                    onImportQRCode()
                    showImportOptions = false
                }
            } else {
                null
            }
        )
    }
}

@Composable
private fun ExportOptionsDialogConnected(
    title: String,
    supportsQRCode: Boolean,
    requiresEncryption: Boolean,
    initialEncryptionEnabled: Boolean,
    initialPassword: String,
    onDismiss: () -> Unit,
    onExportFile: (encrypted: Boolean, password: String) -> Unit,
    onExportQRCode: ((encrypted: Boolean, password: String) -> Unit)?
) {
    var useEncryption by remember { mutableStateOf(if (requiresEncryption) true else initialEncryptionEnabled) }
    var password by remember { mutableStateOf(initialPassword) }

    val effectiveEncryption = if (requiresEncryption) true else useEncryption
    val passwordRequired = effectiveEncryption

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Lucide.Download, contentDescription = null) },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("加密导出")
                    Switch(
                        checked = effectiveEncryption,
                        onCheckedChange = { useEncryption = it },
                        enabled = !requiresEncryption
                    )
                }

                if (passwordRequired) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("密码") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (requiresEncryption) {
                    Text(
                        text = "此项导出包含敏感信息，必须加密。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val canProceed = !passwordRequired || password.isNotBlank()

                Button(
                    onClick = { onExportFile(effectiveEncryption, password) },
                    enabled = canProceed,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Lucide.FileText, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("导出为文件")
                }

                if (supportsQRCode && onExportQRCode != null) {
                    OutlinedButton(
                        onClick = { onExportQRCode(effectiveEncryption, password) },
                        enabled = canProceed,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Lucide.QrCode, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("生成二维码")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun ImportOptionsDialogConnected(
    title: String,
    supportsQRCode: Boolean,
    initialPassword: String,
    onDismiss: () -> Unit,
    onImportFile: (password: String) -> Unit,
    onImportQRCode: ((password: String) -> Unit)?
) {
    var password by remember { mutableStateOf(initialPassword) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Lucide.Upload, contentDescription = null) },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("解密密码（如果已加密）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "选择导入方式：",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onImportFile(password) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Lucide.FileText, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("从文件导入")
                }

                if (supportsQRCode && onImportQRCode != null) {
                    OutlinedButton(
                        onClick = { onImportQRCode(password) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Lucide.QrCode, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("扫描二维码")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 导出类型枚举
 */
enum class ExportType {
    PROVIDERS,
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
 * 供应商单选对话框（用于API配置）
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

/**
 * 知识库选择对话框
 */
@Composable
private fun KnowledgeBaseSelectionDialog(
    knowledgeBases: List<KnowledgeBaseEntity>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var selectedId by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Lucide.Database, contentDescription = null) },
        title = { Text("选择知识库") },
        text = {
            if (knowledgeBases.isEmpty()) {
                Text(
                    text = "暂无知识库",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn {
                    items(knowledgeBases) { base ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedId = base.id }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedId == base.id,
                                onClick = { selectedId = base.id }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = base.name,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                base.description?.takeIf { it.isNotBlank() }?.let { desc ->
                                    Text(
                                        text = desc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

/**
 * 数据库备份模块
 */
@Composable
private fun DatabaseBackupSection(
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }

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
                    imageVector = Lucide.HardDrive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "数据库备份",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "备份或恢复整个数据库（包含所有聊天记录、助手、知识库等）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 警告提示
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Lucide.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "恢复数据库将覆盖当前所有数据，请谨慎操作",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onBackup,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Lucide.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("备份")
                }

                Button(
                    onClick = { showRestoreConfirmDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Lucide.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("恢复")
                }
            }
        }
    }

    // 恢复确认对话框
    if (showRestoreConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirmDialog = false },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("确认恢复数据库") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("恢复数据库将会：")
                    Text("• 覆盖当前所有聊天记录")
                    Text("• 覆盖所有助手配置")
                    Text("• 覆盖所有知识库数据")
                    Text("• 覆盖所有其他数据")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "此操作不可撤销，请确保已备份当前数据！",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreConfirmDialog = false
                        onRestore()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("确认恢复")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirmDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
