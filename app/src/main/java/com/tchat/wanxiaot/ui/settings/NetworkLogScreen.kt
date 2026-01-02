package com.tchat.wanxiaot.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tchat.network.log.NetworkLogEntry
import com.tchat.network.log.NetworkLogStatus
import com.tchat.network.log.NetworkLogger
import org.json.JSONObject

/**
 * 网络日志查看界面
 *
 * 展示 AI API 的请求和响应信息，便于调试
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkLogScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var logs by remember { mutableStateOf(NetworkLogger.getLogs()) }

    // 监听日志变化
    DisposableEffect(Unit) {
        val listener = { logs = NetworkLogger.getLogs() }
        NetworkLogger.addListener(listener)
        onDispose {
            NetworkLogger.removeListener(listener)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("网络日志") },
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
                ),
                actions = {
                    // 刷新按钮
                    IconButton(onClick = { logs = NetworkLogger.getLogs() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "刷新"
                        )
                    }
                    // 清除按钮
                    IconButton(onClick = {
                        NetworkLogger.clear()
                        logs = emptyList()
                        Toast.makeText(context, "日志已清除", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "清除"
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "暂无网络日志",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "发送消息后这里会显示请求和响应信息",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
            ) {
                items(logs, key = { it.id }) { entry ->
                    NetworkLogItem(
                        entry = entry,
                        onCopy = { text ->
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Network Log", text)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NetworkLogItem(
    entry: NetworkLogEntry,
    onCopy: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(12.dp)
        ) {
            // 头部信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 状态指示器
                    StatusIndicator(entry.status)

                    // Provider 和 Model
                    Column {
                        Text(
                            text = entry.provider,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = entry.model,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 时间和耗时
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = entry.formattedTimestamp(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        entry.durationMs?.let { ms ->
                            Text(
                                text = "${ms}ms",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (ms > 5000) MaterialTheme.colorScheme.error
                                       else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 展开/收起按钮
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "收起" else "展开",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // URL
            Text(
                text = entry.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )

            // 错误信息
            entry.error?.let { error ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            // 展开的详细内容
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HorizontalDivider()

                    // 请求体
                    JsonSection(
                        title = "请求 (Request Body)",
                        json = entry.requestBody,
                        onCopy = onCopy
                    )

                    // 响应体
                    entry.responseBody?.let { response ->
                        HorizontalDivider()
                        JsonSection(
                            title = "响应 (Response)",
                            json = response,
                            onCopy = onCopy,
                            isResponse = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusIndicator(status: NetworkLogStatus) {
    val color = when (status) {
        NetworkLogStatus.PENDING -> Color(0xFFFFA500) // Orange
        NetworkLogStatus.SUCCESS -> Color(0xFF4CAF50) // Green
        NetworkLogStatus.ERROR -> Color(0xFFF44336) // Red
    }

    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun JsonSection(
    title: String,
    json: String,
    onCopy: (String) -> Unit,
    isResponse: Boolean = false
) {
    val formattedJson = remember(json) {
        try {
            if (json.trim().startsWith("{")) {
                JSONObject(json).toString(2)
            } else {
                json
            }
        } catch (e: Exception) {
            json
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            IconButton(
                onClick = { onCopy(formattedJson) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "复制",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = if (isResponse) MaterialTheme.colorScheme.surfaceContainerHigh
                   else MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                Text(
                    text = formattedJson,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}
