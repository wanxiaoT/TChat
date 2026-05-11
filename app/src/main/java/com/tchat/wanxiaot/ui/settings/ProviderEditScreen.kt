package com.tchat.wanxiaot.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tchat.wanxiaot.settings.AIProviderType
import com.tchat.wanxiaot.settings.ApiKeyEntry
import com.tchat.wanxiaot.settings.ApiKeyStatus
import com.tchat.wanxiaot.settings.KeySelectionStrategy
import com.tchat.wanxiaot.settings.ModelCapabilityConfig
import com.tchat.wanxiaot.settings.ModelCustomParams
import com.tchat.wanxiaot.settings.ProviderAuthType
import com.tchat.wanxiaot.settings.ProviderBillingMode
import com.tchat.wanxiaot.settings.ProviderConfig
import com.tchat.wanxiaot.settings.ServiceMode
import com.tchat.wanxiaot.settings.SettingsManager
import com.tchat.wanxiaot.ui.components.AppPageScaffold
import com.tchat.wanxiaot.ui.components.AppPill
import com.tchat.wanxiaot.ui.components.SettingsGroupCard
import com.tchat.wanxiaot.ui.components.QRCodeDialog
import com.tchat.wanxiaot.util.NaapiLicenseClient
import com.tchat.wanxiaot.util.NaapiModelCatalogItem
import com.tchat.wanxiaot.util.NaapiOrderStatus
import com.tchat.wanxiaot.util.NaapiPendingOrder
import com.tchat.wanxiaot.util.NaapiPlan
import com.tchat.wanxiaot.util.NaapiTChatSupport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.UUID

