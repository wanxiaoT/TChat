package com.tchat.wanxiaot.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * NAAPI 许可证流程客户端。
 *
 * 说明：NAAPI 是可选服务。这里不包含任何内置密钥，也不会在用户操作前发送设备信息。
 */
class NaapiLicenseClient(
    private val httpClient: OkHttpClient
) {
    suspend fun fetchPlans(endpoint: String): List<NaapiPlan> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${portalBase(endpoint)}/api/tchat/plans")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw Exception(extractMessage(body).ifBlank { "套餐读取失败：HTTP ${response.code}" })
            }

            val root = parseEnvelope(body)
            val plansArray = when (val data = root.opt("data")) {
                is JSONObject -> data.optJSONArray("items")
                    ?: data.optJSONArray("plans")
                    ?: JSONArray()
                is JSONArray -> data
                else -> root.optJSONArray("items") ?: JSONArray()
            }

            val plans = mutableListOf<NaapiPlan>()
            for (i in 0 until plansArray.length()) {
                val item = plansArray.optJSONObject(i) ?: continue
                val id = item.optLong("id", 0L)
                if (id <= 0L) continue
                plans += NaapiPlan(
                    id = id,
                    name = item.optString("name", "套餐 $id"),
                    subtitle = item.optString("subtitle", ""),
                    priceAmount = optIntAny(item, "price_amount", "priceAmount", "amount"),
                    currency = item.optString("currency", "CNY"),
                    quotaAmount = optIntAny(item, "quota_amount", "quotaAmount", "quota"),
                    validDays = optIntAny(item, "valid_days", "validDays", "days"),
                    description = item.optString("description", "")
                )
            }

            if (plans.isEmpty()) {
                throw Exception("暂无可用套餐")
            }
            plans
        }
    }

    suspend fun createLicenseOrder(
        context: Context,
        endpoint: String,
        planId: Long,
        paymentProvider: String = "epay"
    ): NaapiCreateOrderResult = withContext(Dispatchers.IO) {
        val requestBody = JSONObject().apply {
            put("plan_id", planId)
            put("payment_provider", paymentProvider)
            put("device", NaapiTChatSupport.buildDeviceInfoJson(context))
        }

        val request = Request.Builder()
            .url("${portalBase(endpoint)}/api/tchat/license/create-order")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw Exception(extractMessage(body).ifBlank { "订单创建失败：HTTP ${response.code}" })
            }

            val data = parseDataObject(body)
            NaapiCreateOrderResult(
                orderNo = data.optString("order_no"),
                status = data.optString("status", "pending"),
                amount = data.optInt("amount", 0),
                currency = data.optString("currency", "CNY"),
                payUrl = data.optString("pay_url")
            ).also {
                if (it.orderNo.isBlank()) {
                    throw Exception("订单创建失败：缺少订单号")
                }
                if (it.payUrl.isBlank()) {
                    throw Exception("订单创建失败：缺少支付链接")
                }
            }
        }
    }

    suspend fun getOrder(endpoint: String, orderNo: String): NaapiOrderStatus = withContext(Dispatchers.IO) {
        val trimmedOrderNo = orderNo.trim()
        if (trimmedOrderNo.isBlank()) {
            throw Exception("订单号为空")
        }

        val request = Request.Builder()
            .url("${portalBase(endpoint)}/api/tchat/orders/$trimmedOrderNo")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw Exception(extractMessage(body).ifBlank { "订单查询失败：HTTP ${response.code}" })
            }

            val data = parseDataObject(body)
            NaapiOrderStatus(
                orderNo = data.optString("order_no", trimmedOrderNo),
                status = data.optString("status", "pending"),
                amount = data.optInt("amount", 0),
                currency = data.optString("currency", "CNY"),
                planId = data.optLong("plan_id", 0L),
                codePrefix = data.optString("license_prefix").takeIf { it.isNotBlank() }
                    ?: data.optString("licensePrefix").takeIf { it.isNotBlank() }
                    ?: data.optString("code_prefix").takeIf { it.isNotBlank() },
                licenseCode = data.optString("license_code").takeIf { it.isNotBlank() }
                    ?: data.optString("licenseCode").takeIf { it.isNotBlank() },
                redeemCode = data.optString("redeem_code").takeIf { it.isNotBlank() },
                gatewayKey = data.optString("gateway_key").takeIf { it.isNotBlank() }
                    ?: data.optString("gatewayKey").takeIf { it.isNotBlank() },
                gatewayBaseUrl = data.optString("gateway_base_url").takeIf { it.isNotBlank() }
                    ?: data.optString("gatewayBaseUrl").takeIf { it.isNotBlank() }
            )
        }
    }

    suspend fun bindLicenseDevice(
        context: Context,
        endpoint: String,
        licenseCode: String
    ): NaapiActivationResult = withContext(Dispatchers.IO) {
        val trimmedCode = licenseCode.trim()
        if (trimmedCode.isBlank()) {
            return@withContext NaapiActivationResult(false, "请先填写许可证")
        }
        val body = JSONObject().apply {
            put("license_code", trimmedCode)
            put("device", NaapiTChatSupport.buildDeviceInfoJson(context))
        }
        val request = Request.Builder()
            .url("${portalBase(endpoint)}/api/tchat/license/bind-device")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext NaapiActivationResult(
                        success = false,
                        message = extractMessage(responseBody).ifBlank { "设备绑定失败：HTTP ${response.code}" }
                    )
                }
                val data = parseDataObject(responseBody)
                NaapiActivationResult(
                    success = true,
                    message = "当前设备已绑定",
                    gatewayBaseUrl = data.optString("gateway_base_url").takeIf { it.isNotBlank() }
                        ?: data.optString("gatewayBaseUrl").takeIf { it.isNotBlank() }
                )
            }
        } catch (e: Exception) {
            NaapiActivationResult(success = false, message = "设备绑定失败：${e.message ?: "网络异常"}")
        }
    }

    suspend fun fetchUsageSummary(endpoint: String, gatewayKey: String): NaapiUsageSummary = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${portalBase(endpoint)}/api/tchat/usage/summary")
            .addHeader("Authorization", "Bearer ${gatewayKey.trim()}")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw Exception(extractMessage(body).ifBlank { "用量摘要读取失败：HTTP ${response.code}" })
            }
            val data = parseDataObject(body)
            NaapiUsageSummary(
                planName = data.optString("plan_name").ifBlank { data.optString("planName", "") },
                balanceAmount = optDoubleAny(data, "balance_amount", "balanceAmount", "balance"),
                todayAmount = optDoubleAny(data, "today_amount", "todayAmount", "today"),
                monthAmount = optDoubleAny(data, "month_amount", "monthAmount", "month"),
                currency = data.optString("currency", "CNY"),
                totalRequests = optIntAny(data, "total_requests", "totalRequests", "requests"),
                quotaAmount = optDoubleAny(data, "quota_amount", "quotaAmount", "quota"),
                expiresAt = data.optString("expires_at").takeIf { it.isNotBlank() }
                    ?: data.optString("expiresAt").takeIf { it.isNotBlank() }
            )
        }
    }

    suspend fun fetchDevices(endpoint: String, gatewayKey: String): List<NaapiDeviceInfo> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${portalBase(endpoint)}/api/tchat/devices")
            .addHeader("Authorization", "Bearer ${gatewayKey.trim()}")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw Exception(extractMessage(body).ifBlank { "设备列表读取失败：HTTP ${response.code}" })
            }
            val root = parseEnvelope(body)
            val items = when (val data = root.opt("data")) {
                is JSONObject -> data.optJSONArray("items") ?: data.optJSONArray("devices") ?: JSONArray()
                is JSONArray -> data
                else -> root.optJSONArray("items") ?: JSONArray()
            }
            buildList {
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    add(
                        NaapiDeviceInfo(
                            id = item.optString("id").ifBlank { item.optString("device_id", "") },
                            name = item.optString("name").ifBlank { item.optString("model", "设备") },
                            gatewayKeyPrefix = item.optString("gateway_key_prefix")
                                .ifBlank { item.optString("gatewayKeyPrefix", "") },
                            lastUsedAt = item.optString("last_used_at")
                                .ifBlank { item.optString("lastUsedAt", "") },
                            current = item.optBoolean("current", false),
                            revoked = item.optBoolean("revoked", false)
                        )
                    )
                }
            }
        }
    }

    suspend fun fetchUsageLogs(
        endpoint: String,
        gatewayKey: String,
        limit: Int = 20
    ): List<NaapiUsageLogItem> = withContext(Dispatchers.IO) {
        val safeLimit = limit.coerceIn(1, 100)
        val request = Request.Builder()
            .url("${portalBase(endpoint)}/api/tchat/usage/logs?limit=$safeLimit")
            .addHeader("Authorization", "Bearer ${gatewayKey.trim()}")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw Exception(extractMessage(body).ifBlank { "用量明细读取失败：HTTP ${response.code}" })
            }
            val items = parseItemsArray(body, "items", "logs", "records")
            buildList {
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    add(
                        NaapiUsageLogItem(
                            id = item.optString("id").ifBlank { item.optString("request_id", "") },
                            model = item.optString("model").ifBlank { item.optString("model_name", "") },
                            amount = optDoubleAny(item, "amount", "cost", "cost_amount", "costAmount"),
                            currency = item.optString("currency", "CNY"),
                            inputTokens = optIntAny(item, "input_tokens", "inputTokens", "prompt_tokens"),
                            outputTokens = optIntAny(item, "output_tokens", "outputTokens", "completion_tokens"),
                            status = item.optString("status").ifBlank { item.optString("state", "") },
                            createdAt = item.optString("created_at").ifBlank { item.optString("createdAt", "") },
                            deviceName = item.optString("device_name").ifBlank { item.optString("deviceName", "") }
                        )
                    )
                }
            }
        }
    }

    suspend fun fetchOrders(
        endpoint: String,
        gatewayKey: String,
        limit: Int = 20
    ): List<NaapiOrderRecord> = withContext(Dispatchers.IO) {
        val safeLimit = limit.coerceIn(1, 100)
        val request = Request.Builder()
            .url("${portalBase(endpoint)}/api/tchat/orders?limit=$safeLimit")
            .addHeader("Authorization", "Bearer ${gatewayKey.trim()}")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw Exception(extractMessage(body).ifBlank { "订单记录读取失败：HTTP ${response.code}" })
            }
            val items = parseItemsArray(body, "items", "orders", "records")
            buildList {
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    add(
                        NaapiOrderRecord(
                            orderNo = item.optString("order_no").ifBlank { item.optString("orderNo", "") },
                            planName = item.optString("plan_name").ifBlank { item.optString("planName", "") },
                            status = item.optString("status", ""),
                            amount = optDoubleAny(item, "amount", "price_amount", "priceAmount"),
                            currency = item.optString("currency", "CNY"),
                            createdAt = item.optString("created_at").ifBlank { item.optString("createdAt", "") },
                            paidAt = item.optString("paid_at").ifBlank { item.optString("paidAt", "") }
                        )
                    )
                }
            }
        }
    }

    suspend fun fetchModelCatalog(endpoint: String, gatewayKey: String? = null): List<NaapiModelCatalogItem> = withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder()
            .url("${portalBase(endpoint)}/api/tchat/model-catalog")
            .get()
        if (!gatewayKey.isNullOrBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer ${gatewayKey.trim()}")
        }

        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw Exception(extractMessage(body).ifBlank { "模型目录读取失败：HTTP ${response.code}" })
            }
            val root = parseEnvelope(body)
            val items = when (val data = root.opt("data")) {
                is JSONObject -> data.optJSONArray("items") ?: JSONArray()
                is JSONArray -> data
                else -> root.optJSONArray("items") ?: JSONArray()
            }
            buildList {
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val id = item.optString("id")
                    if (id.isBlank()) continue
                    add(
                        NaapiModelCatalogItem(
                            id = id,
                            displayName = item.optString("display_name").ifBlank { item.optString("displayName", "") },
                            vendor = item.optString("vendor", ""),
                            category = item.optString("category", ""),
                            supportsVision = item.optBoolean("supports_vision", item.optBoolean("supportsVision", false)),
                            supportsTools = item.optBoolean("supports_tools", item.optBoolean("supportsTools", false)),
                            supportsResponses = item.optBoolean("supports_responses", item.optBoolean("supportsResponses", false)),
                            speed = item.optString("speed", ""),
                            quality = item.optString("quality", ""),
                            costLevel = item.optString("cost_level").ifBlank { item.optString("costLevel", "") },
                            recommended = item.optBoolean("recommended", false)
                        )
                    )
                }
            }
        }
    }

    private fun portalBase(endpoint: String): String {
        return NaapiTChatSupport.portalBaseFromEndpoint(endpoint).trimEnd('/')
    }

    private fun parseDataObject(raw: String): JSONObject {
        val root = parseEnvelope(raw)
        val data = root.opt("data")
        return when (data) {
            is JSONObject -> data
            else -> root
        }
    }

    private fun parseItemsArray(raw: String, vararg itemKeys: String): JSONArray {
        val root = parseEnvelope(raw)
        val data = root.opt("data")
        if (data is JSONArray) return data
        if (data is JSONObject) {
            for (key in itemKeys) {
                val items = data.optJSONArray(key)
                if (items != null) return items
            }
        }
        for (key in itemKeys) {
            val items = root.optJSONArray(key)
            if (items != null) return items
        }
        return JSONArray()
    }

    private fun parseEnvelope(raw: String): JSONObject {
        val root = runCatching { JSONObject(raw) }.getOrNull()
            ?: throw Exception(raw.take(200).ifBlank { "响应格式无效" })
        if (root.has("success") && !root.optBoolean("success", false)) {
            throw Exception(extractMessage(raw).ifBlank { "请求失败" })
        }
        return root
    }

    private fun extractMessage(raw: String): String {
        return runCatching {
            val json = JSONObject(raw)
            json.optString("message").ifBlank {
                json.optJSONObject("error")?.optString("message").orEmpty()
            }.ifBlank {
                json.optJSONObject("error")?.optString("code").orEmpty()
            }
        }.getOrDefault(raw.take(200))
    }

    private fun optIntAny(obj: JSONObject, vararg names: String): Int {
        for (name in names) {
            if (obj.has(name) && !obj.isNull(name)) {
                return obj.optInt(name, 0)
            }
        }
        return 0
    }

    private fun optDoubleAny(obj: JSONObject, vararg names: String): Double {
        for (name in names) {
            if (obj.has(name) && !obj.isNull(name)) {
                return obj.optDouble(name, 0.0)
            }
        }
        return 0.0
    }
}

