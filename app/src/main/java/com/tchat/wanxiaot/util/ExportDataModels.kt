package com.tchat.wanxiaot.util

import com.tchat.wanxiaot.settings.ProviderConfig
import com.tchat.wanxiaot.settings.ApiKeyEntry
import com.tchat.wanxiaot.settings.ApiKeyStatus
import com.tchat.wanxiaot.settings.KeySelectionStrategy
import com.tchat.wanxiaot.settings.ModelCapabilityConfig
import com.tchat.wanxiaot.settings.ProviderAuthType
import com.tchat.wanxiaot.settings.ProviderBillingMode
import com.tchat.wanxiaot.settings.ServiceMode
import org.json.JSONArray
import org.json.JSONObject

private fun modelCapabilitiesToJson(capabilities: Map<String, ModelCapabilityConfig>): JSONObject {
    val json = JSONObject()
    capabilities.forEach { (modelName, capability) ->
        val item = JSONObject()
        item.put("displayName", capability.displayName)
        item.put("vendor", capability.vendor)
        item.put("category", capability.category)
        item.put("supportsVision", capability.supportsVision)
        item.put("supportsTools", capability.supportsTools)
        item.put("supportsResponses", capability.supportsResponses)
        item.put("supportsImageGeneration", capability.supportsImageGeneration)
        item.put("supportsEmbedding", capability.supportsEmbedding)
        item.put("speed", capability.speed)
        item.put("quality", capability.quality)
        item.put("costLevel", capability.costLevel)
        item.put("recommended", capability.recommended)
        json.put(modelName, item)
    }
    return json
}

private fun jsonToModelCapabilities(json: JSONObject?): Map<String, ModelCapabilityConfig> {
    if (json == null) return emptyMap()
    val result = linkedMapOf<String, ModelCapabilityConfig>()
    val keys = json.keys()
    while (keys.hasNext()) {
        val modelName = keys.next()
        val item = json.optJSONObject(modelName) ?: continue
        result[modelName] = ModelCapabilityConfig(
            modelName = modelName,
            displayName = item.optString("displayName", ""),
            vendor = item.optString("vendor", ""),
            category = item.optString("category", ""),
            supportsVision = item.optBoolean("supportsVision", false),
            supportsTools = item.optBoolean("supportsTools", false),
            supportsResponses = item.optBoolean("supportsResponses", false),
            supportsImageGeneration = item.optBoolean("supportsImageGeneration", false),
            supportsEmbedding = item.optBoolean("supportsEmbedding", false),
            speed = item.optString("speed", ""),
            quality = item.optString("quality", ""),
            costLevel = item.optString("costLevel", ""),
            recommended = item.optBoolean("recommended", false)
        )
    }
    return result
}

private fun stringMapToJson(values: Map<String, String>): JSONObject {
    val json = JSONObject()
    values.forEach { (key, value) ->
        if (key.isNotBlank() && value.isNotBlank()) {
            json.put(key, value)
        }
    }
    return json
}

private fun jsonToStringMap(json: JSONObject?): Map<String, String> {
    if (json == null) return emptyMap()
    val result = linkedMapOf<String, String>()
    val keys = json.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        val value = json.optString(key, "")
        if (key.isNotBlank() && value.isNotBlank()) {
            result[key] = value
        }
    }
    return result
}

/**
 * 导出数据类型
 */
enum class ExportDataType {
    PROVIDERS,           // 供应商配置
    API_CONFIG,          // API配置
    KNOWLEDGE_BASE,      // 知识库
    CHAT_FOLDERS,        // 聊天文件夹
    ASSISTANTS,          // 助手配置
    SKILLS               // Skills 配置
}

/**
 * 导出数据包装器
 */
data class ExportData(
    val type: ExportDataType,
    val version: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val encrypted: Boolean = false,
    val data: String  // JSON字符串
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("type", type.name)
        json.put("version", version)
        json.put("timestamp", timestamp)
        json.put("encrypted", encrypted)
        json.put("data", data)
        return json.toString()
    }

    companion object {
        fun fromJson(jsonString: String): ExportData {
            val json = JSONObject(jsonString)
            return ExportData(
                type = ExportDataType.valueOf(json.getString("type")),
                version = json.getInt("version"),
                timestamp = json.getLong("timestamp"),
                encrypted = json.getBoolean("encrypted"),
                data = json.getString("data")
            )
        }
    }
}

