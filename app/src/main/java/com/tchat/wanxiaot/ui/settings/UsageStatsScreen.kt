package com.tchat.wanxiaot.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tchat.data.database.dao.MessageDao
import com.tchat.data.database.dao.ModelUsageStat
import com.tchat.data.database.dao.ProviderUsageStat
import com.tchat.wanxiaot.settings.ProviderConfig
import com.tchat.wanxiaot.settings.TokenRecordingStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 使用统计数据
 */
data class UsageStats(
    val totalInputTokens: Long = 0,
    val totalOutputTokens: Long = 0,
    val totalMessages: Int = 0,
    val modelStats: List<ModelUsageStat> = emptyList(),
    val providerStats: List<ProviderUsageStat> = emptyList()
)

/**
 * 使用统计页面
 */
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

    // 加载统计数据
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

    // 清空确认对话框
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

    Scaffold(
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = { Text("使用统计") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Token 记录控制卡片
                TokenRecordingControlCard(
                    status = tokenRecordingStatus,
                    onStatusChange = onTokenRecordingStatusChange,
                    onClear = { showClearDialog = true }
                )

                // Token 统计卡片
                TokenStatsCard(stats)

                // 按提供商统计卡片
                ProviderStatsCard(stats, providers)

                // 模型调用统计卡片
                ModelStatsCard(stats)
            }
        }
    }
}

/**
 * Token 记录控制卡片
 */
@Composable
private fun TokenRecordingControlCard(
    status: TokenRecordingStatus,
    onStatusChange: (TokenRecordingStatus) -> Unit,
    onClear: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Token 记录控制",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            HorizontalDivider()

            // 当前状态显示
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "当前状态",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = when (status) {
                        TokenRecordingStatus.ENABLED -> "记录中"
                        TokenRecordingStatus.PAUSED -> "已暂停"
                        TokenRecordingStatus.DISABLED -> "已关闭"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = when (status) {
                        TokenRecordingStatus.ENABLED -> MaterialTheme.colorScheme.primary
                        TokenRecordingStatus.PAUSED -> MaterialTheme.colorScheme.tertiary
                        TokenRecordingStatus.DISABLED -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            // 控制按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 开始/继续按钮
                FilledTonalButton(
                    onClick = { onStatusChange(TokenRecordingStatus.ENABLED) },
                    enabled = status != TokenRecordingStatus.ENABLED,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("开始")
                }

                // 暂停按钮
                FilledTonalButton(
                    onClick = { onStatusChange(TokenRecordingStatus.PAUSED) },
                    enabled = status == TokenRecordingStatus.ENABLED,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("暂停")
                }

                // 关闭按钮
                FilledTonalButton(
                    onClick = { onStatusChange(TokenRecordingStatus.DISABLED) },
                    enabled = status != TokenRecordingStatus.DISABLED,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("关闭")
                }
            }

            // 清空按钮
            OutlinedButton(
                onClick = onClear,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("清空统计数据")
            }
        }
    }
}

@Composable
private fun TokenStatsCard(stats: UsageStats) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Token 统计",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            HorizontalDivider()

            // 上行 Token
            StatRow(
                label = "上行 Token (输入)",
                value = formatNumber(stats.totalInputTokens)
            )

            // 下行 Token
            StatRow(
                label = "下行 Token (输出)",
                value = formatNumber(stats.totalOutputTokens)
            )

            // 总 Token
            StatRow(
                label = "总 Token",
                value = formatNumber(stats.totalInputTokens + stats.totalOutputTokens),
                isHighlighted = true
            )
        }
    }
}

/**
 * 按提供商统计卡片
 */
@Composable
private fun ProviderStatsCard(stats: UsageStats, providers: List<ProviderConfig>) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "按提供商统计",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            HorizontalDivider()

            if (stats.providerStats.isNotEmpty()) {
                stats.providerStats.forEach { providerStat ->
                    // 查找提供商名称
                    val providerName = providers.find { it.id == providerStat.providerId }?.name
                        ?: providerStat.providerId.take(8)

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = providerName,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "↑${formatNumber(providerStat.inputTokens)} / ↓${formatNumber(providerStat.outputTokens)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "${providerStat.messageCount} 次调用",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(top = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
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
}

@Composable
private fun ModelStatsCard(stats: UsageStats) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "模型调用统计",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            HorizontalDivider()

            // 总调用次数
            StatRow(
                label = "总调用次数",
                value = "${stats.totalMessages} 次",
                isHighlighted = true
            )

            if (stats.modelStats.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "各模型调用次数",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                stats.modelStats.forEach { modelStat ->
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
            style = if (isHighlighted) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.bodyMedium
            },
            fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
            color = if (isHighlighted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

/**
 * 格式化数字显示
 */
private fun formatNumber(number: Long): String {
    return when {
        number >= 1_000_000 -> String.format("%.2fM", number / 1_000_000.0)
        number >= 1_000 -> String.format("%.2fK", number / 1_000.0)
        else -> number.toString()
    }
}
