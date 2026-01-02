package com.tchat.wanxiaot.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tchat.data.database.dao.MessageDao
import com.tchat.data.database.dao.ModelUsageStat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 使用统计数据
 */
data class UsageStats(
    val totalInputTokens: Long = 0,
    val totalOutputTokens: Long = 0,
    val totalMessages: Int = 0,
    val modelStats: List<ModelUsageStat> = emptyList()
)

/**
 * 使用统计页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageStatsScreen(
    messageDao: MessageDao,
    onBack: () -> Unit,
    showTopBar: Boolean = true
) {
    val scope = rememberCoroutineScope()
    var stats by remember { mutableStateOf(UsageStats()) }
    var isLoading by remember { mutableStateOf(true) }

    // 加载统计数据
    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            val inputTokens = messageDao.getTotalInputTokens() ?: 0L
            val outputTokens = messageDao.getTotalOutputTokens() ?: 0L
            val totalMessages = messageDao.getTotalAssistantMessages()
            val modelStats = messageDao.getModelUsageStats()

            stats = UsageStats(
                totalInputTokens = inputTokens,
                totalOutputTokens = outputTokens,
                totalMessages = totalMessages,
                modelStats = modelStats
            )
            isLoading = false
        }
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
                // Token 统计卡片
                TokenStatsCard(stats)

                // 模型调用统计卡片
                ModelStatsCard(stats)
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
