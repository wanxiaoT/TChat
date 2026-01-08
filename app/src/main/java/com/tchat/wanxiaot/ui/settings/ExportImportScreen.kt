package com.tchat.wanxiaot.ui.settings

import android.graphics.Bitmap
import androidx.compose.foundation.Image
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.composables.icons.lucide.*
import com.tchat.wanxiaot.settings.ProviderConfig
import com.tchat.wanxiaot.util.ExportDataType

/**
 * 导出/导入设置页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportImportScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
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
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 供应商配置
            item {
                ExportImportSection(
                    title = "供应商配置",
                    description = "导出或导入AI供应商配置（包括模型列表和自定义参数）",
                    icon = Lucide.Server,
                    onExportFile = { /* TODO */ },
                    onExportQRCode = { /* TODO */ },
                    onImportFile = { /* TODO */ },
                    onImportQRCode = { /* TODO */ }
                )
            }

            // API配置（含密钥）
            item {
                ExportImportSection(
                    title = "API配置",
                    description = "导出或导入API配置（包含API密钥，强烈建议加密）",
                    icon = Lucide.Key,
                    onExportFile = { /* TODO */ },
                    onExportQRCode = { /* TODO */ },
                    onImportFile = { /* TODO */ },
                    onImportQRCode = { /* TODO */ },
                    requiresEncryption = true
                )
            }

            // 知识库
            item {
                ExportImportSection(
                    title = "知识库",
                    description = "导出或导入知识库（包含原始文件、向量数据和配置）",
                    icon = Lucide.Database,
                    onExportFile = { /* TODO */ },
                    onImportFile = { /* TODO */ },
                    supportsQRCode = false  // 知识库数据量大，不支持二维码
                )
            }
        }
    }
}

@Composable
private fun ExportImportSection(
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
                // 导出按钮
                OutlinedButton(
                    onClick = { showExportOptions = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Lucide.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("导出")
                }

                // 导入按钮
                Button(
                    onClick = { showImportOptions = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Lucide.Upload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("导入")
                }
            }
        }
    }

    // 导出选项对话框
    if (showExportOptions) {
        ExportOptionsDialog(
            title = "导出$title",
            supportsQRCode = supportsQRCode,
            requiresEncryption = requiresEncryption,
            onDismiss = { showExportOptions = false },
            onExportFile = {
                onExportFile()
                showExportOptions = false
            },
            onExportQRCode = if (supportsQRCode && onExportQRCode != null) {
                {
                    onExportQRCode()
                    showExportOptions = false
                }
            } else null
        )
    }

    // 导入选项对话框
    if (showImportOptions) {
        ImportOptionsDialog(
            title = "导入$title",
            supportsQRCode = supportsQRCode,
            onDismiss = { showImportOptions = false },
            onImportFile = {
                onImportFile()
                showImportOptions = false
            },
            onImportQRCode = if (supportsQRCode && onImportQRCode != null) {
                {
                    onImportQRCode()
                    showImportOptions = false
                }
            } else null
        )
    }
}

@Composable
private fun ExportOptionsDialog(
    title: String,
    supportsQRCode: Boolean,
    requiresEncryption: Boolean,
    onDismiss: () -> Unit,
    onExportFile: () -> Unit,
    onExportQRCode: (() -> Unit)?
) {
    var useEncryption by remember { mutableStateOf(requiresEncryption) }
    var password by remember { mutableStateOf("") }
    var showPasswordInput by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Lucide.Download, contentDescription = null) },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // 加密选项
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("加密导出")
                    Switch(
                        checked = useEncryption,
                        onCheckedChange = {
                            useEncryption = it
                            if (it) showPasswordInput = true
                        },
                        enabled = !requiresEncryption  // 如果必须加密，则禁用开关
                    )
                }

                if (useEncryption && showPasswordInput) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("加密密码") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Text(
                    text = "选择导出方式：",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 导出为文件
                Button(
                    onClick = onExportFile,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Lucide.FileText, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("导出为文件")
                }

                // 导出为二维码
                if (supportsQRCode && onExportQRCode != null) {
                    OutlinedButton(
                        onClick = onExportQRCode,
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
private fun ImportOptionsDialog(
    title: String,
    supportsQRCode: Boolean,
    onDismiss: () -> Unit,
    onImportFile: () -> Unit,
    onImportQRCode: (() -> Unit)?
) {
    var password by remember { mutableStateOf("") }
    var showPasswordInput by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Lucide.Upload, contentDescription = null) },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (showPasswordInput) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("解密密码（如果文件已加密）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

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
                // 从文件导入
                Button(
                    onClick = onImportFile,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Lucide.FileText, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("从文件导入")
                }

                // 从二维码导入
                if (supportsQRCode && onImportQRCode != null) {
                    OutlinedButton(
                        onClick = onImportQRCode,
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
 * 二维码显示对话框
 */
@Composable
fun QRCodeDisplayDialog(
    qrCodeBitmap: Bitmap,
    title: String,
    onDismiss: () -> Unit,
    onShare: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge
                )

                // 二维码图片
                Image(
                    bitmap = qrCodeBitmap.asImageBitmap(),
                    contentDescription = "二维码",
                    modifier = Modifier.size(300.dp)
                )

                Text(
                    text = "使用其他设备扫描此二维码",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("关闭")
                    }
                    Button(
                        onClick = onShare,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Lucide.Share2, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("分享")
                    }
                }
            }
        }
    }
}

/**
 * 供应商选择对话框（用于多选导出）
 */
@Composable
fun ProviderSelectionDialog(
    providers: List<ProviderConfig>,
    selectedProviders: Set<String>,
    onProviderToggle: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Lucide.ListChecks, contentDescription = null) },
        title = { Text("选择要导出的供应商") },
        text = {
            LazyColumn {
                items(providers) { provider ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = provider.id in selectedProviders,
                            onCheckedChange = { onProviderToggle(provider.id) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
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
                onClick = onConfirm,
                enabled = selectedProviders.isNotEmpty()
            ) {
                Text("确定（已选 ${selectedProviders.size}）")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
