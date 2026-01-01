package com.tchat.wanxiaot.settings

import android.content.Context
import android.content.SharedPreferences
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

            val providers = parseProviders(providersJson)

            AppSettings(
                currentProviderId = currentProviderId,
                currentModel = currentModel,
                currentAssistantId = currentAssistantId,
                providers = providers
            )
        } catch (e: Exception) {
            e.printStackTrace()
            AppSettings()
        }
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
}
