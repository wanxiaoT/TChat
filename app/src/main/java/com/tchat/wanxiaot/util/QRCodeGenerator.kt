package com.tchat.wanxiaot.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.tchat.wanxiaot.settings.AIProviderType
import com.tchat.wanxiaot.settings.ProviderConfig
import org.json.JSONArray
import org.json.JSONObject

/**
 * 二维码生成工具
 */

/**
 * n=name（名字）
 * t=type（类型）
 * e=endpoint（端点）
 * k=apiKey（API密钥）
 * m=models（模型列表，可选）
 * s=selectedModel（当前选择的模型）
 */

object QRCodeGenerator {

    /**
     * 将服务商配置转换为精简JSON
     * @param provider 服务商配置
     * @param includeModels 是否包含模型列表
     */
    fun providerToJson(provider: ProviderConfig, includeModels: Boolean = true): String {
        val json = JSONObject()
        json.put("n", provider.name)
        json.put("t", when (provider.providerType) {
            AIProviderType.OPENAI -> "o"
            AIProviderType.OPENAI_RESPONSES -> "r"
            AIProviderType.ANTHROPIC -> "a"
            AIProviderType.GEMINI -> "g"
            AIProviderType.DEEPSEEK -> "d"
            AIProviderType.OPENROUTER -> "or"
            AIProviderType.OLLAMA -> "l"
            AIProviderType.NAAPI_TCHAT -> "n"
        })
        json.put("e", provider.endpoint)
        json.put("k", provider.apiKey)
        json.put("sm", provider.serviceMode.name)
        json.put("bm", provider.billingMode.name)
        json.put("at", provider.authType.name)
        json.put("ap", provider.apiPath)
        json.put("mp", provider.modelsPath)
        json.put("ip", provider.imagesPath)
        json.put("ep", provider.embeddingsPath)
        json.put("ah", provider.authHeaderName)
        json.put("ax", provider.authHeaderPrefix)
        
        if (includeModels && provider.availableModels.isNotEmpty()) {
            json.put("m", JSONArray(provider.availableModels))
            if (provider.selectedModel.isNotEmpty()) {
                json.put("s", provider.selectedModel)
            }
        }
        
        return json.toString()
    }

    /**
     * 从JSON解析服务商类型
     */
    fun parseProviderType(typeCode: String): AIProviderType {
        return when (typeCode) {
            "o" -> AIProviderType.OPENAI
            "r" -> AIProviderType.OPENAI_RESPONSES
            "a" -> AIProviderType.ANTHROPIC
            "g" -> AIProviderType.GEMINI
            "d" -> AIProviderType.DEEPSEEK
            "or" -> AIProviderType.OPENROUTER
            "l" -> AIProviderType.OLLAMA
            "n" -> AIProviderType.NAAPI_TCHAT
            else -> AIProviderType.OPENAI
        }
    }

    /**
     * 从JSON解析服务商配置
     * @param jsonString 二维码扫描得到的JSON字符串
     * @return ProviderConfig 或 null（解析失败时）
     */
    fun jsonToProvider(jsonString: String): ProviderConfig? {
        return try {
            val json = JSONObject(jsonString)
            val name = json.optString("n", "")
            val typeCode = json.optString("t", "o")
            val endpoint = json.optString("e", "")
            val apiKey = json.optString("k", "")

            if (endpoint.isBlank() || apiKey.isBlank()) {
                return null
            }

            val providerType = parseProviderType(typeCode)
            
            // 解析模型列表
            val modelsArray = json.optJSONArray("m")
            val models = if (modelsArray != null) {
                (0 until modelsArray.length()).map { modelsArray.getString(it) }
            } else {
                providerType.defaultModels
            }
            
            // 解析当前选择的模型
            val selectedModel = json.optString("s", "").ifEmpty {
                models.firstOrNull() ?: ""
            }

            ProviderConfig(
                name = name.ifBlank { providerType.displayName },
                providerType = providerType,
                serviceMode = runCatching {
                    com.tchat.wanxiaot.settings.ServiceMode.valueOf(json.optString("sm", "CUSTOM"))
                }.getOrDefault(com.tchat.wanxiaot.settings.ServiceMode.CUSTOM),
                billingMode = runCatching {
                    com.tchat.wanxiaot.settings.ProviderBillingMode.valueOf(json.optString("bm", "USER_API_KEY"))
                }.getOrDefault(com.tchat.wanxiaot.settings.ProviderBillingMode.USER_API_KEY),
                authType = runCatching {
                    com.tchat.wanxiaot.settings.ProviderAuthType.valueOf(json.optString("at", "BEARER"))
                }.getOrDefault(com.tchat.wanxiaot.settings.ProviderAuthType.BEARER),
                endpoint = endpoint,
                apiKey = apiKey,
                apiPath = json.optString("ap", ""),
                modelsPath = json.optString("mp", ""),
                imagesPath = json.optString("ip", ""),
                embeddingsPath = json.optString("ep", ""),
                authHeaderName = json.optString("ah", "Authorization"),
                authHeaderPrefix = json.optString("ax", "Bearer "),
                selectedModel = selectedModel,
                availableModels = models
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 生成二维码位图
     * @param content 二维码内容
     * @param size 二维码尺寸（像素）
     * @return 二维码Bitmap
     */
    fun generateQRCode(content: String, size: Int = 512): Bitmap {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 1
        )

        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }

        return bitmap
    }
}
