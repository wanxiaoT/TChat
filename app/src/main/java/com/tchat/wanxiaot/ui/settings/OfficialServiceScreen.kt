package com.tchat.wanxiaot.ui.settings

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tchat.wanxiaot.settings.ProviderAuthType
import com.tchat.wanxiaot.settings.SettingsManager
import com.tchat.wanxiaot.ui.components.AppEmptyState
import com.tchat.wanxiaot.ui.components.AppPageScaffold
import com.tchat.wanxiaot.ui.components.AppPill
import com.tchat.wanxiaot.ui.components.AppSectionCard
import com.tchat.wanxiaot.util.NaapiDeviceInfo
import com.tchat.wanxiaot.util.NaapiLicenseClient
import com.tchat.wanxiaot.util.NaapiOrderRecord
import com.tchat.wanxiaot.util.NaapiTChatSupport
import com.tchat.wanxiaot.util.NaapiUsageLogItem
import com.tchat.wanxiaot.util.NaapiUsageSummary
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficialServiceScreen(
    settingsManager: SettingsManager,
    onBack: () -> Unit,
    showTopBar: Boolean = true
) {
    val context = LocalContext.current
    val settings by settingsManager.settings.collectAsStateWithLifecycle()
    val provider = settings.providers.firstOrNull { it.isOfficialService() }
    val scope = rememberCoroutineScope()
    val client = remember { NaapiLicenseClient(OkHttpClient()) }

    var summary by remember { mutableStateOf<NaapiUsageSummary?>(null) }
    var devices by remember { mutableStateOf<List<NaapiDeviceInfo>>(emptyList()) }
    var usageLogs by remember { mutableStateOf<List<NaapiUsageLogItem>>(emptyList()) }
    var orders by remember { mutableStateOf<List<NaapiOrderRecord>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        val officialProvider = provider ?: return
        val credential = officialProvider.apiKey.takeIf {
            officialProvider.authType != ProviderAuthType.NONE && it.isNotBlank()
        }
        if (credential.isNullOrBlank()) {
            message = "请先在“服务商”里购买或激活官方服务，保存许可证后再查看。"
            return
        }
        scope.launch {
            isLoading = true
            message = null
            try {
                summary = client.fetchUsageSummary(officialProvider.resolvedEndpoint(), credential)
                devices = client.fetchDevices(officialProvider.resolvedEndpoint(), credential)
                val detailErrors = mutableListOf<String>()
                usageLogs = runCatching {
                    client.fetchUsageLogs(officialProvider.resolvedEndpoint(), credential)
                }.getOrElse {
                    detailErrors += "用量明细暂不可读：${it.message ?: "服务暂无返回"}"
                    emptyList()
                }
                orders = runCatching {
                    client.fetchOrders(officialProvider.resolvedEndpoint(), credential)
                }.getOrElse {
                    detailErrors += "订单记录暂不可读：${it.message ?: "服务暂无返回"}"
                    emptyList()
                }
                if (detailErrors.isNotEmpty()) {
                    message = detailErrors.joinToString("；")
                }
            } catch (e: Exception) {
                message = e.message ?: "官方服务信息读取失败"
            } finally {
                isLoading = false
            }
        }
    }

    fun openOfficialPortal(path: String = "") {
        val officialProvider = provider ?: return
        val portalBase = NaapiTChatSupport.portalBaseFromEndpoint(officialProvider.resolvedEndpoint()).trimEnd('/')
        val url = if (path.isBlank()) portalBase else "$portalBase/${path.trimStart('/')}"
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }.onFailure {
            message = "无法打开官方服务页面：${it.message ?: url}"
        }
    }

    LaunchedEffect(provider?.id, provider?.apiKey) {
        if (provider != null && provider.apiKey.isNotBlank()) {
            refresh()
        }
    }

    AppPageScaffold(
        title = "服务与套餐",
        eyebrow = "Official Service",
        subtitle = "官方服务状态、余额、设备与用量",
        showTopBar = showTopBar,
        onBack = onBack
    ) { innerPadding ->
        if (provider == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                AppEmptyState(
                    icon = Icons.Default.CreditCard,
                    title = "尚未配置官方服务",
                    description = "前往“服务商”添加 TChat 官方服务，可购买套餐或填入兑换码。"
                )
            }
            return@AppPageScaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            message?.let {
                AppSectionCard(
                    title = "提示",
                    description = it
                ) {}
            }

            SummaryCard(summary)

            UsageLogsCard(usageLogs, isLoading)

            AppSectionCard(
                title = "设备管理",
                description = "展示当前许可证关联的设备。服务端支持吊销、重置与限速时，这里会同步展示。"
            ) {
                if (devices.isEmpty()) {
                    Text(
                        text = if (isLoading) "正在读取设备..." else "暂无设备数据",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    devices.forEachIndexed { index, device ->
                        DeviceRow(device)
                        if (index != devices.lastIndex) HorizontalDivider()
                    }
                }
            }

            OrdersCard(orders, isLoading)

            AppSectionCard(
                title = "操作",
                description = "余额刷新在本页完成，续费、套餐升级与发票页面由 t.naapi.cc 提供。"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(
                        onClick = { refresh() },
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.size(8.dp))
                        Text(if (isLoading) "正在刷新" else "刷新官方服务状态")
                    }
                    FilledTonalButton(
                        onClick = { openOfficialPortal("pricing") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CreditCard, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("续费或升级套餐")
                    }
                    FilledTonalButton(
                        onClick = { openOfficialPortal("orders") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("查看完整订单记录")
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageLogsCard(
    logs: List<NaapiUsageLogItem>,
    isLoading: Boolean
) {
    AppSectionCard(
        title = "最近请求",
        description = "展示官方服务最近的模型调用、费用与 token 统计。"
    ) {
        if (logs.isEmpty()) {
            Text(
                text = if (isLoading) "正在读取最近请求..." else "暂无最近请求数据",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@AppSectionCard
        }
        logs.take(8).forEachIndexed { index, item ->
            UsageLogRow(item)
            if (index != logs.take(8).lastIndex) HorizontalDivider()
        }
    }
}

@Composable
private fun OrdersCard(
    orders: List<NaapiOrderRecord>,
    isLoading: Boolean
) {
    AppSectionCard(
        title = "订单记录",
        description = "显示最近的套餐购买与支付状态。"
    ) {
        if (orders.isEmpty()) {
            Text(
                text = if (isLoading) "正在读取订单..." else "暂无订单数据",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@AppSectionCard
        }
        orders.take(6).forEachIndexed { index, order ->
            OrderRecordRow(order)
            if (index != orders.take(6).lastIndex) HorizontalDivider()
        }
    }
}

@Composable
private fun UsageLogRow(item: NaapiUsageLogItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.model.ifBlank { "模型请求" },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOf(
                    item.createdAt.takeIf { it.isNotBlank() },
                    item.deviceName.takeIf { it.isNotBlank() },
                    item.status.takeIf { it.isNotBlank() },
                    "输入 ${item.inputTokens}",
                    "输出 ${item.outputTokens}"
                ).filterNotNull().joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = formatCurrency(item.amount, item.currency),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OrderRecordRow(order: NaapiOrderRecord) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.CreditCard,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = order.planName.ifBlank { order.orderNo.ifBlank { "套餐订单" } },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOf(
                    order.status.takeIf { it.isNotBlank() },
                    order.createdAt.takeIf { it.isNotBlank() }?.let { "创建 $it" },
                    order.paidAt.takeIf { it.isNotBlank() }?.let { "支付 $it" }
                ).filterNotNull().joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = formatCurrency(order.amount, order.currency),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SummaryCard(summary: NaapiUsageSummary?) {
    AppSectionCard(
        title = "用量透明",
        description = "余额、今日、本月与请求数。"
    ) {
        if (summary == null) {
            Text(
                            text = "暂无用量数据，请刷新或检查许可证。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@AppSectionCard
        }

        StatRow("剩余额度", formatCurrency(summary.balanceAmount, summary.currency), true)
        HorizontalDivider()
        StatRow("今日用量", formatCurrency(summary.todayAmount, summary.currency))
        HorizontalDivider()
        StatRow("本月用量", formatCurrency(summary.monthAmount, summary.currency))
        HorizontalDivider()
        StatRow("请求次数", "${summary.totalRequests} 次")
        summary.expiresAt?.let {
            HorizontalDivider()
            StatRow("到期时间", it)
        }
    }
}

@Composable
private fun DeviceRow(device: NaapiDeviceInfo) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Devices,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = device.name.ifBlank { "设备" },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (device.current) AppPill(text = "当前设备")
                if (device.revoked) AppPill(text = "已停用")
            }
            Text(
                text = listOf(
                    device.gatewayKeyPrefix.takeIf { it.isNotBlank() }?.let { "Key: $it" },
                    device.lastUsedAt.takeIf { it.isNotBlank() }?.let { "最后使用: $it" }
                ).filterNotNull().joinToString(" · ").ifBlank { device.id },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String,
    highlighted: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value,
            style = if (highlighted) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal,
            color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatCurrency(amount: Double, currency: String): String {
    return when (currency.uppercase()) {
        "CNY", "RMB" -> "¥%.2f".format(amount)
        "USD" -> "$%.2f".format(amount)
        else -> "${currency.uppercase()} %.2f".format(amount)
    }
}
