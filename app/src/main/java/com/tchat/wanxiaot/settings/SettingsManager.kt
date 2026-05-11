package com.tchat.wanxiaot.settings

import android.content.Context
import android.content.SharedPreferences
import com.tchat.data.database.AppDatabase
import com.tchat.data.database.entity.AppSettingsEntity
import com.tchat.data.deepresearch.service.WebSearchProvider
import com.tchat.data.model.ChatToolbarItem
import com.tchat.data.model.ChatToolbarItemConfig
import com.tchat.data.model.ChatToolbarSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class SettingsManager(context: Context) {
    private val database = AppDatabase.getInstance(context)
    private val settingsDao = database.appSettingsDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val settingsWriteMutex = Mutex()

    // 用于从 SharedPreferences 迁移数据
    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    private val migrationKey = "settings_migrated_to_room"

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()
    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()
    private val initialized = CompletableDeferred<Unit>()

    // 原子轮询索引管理（解决多线程竞态问题）
    private val roundRobinIndices = ConcurrentHashMap<String, AtomicInteger>()
    private val runtimeProviders = ConcurrentHashMap<String, ProviderConfig>()
    private val providerPersistJobs = ConcurrentHashMap<String, Job>()

    init {
        scope.launch {
            val initialSettings = runCatching { loadSettings() }.getOrDefault(AppSettings())
            applyPersistedSettings(initialSettings)
            _isLoaded.value = true
            initialized.complete(Unit)

            settingsDao.getSettingsFlow().collect { entity ->
                entity?.let { applyPersistedSettings(entityToAppSettings(it)) }
            }
        }
    }

    /**
     * 异步加载设置（用于初始化）
     */
    private suspend fun loadSettings(): AppSettings {
        // 检查是否需要从 SharedPreferences 迁移
        if (!prefs.getBoolean(migrationKey, false)) {
            migrateFromSharedPreferences()
        }

        val entity = settingsDao.getSettings()
        if (entity != null) {
            return entityToAppSettings(entity)
        }

        // 如果数据库中没有设置，创建默认设置
        val defaultEntity = AppSettingsEntity()
        settingsWriteMutex.withLock {
            settingsDao.insertOrReplace(defaultEntity)
        }
        return AppSettings()
    }

    private fun applyPersistedSettings(persistedSettings: AppSettings) {
        synchronizeRuntimeProviders(persistedSettings.providers)
        _settings.value = persistedSettings
    }

    private fun synchronizeRuntimeProviders(providers: List<ProviderConfig>) {
        val validProviderIds = providers.mapTo(mutableSetOf()) { it.id }
        val iterator = runtimeProviders.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in validProviderIds && providerPersistJobs[entry.key]?.isActive != true) {
                iterator.remove()
                roundRobinIndices.remove(entry.key)
            }
        }

        providers.forEach { provider ->
            if (providerPersistJobs[provider.id]?.isActive != true) {
                runtimeProviders[provider.id] = provider
            }
            val atomicIndex = roundRobinIndices[provider.id]
            if (atomicIndex == null) {
                roundRobinIndices[provider.id] = AtomicInteger(provider.roundRobinIndex)
            } else if (providerPersistJobs[provider.id]?.isActive != true) {
                atomicIndex.set(provider.roundRobinIndex)
            }
        }
    }

    suspend fun awaitLoadedSettings(): AppSettings {
        initialized.await()
        return _settings.value
    }

    fun getProviderSnapshot(providerId: String): ProviderConfig? {
        return runtimeProviders[providerId] ?: _settings.value.providers.find { it.id == providerId }
    }

    /**
     * 从 SharedPreferences 迁移数据到 Room
     */
    private suspend fun migrateFromSharedPreferences() {
        try {
            val currentProviderId = prefs.getString("current_provider_id", "") ?: ""
            val currentModel = prefs.getString("current_model", "") ?: ""
            val currentAssistantId = prefs.getString("current_assistant_id", "") ?: ""
            val providersJson = prefs.getString("providers", "[]") ?: "[]"
            val deepResearchJson = prefs.getString("deep_research_settings", "{}") ?: "{}"
            val providerGridColumnCount = prefs.getInt("provider_grid_column_count", 1)
            val regexRulesJson = prefs.getString("regex_rules", "[]") ?: "[]"

            val entity = AppSettingsEntity(
                id = 1,
                currentProviderId = currentProviderId,
                currentModel = currentModel,
                currentAssistantId = currentAssistantId,
                providersJson = providersJson,
                deepResearchSettingsJson = deepResearchJson,
                providerGridColumnCount = providerGridColumnCount,
                regexRulesJson = regexRulesJson
            )

            settingsDao.insertOrReplace(entity)

            // 标记迁移完成
            prefs.edit().putBoolean(migrationKey, true).apply()
        } catch (e: Exception) {
            // Ignore malformed legacy settings and fall back to defaults.
        }
    }

    /**
     * 将数据库实体转换为 AppSettings
     */
    private fun entityToAppSettings(entity: AppSettingsEntity): AppSettings {
        return AppSettings(
            currentProviderId = entity.currentProviderId,
            currentModel = entity.currentModel,
            currentAssistantId = entity.currentAssistantId,
            providers = parseProviders(entity.providersJson),
            deepResearchSettings = parseDeepResearchSettings(entity.deepResearchSettingsJson),
            providerGridColumnCount = entity.providerGridColumnCount,
            regexRules = parseRegexRules(entity.regexRulesJson),
            tokenRecordingStatus = try {
                TokenRecordingStatus.valueOf(entity.tokenRecordingStatus)
            } catch (e: Exception) {
                TokenRecordingStatus.ENABLED
            },
            ttsSettings = parseTtsSettings(entity.ttsSettingsJson),
            r2Settings = parseR2Settings(entity.r2SettingsJson),
            language = entity.language,
            ocrSettings = parseOcrSettings(entity.ocrSettingsJson, entity.ocrModel),
            chatToolbarSettings = parseChatToolbarSettings(entity.chatToolbarSettingsJson)
        )
    }

    /**
     * 将 AppSettings 转换为数据库实体
     */
    private fun appSettingsToEntity(settings: AppSettings): AppSettingsEntity {
        return AppSettingsEntity(
            id = 1,
            currentProviderId = settings.currentProviderId,
            currentModel = settings.currentModel,
            currentAssistantId = settings.currentAssistantId,
            providersJson = serializeProviders(settings.providers),
            deepResearchSettingsJson = serializeDeepResearchSettings(settings.deepResearchSettings),
            providerGridColumnCount = settings.providerGridColumnCount,
            regexRulesJson = serializeRegexRules(settings.regexRules),
            tokenRecordingStatus = settings.tokenRecordingStatus.name,
            ttsSettingsJson = serializeTtsSettings(settings.ttsSettings),
            r2SettingsJson = serializeR2Settings(settings.r2Settings),
            language = settings.language,
            ocrModel = settings.ocrSettings.model,
            ocrSettingsJson = serializeOcrSettings(settings.ocrSettings),
            chatToolbarSettingsJson = serializeChatToolbarSettings(settings.chatToolbarSettings)
        )
    }

    private fun parseDeepResearchSettings(json: String): DeepResearchSettings {
        return try {
            val obj = JSONObject(json)
            DeepResearchSettings(
                aiProviderType = obj.optString("aiProviderType", "openai"),
                aiApiKey = obj.optString("aiApiKey", ""),
                aiApiBase = obj.optString("aiApiBase", ""),
                aiModel = obj.optString("aiModel", ""),
                webSearchProvider = try {
                    WebSearchProvider.valueOf(obj.optString("webSearchProvider", "TAVILY"))
                } catch (e: Exception) {
                    WebSearchProvider.TAVILY
                },
                webSearchApiKey = obj.optString("webSearchApiKey", ""),
                webSearchApiBase = obj.optString("webSearchApiBase", "").takeIf { it.isNotEmpty() },
                tavilyAdvancedSearch = obj.optBoolean("tavilyAdvancedSearch", false),
                tavilySearchTopic = obj.optString("tavilySearchTopic", "general"),
                breadth = obj.optInt("breadth", 3),
                maxDepth = obj.optInt("maxDepth", 2),
                language = obj.optString("language", "zh"),
                searchLanguage = obj.optString("searchLanguage", "en"),
                maxSearchResults = obj.optInt("maxSearchResults", 5),
                concurrencyLimit = obj.optInt("concurrencyLimit", 2)
            )
        } catch (e: Exception) {
            DeepResearchSettings()
        }
    }

    private fun serializeDeepResearchSettings(settings: DeepResearchSettings): String {
        val obj = JSONObject()
        obj.put("aiProviderType", settings.aiProviderType)
        obj.put("aiApiKey", settings.aiApiKey)
        obj.put("aiApiBase", settings.aiApiBase)
        obj.put("aiModel", settings.aiModel)
        obj.put("webSearchProvider", settings.webSearchProvider.name)
        obj.put("webSearchApiKey", settings.webSearchApiKey)
        obj.put("webSearchApiBase", settings.webSearchApiBase ?: "")
        obj.put("tavilyAdvancedSearch", settings.tavilyAdvancedSearch)
        obj.put("tavilySearchTopic", settings.tavilySearchTopic)
        obj.put("breadth", settings.breadth)
        obj.put("maxDepth", settings.maxDepth)
        obj.put("language", settings.language)
        obj.put("searchLanguage", settings.searchLanguage)
        obj.put("maxSearchResults", settings.maxSearchResults)
        obj.put("concurrencyLimit", settings.concurrencyLimit)
        return obj.toString()
    }

    private fun parseProviders(json: String): List<ProviderConfig> {
        val providers = mutableListOf<ProviderConfig>()
        try {
            val jsonArray = JSONArray(json)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                providers.add(
                    ProviderConfig(
                        id = obj.optString("id", ""),
                        name = obj.optString("name", ""),
                        providerType = try {
                            AIProviderType.valueOf(obj.optString("providerType", "OPENAI"))
                        } catch (e: Exception) {
                            AIProviderType.OPENAI
                        },
                        serviceMode = try {
                            ServiceMode.valueOf(obj.optString("serviceMode", "CUSTOM"))
                        } catch (e: Exception) {
                            ServiceMode.CUSTOM
                        },
                        billingMode = try {
                            ProviderBillingMode.valueOf(obj.optString("billingMode", "USER_API_KEY"))
                        } catch (e: Exception) {
                            ProviderBillingMode.USER_API_KEY
                        },
                        authType = try {
                            ProviderAuthType.valueOf(obj.optString("authType", "BEARER"))
                        } catch (e: Exception) {
                            ProviderAuthType.BEARER
                        },
                        apiKey = obj.optString("apiKey", ""),
                        endpoint = obj.optString("endpoint", ""),
                        apiPath = obj.optString("apiPath", ""),
                        modelsPath = obj.optString("modelsPath", ""),
                        imagesPath = obj.optString("imagesPath", ""),
                        embeddingsPath = obj.optString("embeddingsPath", ""),
                        modelCatalogPath = obj.optString("modelCatalogPath", ""),
                        authHeaderName = obj.optString("authHeaderName", "Authorization"),
                        authHeaderPrefix = obj.optString("authHeaderPrefix", "Bearer "),
                        useProxy = obj.optBoolean("useProxy", false),
                        selectedModel = obj.optString("selectedModel", ""),
                        availableModels = parseStringList(obj.optJSONArray("availableModels")),
                        modelCapabilities = parseModelCapabilities(obj.optJSONObject("modelCapabilities")),
                        modelCustomParams = parseModelCustomParams(obj.optJSONObject("modelCustomParams")),
                        customHeaders = parseStringMap(obj.optJSONObject("customHeaders")),
                        // 多 Key 支持
                        apiKeys = parseApiKeys(obj.optJSONArray("apiKeys")),
                        multiKeyEnabled = obj.optBoolean("multiKeyEnabled", false),
                        keySelectionStrategy = try {
                            KeySelectionStrategy.valueOf(obj.optString("keySelectionStrategy", "ROUND_ROBIN"))
                        } catch (e: Exception) {
                            KeySelectionStrategy.ROUND_ROBIN
                        },
                        roundRobinIndex = obj.optInt("roundRobinIndex", 0),
                        maxFailuresBeforeDisable = obj.optInt("maxFailuresBeforeDisable", 3),
                        autoRecoveryMinutes = obj.optInt("autoRecoveryMinutes", 5)
                    )
                )
            }
        } catch (e: Exception) {
            // Ignore malformed provider payloads and keep best-effort results.
        }
        return providers
    }

    private fun parseApiKeys(jsonArray: JSONArray?): List<ApiKeyEntry> {
        if (jsonArray == null) return emptyList()
        val keys = mutableListOf<ApiKeyEntry>()
        try {
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                keys.add(
                    ApiKeyEntry(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        key = obj.optString("key", ""),
                        name = obj.optString("name", ""),
                        isEnabled = obj.optBoolean("isEnabled", true),
                        priority = obj.optInt("priority", 5),
                        requestCount = obj.optInt("requestCount", 0),
                        successCount = obj.optInt("successCount", 0),
                        failureCount = obj.optInt("failureCount", 0),
                        lastUsedAt = obj.optLong("lastUsedAt", 0),
                        lastError = obj.optString("lastError", "").takeIf { it.isNotEmpty() },
                        status = try {
                            ApiKeyStatus.valueOf(obj.optString("status", "ACTIVE"))
                        } catch (e: Exception) {
                            ApiKeyStatus.ACTIVE
                        },
                        statusChangedAt = obj.optLong("statusChangedAt", 0)
                    )
                )
            }
        } catch (e: Exception) {
            // Ignore malformed API key payloads and keep best-effort results.
        }
        return keys
    }

    private fun parseModelCustomParams(jsonObj: JSONObject?): Map<String, ModelCustomParams> {
        if (jsonObj == null) return emptyMap()
        val result = mutableMapOf<String, ModelCustomParams>()
        try {
            val keys = jsonObj.keys()
            while (keys.hasNext()) {
                val modelName = keys.next()
                val paramsObj = jsonObj.optJSONObject(modelName) ?: continue
                result[modelName] = ModelCustomParams(
                    modelName = modelName,
                    temperature = if (paramsObj.has("temperature")) paramsObj.optDouble("temperature").toFloat() else null,
                    topP = if (paramsObj.has("topP")) paramsObj.optDouble("topP").toFloat() else null,
                    topK = if (paramsObj.has("topK")) paramsObj.optInt("topK") else null,
                    presencePenalty = if (paramsObj.has("presencePenalty")) paramsObj.optDouble("presencePenalty").toFloat() else null,
                    frequencyPenalty = if (paramsObj.has("frequencyPenalty")) paramsObj.optDouble("frequencyPenalty").toFloat() else null,
                    repetitionPenalty = if (paramsObj.has("repetitionPenalty")) paramsObj.optDouble("repetitionPenalty").toFloat() else null,
                    maxTokens = if (paramsObj.has("maxTokens")) paramsObj.optInt("maxTokens") else null,
                    extraParams = paramsObj.optString("extraParams", "{}")
                )
            }
        } catch (e: Exception) {
            // Ignore malformed model parameter payloads and keep best-effort results.
        }
        return result
    }

    private fun parseStringList(jsonArray: JSONArray?): List<String> {
        if (jsonArray == null) return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until jsonArray.length()) {
            list.add(jsonArray.optString(i, ""))
        }
        return list.filter { it.isNotEmpty() }
    }

    private fun parseStringMap(jsonObj: JSONObject?): Map<String, String> {
        if (jsonObj == null) return emptyMap()
        val result = linkedMapOf<String, String>()
        try {
            val keys = jsonObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = jsonObj.optString(key, "")
                if (key.isNotBlank() && value.isNotBlank()) {
                    result[key] = value
                }
            }
        } catch (e: Exception) {
            // Ignore malformed custom header payloads.
        }
        return result
    }

    private fun parseModelCapabilities(jsonObj: JSONObject?): Map<String, ModelCapabilityConfig> {
        if (jsonObj == null) return emptyMap()
        val result = linkedMapOf<String, ModelCapabilityConfig>()
        try {
            val keys = jsonObj.keys()
            while (keys.hasNext()) {
                val modelName = keys.next()
                val capabilityObj = jsonObj.optJSONObject(modelName) ?: continue
                result[modelName] = ModelCapabilityConfig(
                    modelName = modelName,
                    displayName = capabilityObj.optString("displayName", ""),
                    vendor = capabilityObj.optString("vendor", ""),
                    category = capabilityObj.optString("category", ""),
                    supportsVision = capabilityObj.optBoolean("supportsVision", false),
                    supportsTools = capabilityObj.optBoolean("supportsTools", false),
                    supportsResponses = capabilityObj.optBoolean("supportsResponses", false),
                    supportsImageGeneration = capabilityObj.optBoolean("supportsImageGeneration", false),
                    supportsEmbedding = capabilityObj.optBoolean("supportsEmbedding", false),
                    speed = capabilityObj.optString("speed", ""),
                    quality = capabilityObj.optString("quality", ""),
                    costLevel = capabilityObj.optString("costLevel", ""),
                    recommended = capabilityObj.optBoolean("recommended", false)
                )
            }
        } catch (e: Exception) {
            // Ignore malformed model capability payloads.
        }
        return result
    }

    private fun serializeProviders(providers: List<ProviderConfig>): String {
        val jsonArray = JSONArray()
        providers.forEach { provider ->
            val obj = JSONObject()
            obj.put("id", provider.id)
            obj.put("name", provider.name)
            obj.put("providerType", provider.providerType.name)
            obj.put("serviceMode", provider.serviceMode.name)
            obj.put("billingMode", provider.billingMode.name)
            obj.put("authType", provider.authType.name)
            obj.put("apiKey", provider.apiKey)
            obj.put("endpoint", provider.endpoint)
            obj.put("apiPath", provider.apiPath)
            obj.put("modelsPath", provider.modelsPath)
            obj.put("imagesPath", provider.imagesPath)
            obj.put("embeddingsPath", provider.embeddingsPath)
            obj.put("modelCatalogPath", provider.modelCatalogPath)
            obj.put("authHeaderName", provider.authHeaderName)
            obj.put("authHeaderPrefix", provider.authHeaderPrefix)
            obj.put("useProxy", provider.useProxy)
            obj.put("selectedModel", provider.selectedModel)
            obj.put("availableModels", JSONArray(provider.availableModels))
            obj.put("modelCapabilities", serializeModelCapabilities(provider.modelCapabilities))
            obj.put("modelCustomParams", serializeModelCustomParams(provider.modelCustomParams))
            obj.put("customHeaders", serializeStringMap(provider.customHeaders))
            // 多 Key 管理字段
            obj.put("apiKeys", serializeApiKeys(provider.apiKeys))
            obj.put("multiKeyEnabled", provider.multiKeyEnabled)
            obj.put("keySelectionStrategy", provider.keySelectionStrategy.name)
            obj.put("roundRobinIndex", provider.roundRobinIndex)
            obj.put("maxFailuresBeforeDisable", provider.maxFailuresBeforeDisable)
            obj.put("autoRecoveryMinutes", provider.autoRecoveryMinutes)
            jsonArray.put(obj)
        }
        return jsonArray.toString()
    }

    private fun serializeApiKeys(apiKeys: List<ApiKeyEntry>): JSONArray {
        val jsonArray = JSONArray()
        apiKeys.forEach { key ->
            val obj = JSONObject()
            obj.put("id", key.id)
            obj.put("key", key.key)
            obj.put("name", key.name)
            obj.put("isEnabled", key.isEnabled)
            obj.put("priority", key.priority)
            obj.put("requestCount", key.requestCount)
            obj.put("successCount", key.successCount)
            obj.put("failureCount", key.failureCount)
            obj.put("lastUsedAt", key.lastUsedAt)
            key.lastError?.let { obj.put("lastError", it) }
            obj.put("status", key.status.name)
            obj.put("statusChangedAt", key.statusChangedAt)
            jsonArray.put(obj)
        }
        return jsonArray
    }

    private fun serializeModelCustomParams(params: Map<String, ModelCustomParams>): JSONObject {
        val result = JSONObject()
        params.forEach { (modelName, customParams) ->
            val paramsObj = JSONObject()
            customParams.temperature?.let { paramsObj.put("temperature", it.toDouble()) }
            customParams.topP?.let { paramsObj.put("topP", it.toDouble()) }
            customParams.topK?.let { paramsObj.put("topK", it) }
            customParams.presencePenalty?.let { paramsObj.put("presencePenalty", it.toDouble()) }
            customParams.frequencyPenalty?.let { paramsObj.put("frequencyPenalty", it.toDouble()) }
            customParams.repetitionPenalty?.let { paramsObj.put("repetitionPenalty", it.toDouble()) }
            customParams.maxTokens?.let { paramsObj.put("maxTokens", it) }
            if (customParams.extraParams.isNotEmpty() && customParams.extraParams != "{}") {
                paramsObj.put("extraParams", customParams.extraParams)
            }
            result.put(modelName, paramsObj)
        }
        return result
    }

    private fun serializeModelCapabilities(capabilities: Map<String, ModelCapabilityConfig>): JSONObject {
        val result = JSONObject()
        capabilities.forEach { (modelName, capability) ->
            val obj = JSONObject()
            obj.put("displayName", capability.displayName)
            obj.put("vendor", capability.vendor)
            obj.put("category", capability.category)
            obj.put("supportsVision", capability.supportsVision)
            obj.put("supportsTools", capability.supportsTools)
            obj.put("supportsResponses", capability.supportsResponses)
            obj.put("supportsImageGeneration", capability.supportsImageGeneration)
            obj.put("supportsEmbedding", capability.supportsEmbedding)
            obj.put("speed", capability.speed)
            obj.put("quality", capability.quality)
            obj.put("costLevel", capability.costLevel)
            obj.put("recommended", capability.recommended)
            result.put(modelName, obj)
        }
        return result
    }

    private fun serializeStringMap(values: Map<String, String>): JSONObject {
        val obj = JSONObject()
        values.forEach { (key, value) ->
            if (key.isNotBlank() && value.isNotBlank()) {
                obj.put(key, value)
            }
        }
        return obj
    }

    private fun parseRegexRules(json: String): List<RegexRule> {
        val rules = mutableListOf<RegexRule>()
        try {
            val jsonArray = JSONArray(json)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                rules.add(
                    RegexRule(
                        id = obj.optString("id", ""),
                        name = obj.optString("name", ""),
                        pattern = obj.optString("pattern", ""),
                        replacement = obj.optString("replacement", ""),
                        isEnabled = obj.optBoolean("isEnabled", true),
                        description = obj.optString("description", ""),
                        order = obj.optInt("order", 0)
                    )
                )
            }
        } catch (e: Exception) {
            // Ignore malformed regex payloads and keep best-effort results.
        }
        return rules.sortedBy { it.order }
    }

    private fun serializeRegexRules(rules: List<RegexRule>): String {
        val jsonArray = JSONArray()
        rules.forEach { rule ->
            val obj = JSONObject()
            obj.put("id", rule.id)
            obj.put("name", rule.name)
            obj.put("pattern", rule.pattern)
            obj.put("replacement", rule.replacement)
            obj.put("isEnabled", rule.isEnabled)
            obj.put("description", rule.description)
            obj.put("order", rule.order)
            jsonArray.put(obj)
        }
        return jsonArray.toString()
    }

    private fun parseTtsSettings(json: String): TtsSettings {
        return try {
            val obj = JSONObject(json)
            TtsSettings(
                enabled = obj.optBoolean("enabled", false),
                autoSpeak = obj.optBoolean("autoSpeak", false),
                speechRate = obj.optDouble("speechRate", 1.0).toFloat(),
                pitch = obj.optDouble("pitch", 1.0).toFloat(),
                language = obj.optString("language", "zh-CN"),
                enginePackage = obj.optString("enginePackage", "")
            )
        } catch (e: Exception) {
            TtsSettings()
        }
    }

    private fun serializeTtsSettings(settings: TtsSettings): String {
        val obj = JSONObject()
        obj.put("enabled", settings.enabled)
        obj.put("autoSpeak", settings.autoSpeak)
        obj.put("speechRate", settings.speechRate.toDouble())
        obj.put("pitch", settings.pitch.toDouble())
        obj.put("language", settings.language)
        obj.put("enginePackage", settings.enginePackage)
        return obj.toString()
    }

    private fun parseR2Settings(json: String): R2Settings {
        return try {
            val obj = JSONObject(json)
            R2Settings(
                enabled = obj.optBoolean("enabled", false),
                accountId = obj.optString("accountId", ""),
                accessKeyId = obj.optString("accessKeyId", ""),
                secretAccessKey = obj.optString("secretAccessKey", ""),
                bucketName = obj.optString("bucketName", ""),
                customEndpoint = obj.optString("customEndpoint", "")
            )
        } catch (e: Exception) {
            R2Settings()
        }
    }

    private fun serializeR2Settings(settings: R2Settings): String {
        val obj = JSONObject()
        obj.put("enabled", settings.enabled)
        obj.put("accountId", settings.accountId)
        obj.put("accessKeyId", settings.accessKeyId)
        obj.put("secretAccessKey", settings.secretAccessKey)
        obj.put("bucketName", settings.bucketName)
        obj.put("customEndpoint", settings.customEndpoint)
        return obj.toString()
    }

    private fun parseOcrSettings(json: String, legacyOcrModel: String): OcrSettings {
        return try {
            val obj = JSONObject(json)
            // 如果 JSON 为空或没有 model 字段，使用旧的 ocrModel 字段进行兼容
            val model = obj.optString("model", "").ifEmpty { legacyOcrModel }
            OcrSettings(
                model = model.ifEmpty { OcrModel.MLKIT_LATIN.name },
                aiProviderId = obj.optString("aiProviderId", ""),
                aiModel = obj.optString("aiModel", ""),
                customPrompt = obj.optString("customPrompt", OcrSettings.DEFAULT_OCR_PROMPT)
            )
        } catch (e: Exception) {
            // 如果解析失败，使用旧的 ocrModel 字段
            OcrSettings(model = legacyOcrModel.ifEmpty { OcrModel.MLKIT_LATIN.name })
        }
    }

    private fun serializeOcrSettings(settings: OcrSettings): String {
        val obj = JSONObject()
        obj.put("model", settings.model)
        obj.put("aiProviderId", settings.aiProviderId)
        obj.put("aiModel", settings.aiModel)
        obj.put("customPrompt", settings.customPrompt)
        return obj.toString()
    }

    private fun parseChatToolbarSettings(json: String): ChatToolbarSettings {
        return try {
            val jsonArray = JSONArray(json)
            val items = mutableListOf<ChatToolbarItemConfig>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                val item = runCatching {
                    ChatToolbarItem.valueOf(obj.optString("id"))
                }.getOrNull() ?: continue
                items.add(
                    ChatToolbarItemConfig(
                        item = item,
                        visible = obj.optBoolean("visible", true)
                    )
                )
            }
            ChatToolbarSettings(items).normalized()
        } catch (e: Exception) {
            ChatToolbarSettings()
        }
    }

    private fun serializeChatToolbarSettings(settings: ChatToolbarSettings): String {
        val jsonArray = JSONArray()
        settings.normalized().items.forEach { config ->
            val obj = JSONObject()
            obj.put("id", config.item.name)
            obj.put("visible", config.visible)
            jsonArray.put(obj)
        }
        return jsonArray.toString()
    }

    fun updateSettings(settings: AppSettings) {
        synchronizeRuntimeProviders(settings.providers)
        _settings.value = settings
        scope.launch {
            settingsWriteMutex.withLock {
                settingsDao.insertOrReplace(appSettingsToEntity(settings))
            }
        }
    }

    fun updateProviderRuntime(
        provider: ProviderConfig,
        reflectInUi: Boolean = false,
        debounceMs: Long = 0L
    ) {
        runtimeProviders[provider.id] = provider
        roundRobinIndices[provider.id]?.set(provider.roundRobinIndex)

        if (reflectInUi) {
            val currentSettings = _settings.value
            if (currentSettings.providers.any { it.id == provider.id }) {
                _settings.value = currentSettings.copy(
                    providers = currentSettings.providers.map {
                        if (it.id == provider.id) provider else it
                    }
                )
            }
        }

        providerPersistJobs[provider.id]?.cancel()
        providerPersistJobs[provider.id] = scope.launch {
            if (debounceMs > 0) {
                delay(debounceMs)
            }
            persistProvider(provider.id)
        }
    }

    private suspend fun persistProvider(providerId: String) {
        val provider = runtimeProviders[providerId] ?: return
        settingsWriteMutex.withLock {
            val currentSettings = _settings.value
            if (currentSettings.providers.none { it.id == providerId }) {
                return
            }
            val providers = currentSettings.providers.map {
                if (it.id == providerId) provider else it
            }
            settingsDao.updateProvidersJson(serializeProviders(providers))
        }
    }

    /**
     * 添加新的服务商配置
     */
    fun addProvider(provider: ProviderConfig) {
        val currentSettings = _settings.value
        val newProviders = currentSettings.providers + provider
        val newSettings = currentSettings.copy(
            providers = newProviders,
            currentProviderId = if (currentSettings.currentProviderId.isEmpty()) provider.id else currentSettings.currentProviderId
        )
        updateSettings(newSettings)
    }

    /**
     * 更新服务商配置
     */
    fun updateProvider(provider: ProviderConfig) {
        val currentSettings = _settings.value
        val newProviders = currentSettings.providers.map {
            if (it.id == provider.id) provider else it
        }
        updateSettings(currentSettings.copy(providers = newProviders))
    }

    /**
     * 删除服务商配置
     */
    fun deleteProvider(providerId: String) {
        val currentSettings = _settings.value
        val newProviders = currentSettings.providers.filter { it.id != providerId }
        val newCurrentId = if (currentSettings.currentProviderId == providerId) {
            newProviders.firstOrNull()?.id ?: ""
        } else {
            currentSettings.currentProviderId
        }
        updateSettings(currentSettings.copy(
            providers = newProviders,
            currentProviderId = newCurrentId
        ))
    }

    /**
     * 设置当前使用的服务商
     */
    fun setCurrentProvider(providerId: String) {
        val currentSettings = _settings.value
        if (currentSettings.providers.any { it.id == providerId }) {
            // 切换服务商时，重置模型选择为该服务商的默认模型
            val provider = currentSettings.providers.find { it.id == providerId }
            val defaultModel = provider?.selectedModel?.ifEmpty {
                provider.availableModels.firstOrNull() ?: ""
            } ?: ""
            updateSettings(currentSettings.copy(
                currentProviderId = providerId,
                currentModel = defaultModel
            ))
        }
    }

    /**
     * 设置当前使用的模型
     */
    fun setCurrentModel(model: String) {
        val currentSettings = _settings.value
        updateSettings(currentSettings.copy(currentModel = model))
    }

    /**
     * 设置当前使用的助手
     */
    fun setCurrentAssistant(assistantId: String) {
        val currentSettings = _settings.value
        updateSettings(currentSettings.copy(currentAssistantId = assistantId))
    }

    /**
     * 更新深度研究设置
     */
    fun updateDeepResearchSettings(settings: DeepResearchSettings) {
        val currentSettings = _settings.value
        updateSettings(currentSettings.copy(deepResearchSettings = settings))
    }

    /**
     * 更新服务商列表网格列数
     */
    fun updateProviderGridColumnCount(columnCount: Int) {
        val currentSettings = _settings.value
        val validColumnCount = columnCount.coerceIn(1, 3)
        updateSettings(currentSettings.copy(providerGridColumnCount = validColumnCount))
    }

    // ==================== 正则规则管理 ====================

    /**
     * 添加正则规则
     */
    fun addRegexRule(rule: RegexRule) {
        val currentSettings = _settings.value
        val newRules = currentSettings.regexRules + rule
        updateSettings(currentSettings.copy(regexRules = newRules))
    }

    /**
     * 更新正则规则
     */
    fun updateRegexRule(rule: RegexRule) {
        val currentSettings = _settings.value
        val newRules = currentSettings.regexRules.map {
            if (it.id == rule.id) rule else it
        }
        updateSettings(currentSettings.copy(regexRules = newRules))
    }

    /**
     * 删除正则规则
     */
    fun deleteRegexRule(ruleId: String) {
        val currentSettings = _settings.value
        val newRules = currentSettings.regexRules.filter { it.id != ruleId }
        updateSettings(currentSettings.copy(regexRules = newRules))
    }

    /**
     * 更新正则规则启用状态
     */
    fun setRegexRuleEnabled(ruleId: String, enabled: Boolean) {
        val currentSettings = _settings.value
        val newRules = currentSettings.regexRules.map {
            if (it.id == ruleId) it.copy(isEnabled = enabled) else it
        }
        updateSettings(currentSettings.copy(regexRules = newRules))
    }

    /**
     * 批量添加正则规则（用于添加预设规则）
     */
    fun addRegexRules(rules: List<RegexRule>) {
        val currentSettings = _settings.value
        val existingIds = currentSettings.regexRules.map { it.id }.toSet()
        val newRules = rules.filter { it.id !in existingIds }
        if (newRules.isNotEmpty()) {
            updateSettings(currentSettings.copy(regexRules = currentSettings.regexRules + newRules))
        }
    }

    // ==================== 模型自定义参数管理 ====================

    /**
     * 更新模型自定义参数
     */
    fun updateModelCustomParams(providerId: String, modelName: String, params: ModelCustomParams) {
        val currentSettings = _settings.value
        val newProviders = currentSettings.providers.map { provider ->
            if (provider.id == providerId) {
                val newParams = provider.modelCustomParams.toMutableMap()
                newParams[modelName] = params
                provider.copy(modelCustomParams = newParams)
            } else {
                provider
            }
        }
        updateSettings(currentSettings.copy(providers = newProviders))
    }

    /**
     * 删除模型自定义参数
     */
    fun deleteModelCustomParams(providerId: String, modelName: String) {
        val currentSettings = _settings.value
        val newProviders = currentSettings.providers.map { provider ->
            if (provider.id == providerId) {
                val newParams = provider.modelCustomParams.toMutableMap()
                newParams.remove(modelName)
                provider.copy(modelCustomParams = newParams)
            } else {
                provider
            }
        }
        updateSettings(currentSettings.copy(providers = newProviders))
    }

    // ==================== Token 记录状态管理 ====================

    /**
     * 更新 Token 记录状态
     */
    fun updateTokenRecordingStatus(status: TokenRecordingStatus) {
        val currentSettings = _settings.value
        updateSettings(currentSettings.copy(tokenRecordingStatus = status))
    }

    /**
     * 检查是否应该记录 Token
     */
    fun shouldRecordTokens(): Boolean {
        return _settings.value.tokenRecordingStatus == TokenRecordingStatus.ENABLED
    }

    // ==================== 原子轮询索引管理 ====================

    /**
     * 原子地获取并递增轮询索引（解决多线程竞态问题）
     *
     * @param providerId 服务商 ID
     * @param availableKeyCount 当前可用的 Key 数量
     * @return 当前应该使用的索引值
     */
    fun getAndIncrementRoundRobinIndex(providerId: String, availableKeyCount: Int): Int {
        if (availableKeyCount <= 0) return 0

        val atomicIndex = roundRobinIndices.getOrPut(providerId) {
            // 从持久化设置中初始化
            val provider = getProviderSnapshot(providerId)
            AtomicInteger(provider?.roundRobinIndex ?: 0)
        }

        // 原子操作：CAS 循环获取当前值并递增
        while (true) {
            val current = atomicIndex.get()
            val actualIndex = current % availableKeyCount
            val next = (current + 1) % availableKeyCount
            if (atomicIndex.compareAndSet(current, next)) {
                getProviderSnapshot(providerId)?.let { provider ->
                    updateProviderRuntime(
                        provider = provider.copy(roundRobinIndex = next),
                        reflectInUi = false,
                        debounceMs = 2_000L
                    )
                }
                return actualIndex
            }
            // CAS 失败说明被其他线程修改了，重试
        }
    }

    /**
     * 重置轮询索引（当 Key 列表变化时调用）
     */
    fun resetRoundRobinIndex(providerId: String) {
        roundRobinIndices[providerId]?.set(0)
        getProviderSnapshot(providerId)?.let { provider ->
            updateProviderRuntime(
                provider = provider.copy(roundRobinIndex = 0),
                reflectInUi = false,
                debounceMs = 500L
            )
        }
    }

    // ==================== R2 云备份设置管理 ====================

    /**
     * 更新 R2 云备份设置
     */
    fun updateR2Settings(r2Settings: R2Settings) {
        val currentSettings = _settings.value
        updateSettings(currentSettings.copy(r2Settings = r2Settings))
    }

    // ==================== 语言设置管理 ====================

    /**
     * 设置应用显示语言
     */
    fun setLanguage(languageCode: String) {
        val currentSettings = _settings.value
        updateSettings(currentSettings.copy(language = languageCode))
    }

    // ==================== OCR 设置管理 ====================

    /**
     * 更新 OCR 设置
     */
    fun updateOcrSettings(ocrSettings: OcrSettings) {
        val currentSettings = _settings.value
        updateSettings(currentSettings.copy(ocrSettings = ocrSettings))
    }

    // ==================== 聊天工具栏显示设置 ====================

    fun updateChatToolbarSettings(chatToolbarSettings: ChatToolbarSettings) {
        val currentSettings = _settings.value
        updateSettings(currentSettings.copy(chatToolbarSettings = chatToolbarSettings.normalized()))
    }
}