/**
 * 服务商编辑页面 - Material You 设计
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderEditScreen(
    provider: ProviderConfig?,
    settingsManager: SettingsManager,
    onBack: () -> Unit,
    onSave: (ProviderConfig) -> Unit,
    onDelete: (() -> Unit)?,
    isNewProvider: Boolean = provider == null
) {
    val isNew = isNewProvider
    val providerId = remember(provider?.id) { provider?.id ?: UUID.randomUUID().toString() }
    val context = LocalContext.current

    var name by remember { mutableStateOf(provider?.name ?: "") }
    var providerType by remember { mutableStateOf(provider?.providerType ?: AIProviderType.OPENAI) }
    var serviceMode by remember { mutableStateOf(provider?.serviceMode ?: ServiceMode.CUSTOM) }
    var billingMode by remember { mutableStateOf(provider?.billingMode ?: ProviderBillingMode.USER_API_KEY) }
    var authType by remember { mutableStateOf(provider?.authType ?: ProviderAuthType.BEARER) }
    var apiKey by remember { mutableStateOf(provider?.apiKey ?: "") }
    var endpoint by remember { mutableStateOf(provider?.endpoint ?: providerType.defaultEndpoint) }
    var apiPath by remember { mutableStateOf(provider?.apiPath ?: providerType.defaultApiPath) }
    var modelsPath by remember { mutableStateOf(provider?.modelsPath ?: providerType.defaultModelsPath) }
    var imagesPath by remember { mutableStateOf(provider?.imagesPath ?: providerType.defaultImagesPath) }
    var embeddingsPath by remember { mutableStateOf(provider?.embeddingsPath ?: "/embeddings") }
    var modelCatalogPath by remember {
        mutableStateOf(provider?.modelCatalogPath ?: if (providerType == AIProviderType.NAAPI_TCHAT) "/api/tchat/model-catalog" else "")
    }
    var authHeaderName by remember { mutableStateOf(provider?.authHeaderName ?: "Authorization") }
    var authHeaderPrefix by remember { mutableStateOf(provider?.authHeaderPrefix ?: "Bearer ") }
    var useProxy by remember { mutableStateOf(provider?.useProxy ?: false) }
    var savedModels by remember {
        mutableStateOf(
            if (provider?.providerType == AIProviderType.NAAPI_TCHAT) {
                emptyList()
            } else {
                provider?.availableModels ?: emptyList()
            }
        )
    }
    var modelCapabilities by remember { mutableStateOf(provider?.modelCapabilities ?: emptyMap()) }
    var customHeaders by remember { mutableStateOf(provider?.customHeaders ?: emptyMap()) }
    var headerName by remember { mutableStateOf("") }
    var headerValue by remember { mutableStateOf("") }
    var redeemCode by remember { mutableStateOf("") }
    var selectedModel by remember {
        mutableStateOf(
            if (provider?.providerType == AIProviderType.NAAPI_TCHAT) {
                ""
            } else {
                provider?.selectedModel ?: savedModels.firstOrNull() ?: ""
            }
        )
    }

    // 多 Key 管理
    var apiKeys by remember { mutableStateOf(provider?.apiKeys ?: emptyList()) }
    var multiKeyEnabled by remember { mutableStateOf(provider?.multiKeyEnabled ?: false) }
    var keySelectionStrategy by remember { mutableStateOf(provider?.keySelectionStrategy ?: KeySelectionStrategy.ROUND_ROBIN) }
    var roundRobinIndex by remember { mutableStateOf(provider?.roundRobinIndex ?: 0) }
    var maxFailuresBeforeDisable by remember { mutableStateOf(provider?.maxFailuresBeforeDisable ?: 3) }
    var autoRecoveryMinutes by remember { mutableStateOf(provider?.autoRecoveryMinutes ?: 5) }

    // 多 Key UI 状态
    var showAddKeyDialog by remember { mutableStateOf(false) }
    var editingKey by remember { mutableStateOf<ApiKeyEntry?>(null) }
    var deletingKey by remember { mutableStateOf<ApiKeyEntry?>(null) }
    var strategyExpanded by remember { mutableStateOf(false) }
    var customModel by remember { mutableStateOf("") }

    var fetchedModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var showModelPicker by remember { mutableStateOf(false) }

    var typeExpanded by remember { mutableStateOf(false) }
    var isFetchingModels by remember { mutableStateOf(false) }
    var fetchError by remember { mutableStateOf<String?>(null) }
    var isActivatingNaapi by remember { mutableStateOf(false) }
    var naapiActivationMessage by remember { mutableStateOf<String?>(null) }
    var naapiActivationSuccess by remember { mutableStateOf<Boolean?>(null) }
    var isLoadingNaapiPlans by remember { mutableStateOf(false) }
    var isCreatingNaapiOrder by remember { mutableStateOf(false) }
    var isPollingNaapiOrder by remember { mutableStateOf(false) }
    var showNaapiPlanDialog by remember { mutableStateOf(false) }
    var naapiPlans by remember { mutableStateOf<List<NaapiPlan>>(emptyList()) }
    var naapiLicenseMessage by remember { mutableStateOf<String?>(null) }
    var naapiLicenseSuccess by remember { mutableStateOf<Boolean?>(null) }
    var naapiPendingOrder by remember(context) { mutableStateOf(NaapiTChatSupport.loadPendingOrder(context)) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showQRDialog by remember { mutableStateOf(false) }
    var showModelParamsDialog by remember { mutableStateOf<String?>(null) }
    var showModelCapabilityDialog by remember { mutableStateOf<String?>(null) }
    var modelCustomParams by remember { mutableStateOf(provider?.modelCustomParams ?: emptyMap()) }
    var isTestingConnection by remember { mutableStateOf(false) }
    var connectionTestMessage by remember { mutableStateOf<String?>(null) }
    var connectionTestSuccess by remember { mutableStateOf<Boolean?>(null) }

    val scope = rememberCoroutineScope()
    val httpClient = remember { OkHttpClient() }
    val naapiLicenseClient = remember(httpClient) { NaapiLicenseClient(httpClient) }
    val isNaapiProvider = providerType == AIProviderType.NAAPI_TCHAT
    val isNaapiLicenseBusy = isLoadingNaapiPlans || isCreatingNaapiOrder || isPollingNaapiOrder
    var hasPersistedProvider by remember(providerId, isNew) { mutableStateOf(!isNew) }

    fun buildCurrentProvider(): ProviderConfig {
        return ProviderConfig(
            id = providerId,
            name = name.trim().ifEmpty { providerType.displayName },
            providerType = providerType,
            serviceMode = serviceMode,
            billingMode = billingMode,
            authType = authType,
            apiKey = apiKey.trim(),
            endpoint = endpoint.trim(),
            apiPath = apiPath.trim(),
            modelsPath = modelsPath.trim(),
            imagesPath = imagesPath.trim(),
            embeddingsPath = embeddingsPath.trim(),
            modelCatalogPath = modelCatalogPath.trim(),
            authHeaderName = authHeaderName.trim().ifBlank { "Authorization" },
            authHeaderPrefix = authHeaderPrefix,
            useProxy = useProxy,
            selectedModel = selectedModel,
            availableModels = savedModels,
            modelCapabilities = modelCapabilities,
            modelCustomParams = modelCustomParams,
            customHeaders = if (providerType == AIProviderType.NAAPI_TCHAT && authType != ProviderAuthType.GATEWAY_KEY) {
                NaapiTChatSupport.withDeviceHeader(context, customHeaders)
            } else {
                customHeaders - NaapiTChatSupport.DEVICE_HEADER
            },
            apiKeys = apiKeys,
            multiKeyEnabled = multiKeyEnabled,
            keySelectionStrategy = keySelectionStrategy,
            roundRobinIndex = roundRobinIndex,
            maxFailuresBeforeDisable = maxFailuresBeforeDisable,
            autoRecoveryMinutes = autoRecoveryMinutes
        )
    }

    fun persistCurrentProviderSilently(): ProviderConfig {
        val currentProvider = buildCurrentProvider()
        val exists = settingsManager.settings.value.providers.any { it.id == currentProvider.id }
        if (exists) {
            settingsManager.updateProvider(currentProvider)
        } else {
            settingsManager.addProvider(currentProvider)
        }
        settingsManager.setCurrentProvider(currentProvider.id)
        hasPersistedProvider = true
        return currentProvider
    }

    fun openNaapiPayPage(order: NaapiPendingOrder): Boolean {
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(order.payUrl))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }.isSuccess
    }

    fun applyNaapiModelCatalog(catalog: List<NaapiModelCatalogItem>) {
        val models = catalog.map { it.id.trim() }.filter { it.isNotBlank() }.distinct()
        val catalogCapabilities = catalog.associate { item ->
            item.id to ModelCapabilityConfig(
                modelName = item.id,
                displayName = item.displayName,
                vendor = item.vendor,
                category = item.category,
                supportsVision = item.supportsVision,
                supportsTools = item.supportsTools,
                supportsResponses = item.supportsResponses,
                speed = item.speed,
                quality = item.quality,
                costLevel = item.costLevel,
                recommended = item.recommended
            )
        }
        savedModels = models
        fetchedModels = models
        modelCapabilities = modelCapabilities + catalogCapabilities
        selectedModel = if (selectedModel in models) {
            selectedModel
        } else {
            models.firstOrNull().orEmpty()
        }
    }

    suspend fun refreshNaapiModels(showError: Boolean) {
        isFetchingModels = true
        if (showError) {
            fetchError = null
        }
        try {
            val catalog = naapiLicenseClient.fetchModelCatalog(
                endpoint = endpoint.ifBlank { NaapiTChatSupport.DEFAULT_ENDPOINT },
                gatewayKey = apiKey.trim().takeIf { it.isNotBlank() && authType != ProviderAuthType.NONE }
            )
            applyNaapiModelCatalog(catalog)
            if (showError) {
                fetchError = null
            }
        } catch (e: Exception) {
            savedModels = emptyList()
            selectedModel = ""
            if (showError) {
                fetchError = e.message ?: "模型目录读取失败"
            }
        } finally {
            isFetchingModels = false
        }
    }

    BackHandler { onBack() }

    LaunchedEffect(providerType) {
        if (providerType == AIProviderType.NAAPI_TCHAT &&
            authType != ProviderAuthType.GATEWAY_KEY &&
            customHeaders[NaapiTChatSupport.DEVICE_HEADER].isNullOrBlank()
        ) {
            customHeaders = NaapiTChatSupport.withDeviceHeader(context, customHeaders)
        }
    }

    LaunchedEffect(isNaapiProvider, endpoint, authType, if (authType == ProviderAuthType.GATEWAY_KEY) apiKey else "") {
        if (isNaapiProvider) {
            savedModels = emptyList()
            selectedModel = ""
            refreshNaapiModels(showError = false)
        }
    }

    suspend fun applyPaidNaapiOrder(confirmedOrder: NaapiOrderStatus, pendingOrder: NaapiPendingOrder) {
        val licenseCode = confirmedOrder.licenseCode?.trim().orEmpty()
        val gatewayKey = confirmedOrder.gatewayKey?.trim().orEmpty()
        val redeemCodeFromOrder = confirmedOrder.redeemCode?.trim().orEmpty()
        if (licenseCode.isBlank() && gatewayKey.isBlank() && redeemCodeFromOrder.isBlank()) {
            naapiLicenseSuccess = false
            naapiLicenseMessage = "订单已支付，服务端暂未返回可用凭证，可稍后继续查询订单 ${pendingOrder.orderNo}"
            return
        }

        endpoint = confirmedOrder.gatewayBaseUrl?.trim()?.takeIf { it.isNotBlank() }
            ?: NaapiTChatSupport.DEFAULT_ENDPOINT
        if (licenseCode.isNotBlank()) {
            redeemCode = licenseCode
            apiKey = licenseCode
            authType = ProviderAuthType.LICENSE_CODE
            customHeaders = NaapiTChatSupport.withDeviceHeader(context, customHeaders)
            naapiActivationMessage = "已写入许可证，当前设备可使用"
        } else if (gatewayKey.isNotBlank()) {
            apiKey = gatewayKey
            authType = ProviderAuthType.GATEWAY_KEY
            customHeaders = customHeaders - NaapiTChatSupport.DEVICE_HEADER
            naapiActivationMessage = "已写入设备专属 Gateway Key"
        } else {
            redeemCode = redeemCodeFromOrder
            apiKey = redeemCodeFromOrder
            authType = ProviderAuthType.BEARER
            customHeaders = NaapiTChatSupport.withDeviceHeader(context, customHeaders)
            naapiActivationMessage = "已写入兑换码，可继续激活设备"
        }
        serviceMode = ServiceMode.OFFICIAL
        billingMode = ProviderBillingMode.NAAPI_LICENSE
        multiKeyEnabled = false
        apiKeys = emptyList()
        naapiActivationSuccess = true
        naapiLicenseSuccess = true
        NaapiTChatSupport.clearPendingOrder(context, pendingOrder.orderNo)
        naapiPendingOrder = null
        persistCurrentProviderSilently()
        naapiLicenseMessage = "支付已确认，许可证已自动保存，可直接使用"
    }

    suspend fun pollNaapiOrderUntilDone(order: NaapiPendingOrder) {
        if (isPollingNaapiOrder) return
        isPollingNaapiOrder = true
        naapiLicenseSuccess = null
        var attempt = 0
        try {
            while (true) {
                attempt += 1
                val status = naapiLicenseClient.getOrder(order.endpoint, order.orderNo, order.pollToken)
                val normalizedStatus = status.status.lowercase()
                if (normalizedStatus == "paid") {
                    applyPaidNaapiOrder(status, order)
                    return
                }
                if (normalizedStatus in setOf("failed", "cancelled", "canceled", "refunded")) {
                    NaapiTChatSupport.clearPendingOrder(context, order.orderNo)
                    naapiPendingOrder = null
                    naapiLicenseSuccess = false
                    naapiLicenseMessage = "订单 ${status.orderNo} 状态为 ${status.status}"
                    return
                }
                naapiLicenseMessage = "等待支付确认：${status.status}（已查询 ${attempt} 次，可离开本页后回来继续）"
                delay(2_000)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            naapiLicenseSuccess = false
            naapiLicenseMessage = "订单查询中断：${e.message ?: "网络异常"}。订单已保存，可稍后继续查询 ${order.orderNo}"
        } finally {
            isPollingNaapiOrder = false
        }
    }

    val loadNaapiPlans: () -> Unit = {
        scope.launch {
            isLoadingNaapiPlans = true
            naapiLicenseMessage = null
            naapiLicenseSuccess = null
            try {
                if (endpoint.isBlank()) {
                    endpoint = NaapiTChatSupport.DEFAULT_ENDPOINT
                }
                naapiPlans = naapiLicenseClient.fetchPlans(endpoint)
                showNaapiPlanDialog = true
            } catch (e: Exception) {
                naapiLicenseSuccess = false
                naapiLicenseMessage = e.message ?: "套餐读取失败"
            } finally {
                isLoadingNaapiPlans = false
            }
        }
    }

    val startNaapiOrder: (NaapiPlan) -> Unit = { plan ->
        scope.launch {
            isCreatingNaapiOrder = true
            isPollingNaapiOrder = false
            naapiLicenseMessage = null
            naapiLicenseSuccess = null
            try {
                if (endpoint.isBlank()) {
                    endpoint = NaapiTChatSupport.DEFAULT_ENDPOINT
                }
                customHeaders = NaapiTChatSupport.withDeviceHeader(context, customHeaders)

                val order = naapiLicenseClient.createLicenseOrder(
                    context = context,
                    endpoint = endpoint,
                    planId = plan.id
                )
                val pendingOrder = NaapiPendingOrder(
                    endpoint = endpoint,
                    orderNo = order.orderNo,
                    pollToken = order.pollToken,
                    payUrl = order.payUrl,
                    planId = plan.id,
                    createdAt = System.currentTimeMillis()
                )
                NaapiTChatSupport.savePendingOrder(context, pendingOrder)
                naapiPendingOrder = pendingOrder
                showNaapiPlanDialog = false
                isCreatingNaapiOrder = false

                val openedPayPage = openNaapiPayPage(pendingOrder)
                naapiLicenseMessage = if (openedPayPage) {
                    "订单 ${order.orderNo} 已创建，请在支付页完成付款"
                } else {
                    "订单 ${order.orderNo} 已创建，但支付页未打开"
                }

                pollNaapiOrderUntilDone(pendingOrder)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                naapiLicenseSuccess = false
                naapiLicenseMessage = e.message ?: "许可证流程失败"
            } finally {
                isCreatingNaapiOrder = false
            }
        }
    }

    // 删除确认对话框
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("删除服务商") },
            text = { Text("确定要删除 \"${name.ifEmpty { "未命名" }}\" 吗？此操作不可撤销。") },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete?.invoke()
                        showDeleteDialog = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 二维码分享对话框
    if (showQRDialog && provider != null) {
        QRCodeDialog(
            provider = provider,
            onDismiss = { showQRDialog = false }
        )
    }

    if (showNaapiPlanDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isNaapiLicenseBusy) {
                    showNaapiPlanDialog = false
                }
            },
            title = { Text("选择 NAAPI 套餐") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "开源提示：NAAPI 是可选服务。本页面只在你点击购买或激活时，将设备摘要发送到当前端点；代码中没有内置密钥。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    androidx.compose.foundation.lazy.LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(naapiPlans.size) { index ->
                            val plan = naapiPlans[index]
                            Surface(
                                onClick = {
                                    if (!isNaapiLicenseBusy) {
                                        startNaapiOrder(plan)
                                    }
                                },
                                enabled = !isNaapiLicenseBusy,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = MaterialTheme.shapes.medium,
                                tonalElevation = 1.dp
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = plan.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (plan.subtitle.isNotBlank()) {
                                                Text(
                                                    text = plan.subtitle,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        AppPill(text = formatNaapiPrice(plan.priceAmount, plan.currency))
                                    }

                                    Text(
                                        text = formatNaapiPlanMeta(plan),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    if (plan.description.isNotBlank()) {
                                        Text(
                                            text = plan.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                OutlinedButton(
                    onClick = { showNaapiPlanDialog = false },
                    enabled = !isCreatingNaapiOrder && !isPollingNaapiOrder
                ) {
                    Text("关闭")
                }
            }
        )
    }

    // ==================== 多 Key 管理对话框 ====================

    if (showAddKeyDialog) {
        var keysText by remember { mutableStateOf("") }
        var nameText by remember { mutableStateOf("") }
        var enabled by remember { mutableStateOf(true) }
        var priority by remember { mutableStateOf(5f) }
        var errorText by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showAddKeyDialog = false },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            title = { Text("添加 API Key") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = keysText,
                        onValueChange = { keysText = it; errorText = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Key（可多条）") },
                        placeholder = { Text("sk-xxxx...") },
                        supportingText = { Text("可用空格/换行/逗号分隔，一次添加多条") },
                        minLines = 2,
                        maxLines = 4
                    )

                    OutlinedTextField(
                        value = nameText,
                        onValueChange = { nameText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("名称（可选）") },
                        placeholder = { Text("如：备用 Key 1") },
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Switch(checked = enabled, onCheckedChange = { enabled = it })
                            Text("启用")
                        }

                        Text("优先级: ${priority.toInt()}")
                    }

                    Slider(
                        value = priority,
                        onValueChange = { priority = it },
                        valueRange = 1f..10f,
                        steps = 8
                    )

                    if (errorText != null) {
                        Text(
                            text = errorText!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = splitApiKeys(keysText)
                        if (parsed.isEmpty()) {
                            errorText = "请输入至少一个 Key"
                            return@Button
                        }

                        val existing = apiKeys.map { it.key.trim() }.toSet()
                        val newKeys = parsed.filter { it.trim() !in existing }
                        if (newKeys.isEmpty()) {
                            errorText = "这些 Key 已存在"
                            return@Button
                        }

                        val effectiveName = nameText.trim()
                        val prio = priority.toInt().coerceIn(1, 10)
                        val newEntries = newKeys.map { keyValue ->
                            ApiKeyEntry(
                                key = keyValue.trim(),
                                name = if (parsed.size == 1) effectiveName else "",
                                isEnabled = enabled,
                                priority = prio
                            )
                        }

                        apiKeys = apiKeys + newEntries
                        multiKeyEnabled = true
                        showAddKeyDialog = false
                    }
                ) {
                    Text("添加")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddKeyDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    editingKey?.let { keyToEdit ->
        var keyText by remember(keyToEdit.id) { mutableStateOf(keyToEdit.key) }
        var nameText by remember(keyToEdit.id) { mutableStateOf(keyToEdit.name) }
        var enabled by remember(keyToEdit.id) { mutableStateOf(keyToEdit.isEnabled) }
        var priority by remember(keyToEdit.id) { mutableStateOf(keyToEdit.priority.toFloat()) }
        var errorText by remember(keyToEdit.id) { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { editingKey = null },
            icon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
            title = { Text("编辑 API Key") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = keyText,
                        onValueChange = { keyText = it; errorText = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Key") },
                        placeholder = { Text("sk-xxxx...") },
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = nameText,
                        onValueChange = { nameText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("名称（可选）") },
                        placeholder = { Text("如：主 Key") },
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Switch(checked = enabled, onCheckedChange = { enabled = it })
                            Text("启用")
                        }

                        Text("优先级: ${priority.toInt()}")
                    }

                    Slider(
                        value = priority,
                        onValueChange = { priority = it },
                        valueRange = 1f..10f,
                        steps = 8
                    )

                    Text(
                        text = "状态: ${keyToEdit.status.displayLabel()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (errorText != null) {
                        Text(
                            text = errorText!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmedKey = keyText.trim()
                        if (trimmedKey.isBlank()) {
                            errorText = "Key 不能为空"
                            return@Button
                        }

                        val hasDuplicate = apiKeys.any { it.id != keyToEdit.id && it.key.trim() == trimmedKey }
                        if (hasDuplicate) {
                            errorText = "该 Key 已存在"
                            return@Button
                        }

                        val changed = trimmedKey != keyToEdit.key
                        val prio = priority.toInt().coerceIn(1, 10)
                        val updated = keyToEdit.copy(
                            key = trimmedKey,
                            name = nameText.trim(),
                            isEnabled = enabled,
                            priority = prio,
                            requestCount = if (changed) 0 else keyToEdit.requestCount,
                            successCount = if (changed) 0 else keyToEdit.successCount,
                            failureCount = if (changed) 0 else keyToEdit.failureCount,
                            lastUsedAt = if (changed) 0 else keyToEdit.lastUsedAt,
                            lastError = if (changed) null else keyToEdit.lastError,
                            status = if (changed) ApiKeyStatus.ACTIVE else keyToEdit.status,
                            statusChangedAt = if (changed) 0 else keyToEdit.statusChangedAt
                        )

                        apiKeys = apiKeys.map { if (it.id == keyToEdit.id) updated else it }
                        editingKey = null
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { editingKey = null }) {
                    Text("取消")
                }
            }
        )
    }

    deletingKey?.let { keyToDelete ->
        AlertDialog(
            onDismissRequest = { deletingKey = null },
            icon = {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("删除 API Key") },
            text = { Text("确定要删除 \"${keyToDelete.getDisplayName()}\" 吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        apiKeys = apiKeys.filterNot { it.id == keyToDelete.id }
                        deletingKey = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { deletingKey = null }) {
                    Text("取消")
                }
            }
        )
    }

    // 模型参数配置对话框
    showModelParamsDialog?.let { modelName ->
        val currentParams = modelCustomParams[modelName] ?: ModelCustomParams(modelName = modelName)
        ModelParamsDialog(
            modelName = modelName,
            params = currentParams,
            onDismiss = { showModelParamsDialog = null },
            onSave = { newParams ->
                modelCustomParams = if (newParams.hasAnyValue()) {
                    modelCustomParams + (modelName to newParams)
                } else {
                    modelCustomParams - modelName
                }
                showModelParamsDialog = null
            }
        )
    }

    showModelCapabilityDialog?.let { modelName ->
        val currentCapability = modelCapabilities[modelName] ?: ModelCapabilityConfig(modelName = modelName)
        ModelCapabilityDialog(
            modelName = modelName,
            capability = currentCapability,
            onDismiss = { showModelCapabilityDialog = null },
            onSave = { newCapability ->
                modelCapabilities = modelCapabilities + (modelName to newCapability)
                showModelCapabilityDialog = null
            },
            onClear = {
                modelCapabilities = modelCapabilities - modelName
                showModelCapabilityDialog = null
            }
        )
    }

    // 模型选择器弹窗
    if (showModelPicker && fetchedModels.isNotEmpty()) {
        var selectedToAdd by remember { mutableStateOf(setOf<String>()) }
        
        AlertDialog(
            onDismissRequest = { showModelPicker = false },
            title = { Text("选择要添加的模型") },
            text = {
                Column(modifier = Modifier.heightIn(max = 400.dp)) {
                    Text(
                        text = "共 ${fetchedModels.size} 个可用模型",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    androidx.compose.foundation.lazy.LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(fetchedModels.size) { index ->
                            val model = fetchedModels[index]
                            val isAlreadySaved = savedModels.contains(model)
                            val isSelected = selectedToAdd.contains(model)
                            
                            Surface(
                                onClick = {
                                    if (!isAlreadySaved) {
                                        selectedToAdd = if (isSelected) {
                                            selectedToAdd - model
                                        } else {
                                            selectedToAdd + model
                                        }
                                    }
                                },
                                color = when {
                                    isAlreadySaved -> MaterialTheme.colorScheme.surfaceContainerHighest
                                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                                    else -> MaterialTheme.colorScheme.surface
                                },
                                shape = MaterialTheme.shapes.small,
                                enabled = !isAlreadySaved
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = model,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = when {
                                            isAlreadySaved -> MaterialTheme.colorScheme.onSurfaceVariant
                                            isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                            else -> MaterialTheme.colorScheme.onSurface
                                        },
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isAlreadySaved) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    } else if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        savedModels = savedModels + selectedToAdd.toList()
                        if (selectedModel.isEmpty() && savedModels.isNotEmpty()) {
                            selectedModel = savedModels.first()
                        }
                        showModelPicker = false
                    },
                    enabled = selectedToAdd.isNotEmpty()
                ) {
                    Text("添加 ${selectedToAdd.size} 个")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showModelPicker = false }) {
                    Text("取消")
                }
            }
        )
    }

    AppPageScaffold(
        title = if (isNew) "添加服务商" else "编辑服务商",
        eyebrow = "Provider Editor",
        subtitle = providerType.displayName,
        onBack = onBack,
        actions = {
            if (!isNew) {
                IconButton(onClick = { showQRDialog = true }) {
                    Icon(Icons.Outlined.Share, contentDescription = "分享")
                }
                if (onDelete != null) {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    val newProvider = buildCurrentProvider()
                    onSave(newProvider)
                    if (!hasPersistedProvider) {
                        settingsManager.setCurrentProvider(newProvider.id)
                    }
                    hasPersistedProvider = true
                    onBack()
                },
                icon = { Icon(Icons.Default.Check, contentDescription = null) },
                text = { Text(if (isNew) "添加服务商" else "保存修改") },
                expanded = true
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {

            // 基本信息卡片
            SettingsGroupCard(
                title = "基本信息",
                description = "先定义服务商类型、名称和默认端点。"
            ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("名称") },
                        placeholder = { Text("例如：我的 OpenAI") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    ExposedDropdownMenuBox(
                        expanded = typeExpanded,
                        onExpandedChange = { typeExpanded = !typeExpanded }
                    ) {
                        OutlinedTextField(
                            value = providerType.displayName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("服务商类型") },
                            leadingIcon = {
                                Icon(
                                    imageVector = providerType.icon(),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = typeExpanded,
                            onDismissRequest = { typeExpanded = false }
                        ) {
                            AIProviderType.entries.forEach { type ->
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(
                                            imageVector = type.icon(),
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = if (type == providerType)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    text = {
                                        Text(
                                            text = type.displayName,
                                            color = if (type == providerType)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        providerType = type
                                        endpoint = type.defaultEndpoint
                                        apiPath = type.defaultApiPath
                                        modelsPath = type.defaultModelsPath
                                        imagesPath = type.defaultImagesPath
                                        embeddingsPath = "/embeddings"
                                        modelCatalogPath = if (type == AIProviderType.NAAPI_TCHAT) "/api/tchat/model-catalog" else ""
                                        savedModels = if (type == AIProviderType.NAAPI_TCHAT) emptyList() else type.defaultModels
                                        selectedModel = savedModels.firstOrNull() ?: ""
                                        modelCapabilities = emptyMap()
                                        when (type) {
                                            AIProviderType.NAAPI_TCHAT -> {
                                                serviceMode = ServiceMode.OFFICIAL
                                                billingMode = ProviderBillingMode.NAAPI_LICENSE
                                                authType = ProviderAuthType.LICENSE_CODE
                                                authHeaderName = "Authorization"
                                                authHeaderPrefix = "Bearer "
                                            }
                                            AIProviderType.OLLAMA -> {
                                                serviceMode = ServiceMode.LOCAL
                                                billingMode = ProviderBillingMode.LOCAL
                                                authType = ProviderAuthType.NONE
                                                authHeaderName = ""
                                                authHeaderPrefix = ""
                                                apiKey = apiKey.ifBlank { "ollama" }
                                            }
                                            else -> {
                                                serviceMode = ServiceMode.CUSTOM
                                                billingMode = ProviderBillingMode.USER_API_KEY
                                                authType = ProviderAuthType.BEARER
                                                authHeaderName = "Authorization"
                                                authHeaderPrefix = "Bearer "
                                            }
                                        }
                                        customHeaders = if (type == AIProviderType.NAAPI_TCHAT && authType != ProviderAuthType.GATEWAY_KEY) {
                                            NaapiTChatSupport.withDeviceHeader(context, customHeaders)
                                        } else {
                                            customHeaders - NaapiTChatSupport.DEVICE_HEADER
                                        }
                                        naapiActivationMessage = null
                                        naapiActivationSuccess = null
                                        naapiLicenseMessage = null
                                        naapiLicenseSuccess = null
                                        naapiPendingOrder = if (type == AIProviderType.NAAPI_TCHAT) {
                                            NaapiTChatSupport.loadPendingOrder(context)
                                        } else {
                                            null
                                        }
                                        typeExpanded = false
                                    }
                                )
                            }
                        }
                    }
            }

            SettingsGroupCard(
                title = "服务模式",
                description = "普通用户可使用官方服务，高级用户保留自定义与本地模型。"
            ) {
                Text(
                    text = when (serviceMode) {
                        ServiceMode.OFFICIAL -> "官方服务：使用 t.naapi.cc 套餐与许可证。"
                        ServiceMode.CUSTOM -> "自定义服务：使用自己的 API Key、端点、路径和 Header。"
                        ServiceMode.LOCAL -> "本地模型：面向 Ollama / LM Studio 等本机服务。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ServiceMode.entries.forEach { mode ->
                        FilterChip(
                            selected = serviceMode == mode,
                            onClick = {
                                serviceMode = mode
                                when (mode) {
                                    ServiceMode.OFFICIAL -> {
                                        providerType = AIProviderType.NAAPI_TCHAT
                                        billingMode = ProviderBillingMode.NAAPI_LICENSE
                                        authType = ProviderAuthType.LICENSE_CODE
                                        endpoint = AIProviderType.NAAPI_TCHAT.defaultEndpoint
                                        apiPath = AIProviderType.NAAPI_TCHAT.defaultApiPath
                                        modelsPath = AIProviderType.NAAPI_TCHAT.defaultModelsPath
                                        imagesPath = AIProviderType.NAAPI_TCHAT.defaultImagesPath
                                        modelCatalogPath = "/api/tchat/model-catalog"
                                        savedModels = emptyList()
                                        selectedModel = ""
                                    }
                                    ServiceMode.CUSTOM -> {
                                        billingMode = ProviderBillingMode.USER_API_KEY
                                        if (authType == ProviderAuthType.NONE) {
                                            authType = ProviderAuthType.BEARER
                                            authHeaderName = "Authorization"
                                            authHeaderPrefix = "Bearer "
                                        }
                                    }
                                    ServiceMode.LOCAL -> {
                                        providerType = AIProviderType.OLLAMA
                                        billingMode = ProviderBillingMode.LOCAL
                                        authType = ProviderAuthType.NONE
                                        endpoint = AIProviderType.OLLAMA.defaultEndpoint
                                        apiPath = AIProviderType.OLLAMA.defaultApiPath
                                        modelsPath = AIProviderType.OLLAMA.defaultModelsPath
                                        imagesPath = AIProviderType.OLLAMA.defaultImagesPath
                                        savedModels = AIProviderType.OLLAMA.defaultModels
                                        selectedModel = savedModels.firstOrNull() ?: ""
                                    }
                                }
                            },
                            label = { Text(mode.displayLabel()) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProviderBillingMode.entries.forEach { mode ->
                        FilterChip(
                            selected = billingMode == mode,
                            onClick = { billingMode = mode },
                            label = { Text(mode.displayLabel()) }
                        )
                    }
                }
            }

            // API 配置卡片
            SettingsGroupCard(
                title = "API 配置",
                description = "单 Key 直连或作为多 Key 方案的备用入口。"
            ) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = {
                            apiKey = it
                            naapiActivationMessage = null
                            naapiActivationSuccess = null
                            naapiLicenseMessage = null
                            naapiLicenseSuccess = null
                        },
                                    label = { Text(if (isNaapiProvider) "许可证 / API Key" else "API Key") },
                        placeholder = {
                            Text(if (isNaapiProvider) "naapi_dev_xxxxx" else "sk-xxxxxxxxxxxxxxxxxxxxxxxx")
                        },
                        supportingText = {
                            Text(
                                if (isNaapiProvider) {
                                            "建议保存许可证；旧版 Gateway Key 仍可兼容"
                                } else if (multiKeyEnabled && apiKeys.isNotEmpty()) {
                                    "已启用多 Key，聊天将使用下方 Key 列表（此处作为备用）"
                                } else {
                                    "需要保密，请妥善保管"
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    if (isNaapiProvider) {
                        OutlinedTextField(
                            value = redeemCode,
                            onValueChange = {
                                redeemCode = it
                                naapiActivationMessage = null
                                naapiActivationSuccess = null
                            },
                            label = { Text("兑换码（激活时使用）") },
                            placeholder = { Text("NAAPI-TCHAT-XXXX-XXXX-XXXX") },
                                    supportingText = { Text("已有许可证或旧版兑换码时填写；许可证会绑定当前设备") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.56f),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "设备 ID：${NaapiTChatSupport.maskedDeviceId(context)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "官方服务使用 t.naapi.cc 套餐。开通后使用许可证访问官方 OpenAI 兼容网关。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FilledTonalButton(
                                        onClick = loadNaapiPlans,
                                        enabled = !isNaapiLicenseBusy && !isActivatingNaapi
                                    ) {
                                        if (isLoadingNaapiPlans || isCreatingNaapiOrder || isPollingNaapiOrder) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(Modifier.width(8.dp))
                                        }
                                        Text(
                                            when {
                                                isLoadingNaapiPlans -> "读取套餐"
                                                isCreatingNaapiOrder -> "创建订单"
                                                isPollingNaapiOrder -> "确认支付"
                                                else -> "获取许可证"
                                            }
                                        )
                                    }

                                    FilledTonalButton(
                                        onClick = {
                                            scope.launch {
                                                isActivatingNaapi = true
                                                naapiActivationMessage = null
                                                naapiActivationSuccess = null
                                                customHeaders = NaapiTChatSupport.withDeviceHeader(context, customHeaders)
                                                val credential = redeemCode.ifBlank { apiKey }
                                                val result = if (credential.trim().startsWith("TCHAT-LIC-", ignoreCase = true)) {
                                                    naapiLicenseClient.bindLicenseDevice(
                                                        context = context,
                                                        endpoint = endpoint,
                                                        licenseCode = credential
                                                    )
                                                } else {
                                                    NaapiTChatSupport.activateDevice(
                                                        context = context,
                                                        httpClient = httpClient,
                                                        endpoint = endpoint,
                                                        redeemCode = credential
                                                    )
                                                }
                                                if (result.success) {
                                                    result.gatewayBaseUrl?.takeIf { it.isNotBlank() }?.let {
                                                        endpoint = it
                                                    }
                                                    if (credential.trim().startsWith("TCHAT-LIC-", ignoreCase = true)) {
                                                        apiKey = credential.trim()
                                                        redeemCode = credential.trim()
                                                        authType = ProviderAuthType.LICENSE_CODE
                                                        customHeaders = NaapiTChatSupport.withDeviceHeader(context, customHeaders)
                                                        billingMode = ProviderBillingMode.NAAPI_LICENSE
                                                    } else {
                                                        result.gatewayKey?.takeIf { it.isNotBlank() }?.let {
                                                            apiKey = it
                                                            authType = ProviderAuthType.GATEWAY_KEY
                                                            customHeaders = customHeaders - NaapiTChatSupport.DEVICE_HEADER
                                                        }
                                                        billingMode = ProviderBillingMode.OFFICIAL_TCHAT
                                                    }
                                                    serviceMode = ServiceMode.OFFICIAL
                                                }
                                                naapiActivationSuccess = result.success
                                                naapiActivationMessage = result.message
                                                isActivatingNaapi = false
                                            }
                                        },
                                        enabled = !isActivatingNaapi &&
                                            !isNaapiLicenseBusy &&
                                            (apiKey.isNotBlank() || redeemCode.isNotBlank()) &&
                                            endpoint.isNotBlank()
                                    ) {
                                        if (isActivatingNaapi) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(Modifier.width(8.dp))
                                        }
                                        Text("激活当前设备")
                                    }
                                }

                                naapiPendingOrder?.let { pendingOrder ->
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = "未完成订单：${pendingOrder.orderNo}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(
                                                onClick = {
                                                    scope.launch {
                                                        pollNaapiOrderUntilDone(pendingOrder)
                                                    }
                                                },
                                                enabled = !isNaapiLicenseBusy,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("继续查询订单")
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    val opened = openNaapiPayPage(pendingOrder)
                                                    naapiLicenseSuccess = opened
                                                    naapiLicenseMessage = if (opened) {
                                                        "已重新打开支付页"
                                                    } else {
                                                        "支付页未打开，请检查浏览器或系统限制"
                                                    }
                                                },
                                                enabled = !isNaapiLicenseBusy,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("打开支付页")
                                            }
                                            TextButton(
                                                onClick = {
                                                    NaapiTChatSupport.clearPendingOrder(context, pendingOrder.orderNo)
                                                    naapiPendingOrder = null
                                                    naapiLicenseSuccess = null
                                                    naapiLicenseMessage = "已清除本地未完成订单"
                                                },
                                                enabled = !isNaapiLicenseBusy,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("清除")
                                            }
                                        }
                                    }
                                }

                                naapiLicenseMessage?.let { message ->
                                    Text(
                                        text = message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (naapiLicenseSuccess == true) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.error
                                        }
                                    )
                                }

                                naapiActivationMessage?.let { message ->
                                    Text(
                                        text = message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (naapiActivationSuccess == true) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.error
                                        }
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = endpoint,
                        onValueChange = {
                            endpoint = it
                            naapiLicenseMessage = null
                            naapiLicenseSuccess = null
                        },
                        label = { Text("API 端点") },
                        placeholder = { Text(providerType.defaultEndpoint) },
                        supportingText = { Text("留空使用默认端点") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Text(
                        text = "鉴权方式",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ProviderAuthType.entries.forEach { type ->
                            FilterChip(
                                selected = authType == type,
                                onClick = {
                                    authType = type
                                    when (type) {
                    ProviderAuthType.BEARER,
                    ProviderAuthType.LICENSE_CODE,
                    ProviderAuthType.GATEWAY_KEY -> {
                        authHeaderName = "Authorization"
                        authHeaderPrefix = "Bearer "
                                        }
                                        ProviderAuthType.API_KEY -> {
                                            if (authHeaderName.isBlank() || authHeaderName == "Authorization") {
                                                authHeaderName = "x-api-key"
                                            }
                                            authHeaderPrefix = ""
                                        }
                                        ProviderAuthType.NONE -> {
                                            authHeaderName = ""
                                            authHeaderPrefix = ""
                                        }
                                    }
                                },
                                label = { Text(type.displayLabel()) }
                            )
                        }
                    }

                    if (authType != ProviderAuthType.NONE) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = authHeaderName,
                                onValueChange = { authHeaderName = it },
                                label = { Text("鉴权 Header") },
                                placeholder = { Text("Authorization") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = authHeaderPrefix,
                                onValueChange = { authHeaderPrefix = it },
                                label = { Text("前缀") },
                                placeholder = { Text("Bearer ") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = {
                                scope.launch {
                                    isTestingConnection = true
                                    connectionTestMessage = null
                                    connectionTestSuccess = null
                                    try {
                                        testProviderConnection(
                                            httpClient = httpClient,
                                            endpoint = endpoint,
                                            apiKey = apiKey,
                                            providerType = providerType,
                                            modelsPath = modelsPath.ifBlank { providerType.defaultModelsPath },
                                            authHeaderName = authHeaderName,
                                            authHeaderValue = authHeaderValueFor(authType, authHeaderPrefix, apiKey),
                                            extraHeaders = customHeaders
                                        )
                                        connectionTestSuccess = true
                                        connectionTestMessage = "连通测试通过"
                                    } catch (e: Exception) {
                                        connectionTestSuccess = false
                                        connectionTestMessage = e.message ?: "连通测试失败"
                                    } finally {
                                        isTestingConnection = false
                                    }
                                }
                            },
                            enabled = !isTestingConnection &&
                                endpoint.isNotBlank() &&
                                (authType == ProviderAuthType.NONE || apiKey.isNotBlank())
                        ) {
                            if (isTestingConnection) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("测试连通")
                        }

                        connectionTestMessage?.let { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (connectionTestSuccess == true) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
            }

            SettingsGroupCard(
                title = "路径与扩展端点",
                description = "兼容 NewAPI、自建网关、Responses、图片与 Embedding 路由。"
            ) {
                OutlinedTextField(
                    value = apiPath,
                    onValueChange = { apiPath = it },
                    label = { Text("聊天路径") },
                    placeholder = { Text(providerType.defaultApiPath) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = modelsPath,
                    onValueChange = { modelsPath = it },
                    label = { Text("模型列表路径") },
                    placeholder = { Text(providerType.defaultModelsPath) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = imagesPath,
                    onValueChange = { imagesPath = it },
                    label = { Text("图片生成路径") },
                    placeholder = { Text(providerType.defaultImagesPath.ifBlank { "可留空" }) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = embeddingsPath,
                    onValueChange = { embeddingsPath = it },
                    label = { Text("Embedding 路径") },
                    placeholder = { Text("/embeddings") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = modelCatalogPath,
                    onValueChange = { modelCatalogPath = it },
                    label = { Text("友好模型目录路径") },
                    placeholder = { Text("/api/tchat/model-catalog") },
                    supportingText = { Text("官方服务可返回模型展示名、推荐标记与能力标签") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("启用代理", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "保留代理配置开关，后续可接入全局代理设置",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = useProxy, onCheckedChange = { useProxy = it })
                }
            }

            SettingsGroupCard(
                title = "自定义 Header",
                description = "可添加任意网关需要的 Header，例如组织 ID、项目 ID 或路由标记。"
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = headerName,
                        onValueChange = { headerName = it },
                        label = { Text("Header 名") },
                        placeholder = { Text("X-Custom-Header") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = headerValue,
                        onValueChange = { headerValue = it },
                        label = { Text("Header 值") },
                        placeholder = { Text("value") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    FilledIconButton(
                        onClick = {
                            val key = headerName.trim()
                            val value = headerValue.trim()
                            if (key.isNotBlank() && value.isNotBlank()) {
                                customHeaders = customHeaders + (key to value)
                                headerName = ""
                                headerValue = ""
                            }
                        },
                        enabled = headerName.isNotBlank() && headerValue.isNotBlank()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "添加 Header")
                    }
                }

                if (customHeaders.isEmpty()) {
                    Text(
                        text = "暂无自定义 Header",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        customHeaders.forEach { (key, value) ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = key,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = maskSecret(value),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { customHeaders = customHeaders - key }) {
                                        Icon(
                                            Icons.Outlined.Delete,
                                            contentDescription = "删除 Header",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 多 Key 管理卡片
            SettingsGroupCard(
                title = "多 Key 管理",
                description = "用于轮询、优先级和故障切换，降低单 Key 失效风险。"
            ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "启用多 Key",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "同一服务商配置多个 Key，自动轮询与故障切换",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = multiKeyEnabled,
                            onCheckedChange = { enabled ->
                                multiKeyEnabled = enabled
                                if (enabled && apiKeys.isEmpty()) {
                                    val migrated = splitApiKeys(apiKey)
                                    if (migrated.isNotEmpty()) {
                                        apiKeys = migrated.map { key -> ApiKeyEntry(key = key) }
                                    }
                                }
                            }
                        )
                    }

                    if (multiKeyEnabled) {
                        // 策略选择
                        ExposedDropdownMenuBox(
                            expanded = strategyExpanded,
                            onExpandedChange = { strategyExpanded = !strategyExpanded }
                        ) {
                            OutlinedTextField(
                                value = keySelectionStrategy.displayLabel(),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("选择策略") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = strategyExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            )
                            ExposedDropdownMenu(
                                expanded = strategyExpanded,
                                onDismissRequest = { strategyExpanded = false }
                            ) {
                                KeySelectionStrategy.entries.forEach { strategy ->
                                    DropdownMenuItem(
                                        text = { Text(strategy.displayLabel()) },
                                        onClick = {
                                            keySelectionStrategy = strategy
                                            strategyExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "失败阈值: $maxFailuresBeforeDisable（达到后标记为错误）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = maxFailuresBeforeDisable.toFloat(),
                                onValueChange = { maxFailuresBeforeDisable = it.toInt().coerceIn(1, 10) },
                                valueRange = 1f..10f,
                                steps = 8
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "自动恢复: $autoRecoveryMinutes 分钟（错误 Key 冷却后重新启用）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = autoRecoveryMinutes.toFloat(),
                                onValueChange = { autoRecoveryMinutes = it.toInt().coerceIn(1, 30) },
                                valueRange = 1f..30f,
                                steps = 28
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val availableCount = apiKeys.count { it.isEnabled && it.status == ApiKeyStatus.ACTIVE }
                            Text(
                                text = "可用 Key：$availableCount / ${apiKeys.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(onClick = { showAddKeyDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("添加 Key")
                            }
                        }

                        if (apiKeys.isEmpty()) {
                            Text(
                                text = "暂无 Key，请点击“添加 Key”",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                apiKeys.forEach { key ->
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = MaterialTheme.shapes.medium,
                                        color = MaterialTheme.colorScheme.surfaceContainerLow
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = key.getDisplayName(),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${key.getMaskedKey()} · 优先级 ${key.priority} · ${key.status.displayLabel()}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Switch(
                                                    checked = key.isEnabled,
                                                    onCheckedChange = { enabled ->
                                                        apiKeys = apiKeys.map {
                                                            if (it.id == key.id) it.copy(isEnabled = enabled) else it
                                                        }
                                                    }
                                                )
                                                IconButton(onClick = { editingKey = key }) {
                                                    Icon(Icons.Outlined.Edit, contentDescription = "编辑")
                                                }
                                                IconButton(onClick = { deletingKey = key }) {
                                                    Icon(
                                                        Icons.Outlined.Delete,
                                                        contentDescription = "删除",
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
            }

            // 模型配置卡片
            SettingsGroupCard(
                title = "模型配置",
                description = "拉取、筛选并维护这个服务商可用的聊天模型。"
            ) {
                    val modelFetchKey = remember(apiKey, multiKeyEnabled, apiKeys) {
                        if (multiKeyEnabled && apiKeys.isNotEmpty()) {
                            apiKeys.firstOrNull { it.isEnabled }?.key ?: apiKey
                        } else {
                            apiKey
                        }
                    }.trim()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "模型配置",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        FilledTonalButton(
                            onClick = {
                                scope.launch {
                                    isFetchingModels = true
                                    fetchError = null
                                    try {
                                        if (providerType == AIProviderType.NAAPI_TCHAT) {
                                            val catalog = naapiLicenseClient.fetchModelCatalog(
                                                endpoint = endpoint.ifBlank { NaapiTChatSupport.DEFAULT_ENDPOINT },
                                                gatewayKey = modelFetchKey.takeIf { it.isNotBlank() && authType != ProviderAuthType.NONE }
                                            )
                                            applyNaapiModelCatalog(catalog)
                                            showModelPicker = false
                                        } else {
                                            val models = fetchModelsFromApi(
                                                httpClient = httpClient,
                                                endpoint = endpoint,
                                                apiKey = modelFetchKey,
                                                providerType = providerType,
                                                modelsPath = modelsPath.ifBlank { providerType.defaultModelsPath },
                                                authHeaderName = authHeaderName,
                                                authHeaderValue = authHeaderValueFor(authType, authHeaderPrefix, modelFetchKey),
                                                extraHeaders = if (providerType == AIProviderType.NAAPI_TCHAT) {
                                                    if (authType == ProviderAuthType.GATEWAY_KEY) {
                                                        customHeaders
                                                    } else {
                                                        NaapiTChatSupport.withDeviceHeader(context, customHeaders)
                                                    }
                                                } else {
                                                    customHeaders
                                                }
                                            )
                                            fetchedModels = models
                                            showModelPicker = true
                                        }
                                    } catch (e: Exception) {
                                        fetchError = e.message ?: "拉取失败"
                                    }
                                    isFetchingModels = false
                                }
                            },
                            enabled = !isFetchingModels &&
                                endpoint.isNotBlank() &&
                                (providerType == AIProviderType.NAAPI_TCHAT || modelFetchKey.isNotBlank())
                        ) {
                            if (isFetchingModels) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (providerType == AIProviderType.NAAPI_TCHAT) "刷新模型" else "拉取模型")
                        }
                    }

                    if (fetchError != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = fetchError!!,
                                modifier = Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Text(
                        text = "${savedModels.size} 个模型",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // 已保存的模型列表
                    if (savedModels.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            savedModels.forEach { model ->
                                Surface(
                                    onClick = { selectedModel = model },
                                    color = if (model == selectedModel)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surfaceContainerLow,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            if (model == selectedModel) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Text(
                                                text = model,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (model == selectedModel)
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                else
                                                    MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            // 显示已配置标记
                                            if (modelCustomParams.containsKey(model)) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                                    shape = MaterialTheme.shapes.extraSmall
                                                ) {
                                                    Text(
                                                        text = "已配置",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            val capabilityLabels = modelCapabilities[model]?.labels().orEmpty().take(3)
                                            capabilityLabels.forEach { label ->
                                                Surface(
                                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                                                    shape = MaterialTheme.shapes.extraSmall
                                                ) {
                                                    Text(
                                                        text = label,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        // 配置按钮
                                        IconButton(
                                            onClick = { showModelParamsDialog = model },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Outlined.Settings,
                                                contentDescription = "配置参数",
                                                modifier = Modifier.size(18.dp),
                                                tint = if (modelCustomParams.containsKey(model))
                                                    MaterialTheme.colorScheme.primary
                                                else
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(
                                            onClick = { showModelCapabilityDialog = model },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = "能力标签",
                                                modifier = Modifier.size(18.dp),
                                                tint = if (modelCapabilities.containsKey(model))
                                                    MaterialTheme.colorScheme.primary
                                                else
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                savedModels = savedModels - model
                                                modelCapabilities = modelCapabilities - model
                                                if (selectedModel == model) {
                                                    selectedModel = savedModels.firstOrNull() ?: ""
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Outlined.Delete,
                                                contentDescription = "删除",
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "暂无保存的模型，请拉取或手动添加",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // 手动添加模型
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customModel,
                            onValueChange = { customModel = it },
                            label = { Text("手动添加") },
                            placeholder = { Text("模型名称") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        FilledIconButton(
                            onClick = {
                                if (customModel.isNotBlank()) {
                                    if (!savedModels.contains(customModel)) {
                                        savedModels = savedModels + customModel
                                    }
                                    selectedModel = customModel
                                    customModel = ""
                                }
                            },
                            enabled = customModel.isNotBlank()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "添加")
                        }
                    }
            }

            // 底部留空给 FAB
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

/**
 * 从 API 拉取模型列表
 */
