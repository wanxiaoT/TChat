package com.tchat.wanxiaot.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.tchat.wanxiaot.ui.components.AppHeroCard
import com.tchat.wanxiaot.ui.components.AppPageScaffold
import com.tchat.wanxiaot.ui.components.AppPill
import com.tchat.wanxiaot.ui.components.AppSectionCard
import com.tchat.wanxiaot.ui.components.AppSectionSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrSettingsScreen(
    settingsManager: SettingsManager,
    onBack: () -> Unit,
    showTopBar: Boolean = true
) {
    val settings by settingsManager.settings.collectAsStateWithLifecycle()
    val ocrSettings = settings.ocrSettings
    val selectedModel = OcrModel.fromName(ocrSettings.model)

    // 获取可用的 AI 提供商列表
    val providers = settings.providers
    val selectedProvider = providers.find { it.id == ocrSettings.aiProviderId }

    var showProviderDialog by remember { mutableStateOf(false) }
    var showModelDialog by remember { mutableStateOf(false) }
    var showPromptDialog by remember { mutableStateOf(false) }

    AppPageScaffold(
        title = "OCR",
        eyebrow = "Recognition",
        subtitle = "识别模型、AI Provider 与识别提示词",
        showTopBar = showTopBar,
        onBack = onBack
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            AppHeroCard(
                eyebrow = "Screen Parsing",
                title = "把截图识别链路配置得更稳一些",
                description = "OCR 会直接影响密钥、URL 与界面文本提取质量，模型选择不能随便凑合。",
                icon = Lucide.Pencil,
                trailing = {
                    AppPill(text = selectedModel.displayName)
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            AppSectionCard(
                title = "识别模型",
                description = "用于打野助手框选屏幕内容进行 OCR（识别并提取 API Key / URL）。"
            ) {
                OcrModelCard(
                    model = OcrModel.MLKIT_LATIN,
                    isSelected = selectedModel == OcrModel.MLKIT_LATIN,
                    description = "适合英文/数字（API Key、URL 识别更稳）",
                    onClick = {
                        settingsManager.updateOcrSettings(ocrSettings.copy(model = OcrModel.MLKIT_LATIN.name))
                    }
                )

                OcrModelCard(
                    model = OcrModel.MLKIT_CHINESE,
                    isSelected = selectedModel == OcrModel.MLKIT_CHINESE,
                    description = "适合包含中文界面的截图（会自动识别英文/数字）",
                    onClick = {
                        settingsManager.updateOcrSettings(ocrSettings.copy(model = OcrModel.MLKIT_CHINESE.name))
                    }
                )

                OcrModelCard(
                    model = OcrModel.AI_VISION,
                    isSelected = selectedModel == OcrModel.AI_VISION,
                    description = "使用已配置的 AI 提供商进行 OCR 识别（需要网络）",
                    onClick = {
                        settingsManager.updateOcrSettings(ocrSettings.copy(model = OcrModel.AI_VISION.name))
                    }
                )
            }

            // 当选择 AI 视觉模型时，显示额外配置
            if (selectedModel == OcrModel.AI_VISION) {
                Spacer(modifier = Modifier.height(16.dp))

                AppSectionCard(
                    title = "AI 提供商配置",
                    description = "当使用 AI Vision 时，需要额外指定服务商、模型和识别提示词。"
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        tonalElevation = 0.dp
                    ) {
                        ListItem(
                            modifier = Modifier.clickable { showProviderDialog = true },
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

                    if (selectedProvider != null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                            tonalElevation = 0.dp
                        ) {
                            ListItem(
                                modifier = Modifier.clickable { showModelDialog = true },
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

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        tonalElevation = 0.dp
                    ) {
                        ListItem(
                            modifier = Modifier.clickable { showPromptDialog = true },
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
            }

            Spacer(modifier = Modifier.height(12.dp))
            AppSectionCard(title = "提示") {
                Text(
                    text = "首次使用 OCR 需要授权「录屏/屏幕捕获」权限。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        tonalElevation = 0.dp
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
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onSelect(provider.id) },
                            shape = MaterialTheme.shapes.large,
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                            tonalElevation = 0.dp
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
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onSelect(model) },
                            shape = MaterialTheme.shapes.large,
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                            tonalElevation = 0.dp
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
