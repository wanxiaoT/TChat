package com.tchat.wanxiaot.ui.settings

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.FileProvider
import com.composables.icons.lucide.*
import com.tchat.data.database.entity.KnowledgeBaseEntity
import com.tchat.wanxiaot.settings.ProviderConfig
import com.tchat.wanxiaot.settings.SettingsManager
import com.tchat.wanxiaot.ui.components.AppEmptyState
import com.tchat.wanxiaot.ui.components.AppIconTile
import com.tchat.wanxiaot.ui.components.AppPageScaffold
import com.tchat.wanxiaot.ui.components.AppPill
import com.tchat.wanxiaot.ui.components.SettingsGroupCard
import com.tchat.wanxiaot.ui.components.SettingsSurface
import com.tchat.wanxiaot.util.CloudBackupInfo
import com.tchat.wanxiaot.util.CloudBackupManager
import kotlinx.coroutines.launch
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
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true
) {
    val context = LocalContext.current
    val viewModel = remember(context, settingsManager) {
        ExportImportViewModel(context, settingsManager)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val selectedProviderIds by viewModel.selectedProviderIds.collectAsStateWithLifecycle()
    val knowledgeBases by viewModel.knowledgeBases.collectAsStateWithLifecycle()

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
    var showSkillSelection by remember { mutableStateOf(false) }

    val skills by viewModel.skills.collectAsStateWithLifecycle()
    val selectedSkillIds by viewModel.selectedSkillIds.collectAsStateWithLifecycle()

    // 云备份相关状态
    val r2Settings = settingsManager.settings.collectAsStateWithLifecycle().value.r2Settings
    val cloudBackupManager = remember(context, settingsManager) {
        CloudBackupManager(context, settingsManager)
    }
    var showCloudBackupList by remember { mutableStateOf(false) }
    var cloudBackups by remember { mutableStateOf<List<CloudBackupInfo>>(emptyList()) }
    var isLoadingCloudBackups by remember { mutableStateOf(false) }
    var cloudBackupError by remember { mutableStateOf<String?>(null) }
    var isUploadingToCloud by remember { mutableStateOf(false) }
    var showCloudRestoreConfirm by remember { mutableStateOf<CloudBackupInfo?>(null) }
    var isDownloadingFromCloud by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

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
                ExportType.SKILLS -> {
                    viewModel.exportSkillsToUri(selectedSkillIds.toList(), it)
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
                ExportType.SKILLS -> {
                    val file = uriToFile(context, it, "skills_import.json")
                    viewModel.importSkillsFromFile(file)
                }
                null -> {}
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

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

            else -> Unit
        }
    }

    AppPageScaffold(
        title = "导出/导入",
        eyebrow = "Transfer Center",
        subtitle = "配置、数据库与云端备份迁移",
        showTopBar = showTopBar,
        onBack = if (showTopBar) onBackClick else null,
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

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

                item {
                    CloudBackupSection(
                        isConfigured = r2Settings.isConfigured,
                        bucketName = r2Settings.bucketName,
                        isUploading = isUploadingToCloud,
                        onUpload = {
                            scope.launch {
                                isUploadingToCloud = true
                                try {
                                    val backupFile = viewModel.createBackupFile()
                                    if (backupFile != null) {
                                        val result = cloudBackupManager.uploadBackup(backupFile)
                                        if (result.isSuccess) {
                                            Toast.makeText(context, "上传成功", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "上传失败: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                        }
                                        backupFile.delete()
                                    } else {
                                        Toast.makeText(context, "创建备份文件失败", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "上传失败: ${e.message}", Toast.LENGTH_LONG).show()
                                } finally {
                                    isUploadingToCloud = false
                                }
                            }
                        },
                        onShowList = {
                            scope.launch {
                                isLoadingCloudBackups = true
                                cloudBackupError = null
                                val result = cloudBackupManager.listBackups()
                                if (result.isSuccess) {
                                    cloudBackups = result.getOrDefault(emptyList())
                                    showCloudBackupList = true
                                } else {
                                    cloudBackupError = result.exceptionOrNull()?.message
                                    Toast.makeText(context, "获取列表失败: ${cloudBackupError}", Toast.LENGTH_LONG).show()
                                }
                                isLoadingCloudBackups = false
                            }
                        },
                        isLoadingList = isLoadingCloudBackups
                    )
                }

                item {
                    ExportImportSectionConnected(
                        title = "供应商配置",
                        description = "导出或导入 AI 供应商配置，包括模型列表与自定义参数。",
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

                item {
                    ExportImportSectionConnected(
                        title = "API 配置",
                        description = "导出或导入包含密钥的 API 配置，建议始终加密保存。",
                        icon = Lucide.Key,
                        encryptionEnabled = useEncryption,
                        encryptionPassword = encryptionPassword,
                        onEncryptionEnabledChange = { useEncryption = it },
                        onEncryptionPasswordChange = { encryptionPassword = it },
                        onExportFile = {
                            currentExportType = ExportType.API_CONFIG
                            useEncryption = true
                            showProviderSingleSelection = true
                            pendingExportAction = {
                                selectedProviderId?.let {
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

                item {
                    ExportImportSectionConnected(
                        title = "知识库",
                        description = "导出或导入知识库，包含原始文件、向量数据与相关配置。",
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

                item {
                    ExportImportSectionConnected(
                        title = "Skills",
                        description = "迁移自定义 Skills，不包含内置系统 Skills。",
                        icon = Lucide.Sparkles,
                        encryptionEnabled = false,
                        encryptionPassword = "",
                        onEncryptionEnabledChange = {},
                        onEncryptionPasswordChange = {},
                        onExportFile = {
                            currentExportType = ExportType.SKILLS
                            showSkillSelection = true
                        },
                        onImportFile = {
                            currentExportType = ExportType.SKILLS
                            importFileLauncher.launch(arrayOf("application/json", "*/*"))
                        },
                        supportsQRCode = false
                    )
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 20.dp)
            )

            if (uiState is ExportImportUiState.Loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.widthIn(max = 280.dp)) {
                        SettingsSurface {
                            Column(
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    text = (uiState as ExportImportUiState.Loading).message,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
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

    // Skills 选择对话框
    if (showSkillSelection) {
        SkillSelectionDialog(
            skills = skills.filter { !it.isBuiltIn },
            selectedSkillIds = selectedSkillIds,
            onSkillToggle = { viewModel.toggleSkillSelection(it) },
            onDismiss = {
                showSkillSelection = false
                viewModel.clearSkillSelection()
            },
            onConfirm = {
                showSkillSelection = false
                exportFileLauncher.launch("skills_${System.currentTimeMillis()}.json")
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

    // 云备份列表对话框
    if (showCloudBackupList) {
        CloudBackupListDialog(
            backups = cloudBackups,
            cloudBackupManager = cloudBackupManager,
            onDismiss = { showCloudBackupList = false },
            onRestore = { backup ->
                showCloudRestoreConfirm = backup
            },
            onDelete = { backup ->
                scope.launch {
                    val result = cloudBackupManager.deleteBackup(backup.key)
                    if (result.isSuccess) {
                        cloudBackups = cloudBackups.filter { it.key != backup.key }
                        Toast.makeText(context, "删除成功", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "删除失败: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    // 云备份恢复确认对话框
    showCloudRestoreConfirm?.let { backup ->
        AlertDialog(
            onDismissRequest = { showCloudRestoreConfirm = null },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("确认从云端恢复") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("将从云端下载并恢复备份：")
                    Text(
                        text = backup.key,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
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
                        val backupToRestore = backup
                        showCloudRestoreConfirm = null
                        showCloudBackupList = false

                        scope.launch {
                            isDownloadingFromCloud = true
                            try {
                                // 下载到临时文件
                                val tempFile = File(context.cacheDir, "cloud_restore_${System.currentTimeMillis()}.zip")
                                val downloadResult = cloudBackupManager.downloadBackup(backupToRestore.key, tempFile)

                                if (downloadResult.isSuccess) {
                                    // 恢复数据库
                                    viewModel.restoreDatabaseFromFile(tempFile)
                                    tempFile.delete()
                                } else {
                                    Toast.makeText(context, "下载失败: ${downloadResult.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "恢复失败: ${e.message}", Toast.LENGTH_LONG).show()
                            } finally {
                                isDownloadingFromCloud = false
                            }
                        }
                    },
                    enabled = !isDownloadingFromCloud,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    if (isDownloadingFromCloud) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onError
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("下载中...")
                    } else {
                        Text("确认恢复")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCloudRestoreConfirm = null },
                    enabled = !isDownloadingFromCloud
                ) {
                    Text("取消")
                }
            }
        )
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
    onImportFile: () -> Unit,
    modifier: Modifier = Modifier,
    onExportQRCode: (() -> Unit)? = null,
    onImportQRCode: (() -> Unit)? = null,
    supportsQRCode: Boolean = true,
    requiresEncryption: Boolean = false
) {
    var showExportOptions by remember { mutableStateOf(false) }
    var showImportOptions by remember { mutableStateOf(false) }

    SettingsGroupCard(
        modifier = modifier,
        title = title,
        description = description
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconTile(icon = icon)
            AppPill(text = if (supportsQRCode) "文件 / 二维码" else "仅文件")
        }

        if (requiresEncryption) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.74f),
                shape = MaterialTheme.shapes.large
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
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
    KNOWLEDGE_BASE,
    SKILLS
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
                items(providers, key = { it.id }) { provider ->
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
                    items(knowledgeBases, key = { it.id }) { base ->
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
 * Skills 选择对话框
 */
@Composable
private fun SkillSelectionDialog(
    skills: List<com.tchat.data.database.entity.SkillEntity>,
    selectedSkillIds: Set<String>,
    onSkillToggle: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Lucide.Sparkles, contentDescription = null) },
        title = { Text("选择要导出的 Skills") },
        text = {
            if (skills.isEmpty()) {
                Text(
                    text = "暂无自定义 Skills（内置 Skills 不支持导出）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column {
                    Text(
                        text = "留空则导出所有自定义 Skills",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyColumn {
                        items(skills, key = { it.id }) { skill ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSkillToggle(skill.id) }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = skill.id in selectedSkillIds,
                                    onCheckedChange = { onSkillToggle(skill.id) }
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = skill.displayName,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = skill.description.take(50) + if (skill.description.length > 50) "..." else "",
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
                onClick = onConfirm,
                enabled = skills.isNotEmpty()
            ) {
                Text(if (selectedSkillIds.isEmpty()) "导出全部" else "导出选中 (${selectedSkillIds.size})")
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

    SettingsGroupCard(
        modifier = modifier,
        title = "数据库备份",
        description = "备份或恢复完整数据库，包含聊天记录、助手与知识库等核心数据。"
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconTile(icon = Lucide.HardDrive)
            AppPill(text = "全量归档")
        }

        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.76f),
            shape = MaterialTheme.shapes.large
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
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
                    text = "恢复数据库会覆盖当前全部数据，请先确认本地备份已经可用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
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

/**
 * 云备份模块
 */
@Composable
private fun CloudBackupSection(
    isConfigured: Boolean,
    bucketName: String,
    isUploading: Boolean,
    onUpload: () -> Unit,
    onShowList: () -> Unit,
    isLoadingList: Boolean,
    modifier: Modifier = Modifier
) {
    SettingsGroupCard(
        modifier = modifier,
        title = "云备份",
        description = "将本地备份同步到 Cloudflare R2，并查看云端历史归档。"
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconTile(icon = Lucide.CloudUpload)
            AppPill(
                text = if (isConfigured) "已连接" else "待配置",
                containerColor = if (isConfigured) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.82f)
                },
                contentColor = if (isConfigured) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        Surface(
            color = if (isConfigured) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.64f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.78f)
            },
            shape = MaterialTheme.shapes.large
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isConfigured) Lucide.CircleCheck else Lucide.CircleAlert,
                    contentDescription = null,
                    tint = if (isConfigured) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (isConfigured) {
                        "已连接存储桶 $bucketName"
                    } else {
                        "尚未配置 R2，请先完成云存储设置。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isConfigured) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onUpload,
                enabled = isConfigured && !isUploading,
                modifier = Modifier.weight(1f)
            ) {
                if (isUploading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Lucide.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isUploading) "上传中..." else "上传备份")
            }

            Button(
                onClick = onShowList,
                enabled = isConfigured && !isLoadingList,
                modifier = Modifier.weight(1f)
            ) {
                if (isLoadingList) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Lucide.List, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isLoadingList) "加载中..." else "云端列表")
            }
        }
    }
}

/**
 * 云备份列表对话框
 */
@Composable
fun CloudBackupListDialog(
    backups: List<CloudBackupInfo>,
    cloudBackupManager: CloudBackupManager,
    onDismiss: () -> Unit,
    onRestore: (CloudBackupInfo) -> Unit,
    onDelete: (CloudBackupInfo) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Lucide.Cloud, contentDescription = null) },
        title = { Text("云端备份列表") },
        text = {
            if (backups.isEmpty()) {
                AppEmptyState(
                    title = "暂无云端备份",
                    description = "上传首个数据库备份后，这里会显示云端归档列表。",
                    icon = Lucide.CloudOff
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(backups, key = { it.key }) { backup ->
                        CloudBackupItem(
                            backup = backup,
                            cloudBackupManager = cloudBackupManager,
                            onRestore = { onRestore(backup) },
                            onDelete = { onDelete(backup) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

/**
 * 云备份列表项
 */
@Composable
private fun CloudBackupItem(
    backup: CloudBackupInfo,
    cloudBackupManager: CloudBackupManager,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    SettingsSurface {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = backup.key,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1
                    )
                    Text(
                        text = "${cloudBackupManager.formatFileSize(backup.size)} · ${cloudBackupManager.formatDate(backup.lastModified)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRestore,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Lucide.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("恢复", style = MaterialTheme.typography.bodySmall)
                }

                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Lucide.Trash2, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("删除", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    // 删除确认对话框
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("确认删除") },
            text = { Text("确定要删除云端备份 \"${backup.key}\" 吗？此操作不可撤销。") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}