private suspend fun fetchModelsFromApi(
    httpClient: OkHttpClient,
    endpoint: String,
    apiKey: String,
    providerType: AIProviderType,
    modelsPath: String = providerType.defaultModelsPath,
    authHeaderName: String = "Authorization",
    authHeaderValue: String? = authHeaderValueFor(ProviderAuthType.BEARER, "Bearer ", apiKey),
    extraHeaders: Map<String, String> = emptyMap()
): List<String> = withContext(Dispatchers.IO) {
    val normalizedEndpoint = endpoint.trim().trimEnd('/')

    val url = when (providerType) {
        AIProviderType.OPENAI,
        AIProviderType.OPENAI_RESPONSES,
        AIProviderType.DEEPSEEK,
        AIProviderType.OPENROUTER,
        AIProviderType.OLLAMA,
        AIProviderType.NAAPI_TCHAT -> buildEndpointUrl(normalizedEndpoint, modelsPath.ifBlank { providerType.defaultModelsPath })
        AIProviderType.ANTHROPIC -> {
            // Anthropic models API 使用 /v1/models，并需要 x-api-key + anthropic-version
            val baseUrl = if (normalizedEndpoint.endsWith("/v1")) normalizedEndpoint else "$normalizedEndpoint/v1"
            buildEndpointUrl(baseUrl, modelsPath.ifBlank { "/models" })
        }
        AIProviderType.GEMINI -> {
            val base = buildEndpointUrl(normalizedEndpoint, modelsPath.ifBlank { "/models" })
            if (apiKey.isBlank()) base else "$base?key=$apiKey"
        }
    }

    val requestBuilder = Request.Builder().url(url)

    when (providerType) {
        AIProviderType.OPENAI,
        AIProviderType.OPENAI_RESPONSES,
        AIProviderType.DEEPSEEK,
        AIProviderType.OPENROUTER,
        AIProviderType.OLLAMA,
        AIProviderType.NAAPI_TCHAT -> {
            if (!authHeaderValue.isNullOrBlank() && authHeaderName.isNotBlank()) {
                requestBuilder.addHeader(authHeaderName, authHeaderValue)
            }
            extraHeaders.forEach { (name, value) ->
                if (name.isNotBlank() && value.isNotBlank()) {
                    requestBuilder.addHeader(name, value)
                }
            }
        }
        AIProviderType.ANTHROPIC -> {
            if (apiKey.isNotBlank()) {
                requestBuilder.addHeader("x-api-key", apiKey)
            }
            requestBuilder.addHeader("anthropic-version", "2023-06-01")
        }
        AIProviderType.GEMINI -> {
            // Gemini 使用 URL query 的 key；某些代理也支持 header，但这里保持最小集合。
            extraHeaders.forEach { (name, value) ->
                if (name.isNotBlank() && value.isNotBlank()) {
                    requestBuilder.addHeader(name, value)
                }
            }
        }
    }

    val response = httpClient.newCall(requestBuilder.build()).execute()

    if (!response.isSuccessful) {
        throw Exception("HTTP ${response.code}: ${response.message}")
    }

    val responseBody = response.body?.string() ?: throw Exception("空响应")

    val jsonObject = JSONObject(responseBody)
    val dataArray = jsonObject.optJSONArray("data") ?: jsonObject.optJSONArray("models")
        ?: throw Exception("无效的响应格式：未找到 data/models 字段")

    val models = LinkedHashSet<String>()
    for (i in 0 until dataArray.length()) {
        val modelObj = dataArray.optJSONObject(i) ?: continue

        // OpenAI: id；Gemini: name；兼容部分代理: model
        val rawId = sequenceOf(
            modelObj.optString("id", ""),
            modelObj.optString("name", ""),
            modelObj.optString("model", "")
        ).firstOrNull { it.isNotBlank() }.orEmpty()

        val modelId = rawId
            .trim()
            .removePrefix("models/")
            .removePrefix("/models/")

        if (modelId.isBlank()) continue

        if (modelId.contains("embedding", ignoreCase = true) ||
            modelId.contains("whisper", ignoreCase = true) ||
            modelId.contains("tts", ignoreCase = true) ||
            modelId.contains("dall-e", ignoreCase = true)
        ) {
            continue
        }

        models.add(modelId)
    }

    if (models.isEmpty()) {
        throw Exception("未找到可用模型")
    }

    models.toList()
}

