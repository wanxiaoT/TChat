package com.tchat.wanxiaot.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Lucide
import com.tchat.wanxiaot.settings.OcrModel
import com.tchat.wanxiaot.settings.OcrSettings
import com.tchat.wanxiaot.settings.ProviderConfig
import com.tchat.wanxiaot.settings.SettingsManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrSettingsScreen(
    settingsManager: SettingsManager,
    onBack: () -> Unit,
    showTopBar: Boolean = true
) {
    val settings by settingsManager.settings.collectAsState()
    val ocrSettings = settings.ocrSettings
    val selectedModel = OcrModel.fromName(ocrSettings.model)

    // 获取可用的 AI 提供商列表
    val providers = settings.providers
    val selectedProvider = providers.find { it.id == ocrSettings.aiProviderId }

    var showProviderDialog by remember { mutableStateOf(false) }
    var showModelDialog by remember { mutableStateOf(false) }
    var showPromptDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = { Text("OCR") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Lucide.ArrowLeft,
                                contentDescription = "返回"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "识别模型",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "用于打野助手框选屏幕内容进行 OCR（识别并提取 API Key / URL）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ML Kit Latin 选项
            OcrModelCard(
                model = OcrModel.MLKIT_LATIN,
                isSelected = selectedModel == OcrModel.MLKIT_LATIN,
                description = "适合英文/数字（API Key、URL 识别更稳）",
                onClick = {
                    settingsManager.updateOcrSettings(ocrSettings.copy(model = OcrModel.MLKIT_LATIN.name))
                }
            )

            // ML Kit Chinese 选项
            OcrModelCard(
                model = OcrModel.MLKIT_CHINESE,
                isSelected = selectedModel == OcrModel.MLKIT_CHINESE,
                description = "适合包含中文界面的截图（会自动识别英文/数字）",
                onClick = {
                    settingsManager.updateOcrSettings(ocrSettings.copy(model = OcrModel.MLKIT_CHINESE.name))
                }
            )

            // AI Vision 选项
            OcrModelCard(
                model = OcrModel.AI_VISION,
                isSelected = selectedModel == OcrModel.AI_VISION,
                description = "使用已配置的 AI 提供商进行 OCR 识别（需要网络）",
                onClick = {
                    settingsManager.updateOcrSettings(ocrSettings.copy(model = OcrModel.AI_VISION.name))
                }
            )

            // 当选择 AI 视觉模型时，显示额外配置
            if (selectedModel == OcrModel.AI_VISION) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "AI 提供商配置",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))

                // AI 提供商选择
                OutlinedCard(
                    onClick = { showProviderDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ListItem(
                        headlineContent = {
                            Text(selectedProvider?.name ?: "请选择提供商")
                        },
                        supportingContent = {
                            if (selectedProvider == null && providers.isEmpty()) {
                                Text(
                                    "请先在设置中添加 AI 提供商",
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else if (selectedProvider == null) {
                                Text("点击选择一个提供商")
                            } else {
                                Text(selectedProvider.providerType.displayName)
                            }
                        },
                        trailingContent = {
                            Icon(Lucide.ChevronRight, contentDescription = null)
                        }
                    )
                }

                // 模型选择（如果已选择提供商）
                if (selectedProvider != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedCard(
                        onClick = { showModelDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ListItem(
                            headlineContent = {
                                Text(ocrSettings.aiModel.ifEmpty { "请选择模型" })
                            },
                            supportingContent = {
                                Text("用于 OCR 识别的视觉模型")
                            },
                            trailingContent = {
                                Icon(Lucide.ChevronRight, contentDescription = null)
                            }
                        )
                    }
                }

                // 自定义 Prompt
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedCard(
                    onClick = { showPromptDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ListItem(
                        headlineContent = { Text("识别提示词") },
                        supportingContent = {
                            Text(
                                ocrSettings.customPrompt.take(50) + if (ocrSettings.customPrompt.length > 50) "..." else "",
                                maxLines = 2
                            )
                        },
                        trailingContent = {
                            Icon(Lucide.Pencil, contentDescription = null)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "提示：首次使用 OCR 需要授权「录屏/屏幕捕获」权限。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // 提供商选择对话框
    if (showProviderDialog) {
        ProviderSelectionDialog(
            providers = providers,
            selectedId = ocrSettings.aiProviderId,
            onSelect = { providerId ->
                val provider = providers.find { it.id == providerId }
                settingsManager.updateOcrSettings(
                    ocrSettings.copy(
                        aiProviderId = providerId,
                        aiModel = provider?.selectedModel ?: provider?.availableModels?.firstOrNull() ?: ""
                    )
                )
                showProviderDialog = false
            },
            onDismiss = { showProviderDialog = false }
        )
    }

    // 模型选择对话框
    if (showModelDialog && selectedProvider != null) {
        ModelSelectionDialog(
            models = selectedProvider.availableModels,
            selectedModel = ocrSettings.aiModel,
            onSelect = { model ->
                settingsManager.updateOcrSettings(ocrSettings.copy(aiModel = model))
                showModelDialog = false
            },
            onDismiss = { showModelDialog = false }
        )
    }

    // Prompt 编辑对话框
    if (showPromptDialog) {
        PromptEditDialog(
            currentPrompt = ocrSettings.customPrompt,
            onSave = { newPrompt ->
                settingsManager.updateOcrSettings(ocrSettings.copy(customPrompt = newPrompt))
                showPromptDialog = false
            },
            onDismiss = { showPromptDialog = false }
        )
    }
}

@Composable
private fun OcrModelCard(
    model: OcrModel,
    isSelected: Boolean,
    description: String,
    onClick: () -> Unit
) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = isSelected, onClick = null)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.displayName,
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ProviderSelectionDialog(
    providers: List<ProviderConfig>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择 AI 提供商") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                if (providers.isEmpty()) {
                    Text(
                        "暂无可用的 AI 提供商，请先在设置中添加。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    providers.forEach { provider ->
                        val isSelected = provider.id == selectedId
                        OutlinedCard(
                            onClick = { onSelect(provider.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = isSelected, onClick = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = provider.name,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = provider.providerType.displayName,
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
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun ModelSelectionDialog(
    models: List<String>,
    selectedModel: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择视觉模型") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                if (models.isEmpty()) {
                    Text(
                        "该提供商暂无可用模型，请先在提供商设置中添加模型。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    models.forEach { model ->
                        val isSelected = model == selectedModel
                        OutlinedCard(
                            onClick = { onSelect(model) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = isSelected, onClick = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = model,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun PromptEditDialog(
    currentPrompt: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var editedPrompt by remember { mutableStateOf(currentPrompt) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑识别提示词") },
        text = {
            Column {
                Text(
                    "自定义 AI 识别图片时使用的提示词：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = editedPrompt,
                    onValueChange = { editedPrompt = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    textStyle = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { editedPrompt = OcrSettings.DEFAULT_OCR_PROMPT }
                ) {
                    Text("恢复默认")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(editedPrompt) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
