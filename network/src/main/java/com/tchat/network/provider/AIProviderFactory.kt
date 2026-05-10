package com.tchat.network.provider

/**
 * AI Provider 工厂
 * 根据配置创建对应的 Provider 实例
 */
object AIProviderFactory {

    /**
     * Provider 类型枚举
     */
    enum class ProviderType(val registryId: String) {
        OPENAI("openai"),
        OPENAI_RESPONSES("openai-responses"),
        ANTHROPIC("anthropic"),
        GEMINI("gemini")
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
        val authHeaderValue: String? = null,
        val providerId: String? = null
    )

    /**
     * 根据配置创建对应的 Provider
     */
    fun create(config: ProviderConfig): AIProvider {
        val definition = config.providerId
            ?.let { ProviderRegistry.get(it) }
            ?: ProviderRegistry.get(config.type.registryId)
            ?: error("未知 Provider 类型: ${config.type}")

        return definition.create(config.toProviderCreateConfig())
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
        return ProviderRegistry.get("openai")!!.create(
            ProviderCreateConfig(
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
        )
    }

    fun createOpenAICompatible(
        providerId: String,
        apiKey: String,
        baseUrl: String = "",
        model: String = "",
        customParams: CustomParams? = null,
        extraHeaders: Map<String, String> = emptyMap(),
        chatPath: String = "",
        imagesPath: String = "",
        authHeaderName: String = "Authorization",
        authHeaderValue: String? = null
    ): AIProvider {
        val definition = ProviderRegistry.get(providerId)
            ?: ProviderRegistry.get("openai")!!
        return definition.create(
            ProviderCreateConfig(
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
        return ProviderRegistry.get("openai-responses")!!.create(
            ProviderCreateConfig(
                apiKey = apiKey,
                baseUrl = baseUrl,
                model = model,
                customParams = customParams,
                extraHeaders = extraHeaders,
                chatPath = responsesPath,
                authHeaderName = authHeaderName,
                authHeaderValue = authHeaderValue
            )
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
        val resolvedParams = if (customParams?.maxTokens != null) {
            customParams
        } else {
            (customParams ?: CustomParams()).copy(maxTokens = maxTokens)
        }

        return ProviderRegistry.get("anthropic")!!.create(
            ProviderCreateConfig(
                apiKey = apiKey,
                baseUrl = baseUrl,
                model = model,
                customParams = resolvedParams
            )
        )
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
        return ProviderRegistry.get("gemini")!!.create(
            ProviderCreateConfig(
                apiKey = apiKey,
                baseUrl = baseUrl,
                model = model,
                customParams = customParams
            )
        )
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
        val definition = ProviderRegistry.get(providerType)
            ?: ProviderRegistry.get("openai")!! // 兼容旧行为：未知类型按 OpenAI 兼容格式处理

        return definition.create(
            ProviderCreateConfig(
                apiKey = apiKey,
                baseUrl = baseUrl.orEmpty(),
                model = model,
                customParams = customParams,
                extraHeaders = extraHeaders
            )
        )
    }

    private fun ProviderConfig.toProviderCreateConfig(): ProviderCreateConfig {
        return ProviderCreateConfig(
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
}
