package com.tchat.wanxiaot.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSettings {
        return try {
            val providerName = prefs.getString("provider", AIProvider.OPENAI.name) ?: AIProvider.OPENAI.name
            val provider = try {
                AIProvider.valueOf(providerName)
            } catch (e: Exception) {
                AIProvider.OPENAI  // 如果读取失败，使用默认值
            }

            // 读取模型列表（逗号分隔的字符串）
            val modelsString = prefs.getString("available_models", "") ?: ""
            val availableModels = if (modelsString.isNotBlank()) {
                modelsString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            } else {
                provider.defaultModels  // 使用默认模型
            }

            val selectedModel = prefs.getString("selected_model", "") ?: ""

            AppSettings(
                provider = provider,
                apiKey = prefs.getString("api_key", "") ?: "",
                endpoint = prefs.getString("endpoint", provider.defaultEndpoint) ?: provider.defaultEndpoint,
                selectedModel = selectedModel.ifEmpty { availableModels.firstOrNull() ?: "" },
                availableModels = availableModels
            )
        } catch (e: Exception) {
            // 如果出现任何错误，返回默认设置
            AppSettings()
        }
    }

    fun updateSettings(settings: AppSettings) {
        prefs.edit().apply {
            putString("provider", settings.provider.name)
            putString("api_key", settings.apiKey)
            putString("endpoint", settings.endpoint)
            putString("selected_model", settings.selectedModel)
            // 将模型列表序列化为逗号分隔的字符串
            putString("available_models", settings.availableModels.joinToString(","))
            apply()
        }
        _settings.value = settings
    }
}
