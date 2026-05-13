package com.tchat.wanxiaot.util

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.tchat.wanxiaot.BuildConfig
import com.tchat.wanxiaot.settings.AIProviderType
import com.tchat.wanxiaot.settings.ProviderAuthType
import com.tchat.wanxiaot.settings.ProviderConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

object NaapiTChatSupport {
    const val DEVICE_HEADER = "X-TChat-Device-Id"
    const val DEFAULT_ENDPOINT = "https://t.naapi.cc/v1"

    private const val PREFS_NAME = "naapi_tchat"
    private const val KEY_APP_DEVICE_ID = "app_device_id"
    private const val KEY_PENDING_ORDER = "pending_order"

    fun getOrCreateDeviceId(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_APP_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) {
            return existing
        }
        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_APP_DEVICE_ID, created).apply()
        return created
    }

    fun headers(context: Context): Map<String, String> {
        return mapOf(DEVICE_HEADER to getOrCreateDeviceId(context))
    }

    fun withDeviceHeader(context: Context, headers: Map<String, String>): Map<String, String> {
        return headers + this.headers(context)
    }

    fun requestHeadersForProvider(context: Context, provider: ProviderConfig): Map<String, String> {
        return requestHeadersFor(
            context = context,
            providerType = provider.providerType,
            authType = provider.authType,
            headers = provider.customHeaders
        )
    }

    fun requestHeadersFor(
        context: Context,
        providerType: AIProviderType,
        authType: ProviderAuthType,
        headers: Map<String, String>
    ): Map<String, String> {
        return when {
            providerType == AIProviderType.NAAPI_TCHAT &&
                authType != ProviderAuthType.GATEWAY_KEY &&
                authType != ProviderAuthType.NONE -> {
                withDeviceHeader(context, headers - DEVICE_HEADER)
            }
            providerType == AIProviderType.NAAPI_TCHAT -> headers - DEVICE_HEADER
            else -> headers
        }
    }

    fun persistableCustomHeaders(providerType: AIProviderType, headers: Map<String, String>): Map<String, String> {
        return if (providerType == AIProviderType.NAAPI_TCHAT) {
            headers - DEVICE_HEADER
        } else {
            headers
        }
    }

    fun maskedDeviceId(context: Context): String {
        val value = getOrCreateDeviceId(context)
        return if (value.length <= 12) "****" else "${value.take(8)}…${value.takeLast(4)}"
    }

    fun portalBaseFromEndpoint(endpoint: String): String {
        val normalized = endpoint.trim().ifBlank { DEFAULT_ENDPOINT }.trimEnd('/')
        return when {
            normalized.endsWith("/v1") -> normalized.removeSuffix("/v1")
            else -> normalized
        }
    }

    fun buildDeviceInfoJson(context: Context): JSONObject {
        return JSONObject().apply {
            put("app_device_id", getOrCreateDeviceId(context))
            put("android_id_hash", androidIdHash(context))
            put("manufacturer", Build.MANUFACTURER.orEmpty())
            put("brand", Build.BRAND.orEmpty())
            put("model", Build.MODEL.orEmpty())
            put("android_version", Build.VERSION.RELEASE.orEmpty())
            put("sdk_int", Build.VERSION.SDK_INT)
            put("app_version", BuildConfig.VERSION_NAME)
        }
    }

    fun savePendingOrder(context: Context, order: NaapiPendingOrder) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val payload = JSONObject().apply {
            put("endpoint", order.endpoint)
            put("order_no", order.orderNo)
            put("poll_token", order.pollToken)
            put("pay_url", order.payUrl)
            put("plan_id", order.planId)
            put("created_at", order.createdAt)
        }
        prefs.edit().putString(KEY_PENDING_ORDER, payload.toString()).apply()
    }

    fun loadPendingOrder(context: Context): NaapiPendingOrder? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_PENDING_ORDER, null).orEmpty()
        if (raw.isBlank()) return null
        return runCatching {
            val obj = JSONObject(raw)
            NaapiPendingOrder(
                endpoint = obj.optString("endpoint", DEFAULT_ENDPOINT).ifBlank { DEFAULT_ENDPOINT },
                orderNo = obj.optString("order_no"),
                pollToken = obj.optString("poll_token"),
                payUrl = obj.optString("pay_url"),
                planId = obj.optLong("plan_id", 0L),
                createdAt = obj.optLong("created_at", 0L)
            ).takeIf {
                it.orderNo.isNotBlank() && it.pollToken.isNotBlank() && it.payUrl.isNotBlank()
            }
        }.getOrNull()
    }

    fun clearPendingOrder(context: Context, orderNo: String? = null) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (orderNo.isNullOrBlank()) {
            prefs.edit().remove(KEY_PENDING_ORDER).apply()
            return
        }
        val current = loadPendingOrder(context)
        if (current?.orderNo == orderNo) {
            prefs.edit().remove(KEY_PENDING_ORDER).apply()
        }
    }

    suspend fun activateDevice(
        context: Context,
        httpClient: OkHttpClient,
        endpoint: String,
        redeemCode: String
    ): NaapiActivationResult = withContext(Dispatchers.IO) {
        val trimmedCode = redeemCode.trim()
        if (trimmedCode.isBlank()) {
            return@withContext NaapiActivationResult(false, "请先填写兑换码")
        }

        val portalBase = portalBaseFromEndpoint(endpoint)
        val url = "$portalBase/api/tchat/activate"
        val body = JSONObject().apply {
            put("redeem_code", trimmedCode)
            put("device", buildDeviceInfoJson(context))
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext NaapiActivationResult(
                        success = false,
                        message = extractMessage(responseBody).ifBlank {
                            "设备激活失败：HTTP ${response.code}"
                        }
                    )
                }

                val json = runCatching { JSONObject(responseBody) }.getOrNull()
                val success = json?.optBoolean("success", false) ?: false
                val data = json?.optJSONObject("data") ?: json
                val message = if (success) {
                    "当前设备已激活"
                } else {
                    json?.optString("message").orEmpty().ifBlank { "设备激活失败" }
                }
                NaapiActivationResult(
                    success = success,
                    message = message,
                    gatewayKey = data?.optString("gateway_key")?.takeIf { it.isNotBlank() }
                        ?: data?.optString("gatewayKey")?.takeIf { it.isNotBlank() },
                    gatewayBaseUrl = data?.optString("gateway_base_url")?.takeIf { it.isNotBlank() }
                        ?: data?.optString("gatewayBaseUrl")?.takeIf { it.isNotBlank() }
                )
            }
        } catch (e: Exception) {
            NaapiActivationResult(success = false, message = "设备激活失败：${e.message ?: "网络异常"}")
        }
    }

    private fun androidIdHash(context: Context): String {
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull().orEmpty()
        return if (androidId.isBlank()) "" else sha256Hex(androidId)
    }

    private fun sha256Hex(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun extractMessage(raw: String): String {
        return runCatching {
            val json = JSONObject(raw)
            json.optString("message").ifBlank {
                json.optJSONObject("error")?.optString("message").orEmpty()
            }
        }.getOrDefault(raw.take(200))
    }
}

data class NaapiActivationResult(
    val success: Boolean,
    val message: String,
    val gatewayKey: String? = null,
    val gatewayBaseUrl: String? = null
)

data class NaapiPendingOrder(
    val endpoint: String,
    val orderNo: String,
    val pollToken: String,
    val payUrl: String,
    val planId: Long,
    val createdAt: Long
)
