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
        OPENAI_RESPONSES,
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
        val customParams: CustomParams? = null,
        val extraHeaders: Map<String, String> = emptyMap(),
        val chatPath: String = "",
        val imagesPath: String = "",
        val authHeaderName: String = "Authorization",
        val authHeaderValue: String? = null
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
                customParams = config.customParams,
                extraHeaders = config.extraHeaders,
                chatPath = config.chatPath.ifBlank { "/chat/completions" },
                imagesPath = config.imagesPath.ifBlank { "/images/generations" },
                authHeaderName = config.authHeaderName,
                authHeaderValue = config.authHeaderValue
            )
            ProviderType.OPENAI_RESPONSES -> createOpenAIResponses(
                apiKey = config.apiKey,
                baseUrl = config.baseUrl.ifEmpty { "https://api.openai.com/v1" },
                model = config.model.ifEmpty { "gpt-4.1-mini" },
                customParams = config.customParams,
                extraHeaders = config.extraHeaders,
                responsesPath = config.chatPath.ifBlank { "/responses" },
                authHeaderName = config.authHeaderName,
                authHeaderValue = config.authHeaderValue
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
                baseUrl = config.baseUrl.ifEmpty { "https://generativelanguage.googleapis.com/v1" },
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
        customParams: CustomParams? = null,
        extraHeaders: Map<String, String> = emptyMap(),
        chatPath: String = "/chat/completions",
        imagesPath: String = "/images/generations",
        authHeaderName: String = "Authorization",
        authHeaderValue: String? = null
    ): AIProvider {
        return OpenAIProvider(
            apiKey = apiKey,
            baseUrl = baseUrl,
            model = model,
            customParams = customParams,
            extraHeaders = extraHeaders,
            chatPath = chatPath,
            imagesPath = imagesPath,
            authHeaderName = authHeaderName,
            authHeaderValue = authHeaderValue
        )
    }

    fun createOpenAIResponses(
        apiKey: String,
        baseUrl: String = "https://api.openai.com/v1",
        model: String = "gpt-4.1-mini",
        customParams: CustomParams? = null,
        extraHeaders: Map<String, String> = emptyMap(),
        responsesPath: String = "/responses",
        authHeaderName: String = "Authorization",
        authHeaderValue: String? = null
    ): AIProvider {
        return OpenAIResponsesProvider(
            apiKey = apiKey,
            baseUrl = baseUrl,
            model = model,
            customParams = customParams,
            extraHeaders = extraHeaders,
            responsesPath = responsesPath,
            authHeaderName = authHeaderName,
            authHeaderValue = authHeaderValue
        )
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
        baseUrl: String = "https://generativelanguage.googleapis.com/v1",
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
        customParams: CustomParams? = null,
        extraHeaders: Map<String, String> = emptyMap()
    ): AIProvider {
        val type = when (providerType.lowercase()) {
            "openai", "naapi", "naapi_tchat", "naapi-tchat" -> ProviderType.OPENAI
            "openai_responses", "openai-responses", "responses" -> ProviderType.OPENAI_RESPONSES
            "anthropic" -> ProviderType.ANTHROPIC
            "gemini" -> ProviderType.GEMINI
            "deepseek", "openrouter", "ollama" -> ProviderType.OPENAI
            else -> ProviderType.OPENAI  // 默认使用 OpenAI 格式
        }
        return create(ProviderConfig(
            type = type,
            apiKey = apiKey,
            baseUrl = baseUrl ?: "",
            model = model,
            customParams = customParams,
            extraHeaders = extraHeaders
        ))
    }
}