/**
 * 供应商配置导出数据
 */
data class ProvidersExportData(
    val providers: List<ProviderConfig>
) {
    fun toJson(): String {
        val json = JSONObject()
        val providersArray = JSONArray()
        providers.forEach { provider ->
            val providerJson = JSONObject()
            providerJson.put("id", provider.id)
            providerJson.put("name", provider.name)
            providerJson.put("providerType", provider.providerType.name)
            providerJson.put("serviceMode", provider.serviceMode.name)
            providerJson.put("billingMode", provider.billingMode.name)
            providerJson.put("authType", provider.authType.name)
            providerJson.put("apiKey", provider.apiKey)
            providerJson.put("endpoint", provider.endpoint)
            providerJson.put("apiPath", provider.apiPath)
            providerJson.put("modelsPath", provider.modelsPath)
            providerJson.put("imagesPath", provider.imagesPath)
            providerJson.put("embeddingsPath", provider.embeddingsPath)
            providerJson.put("modelCatalogPath", provider.modelCatalogPath)
            providerJson.put("authHeaderName", provider.authHeaderName)
            providerJson.put("authHeaderPrefix", provider.authHeaderPrefix)
            providerJson.put("useProxy", provider.useProxy)
            providerJson.put("selectedModel", provider.selectedModel)
            providerJson.put("availableModels", JSONArray(provider.availableModels))
            providerJson.put("modelCapabilities", modelCapabilitiesToJson(provider.modelCapabilities))
            providerJson.put(
                "customHeaders",
                stringMapToJson(NaapiTChatSupport.persistableCustomHeaders(provider.providerType, provider.customHeaders))
            )

            // 序列化模型自定义参数
            val paramsJson = JSONObject()
            provider.modelCustomParams.forEach { (modelName, params) ->
                val paramObj = JSONObject()
                params.temperature?.let { paramObj.put("temperature", it.toDouble()) }
                params.topP?.let { paramObj.put("topP", it.toDouble()) }
                params.topK?.let { paramObj.put("topK", it) }
                params.presencePenalty?.let { paramObj.put("presencePenalty", it.toDouble()) }
                params.frequencyPenalty?.let { paramObj.put("frequencyPenalty", it.toDouble()) }
                params.repetitionPenalty?.let { paramObj.put("repetitionPenalty", it.toDouble()) }
                params.maxTokens?.let { paramObj.put("maxTokens", it) }
                if (params.extraParams.isNotEmpty() && params.extraParams != "{}") {
                    paramObj.put("extraParams", params.extraParams)
                }
                paramsJson.put(modelName, paramObj)
            }
            providerJson.put("modelCustomParams", paramsJson)

            // 多 Key 配置（清理运行时统计字段，避免导出过大）
            providerJson.put("multiKeyEnabled", provider.multiKeyEnabled)
            providerJson.put("keySelectionStrategy", provider.keySelectionStrategy.name)
            providerJson.put("maxFailuresBeforeDisable", provider.maxFailuresBeforeDisable)
            providerJson.put("autoRecoveryMinutes", provider.autoRecoveryMinutes)

            val apiKeysArray = JSONArray()
            provider.apiKeys.forEach { key ->
                val keyObj = JSONObject()
                keyObj.put("id", key.id)
                keyObj.put("key", key.key)
                keyObj.put("name", key.name)
                keyObj.put("isEnabled", key.isEnabled)
                keyObj.put("priority", key.priority)
                // reset runtime fields
                keyObj.put("status", ApiKeyStatus.ACTIVE.name)
                keyObj.put("requestCount", 0)
                keyObj.put("successCount", 0)
                keyObj.put("failureCount", 0)
                keyObj.put("lastUsedAt", 0)
                keyObj.put("statusChangedAt", 0)
                apiKeysArray.put(keyObj)
            }
            providerJson.put("apiKeys", apiKeysArray)
            providerJson.put("roundRobinIndex", 0)
            providersArray.put(providerJson)
        }
        json.put("providers", providersArray)
        return json.toString()
    }

    companion object {
        fun fromJson(jsonString: String): ProvidersExportData {
            val json = JSONObject(jsonString)
            val providersArray = json.getJSONArray("providers")
            val providers = mutableListOf<ProviderConfig>()

            for (i in 0 until providersArray.length()) {
                val providerJson = providersArray.getJSONObject(i)

                // 解析模型自定义参数
                val modelCustomParams = mutableMapOf<String, com.tchat.wanxiaot.settings.ModelCustomParams>()
                if (providerJson.has("modelCustomParams")) {
                    val paramsJson = providerJson.getJSONObject("modelCustomParams")
                    val keys = paramsJson.keys()
                    while (keys.hasNext()) {
                        val modelName = keys.next()
                        val paramObj = paramsJson.getJSONObject(modelName)
                        val params = com.tchat.wanxiaot.settings.ModelCustomParams(
                            modelName = modelName,
                            temperature = if (paramObj.has("temperature")) paramObj.getDouble("temperature").toFloat() else null,
                            topP = if (paramObj.has("topP")) paramObj.getDouble("topP").toFloat() else null,
                            topK = if (paramObj.has("topK")) paramObj.getInt("topK") else null,
                            presencePenalty = if (paramObj.has("presencePenalty")) paramObj.getDouble("presencePenalty").toFloat() else null,
                            frequencyPenalty = if (paramObj.has("frequencyPenalty")) paramObj.getDouble("frequencyPenalty").toFloat() else null,
                            repetitionPenalty = if (paramObj.has("repetitionPenalty")) paramObj.getDouble("repetitionPenalty").toFloat() else null,
                            maxTokens = if (paramObj.has("maxTokens")) paramObj.getInt("maxTokens") else null,
                            extraParams = if (paramObj.has("extraParams")) paramObj.getString("extraParams") else "{}"
                        )
                        modelCustomParams[modelName] = params
                    }
                }

                // 解析可用模型列表
                val modelsArray = providerJson.getJSONArray("availableModels")
                val availableModels = mutableListOf<String>()
                for (j in 0 until modelsArray.length()) {
                    availableModels.add(modelsArray.getString(j))
                }

                // 解析供应商类型
                val providerType = try {
                    com.tchat.wanxiaot.settings.AIProviderType.valueOf(providerJson.getString("providerType"))
                } catch (e: Exception) {
                    com.tchat.wanxiaot.settings.AIProviderType.OPENAI
                }

                // 解析多 Key 配置
                val multiKeyEnabled = providerJson.optBoolean("multiKeyEnabled", false)
                val keySelectionStrategy = try {
                    KeySelectionStrategy.valueOf(providerJson.optString("keySelectionStrategy", "ROUND_ROBIN"))
                } catch (e: Exception) {
                    KeySelectionStrategy.ROUND_ROBIN
                }
                val maxFailuresBeforeDisable = providerJson.optInt("maxFailuresBeforeDisable", 3)
                val autoRecoveryMinutes = providerJson.optInt("autoRecoveryMinutes", 5)
                val roundRobinIndex = providerJson.optInt("roundRobinIndex", 0)

                val apiKeys = mutableListOf<ApiKeyEntry>()
                val apiKeysArray = providerJson.optJSONArray("apiKeys")
                if (apiKeysArray != null) {
                    for (j in 0 until apiKeysArray.length()) {
                        val keyObj = apiKeysArray.optJSONObject(j) ?: continue
                        val keyValue = keyObj.optString("key", "").trim()
                        if (keyValue.isBlank()) continue

                        val status = try {
                            ApiKeyStatus.valueOf(keyObj.optString("status", ApiKeyStatus.ACTIVE.name))
                        } catch (e: Exception) {
                            ApiKeyStatus.ACTIVE
                        }

                        apiKeys.add(
                            ApiKeyEntry(
                                id = keyObj.optString("id", java.util.UUID.randomUUID().toString()),
                                key = keyValue,
                                name = keyObj.optString("name", ""),
                                isEnabled = keyObj.optBoolean("isEnabled", true),
                                priority = keyObj.optInt("priority", 5).coerceIn(1, 10),
                                requestCount = keyObj.optInt("requestCount", 0),
                                successCount = keyObj.optInt("successCount", 0),
                                failureCount = keyObj.optInt("failureCount", 0),
                                lastUsedAt = keyObj.optLong("lastUsedAt", 0),
                                lastError = keyObj.optString("lastError", "").takeIf { it.isNotEmpty() },
                                status = status,
                                statusChangedAt = keyObj.optLong("statusChangedAt", 0)
                            )
                        )
                    }
                }

                val provider = ProviderConfig(
                    id = providerJson.getString("id"),
                    name = providerJson.getString("name"),
                    providerType = providerType,
                    serviceMode = runCatching {
                        ServiceMode.valueOf(providerJson.optString("serviceMode", "CUSTOM"))
                    }.getOrDefault(ServiceMode.CUSTOM),
                    billingMode = runCatching {
                        ProviderBillingMode.valueOf(providerJson.optString("billingMode", "USER_API_KEY"))
                    }.getOrDefault(ProviderBillingMode.USER_API_KEY),
                    authType = runCatching {
                        ProviderAuthType.valueOf(providerJson.optString("authType", "BEARER"))
                    }.getOrDefault(ProviderAuthType.BEARER),
                    apiKey = providerJson.getString("apiKey"),
                    endpoint = providerJson.getString("endpoint"),
                    apiPath = providerJson.optString("apiPath", ""),
                    modelsPath = providerJson.optString("modelsPath", ""),
                    imagesPath = providerJson.optString("imagesPath", ""),
                    embeddingsPath = providerJson.optString("embeddingsPath", ""),
                    modelCatalogPath = providerJson.optString("modelCatalogPath", ""),
                    authHeaderName = providerJson.optString("authHeaderName", "Authorization"),
                    authHeaderPrefix = providerJson.optString("authHeaderPrefix", "Bearer "),
                    useProxy = providerJson.optBoolean("useProxy", false),
                    selectedModel = providerJson.getString("selectedModel"),
                    availableModels = availableModels,
                    modelCapabilities = jsonToModelCapabilities(providerJson.optJSONObject("modelCapabilities")),
                    modelCustomParams = modelCustomParams,
                    customHeaders = jsonToStringMap(providerJson.optJSONObject("customHeaders")),
                    // 多 Key 管理
                    apiKeys = apiKeys,
                    multiKeyEnabled = multiKeyEnabled,
                    keySelectionStrategy = keySelectionStrategy,
                    roundRobinIndex = roundRobinIndex,
                    maxFailuresBeforeDisable = maxFailuresBeforeDisable,
                    autoRecoveryMinutes = autoRecoveryMinutes
                )
                providers.add(provider)
            }

            return ProvidersExportData(providers)
        }
    }
}