@Composable
private fun ModelCapabilityDialog(
    modelName: String,
    capability: ModelCapabilityConfig,
    onDismiss: () -> Unit,
    onSave: (ModelCapabilityConfig) -> Unit,
    onClear: () -> Unit
) {
    var displayName by remember { mutableStateOf(capability.displayName) }
    var vendor by remember { mutableStateOf(capability.vendor) }
    var category by remember { mutableStateOf(capability.category) }
    var supportsVision by remember { mutableStateOf(capability.supportsVision) }
    var supportsTools by remember { mutableStateOf(capability.supportsTools) }
    var supportsResponses by remember { mutableStateOf(capability.supportsResponses) }
    var supportsImageGeneration by remember { mutableStateOf(capability.supportsImageGeneration) }
    var supportsEmbedding by remember { mutableStateOf(capability.supportsEmbedding) }
    var speed by remember { mutableStateOf(capability.speed) }
    var quality by remember { mutableStateOf(capability.quality) }
    var costLevel by remember { mutableStateOf(capability.costLevel) }
    var recommended by remember { mutableStateOf(capability.recommended) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("模型能力标签")
                Text(
                    text = modelName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("展示名称") },
                    placeholder = { Text("日常推荐") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = vendor,
                        onValueChange = { vendor = it },
                        label = { Text("厂商") },
                        placeholder = { Text("OpenAI") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("分类") },
                        placeholder = { Text("general") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                CapabilitySwitchRow("推荐模型", recommended) { recommended = it }
                CapabilitySwitchRow("视觉输入", supportsVision) { supportsVision = it }
                CapabilitySwitchRow("工具调用", supportsTools) { supportsTools = it }
                CapabilitySwitchRow("Responses API", supportsResponses) { supportsResponses = it }
                CapabilitySwitchRow("图片生成", supportsImageGeneration) { supportsImageGeneration = it }
                CapabilitySwitchRow("Embedding", supportsEmbedding) { supportsEmbedding = it }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = speed,
                        onValueChange = { speed = it },
                        label = { Text("速度") },
                        placeholder = { Text("fast") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = quality,
                        onValueChange = { quality = it },
                        label = { Text("质量") },
                        placeholder = { Text("balanced") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                OutlinedTextField(
                    value = costLevel,
                    onValueChange = { costLevel = it },
                    label = { Text("成本等级") },
                    placeholder = { Text("low") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        ModelCapabilityConfig(
                            modelName = modelName,
                            displayName = displayName.trim(),
                            vendor = vendor.trim(),
                            category = category.trim(),
                            supportsVision = supportsVision,
                            supportsTools = supportsTools,
                            supportsResponses = supportsResponses,
                            supportsImageGeneration = supportsImageGeneration,
                            supportsEmbedding = supportsEmbedding,
                            speed = speed.trim(),
                            quality = quality.trim(),
                            costLevel = costLevel.trim(),
                            recommended = recommended
                        )
                    )
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onClear) {
                    Text("清除")
                }
                OutlinedButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        }
    )
}

@Composable
private fun CapabilitySwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * 模型参数配置对话框
 */
@Composable
private fun ModelParamsDialog(
    modelName: String,
    params: ModelCustomParams,
    onDismiss: () -> Unit,
    onSave: (ModelCustomParams) -> Unit
) {
    var temperatureEnabled by remember { mutableStateOf(params.temperature != null) }
    var temperature by remember { mutableFloatStateOf(params.temperature ?: 0.7f) }

    var topPEnabled by remember { mutableStateOf(params.topP != null) }
    var topP by remember { mutableFloatStateOf(params.topP ?: 0.9f) }

    var topKEnabled by remember { mutableStateOf(params.topK != null) }
    var topK by remember { mutableStateOf((params.topK ?: 50).toString()) }

    var presencePenaltyEnabled by remember { mutableStateOf(params.presencePenalty != null) }
    var presencePenalty by remember { mutableFloatStateOf(params.presencePenalty ?: 0f) }

    var frequencyPenaltyEnabled by remember { mutableStateOf(params.frequencyPenalty != null) }
    var frequencyPenalty by remember { mutableFloatStateOf(params.frequencyPenalty ?: 0f) }

    var repetitionPenaltyEnabled by remember { mutableStateOf(params.repetitionPenalty != null) }
    var repetitionPenalty by remember { mutableFloatStateOf(params.repetitionPenalty ?: 1f) }

    var maxTokensEnabled by remember { mutableStateOf(params.maxTokens != null) }
    var maxTokens by remember { mutableStateOf((params.maxTokens ?: 4096).toString()) }

    var extraParams by remember { mutableStateOf(params.extraParams) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("模型参数配置")
                Text(
                    text = modelName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 450.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Temperature
                ParamSliderItem(
                    label = "Temperature",
                    enabled = temperatureEnabled,
                    onEnabledChange = { temperatureEnabled = it },
                    value = temperature,
                    onValueChange = { temperature = it },
                    valueRange = 0f..2f,
                    valueDisplay = "%.2f".format(temperature)
                )

                // Top-P
                ParamSliderItem(
                    label = "Top-P",
                    enabled = topPEnabled,
                    onEnabledChange = { topPEnabled = it },
                    value = topP,
                    onValueChange = { topP = it },
                    valueRange = 0f..1f,
                    valueDisplay = "%.2f".format(topP)
                )

                // Top-K
                ParamInputItem(
                    label = "Top-K",
                    enabled = topKEnabled,
                    onEnabledChange = { topKEnabled = it },
                    value = topK,
                    onValueChange = { topK = it },
                    placeholder = "50"
                )

                // Presence Penalty
                ParamSliderItem(
                    label = "Presence Penalty",
                    enabled = presencePenaltyEnabled,
                    onEnabledChange = { presencePenaltyEnabled = it },
                    value = presencePenalty,
                    onValueChange = { presencePenalty = it },
                    valueRange = -2f..2f,
                    valueDisplay = "%.2f".format(presencePenalty)
                )

                // Frequency Penalty
                ParamSliderItem(
                    label = "Frequency Penalty",
                    enabled = frequencyPenaltyEnabled,
                    onEnabledChange = { frequencyPenaltyEnabled = it },
                    value = frequencyPenalty,
                    onValueChange = { frequencyPenalty = it },
                    valueRange = -2f..2f,
                    valueDisplay = "%.2f".format(frequencyPenalty)
                )

                // Repetition Penalty
                ParamSliderItem(
                    label = "Repetition Penalty",
                    enabled = repetitionPenaltyEnabled,
                    onEnabledChange = { repetitionPenaltyEnabled = it },
                    value = repetitionPenalty,
                    onValueChange = { repetitionPenalty = it },
                    valueRange = 0f..2f,
                    valueDisplay = "%.2f".format(repetitionPenalty)
                )

                // Max Tokens
                ParamInputItem(
                    label = "Max Tokens",
                    enabled = maxTokensEnabled,
                    onEnabledChange = { maxTokensEnabled = it },
                    value = maxTokens,
                    onValueChange = { maxTokens = it },
                    placeholder = "4096"
                )

                HorizontalDivider()

                // Extra JSON Params
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "自定义 JSON 参数",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    OutlinedTextField(
                        value = extraParams,
                        onValueChange = { extraParams = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("{\"stop\": [\"<|end|>\"]}") },
                        supportingText = { Text("直接合并到请求体，覆盖同名参数") },
                        minLines = 2,
                        maxLines = 4
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newParams = ModelCustomParams(
                        modelName = modelName,
                        temperature = if (temperatureEnabled) temperature else null,
                        topP = if (topPEnabled) topP else null,
                        topK = if (topKEnabled) topK.toIntOrNull() else null,
                        presencePenalty = if (presencePenaltyEnabled) presencePenalty else null,
                        frequencyPenalty = if (frequencyPenaltyEnabled) frequencyPenalty else null,
                        repetitionPenalty = if (repetitionPenaltyEnabled) repetitionPenalty else null,
                        maxTokens = if (maxTokensEnabled) maxTokens.toIntOrNull() else null,
                        extraParams = extraParams.ifBlank { "{}" }
                    )
                    onSave(newParams)
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 滑块参数项
 */
@Composable
private fun ParamSliderItem(
    label: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    valueDisplay: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = valueDisplay,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 输入框参数项
 */
@Composable
private fun ParamInputItem(
    label: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Checkbox(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            placeholder = { Text(placeholder) },
            modifier = Modifier.width(100.dp),
            singleLine = true
        )
    }
}

private val API_KEY_SPLIT_REGEX = "[\\s,]+".toRegex()

private fun formatNaapiPrice(amount: Int, currency: String): String {
    if (amount <= 0) return "免费"
    val major = amount / 100.0
    return when (currency.uppercase()) {
        "CNY", "RMB" -> "¥%.2f".format(major)
        "USD" -> "$%.2f".format(major)
        else -> "%s %.2f".format(currency.uppercase(), major)
    }
}

private fun formatNaapiPlanMeta(plan: NaapiPlan): String {
    val parts = mutableListOf<String>()
    if (plan.quotaAmount > 0) {
        parts += "额度：${plan.quotaAmount}"
    }
    if (plan.validDays > 0) {
        parts += "有效期：${plan.validDays} 天"
    }
    return parts.ifEmpty { listOf("套餐详情以支付页面为准") }.joinToString(" · ")
}

private fun splitApiKeys(raw: String): List<String> {
    return raw
        .split(API_KEY_SPLIT_REGEX)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
}

private fun ApiKeyStatus.displayLabel(): String {
    return when (this) {
        ApiKeyStatus.ACTIVE -> "正常"
        ApiKeyStatus.DISABLED -> "禁用"
        ApiKeyStatus.ERROR -> "错误"
        ApiKeyStatus.RATE_LIMITED -> "限流"
    }
}

private fun KeySelectionStrategy.displayLabel(): String {
    return when (this) {
        KeySelectionStrategy.ROUND_ROBIN -> "轮询"
        KeySelectionStrategy.PRIORITY -> "优先级"
        KeySelectionStrategy.RANDOM -> "随机"
        KeySelectionStrategy.LEAST_USED -> "最少使用"
    }
}

private fun ServiceMode.displayLabel(): String {
    return when (this) {
        ServiceMode.OFFICIAL -> "官方服务"
        ServiceMode.CUSTOM -> "自定义服务"
        ServiceMode.LOCAL -> "本地模型"
    }
}

private fun ProviderBillingMode.displayLabel(): String {
    return when (this) {
        ProviderBillingMode.OFFICIAL_TCHAT -> "官方套餐"
        ProviderBillingMode.NAAPI_LICENSE -> "许可证"
        ProviderBillingMode.USER_API_KEY -> "自有 Key"
        ProviderBillingMode.LOCAL -> "本地"
        ProviderBillingMode.TEAM -> "团队"
    }
}

private fun ProviderAuthType.displayLabel(): String {
    return when (this) {
        ProviderAuthType.BEARER -> "Bearer"
        ProviderAuthType.LICENSE_CODE -> "许可证"
        ProviderAuthType.API_KEY -> "API Key"
        ProviderAuthType.GATEWAY_KEY -> "Gateway Key"
        ProviderAuthType.NONE -> "无鉴权"
    }
}

private fun authHeaderValueFor(
    authType: ProviderAuthType,
    prefix: String,
    apiKey: String
): String? {
    val trimmed = apiKey.trim()
    if (authType == ProviderAuthType.NONE || trimmed.isBlank()) return null
    val resolvedPrefix = when (authType) {
        ProviderAuthType.BEARER,
        ProviderAuthType.LICENSE_CODE,
        ProviderAuthType.GATEWAY_KEY -> prefix.ifBlank { "Bearer " }
        ProviderAuthType.API_KEY -> prefix
        ProviderAuthType.NONE -> ""
    }
    return "$resolvedPrefix$trimmed".trim()
}

private fun buildEndpointUrl(baseUrl: String, path: String): String {
    val trimmedPath = path.trim().ifBlank { "/" }
    if (trimmedPath.startsWith("http://") || trimmedPath.startsWith("https://")) {
        return trimmedPath
    }
    val normalizedPath = if (trimmedPath.startsWith("/")) trimmedPath else "/$trimmedPath"
    return "${baseUrl.trimEnd('/')}$normalizedPath"
}

private suspend fun testProviderConnection(
    httpClient: OkHttpClient,
    endpoint: String,
    apiKey: String,
    providerType: AIProviderType,
    modelsPath: String,
    authHeaderName: String,
    authHeaderValue: String?,
    extraHeaders: Map<String, String>
) = withContext(Dispatchers.IO) {
    val normalizedEndpoint = endpoint.trim().trimEnd('/')
    val url = when (providerType) {
        AIProviderType.GEMINI -> {
            val base = buildEndpointUrl(normalizedEndpoint, modelsPath.ifBlank { providerType.defaultModelsPath })
            if (apiKey.isBlank()) base else "$base?key=$apiKey"
        }
        AIProviderType.ANTHROPIC -> {
            val baseUrl = if (normalizedEndpoint.endsWith("/v1")) normalizedEndpoint else "$normalizedEndpoint/v1"
            buildEndpointUrl(baseUrl, modelsPath.ifBlank { "/models" })
        }
        else -> buildEndpointUrl(normalizedEndpoint, modelsPath.ifBlank { providerType.defaultModelsPath })
    }

    val requestBuilder = Request.Builder().url(url).get()
    if (!authHeaderValue.isNullOrBlank() && authHeaderName.isNotBlank()) {
        requestBuilder.addHeader(authHeaderName, authHeaderValue)
    }
    if (providerType == AIProviderType.ANTHROPIC && apiKey.isNotBlank()) {
        requestBuilder.addHeader("x-api-key", apiKey)
        requestBuilder.addHeader("anthropic-version", "2023-06-01")
    }
    extraHeaders.forEach { (name, value) ->
        if (name.isNotBlank() && value.isNotBlank()) {
            requestBuilder.addHeader(name, value)
        }
    }

    httpClient.newCall(requestBuilder.build()).execute().use { response ->
        if (!response.isSuccessful) {
            val body = response.body?.string().orEmpty()
            throw Exception("HTTP ${response.code}: ${body.take(160).ifBlank { response.message }}")
        }
    }
}

private fun maskSecret(value: String): String {
    val trimmed = value.trim()
    return when {
        trimmed.length <= 8 -> "****"
        else -> "${trimmed.take(4)}****${trimmed.takeLast(4)}"
    }
}
