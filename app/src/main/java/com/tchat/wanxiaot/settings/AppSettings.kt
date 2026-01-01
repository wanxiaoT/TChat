package com.tchat.wanxiaot.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.Bot
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Sparkles
import java.util.UUID

/**
 * 服务商配置条目
 */
data class ProviderConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",  // 用户自定义名称，如 "我的 OpenAI"
    val providerType: AIProviderType = AIProviderType.OPENAI,
    val apiKey: String = "",
    val endpoint: String = "",
    val selectedModel: String = "",
    val availableModels: List<String> = emptyList()
)

/**
 * 应用设置
 */
data class AppSettings(
    val currentProviderId: String = "",  // 当前使用的服务商 ID
    val currentModel: String = "",  // 当前使用的模型（可在聊天页面切换）
    val currentAssistantId: String = "",  // 当前使用的助手 ID
    val providers: List<ProviderConfig> = emptyList()
) {
    /**
     * 获取当前使用的服务商配置
     */
    fun getCurrentProvider(): ProviderConfig? {
        return providers.find { it.id == currentProviderId }
    }

    /**
     * 获取当前使用的模型（优先使用 currentModel，否则使用服务商的默认模型）
     */
    fun getActiveModel(): String {
        if (currentModel.isNotBlank()) return currentModel
        return getCurrentProvider()?.let { provider ->
            provider.selectedModel.ifEmpty {
                provider.availableModels.firstOrNull() ?: ""
            }
        } ?: ""
    }
}

/**
 * 服务商类型（API 格式）
 */
enum class AIProviderType(
    val displayName: String,
    val defaultEndpoint: String,
    val defaultModels: List<String>,
    val icon: @Composable () -> ImageVector
) {
    OPENAI(
        "OpenAI",
        "https://api.openai.com/v1",
        listOf("gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-3.5-turbo"),
        icon = { Lucide.Sparkles }
    ),
    ANTHROPIC(
        "Anthropic (Claude)",
        "https://api.anthropic.com/v1",
        listOf("claude-3-5-sonnet-20241022", "claude-3-5-haiku-20241022", "claude-3-opus-20240229"),
        icon = { Lucide.Bot }
    ),
    GEMINI(
        "Google Gemini",
        "https://generativelanguage.googleapis.com/v1",
        listOf("gemini-2.0-flash-exp", "gemini-1.5-pro", "gemini-1.5-flash"),
        icon = { Lucide.Sparkles }
    )
}

// 兼容旧版本的别名
typealias AIProvider = AIProviderType
