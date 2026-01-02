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

            val providers = parseProviders(providersJson)
            val deepResearchSettings = parseDeepResearchSettings(deepResearchJson)

            AppSettings(
                currentProviderId = currentProviderId,
                currentModel = currentModel,
                currentAssistantId = currentAssistantId,
                providers = providers,
                deepResearchSettings = deepResearchSettings
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
                        availableModels = parseStringList(obj.optJSONArray("availableModels"))
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return providers
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
}