data class NaapiPlan(
    val id: Long,
    val name: String,
    val subtitle: String,
    val priceAmount: Int,
    val currency: String,
    val quotaAmount: Int,
    val validDays: Int,
    val description: String
)

data class NaapiCreateOrderResult(
    val orderNo: String,
    val status: String,
    val amount: Int,
    val currency: String,
    val payUrl: String
)

data class NaapiOrderStatus(
    val orderNo: String,
    val status: String,
    val amount: Int,
    val currency: String,
    val planId: Long,
    val codePrefix: String?,
    val licenseCode: String?,
    val redeemCode: String?,
    val gatewayKey: String?,
    val gatewayBaseUrl: String?
)

data class NaapiUsageSummary(
    val planName: String,
    val balanceAmount: Double,
    val todayAmount: Double,
    val monthAmount: Double,
    val currency: String,
    val totalRequests: Int,
    val quotaAmount: Double,
    val expiresAt: String?
)

data class NaapiDeviceInfo(
    val id: String,
    val name: String,
    val gatewayKeyPrefix: String,
    val lastUsedAt: String,
    val current: Boolean,
    val revoked: Boolean
)

data class NaapiUsageLogItem(
    val id: String,
    val model: String,
    val amount: Double,
    val currency: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val status: String,
    val createdAt: String,
    val deviceName: String
)

data class NaapiOrderRecord(
    val orderNo: String,
    val planName: String,
    val status: String,
    val amount: Double,
    val currency: String,
    val createdAt: String,
    val paidAt: String
)

data class NaapiModelCatalogItem(
    val id: String,
    val displayName: String,
    val vendor: String,
    val category: String,
    val supportsVision: Boolean,
    val supportsTools: Boolean,
    val supportsResponses: Boolean,
    val speed: String,
    val quality: String,
    val costLevel: String,
    val recommended: Boolean
)