/**
 * API配置导出数据（单个供应商的完整配置，包括API密钥）
 */
data class ApiConfigExportData(
    val provider: ProviderConfig
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("id", provider.id)
        json.put("name", provider.name)
        json.put("providerType", provider.providerType.name)
        json.put("serviceMode", provider.serviceMode.name)
        json.put("billingMode", provider.billingMode.name)
        json.put("authType", provider.authType.name)
        json.put("apiKey", provider.apiKey)
        json.put("endpoint", provider.endpoint)
        json.put("apiPath", provider.apiPath)
        json.put("modelsPath", provider.modelsPath)
        json.put("imagesPath", provider.imagesPath)
        json.put("embeddingsPath", provider.embeddingsPath)
        json.put("modelCatalogPath", provider.modelCatalogPath)
        json.put("authHeaderName", provider.authHeaderName)
        json.put("authHeaderPrefix", provider.authHeaderPrefix)
        json.put("useProxy", provider.useProxy)
        json.put("selectedModel", provider.selectedModel)
        json.put("availableModels", JSONArray(provider.availableModels))
        json.put("modelCapabilities", modelCapabilitiesToJson(provider.modelCapabilities))
        json.put(
            "customHeaders",
            stringMapToJson(NaapiTChatSupport.persistableCustomHeaders(provider.providerType, provider.customHeaders))
        )

        // 序列化模型自定义参数
        val paramsJson = JSONObject()
        provider.modelCustomParams.forEach { (modelName, params) ->
            val paramObj = JSONObject()
            params.temperature?.let { paramObj.put("temperature", it.toDouble()) }
            params.topP?.let { paramObj.put("topP", it.toDouble()) }
            params.topK?.let { paramObj.put("topK", it) }
            params.presencePenalty?.let { paramObj.put("presencePenalty", it.toDouble()) }
            params.frequencyPenalty?.let { paramObj.put("frequencyPenalty", it.toDouble()) }
            params.repetitionPenalty?.let { paramObj.put("repetitionPenalty", it.toDouble()) }
            params.maxTokens?.let { paramObj.put("maxTokens", it) }
            if (params.extraParams.isNotEmpty() && params.extraParams != "{}") {
                paramObj.put("extraParams", params.extraParams)
            }
            paramsJson.put(modelName, paramObj)
        }
        json.put("modelCustomParams", paramsJson)

        // 多 Key 配置（完整导出，包含运行时状态）
        json.put("multiKeyEnabled", provider.multiKeyEnabled)
        json.put("keySelectionStrategy", provider.keySelectionStrategy.name)
        json.put("roundRobinIndex", provider.roundRobinIndex)
        json.put("maxFailuresBeforeDisable", provider.maxFailuresBeforeDisable)
        json.put("autoRecoveryMinutes", provider.autoRecoveryMinutes)

        val apiKeysArray = JSONArray()
        provider.apiKeys.forEach { key ->
            val keyObj = JSONObject()
            keyObj.put("id", key.id)
            keyObj.put("key", key.key)
            keyObj.put("name", key.name)
            keyObj.put("isEnabled", key.isEnabled)
            keyObj.put("priority", key.priority)
            keyObj.put("requestCount", key.requestCount)
            keyObj.put("successCount", key.successCount)
            keyObj.put("failureCount", key.failureCount)
            keyObj.put("lastUsedAt", key.lastUsedAt)
            key.lastError?.let { keyObj.put("lastError", it) }
            keyObj.put("status", key.status.name)
            keyObj.put("statusChangedAt", key.statusChangedAt)
            apiKeysArray.put(keyObj)
        }
        json.put("apiKeys", apiKeysArray)
        return json.toString()
    }

    companion object {
        fun fromJson(jsonString: String): ApiConfigExportData {
            val json = JSONObject(jsonString)

            // 解析模型自定义参数
            val modelCustomParams = mutableMapOf<String, com.tchat.wanxiaot.settings.ModelCustomParams>()
            if (json.has("modelCustomParams")) {
                val paramsJson = json.getJSONObject("modelCustomParams")
                val keys = paramsJson.keys()
                while (keys.hasNext()) {
                    val modelName = keys.next()
                    val paramObj = paramsJson.getJSONObject(modelName)
                    val params = com.tchat.wanxiaot.settings.ModelCustomParams(
                        modelName = modelName,
                        temperature = if (paramObj.has("temperature")) paramObj.getDouble("temperature").toFloat() else null,
                        topP = if (paramObj.has("topP")) paramObj.getDouble("topP").toFloat() else null,
                        topK = if (paramObj.has("topK")) paramObj.getInt("topK") else null,
                        presencePenalty = if (paramObj.has("presencePenalty")) paramObj.getDouble("presencePenalty").toFloat() else null,
                        frequencyPenalty = if (paramObj.has("frequencyPenalty")) paramObj.getDouble("frequencyPenalty").toFloat() else null,
                        repetitionPenalty = if (paramObj.has("repetitionPenalty")) paramObj.getDouble("repetitionPenalty").toFloat() else null,
                        maxTokens = if (paramObj.has("maxTokens")) paramObj.getInt("maxTokens") else null,
                        extraParams = if (paramObj.has("extraParams")) paramObj.getString("extraParams") else "{}"
                    )
                    modelCustomParams[modelName] = params
                }
            }

            // 解析可用模型列表
            val modelsArray = json.getJSONArray("availableModels")
            val availableModels = mutableListOf<String>()
            for (i in 0 until modelsArray.length()) {
                availableModels.add(modelsArray.getString(i))
            }

            // 解析供应商类型
            val providerType = try {
                com.tchat.wanxiaot.settings.AIProviderType.valueOf(json.getString("providerType"))
            } catch (e: Exception) {
                com.tchat.wanxiaot.settings.AIProviderType.OPENAI
            }

            // 解析多 Key 配置
            val multiKeyEnabled = json.optBoolean("multiKeyEnabled", false)
            val keySelectionStrategy = try {
                KeySelectionStrategy.valueOf(json.optString("keySelectionStrategy", "ROUND_ROBIN"))
            } catch (e: Exception) {
                KeySelectionStrategy.ROUND_ROBIN
            }
            val roundRobinIndex = json.optInt("roundRobinIndex", 0)
            val maxFailuresBeforeDisable = json.optInt("maxFailuresBeforeDisable", 3)
            val autoRecoveryMinutes = json.optInt("autoRecoveryMinutes", 5)

            val apiKeys = mutableListOf<ApiKeyEntry>()
            val apiKeysArray = json.optJSONArray("apiKeys")
            if (apiKeysArray != null) {
                for (i in 0 until apiKeysArray.length()) {
                    val keyObj = apiKeysArray.optJSONObject(i) ?: continue
                    val keyValue = keyObj.optString("key", "").trim()
                    if (keyValue.isBlank()) continue

                    val status = try {
                        ApiKeyStatus.valueOf(keyObj.optString("status", ApiKeyStatus.ACTIVE.name))
                    } catch (e: Exception) {
                        ApiKeyStatus.ACTIVE
                    }

                    apiKeys.add(
                        ApiKeyEntry(
                            id = keyObj.optString("id", java.util.UUID.randomUUID().toString()),
                            key = keyValue,
                            name = keyObj.optString("name", ""),
                            isEnabled = keyObj.optBoolean("isEnabled", true),
                            priority = keyObj.optInt("priority", 5).coerceIn(1, 10),
                            requestCount = keyObj.optInt("requestCount", 0),
                            successCount = keyObj.optInt("successCount", 0),
                            failureCount = keyObj.optInt("failureCount", 0),
                            lastUsedAt = keyObj.optLong("lastUsedAt", 0),
                            lastError = keyObj.optString("lastError", "").takeIf { it.isNotEmpty() },
                            status = status,
                            statusChangedAt = keyObj.optLong("statusChangedAt", 0)
                        )
                    )
                }
            }

            val provider = ProviderConfig(
                id = json.getString("id"),
                name = json.getString("name"),
                providerType = providerType,
                serviceMode = runCatching {
                    ServiceMode.valueOf(json.optString("serviceMode", "CUSTOM"))
                }.getOrDefault(ServiceMode.CUSTOM),
                billingMode = runCatching {
                    ProviderBillingMode.valueOf(json.optString("billingMode", "USER_API_KEY"))
                }.getOrDefault(ProviderBillingMode.USER_API_KEY),
                authType = runCatching {
                    ProviderAuthType.valueOf(json.optString("authType", "BEARER"))
                }.getOrDefault(ProviderAuthType.BEARER),
                apiKey = json.getString("apiKey"),
                endpoint = json.getString("endpoint"),
                apiPath = json.optString("apiPath", ""),
                modelsPath = json.optString("modelsPath", ""),
                imagesPath = json.optString("imagesPath", ""),
                embeddingsPath = json.optString("embeddingsPath", ""),
                modelCatalogPath = json.optString("modelCatalogPath", ""),
                authHeaderName = json.optString("authHeaderName", "Authorization"),
                authHeaderPrefix = json.optString("authHeaderPrefix", "Bearer "),
                useProxy = json.optBoolean("useProxy", false),
                selectedModel = json.getString("selectedModel"),
                availableModels = availableModels,
                modelCapabilities = jsonToModelCapabilities(json.optJSONObject("modelCapabilities")),
                modelCustomParams = modelCustomParams,
                customHeaders = jsonToStringMap(json.optJSONObject("customHeaders")),
                // 多 Key 管理
                apiKeys = apiKeys,
                multiKeyEnabled = multiKeyEnabled,
                keySelectionStrategy = keySelectionStrategy,
                roundRobinIndex = roundRobinIndex,
                maxFailuresBeforeDisable = maxFailuresBeforeDisable,
                autoRecoveryMinutes = autoRecoveryMinutes
            )

            return ApiConfigExportData(provider)
        }
    }
}

