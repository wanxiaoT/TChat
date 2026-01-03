package com.tchat.network.provider

/**
 * AI Provider 工厂
 * 根据配置创建对应的 Provider 实例
 */
object AIProviderFactory {

    /**
     * Provider 类型枚举
     */
    enum class ProviderType {
        OPENAI,
        ANTHROPIC,
        GEMINI
    }

    /**
     * Provider 配置
     */
    data class ProviderConfig(
        val type: ProviderType,
        val apiKey: String,
        val baseUrl: String = "",
        val model: String = "",
        val customParams: CustomParams? = null
    )

    /**
     * 根据配置创建对应的 Provider
     */
    fun create(config: ProviderConfig): AIProvider {
        return when (config.type) {
            ProviderType.OPENAI -> createOpenAI(
                apiKey = config.apiKey,
                baseUrl = config.baseUrl.ifEmpty { "https://api.openai.com/v1" },
                model = config.model.ifEmpty { "gpt-3.5-turbo" },
                customParams = config.customParams
            )
            ProviderType.ANTHROPIC -> createAnthropic(
                apiKey = config.apiKey,
                // AnthropicProvider 内部会自动处理 /v1 路径
                baseUrl = config.baseUrl.ifEmpty { "https://api.anthropic.com" },
                model = config.model.ifEmpty { "claude-sonnet-4-20250514" },
                customParams = config.customParams
            )
            ProviderType.GEMINI -> createGemini(
                apiKey = config.apiKey,
                baseUrl = config.baseUrl.ifEmpty { "https://generativelanguage.googleapis.com/v1beta" },
                model = config.model.ifEmpty { "gemini-pro" },
                customParams = config.customParams
            )
        }
    }

    /**
     * 创建 OpenAI Provider
     */
    fun createOpenAI(
        apiKey: String,
        baseUrl: String = "https://api.openai.com/v1",
        model: String = "gpt-3.5-turbo",
        customParams: CustomParams? = null
    ): AIProvider {
        return OpenAIProvider(apiKey, baseUrl, model, customParams)
    }

    /**
     * 创建 Anthropic Provider
     *
     * 支持灵活的 baseUrl 格式：
     * - https://api.anthropic.com
     * - https://api.anthropic.com/v1
     * - https://custom.proxy.com/anthropic
     */
    fun createAnthropic(
        apiKey: String,
        baseUrl: String = "https://api.anthropic.com",
        model: String = "claude-sonnet-4-20250514",
        maxTokens: Int = 8192,
        customParams: CustomParams? = null
    ): AIProvider {
        return AnthropicProvider(apiKey, baseUrl, model, maxTokens, customParams = customParams)
    }

    /**
     * 创建 Gemini Provider
     */
    fun createGemini(
        apiKey: String,
        baseUrl: String = "https://generativelanguage.googleapis.com/v1beta",
        model: String = "gemini-pro",
        customParams: CustomParams? = null
    ): AIProvider {
        return GeminiProvider(apiKey, baseUrl, model, customParams)
    }

    /**
     * 根据字符串类型创建 Provider
     * @param providerType 类型字符串: "openai", "anthropic", "gemini"
     */
    fun create(
        providerType: String,
        apiKey: String,
        baseUrl: String? = null,
        model: String,
        customParams: CustomParams? = null
    ): AIProvider {
        val type = when (providerType.lowercase()) {
            "openai" -> ProviderType.OPENAI
            "anthropic" -> ProviderType.ANTHROPIC
            "gemini" -> ProviderType.GEMINI
            else -> ProviderType.OPENAI  // 默认使用 OpenAI 格式
        }
        return create(ProviderConfig(
            type = type,
            apiKey = apiKey,
            baseUrl = baseUrl ?: "",
            model = model,
            customParams = customParams
        ))
    }
}
