package com.tchat.wanxiaot.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tchat.data.database.dao.MessageDao
import com.tchat.data.database.dao.ModelUsageStat
import com.tchat.data.database.dao.ProviderUsageStat
import com.tchat.wanxiaot.settings.ProviderConfig
import com.tchat.wanxiaot.settings.TokenRecordingStatus
import com.tchat.wanxiaot.ui.components.AppPageScaffold
import com.tchat.wanxiaot.ui.components.AppPill
import com.tchat.wanxiaot.ui.components.AppSectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class UsageStats(
    val totalInputTokens: Long = 0,
    val totalOutputTokens: Long = 0,
    val totalMessages: Int = 0,
    val modelStats: List<ModelUsageStat> = emptyList(),
    val providerStats: List<ProviderUsageStat> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageStatsScreen(
    messageDao: MessageDao,
    onBack: () -> Unit,
    showTopBar: Boolean = true,
    providers: List<ProviderConfig> = emptyList(),
    tokenRecordingStatus: TokenRecordingStatus = TokenRecordingStatus.ENABLED,
    onTokenRecordingStatusChange: (TokenRecordingStatus) -> Unit = {},
    onClearTokenStats: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var stats by remember { mutableStateOf(UsageStats()) }
    var isLoading by remember { mutableStateOf(true) }
    var showClearDialog by remember { mutableStateOf(false) }

    fun loadStats() {
        scope.launch(Dispatchers.IO) {
            val inputTokens = messageDao.getTotalInputTokens() ?: 0L
            val outputTokens = messageDao.getTotalOutputTokens() ?: 0L
            val totalMessages = messageDao.getTotalAssistantMessages()
            val modelStats = messageDao.getModelUsageStats()
            val providerStats = messageDao.getProviderUsageStats()

            stats = UsageStats(
                totalInputTokens = inputTokens,
                totalOutputTokens = outputTokens,
                totalMessages = totalMessages,
                modelStats = modelStats,
                providerStats = providerStats
            )
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadStats()
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空统计数据") },
            text = { Text("确定要清空所有 Token 统计数据吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            messageDao.clearAllTokenStats()
                            loadStats()
                        }
                        onClearTokenStats()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("清空")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    AppPageScaffold(
        title = "使用统计",
        eyebrow = "Usage Metrics",
        subtitle = "Token、提供商与模型调用情况",
        showTopBar = showTopBar,
        onBack = onBack
    ) { innerPadding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

                TokenRecordingControlCard(
                    status = tokenRecordingStatus,
                    onStatusChange = onTokenRecordingStatusChange,
                    onClear = { showClearDialog = true }
                )

                TokenStatsCard(stats)
                ProviderStatsCard(stats, providers)
                ModelStatsCard(stats)
            }
        }
    }
}

@Composable
private fun TokenRecordingControlCard(
    status: TokenRecordingStatus,
    onStatusChange: (TokenRecordingStatus) -> Unit,
    onClear: () -> Unit
) {
    AppSectionCard(
        title = "Token 记录控制",
        description = "可以临时暂停、彻底关闭，或直接清空现有统计。"
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "当前状态",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            AppPill(
                text = when (status) {
                    TokenRecordingStatus.ENABLED -> "记录中"
                    TokenRecordingStatus.PAUSED -> "已暂停"
                    TokenRecordingStatus.DISABLED -> "已关闭"
                },
                containerColor = when (status) {
                    TokenRecordingStatus.ENABLED -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                    TokenRecordingStatus.PAUSED -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f)
                    TokenRecordingStatus.DISABLED -> MaterialTheme.colorScheme.surfaceContainerHigh
                },
                contentColor = when (status) {
                    TokenRecordingStatus.ENABLED -> MaterialTheme.colorScheme.onSecondaryContainer
                    TokenRecordingStatus.PAUSED -> MaterialTheme.colorScheme.onTertiaryContainer
                    TokenRecordingStatus.DISABLED -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = { onStatusChange(TokenRecordingStatus.ENABLED) },
                enabled = status != TokenRecordingStatus.ENABLED,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(4.dp))
                Text("开始")
            }
            FilledTonalButton(
                onClick = { onStatusChange(TokenRecordingStatus.PAUSED) },
                enabled = status == TokenRecordingStatus.ENABLED,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(4.dp))
                Text("暂停")
            }
            FilledTonalButton(
                onClick = { onStatusChange(TokenRecordingStatus.DISABLED) },
                enabled = status != TokenRecordingStatus.DISABLED,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(4.dp))
                Text("关闭")
            }
        }

        OutlinedButton(
            onClick = onClear,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.size(4.dp))
            Text("清空统计数据")
        }
    }
}

@Composable
private fun TokenStatsCard(stats: UsageStats) {
    AppSectionCard(
        title = "Token 统计",
        description = "输入、输出与总量概览。"
    ) {
        StatRow(label = "上行 Token (输入)", value = formatNumber(stats.totalInputTokens))
        HorizontalDivider()
        StatRow(label = "下行 Token (输出)", value = formatNumber(stats.totalOutputTokens))
        HorizontalDivider()
        StatRow(
            label = "总 Token",
            value = formatNumber(stats.totalInputTokens + stats.totalOutputTokens),
            isHighlighted = true
        )
    }
}

@Composable
private fun ProviderStatsCard(stats: UsageStats, providers: List<ProviderConfig>) {
    AppSectionCard(
        title = "按提供商统计",
        description = "看清不同服务商的调用占比与 Token 分布。"
    ) {
        if (stats.providerStats.isNotEmpty()) {
            stats.providerStats.forEachIndexed { index, providerStat ->
                val providerName = providers.find { it.id == providerStat.providerId }?.name
                    ?: providerStat.providerId.take(8)

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = providerName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "↑${formatNumber(providerStat.inputTokens)} / ↓${formatNumber(providerStat.outputTokens)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        AppPill(text = "${providerStat.messageCount} 次调用")
                    }
                }
                if (index != stats.providerStats.lastIndex) {
                    HorizontalDivider()
                }
            }
        } else {
            Text(
                text = "暂无提供商统计数据",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ModelStatsCard(stats: UsageStats) {
    AppSectionCard(
        title = "模型调用统计",
        description = "定位调用最频繁的模型，便于后续做成本和策略优化。"
    ) {
        StatRow(
            label = "总调用次数",
            value = "${stats.totalMessages} 次",
            isHighlighted = true
        )

        if (stats.modelStats.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            stats.modelStats.forEachIndexed { index, modelStat ->
                HorizontalDivider()
                StatRow(
                    label = modelStat.modelName,
                    value = "${modelStat.count} 次"
                )
            }
        } else {
            Text(
                text = "暂无模型调用记录",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String,
    isHighlighted: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = value,
            style = if (isHighlighted) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
            color = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatNumber(number: Long): String {
    return when {
        number >= 1_000_000 -> String.format("%.2fM", number / 1_000_000.0)
        number >= 1_000 -> String.format("%.2fK", number / 1_000.0)
        else -> number.toString()
    }
}
