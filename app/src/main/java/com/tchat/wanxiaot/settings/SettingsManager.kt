package com.tchat.wanxiaot.settings

import android.content.Context
import android.content.SharedPreferences
import com.tchat.data.deepresearch.service.WebSearchProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSettings {
        return try {
            val currentProviderId = prefs.getString("current_provider_id", "") ?: ""
            val currentModel = prefs.getString("current_model", "") ?: ""
            val currentAssistantId = prefs.getString("current_assistant_id", "") ?: ""
            val providersJson = prefs.getString("providers", "[]") ?: "[]"
            val deepResearchJson = prefs.getString("deep_research_settings", "{}") ?: "{}"
            val providerGridColumnCount = prefs.getInt("provider_grid_column_count", 1)
            val regexRulesJson = prefs.getString("regex_rules", "[]") ?: "[]"

            val providers = parseProviders(providersJson)
            val deepResearchSettings = parseDeepResearchSettings(deepResearchJson)
            val regexRules = parseRegexRules(regexRulesJson)

            AppSettings(
                currentProviderId = currentProviderId,
                currentModel = currentModel,
                currentAssistantId = currentAssistantId,
                providers = providers,
                deepResearchSettings = deepResearchSettings,
                providerGridColumnCount = providerGridColumnCount,
                regexRules = regexRules
            )
        } catch (e: Exception) {
            e.printStackTrace()
            AppSettings()
        }
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
                        apiKey = obj.optString("apiKey", ""),
                        endpoint = obj.optString("endpoint", ""),
                        selectedModel = obj.optString("selectedModel", ""),
                        availableModels = parseStringList(obj.optJSONArray("availableModels")),
                        modelCustomParams = parseModelCustomParams(obj.optJSONObject("modelCustomParams"))
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return providers
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
            e.printStackTrace()
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

    private fun serializeProviders(providers: List<ProviderConfig>): String {
        val jsonArray = JSONArray()
        providers.forEach { provider ->
            val obj = JSONObject()
            obj.put("id", provider.id)
            obj.put("name", provider.name)
            obj.put("providerType", provider.providerType.name)
            obj.put("apiKey", provider.apiKey)
            obj.put("endpoint", provider.endpoint)
            obj.put("selectedModel", provider.selectedModel)
            obj.put("availableModels", JSONArray(provider.availableModels))
            obj.put("modelCustomParams", serializeModelCustomParams(provider.modelCustomParams))
            jsonArray.put(obj)
        }
        return jsonArray.toString()
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
            e.printStackTrace()
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

    fun updateSettings(settings: AppSettings) {
        prefs.edit().apply {
            putString("current_provider_id", settings.currentProviderId)
            putString("current_model", settings.currentModel)
            putString("current_assistant_id", settings.currentAssistantId)
            putString("providers", serializeProviders(settings.providers))
            putString("deep_research_settings", serializeDeepResearchSettings(settings.deepResearchSettings))
            putString("regex_rules", serializeRegexRules(settings.regexRules))
            apply()
        }
        _settings.value = settings
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
        prefs.edit().putInt("provider_grid_column_count", validColumnCount).apply()
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
}