/**
 * 知识库导出数据
 */
data class KnowledgeBaseExportData(
    val knowledgeBase: KnowledgeBaseInfo,
    val items: List<KnowledgeItemInfo>,
    val chunks: List<KnowledgeChunkInfo>
) {
    fun toJson(): String {
        val json = JSONObject()

        // 知识库基本信息
        val kbJson = JSONObject()
        kbJson.put("id", knowledgeBase.id)
        kbJson.put("name", knowledgeBase.name)
        kbJson.put("description", knowledgeBase.description)
        kbJson.put("embeddingProviderId", knowledgeBase.embeddingProviderId)
        kbJson.put("embeddingModelId", knowledgeBase.embeddingModelId)
        json.put("knowledgeBase", kbJson)

        // 知识库项目（原始文件/URL）
        val itemsArray = JSONArray()
        items.forEach { item ->
            val itemJson = JSONObject()
            itemJson.put("id", item.id)
            itemJson.put("knowledgeBaseId", item.knowledgeBaseId)
            itemJson.put("title", item.title)
            itemJson.put("sourceType", item.sourceType)
            item.sourceUri?.let { itemJson.put("sourceUri", it) }
            item.content?.let { itemJson.put("content", it) }
            item.metadata?.let { itemJson.put("metadata", it) }
            itemJson.put("status", item.status)
            item.errorMessage?.let { itemJson.put("errorMessage", it) }
            itemJson.put("createdAt", item.createdAt)
            itemJson.put("updatedAt", item.updatedAt)
            itemsArray.put(itemJson)
        }
        json.put("items", itemsArray)

        // 向量数据块
        val chunksArray = JSONArray()
        chunks.forEach { chunk ->
            val chunkJson = JSONObject()
            chunkJson.put("id", chunk.id)
            chunkJson.put("itemId", chunk.itemId)
            chunkJson.put("knowledgeBaseId", chunk.knowledgeBaseId)
            chunkJson.put("content", chunk.content)
            chunkJson.put("embedding", JSONArray(chunk.embedding))
            chunkJson.put("chunkIndex", chunk.chunkIndex)
            chunkJson.put("createdAt", chunk.createdAt)
            chunksArray.put(chunkJson)
        }
        json.put("chunks", chunksArray)

        return json.toString()
    }

    companion object {
        fun fromJson(jsonString: String): KnowledgeBaseExportData {
            val json = JSONObject(jsonString)

            // 解析知识库基本信息
            val kbJson = json.getJSONObject("knowledgeBase")
            val knowledgeBase = KnowledgeBaseInfo(
                id = kbJson.getString("id"),
                name = kbJson.getString("name"),
                description = if (kbJson.has("description")) kbJson.getString("description") else null,
                embeddingProviderId = kbJson.getString("embeddingProviderId"),
                embeddingModelId = kbJson.getString("embeddingModelId")
            )

            // 解析知识库项目
            val itemsArray = json.getJSONArray("items")
            val items = mutableListOf<KnowledgeItemInfo>()
            for (i in 0 until itemsArray.length()) {
                val itemJson = itemsArray.getJSONObject(i)
                val sourceType = when {
                    itemJson.has("sourceType") -> itemJson.optString("sourceType", "text")
                    itemJson.has("type") -> itemJson.optString("type", "text")
                    else -> "text"
                }
                val content = if (itemJson.has("content") && !itemJson.isNull("content")) {
                    itemJson.getString("content")
                } else {
                    null
                }
                val sourceUri = if (itemJson.has("sourceUri") && !itemJson.isNull("sourceUri")) {
                    itemJson.getString("sourceUri")
                } else {
                    null
                }
                val metadata = if (itemJson.has("metadata") && !itemJson.isNull("metadata")) {
                    itemJson.getString("metadata")
                } else {
                    null
                }
                val errorMessage = if (itemJson.has("errorMessage") && !itemJson.isNull("errorMessage")) {
                    itemJson.getString("errorMessage")
                } else {
                    null
                }
                items.add(
                    KnowledgeItemInfo(
                        id = itemJson.getString("id"),
                        knowledgeBaseId = itemJson.optString("knowledgeBaseId", ""),
                        title = itemJson.getString("title"),
                        sourceType = sourceType,
                        sourceUri = sourceUri,
                        content = content,
                        metadata = metadata,
                        status = itemJson.optString("status", "PENDING"),
                        errorMessage = errorMessage,
                        createdAt = itemJson.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = itemJson.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }

            // 解析向量数据块
            val chunksArray = json.getJSONArray("chunks")
            val chunks = mutableListOf<KnowledgeChunkInfo>()
            for (i in 0 until chunksArray.length()) {
                val chunkJson = chunksArray.getJSONObject(i)
                val embedding = chunkJson.optJSONArray("embedding")?.let { embeddingArray ->
                    FloatArray(embeddingArray.length()) { j ->
                        embeddingArray.getDouble(j).toFloat()
                    }
                } ?: run {
                    val embeddingJson = chunkJson.optString("embedding", "[]")
                    val embeddingArray = JSONArray(embeddingJson)
                    FloatArray(embeddingArray.length()) { j ->
                        embeddingArray.getDouble(j).toFloat()
                    }
                }
                chunks.add(
                    KnowledgeChunkInfo(
                        id = chunkJson.getString("id"),
                        itemId = chunkJson.getString("itemId"),
                        knowledgeBaseId = chunkJson.optString("knowledgeBaseId", ""),
                        content = chunkJson.getString("content"),
                        embedding = embedding,
                        chunkIndex = chunkJson.getInt("chunkIndex"),
                        createdAt = chunkJson.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }

            return KnowledgeBaseExportData(knowledgeBase, items, chunks)
        }
    }
}

/**
 * 知识库基本信息
 */
data class KnowledgeBaseInfo(
    val id: String,
    val name: String,
    val description: String?,
    val embeddingProviderId: String,
    val embeddingModelId: String
)

/**
 * 知识库项目信息
 */
data class KnowledgeItemInfo(
    val id: String,
    val knowledgeBaseId: String,
    val title: String,
    val sourceType: String,
    val sourceUri: String?,
    val content: String?,
    val metadata: String?,
    val status: String,
    val errorMessage: String?,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * 知识库向量块信息
 */
data class KnowledgeChunkInfo(
    val id: String,
    val itemId: String,
    val knowledgeBaseId: String,
    val content: String,
    val embedding: FloatArray,
    val chunkIndex: Int,
    val createdAt: Long
)

/**
 * Skills 导出数据
 */
data class SkillsExportData(
    val skills: List<SkillExportInfo>
) {
    fun toJson(): String {
        val json = JSONObject()
        val skillsArray = JSONArray()
        skills.forEach { skill ->
            val skillJson = JSONObject()
            skillJson.put("id", skill.id)
            skillJson.put("name", skill.name)
            skillJson.put("displayName", skill.displayName)
            skillJson.put("description", skill.description)
            skillJson.put("content", skill.content)
            skillJson.put("triggerKeywords", JSONArray(skill.triggerKeywords))
            skillJson.put("priority", skill.priority)
            skillJson.put("enabled", skill.enabled)
            skillJson.put("isBuiltIn", skill.isBuiltIn)
            skillJson.put("createdAt", skill.createdAt)
            skillJson.put("updatedAt", skill.updatedAt)
            skillsArray.put(skillJson)
        }
        json.put("skills", skillsArray)
        return json.toString()
    }

    companion object {
        fun fromJson(jsonString: String): SkillsExportData {
            val json = JSONObject(jsonString)
            val skillsArray = json.getJSONArray("skills")
            val skills = mutableListOf<SkillExportInfo>()

            for (i in 0 until skillsArray.length()) {
                val skillJson = skillsArray.getJSONObject(i)

                // 解析触发关键词
                val keywordsArray = skillJson.optJSONArray("triggerKeywords")
                val triggerKeywords = mutableListOf<String>()
                if (keywordsArray != null) {
                    for (j in 0 until keywordsArray.length()) {
                        triggerKeywords.add(keywordsArray.getString(j))
                    }
                }

                skills.add(
                    SkillExportInfo(
                        id = skillJson.getString("id"),
                        name = skillJson.getString("name"),
                        displayName = skillJson.getString("displayName"),
                        description = skillJson.getString("description"),
                        content = skillJson.getString("content"),
                        triggerKeywords = triggerKeywords,
                        priority = skillJson.optInt("priority", 0),
                        enabled = skillJson.optBoolean("enabled", false),
                        isBuiltIn = skillJson.optBoolean("isBuiltIn", false),
                        createdAt = skillJson.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = skillJson.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }

            return SkillsExportData(skills)
        }
    }
}

/**
 * Skill 导出信息
 */
data class SkillExportInfo(
    val id: String,
    val name: String,
    val displayName: String,
    val description: String,
    val content: String,
    val triggerKeywords: List<String>,
    val priority: Int,
    val enabled: Boolean,
    val isBuiltIn: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)
