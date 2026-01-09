package com.tchat.wanxiaot.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.Square
import com.tchat.data.tts.TtsService
import com.tchat.wanxiaot.settings.TtsSettings

/**
 * TTS 设置页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsSettingsScreen(
    ttsSettings: TtsSettings,
    ttsService: TtsService?,
    onSettingsChange: (TtsSettings) -> Unit,
    onBack: () -> Unit,
    showTopBar: Boolean = true
) {
    // 如果传入的 ttsService 为 null，尝试从 Context 获取
    val context = LocalContext.current
    val actualTtsService = remember(ttsService) {
        ttsService ?: TtsService.getInstance(context)
    }

    val isSpeaking by actualTtsService.isSpeaking.collectAsState()
    val initStatus by actualTtsService.initStatus.collectAsState()
    val availableEngines by actualTtsService.availableEngines.collectAsState()

    // 根据初始化状态判断是否可用
    val isAvailable = initStatus is TtsService.InitStatus.Ready
    val isInitializing = initStatus is TtsService.InitStatus.Initializing
    val errorMessage = (initStatus as? TtsService.InitStatus.Failed)?.reason

    // 当设置中的引擎与当前引擎不同时，切换引擎
    LaunchedEffect(ttsSettings.enginePackage) {
        if (actualTtsService.getCurrentEngine() != ttsSettings.enginePackage) {
            actualTtsService.setEngine(ttsSettings.enginePackage)
        }
    }

    Scaffold(
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = { Text("语音朗读 (TTS)") },
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
        ) {
            // 启用 TTS 开关
            ListItem(
                headlineContent = { Text("启用语音朗读") },
                supportingContent = { Text("开启后可朗读 AI 回复内容") },
                trailingContent = {
                    Switch(
                        checked = ttsSettings.enabled,
                        onCheckedChange = { enabled ->
                            onSettingsChange(ttsSettings.copy(enabled = enabled))
                        }
                    )
                }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // 自动朗读开关
            ListItem(
                headlineContent = { Text("自动朗读") },
                supportingContent = { Text("AI 回复完成后自动开始朗读") },
                trailingContent = {
                    Switch(
                        checked = ttsSettings.autoSpeak,
                        enabled = ttsSettings.enabled,
                        onCheckedChange = { autoSpeak ->
                            onSettingsChange(ttsSettings.copy(autoSpeak = autoSpeak))
                        }
                    )
                }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // TTS 引擎选择
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "TTS 引擎",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (availableEngines.isEmpty()) "正在加载引擎列表..." else "选择用于语音合成的引擎",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (availableEngines.isNotEmpty()) {
                    availableEngines.forEach { engine ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = ttsSettings.enginePackage == engine.packageName,
                                onClick = {
                                    onSettingsChange(ttsSettings.copy(enginePackage = engine.packageName))
                                },
                                enabled = ttsSettings.enabled
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = engine.name,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (engine.packageName.isNotBlank()) {
                                    Text(
                                        text = engine.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (engine.isDefault && engine.packageName.isNotBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = "默认",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                } else if (isInitializing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // 语速设置
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "语速",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = String.format("%.1fx", ttsSettings.speechRate),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = ttsSettings.speechRate,
                    onValueChange = { rate ->
                        onSettingsChange(ttsSettings.copy(speechRate = rate))
                    },
                    valueRange = 0.5f..2.0f,
                    steps = 5,
                    enabled = ttsSettings.enabled
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "0.5x",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "2.0x",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // 音调设置
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "音调",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = String.format("%.1f", ttsSettings.pitch),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = ttsSettings.pitch,
                    onValueChange = { pitch ->
                        onSettingsChange(ttsSettings.copy(pitch = pitch))
                    },
                    valueRange = 0.5f..1.5f,
                    steps = 4,
                    enabled = ttsSettings.enabled
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "低",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "高",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // 测试朗读
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "测试朗读",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when {
                        isInitializing -> "TTS 引擎正在初始化..."
                        errorMessage != null -> errorMessage
                        else -> "点击下方按钮测试当前语音设置"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isInitializing || errorMessage != null)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            if (isSpeaking) {
                                actualTtsService.stop()
                            } else {
                                // 先更新设置再播放
                                actualTtsService.updateSettings(
                                    rate = ttsSettings.speechRate,
                                    pitchValue = ttsSettings.pitch,
                                    locale = java.util.Locale.forLanguageTag(ttsSettings.language)
                                )
                                actualTtsService.speak("你好，这是语音朗读测试。当前语速为 ${String.format("%.1f", ttsSettings.speechRate)} 倍，音调为 ${String.format("%.1f", ttsSettings.pitch)}。")
                            }
                        },
                        enabled = isAvailable,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isInitializing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (isSpeaking) Lucide.Square else Lucide.Play,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            when {
                                isInitializing -> "初始化中..."
                                errorMessage != null -> "不可用"
                                isSpeaking -> "停止"
                                else -> "播放测试"
                            }
                        )
                    }
                }

                // 如果初始化失败，显示打开系统设置的按钮
                if (errorMessage != null || isInitializing) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            // 打开系统 TTS 设置
                            try {
                                val intent = Intent("com.android.settings.TTS_SETTINGS")
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // 如果上面的 Intent 不可用，尝试通用设置
                                try {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    context.startActivity(intent)
                                } catch (e2: Exception) {
                                    // 最后尝试打开通用设置
                                    val intent = Intent(Settings.ACTION_SETTINGS)
                                    context.startActivity(intent)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("打开系统 TTS 设置")
                    }
                }
            }

            // TTS 引擎说明
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "关于 TTS 引擎",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "本功能使用系统内置的 TTS 引擎。如需更好的语音效果，可在系统设置中安装第三方 TTS 引擎（如 Google TTS、讯飞语音等）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
