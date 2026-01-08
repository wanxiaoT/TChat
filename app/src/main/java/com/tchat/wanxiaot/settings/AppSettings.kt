package com.tchat.wanxiaot.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.Bot
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Sparkles
import com.tchat.data.deepresearch.service.WebSearchProvider
import java.util.UUID

/**
 * Token 记录状态
 */
enum class TokenRecordingStatus {
    ENABLED,   // 启用记录
    PAUSED,    // 暂停记录
    DISABLED   // 关闭记录
}

/**
 * 模型自定义参数配置
 * 用于配置 AI 请求体中的额外参数
 */
data class ModelCustomParams(
    val modelName: String = "",
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
    val presencePenalty: Float? = null,
    val frequencyPenalty: Float? = null,
    val repetitionPenalty: Float? = null,
    val maxTokens: Int? = null,
    val extraParams: String = "{}"  // 自定义 JSON 参数，直接合并到请求体
) {
    /** 检查是否有任何参数被设置 */
    fun hasAnyValue(): Boolean {
        return temperature != null ||
                topP != null ||
                topK != null ||
                presencePenalty != null ||
                frequencyPenalty != null ||
                repetitionPenalty != null ||
                maxTokens != null ||
                (extraParams.isNotBlank() && extraParams != "{}")
    }
}

/**
 * 正则表达式规则
 * 用于清理 AI 输出内容
 */
data class RegexRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val pattern: String = "",
    val replacement: String = "",
    val isEnabled: Boolean = true,
    val description: String = "",
    val order: Int = 0
)

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
    val availableModels: List<String> = emptyList(),
    val modelCustomParams: Map<String, ModelCustomParams> = emptyMap()  // 模型名 -> 自定义参数
)

/**
 * 深度研究设置
 */
data class DeepResearchSettings(
    // AI 设置（可选，为空时使用默认服务商）
    val aiProviderType: String = "openai",
    val aiApiKey: String = "",
    val aiApiBase: String = "",
    val aiModel: String = "",

    // 搜索设置
    val webSearchProvider: WebSearchProvider = WebSearchProvider.TAVILY,
    val webSearchApiKey: String = "",
    val webSearchApiBase: String? = null,
    val tavilyAdvancedSearch: Boolean = false,
    val tavilySearchTopic: String = "general",

    // 研究设置
    val breadth: Int = 3,
    val maxDepth: Int = 2,
    val language: String = "zh",
    val searchLanguage: String = "en",
    val maxSearchResults: Int = 5,
    val concurrencyLimit: Int = 2
)

/**
 * 应用设置
 */
data class AppSettings(
    val currentProviderId: String = "",  // 当前使用的服务商 ID
    val currentModel: String = "",  // 当前使用的模型（可在聊天页面切换）
    val currentAssistantId: String = "",  // 当前使用的助手 ID
    val providers: List<ProviderConfig> = emptyList(),
    val deepResearchSettings: DeepResearchSettings = DeepResearchSettings(),
    val providerGridColumnCount: Int = 1,  // 服务商列表网格列数（1-3）
    val regexRules: List<RegexRule> = emptyList(),  // 全局正则表达式规则
    val tokenRecordingStatus: TokenRecordingStatus = TokenRecordingStatus.ENABLED  // Token 记录状态
) {
    // 兼容旧代码
    val defaultProviderId: String get() = currentProviderId

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
