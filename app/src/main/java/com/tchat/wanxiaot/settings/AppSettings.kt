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
 * TTS 引擎类型
 */
enum class TtsEngineType {
    SYSTEM,  // 系统 TTS 引擎
    DOUBAO   // 豆包 TTS（火山引擎）
}

/**
 * 豆包 TTS 音色配置
 */
data class DoubaoVoice(
    val id: String,
    val name: String,
    val description: String = ""
) {
    companion object {
        val DEFAULT_VOICES = listOf(
            DoubaoVoice("zh_female_tianmei_moon_bigtts", "甜美女声", "温柔甜美的女性声音"),
            DoubaoVoice("zh_male_chunhou_moon_bigtts", "醇厚男声", "成熟稳重的男性声音"),
            DoubaoVoice("zh_female_shuangkuai_moon_bigtts", "爽快女声", "干练爽朗的女性声音"),
            DoubaoVoice("zh_male_yangguang_moon_bigtts", "阳光男声", "活力阳光的男性声音"),
            DoubaoVoice("zh_female_wanwanxiaohe_moon_bigtts", "湾湾小何", "台湾口音女声"),
            DoubaoVoice("zh_male_rap_moon_bigtts", "说唱男声", "说唱风格男声"),
            DoubaoVoice("zh_female_story_moon_bigtts", "故事女声", "适合讲故事的女声"),
            DoubaoVoice("zh_male_story_moon_bigtts", "故事男声", "适合讲故事的男声")
        )
    }
}

/**
 * OCR 设置
 */
data class OcrSettings(
    val model: String = OcrModel.MLKIT_LATIN.name,  // OCR 模型类型
    val aiProviderId: String = "",                   // 用于 AI OCR 的提供商 ID
    val aiModel: String = "",                        // 用于 AI OCR 的模型名称
    val customPrompt: String = DEFAULT_OCR_PROMPT    // 自定义 OCR Prompt
) {
    companion object {
        const val DEFAULT_OCR_PROMPT = """请识别图片中的所有文字内容。
如果图片中包含 API Key 或 URL，请特别标注出来，格式如下：
- URL: <识别到的URL>
- API Key: <识别到的Key>

只返回识别到的文字内容，不要添加额外的解释。"""
    }
}

/**
 * TTS 设置
 */
data class TtsSettings(
    val enabled: Boolean = false,           // 是否启用 TTS
    val autoSpeak: Boolean = false,         // AI 回复完成后自动朗读
    val speechRate: Float = 1.0f,           // 语速 (0.1 - 3.0)
    val pitch: Float = 1.0f,                // 音调 (0.1 - 2.0)
    val language: String = "zh-CN",         // 语言代码
    val enginePackage: String = "",         // 系统 TTS 引擎包名，空表示使用系统默认
    // 引擎类型选择
    val engineType: TtsEngineType = TtsEngineType.SYSTEM,
    // 豆包 TTS 配置
    val doubaoAppId: String = "",           // 豆包 App ID
    val doubaoAccessToken: String = "",     // 豆包 Access Token
    val doubaoCluster: String = "volcano_tts",  // 豆包服务集群
    val doubaoVoiceType: String = "zh_female_tianmei_moon_bigtts"  // 豆包音色
)

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
 * API Key 状态
 */
enum class ApiKeyStatus {
    ACTIVE,       // 正常可用
    DISABLED,     // 已禁用（用户手动禁用）
    ERROR,        // 错误状态（连续失败后自动标记）
    RATE_LIMITED  // 限流中
}

/**
 * Key 选择策略
 */
enum class KeySelectionStrategy {
    ROUND_ROBIN,  // 轮询
    PRIORITY,     // 优先级（数字越小优先级越高）
    RANDOM,       // 随机
    LEAST_USED    // 最少使用
}

/**
 * API Key 条目
 * 用于多 Key 管理
 */
data class ApiKeyEntry(
    val id: String = UUID.randomUUID().toString(),
    val key: String,                              // API Key 值
    val name: String = "",                        // 显示名称（可选）
    val isEnabled: Boolean = true,                // 是否启用
    val priority: Int = 5,                        // 优先级 1-10，数字越小越优先
    val requestCount: Int = 0,                    // 总请求次数
    val successCount: Int = 0,                    // 成功请求次数
    val failureCount: Int = 0,                    // 连续失败次数
    val lastUsedAt: Long = 0,                     // 最后使用时间戳
    val lastError: String? = null,                // 最后错误信息
    val status: ApiKeyStatus = ApiKeyStatus.ACTIVE,
    val statusChangedAt: Long = 0                 // 状态变更时间（用于自动恢复）
) {
    /**
     * 获取脱敏显示的 Key
     */
    fun getMaskedKey(): String {
        return if (key.length > 8) {
            "${key.take(4)}****${key.takeLast(4)}"
        } else {
            "****"
        }
    }

    /**
     * 获取显示名称（如果没有设置名称，则显示脱敏 Key）
     */
    fun getDisplayName(): String {
        return name.ifEmpty { getMaskedKey() }
    }
}

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
    val modelCustomParams: Map<String, ModelCustomParams> = emptyMap(),  // 模型名 -> 自定义参数
    // 多 Key 管理
    val apiKeys: List<ApiKeyEntry> = emptyList(),
    val multiKeyEnabled: Boolean = false,
    val keySelectionStrategy: KeySelectionStrategy = KeySelectionStrategy.ROUND_ROBIN,
    val roundRobinIndex: Int = 0,
    val maxFailuresBeforeDisable: Int = 3,
    val autoRecoveryMinutes: Int = 5
) {
    /**
     * 获取有效的 API Key
     * 如果启用了多 Key，则返回 null（需要通过 ApiKeySelector 选择）
     * 否则返回单个 apiKey
     */
    fun getEffectiveApiKey(): String? {
        return if (multiKeyEnabled && apiKeys.isNotEmpty()) {
            null  // 需要通过 ApiKeySelector 选择
        } else {
            apiKey
        }
    }

    /**
     * 获取可用的 Key 数量
     */
    fun getAvailableKeyCount(): Int {
        return if (multiKeyEnabled) {
            apiKeys.count { it.isEnabled && it.status == ApiKeyStatus.ACTIVE }
        } else {
            if (apiKey.isNotBlank()) 1 else 0
        }
    }

    /**
     * 获取总 Key 数量
     */
    fun getTotalKeyCount(): Int {
        return if (multiKeyEnabled) {
            apiKeys.size
        } else {
            if (apiKey.isNotBlank()) 1 else 0
        }
    }
}

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
    val tokenRecordingStatus: TokenRecordingStatus = TokenRecordingStatus.ENABLED,  // Token 记录状态
    val ttsSettings: TtsSettings = TtsSettings(),  // TTS 语音朗读设置
    val r2Settings: R2Settings = R2Settings(),  // Cloudflare R2 云备份设置
    val language: String = "zh-CN",  // 应用显示语言
    val ocrSettings: OcrSettings = OcrSettings()  // OCR 设置
) {
    // 兼容旧代码
    val defaultProviderId: String get() = currentProviderId

    // 兼容旧代码：ocrModel 属性
    val ocrModel: String get() = ocrSettings.model

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
