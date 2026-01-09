package com.tchat.wanxiaot.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val isSpeaking by ttsService?.isSpeaking?.collectAsState() ?: remember { mutableStateOf(false) }

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
                    text = "点击下方按钮测试当前语音设置",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            if (isSpeaking) {
                                ttsService?.stop()
                            } else {
                                ttsService?.speak("你好，这是语音朗读测试。当前语速为 ${String.format("%.1f", ttsSettings.speechRate)} 倍，音调为 ${String.format("%.1f", ttsSettings.pitch)}。")
                            }
                        },
                        enabled = ttsSettings.enabled && ttsService != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (isSpeaking) Lucide.Square else Lucide.Play,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isSpeaking) "停止" else "播放测试")
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
