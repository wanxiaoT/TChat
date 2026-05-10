package com.tchat.network.provider

internal object BuiltinProviderDefinitions {
    val all: List<ProviderDefinition> = listOf(
        openAI(),
        openAIResponses(),
        anthropic(),
        gemini(),
        deepSeek(),
        openRouter(),
        ollama(),
        naapiTChat()
    )

    private fun openAI(): ProviderDefinition {
        return openAICompatible(
            id = "openai",
            displayName = "OpenAI Compatible",
            defaultEndpoint = "https://api.openai.com/v1",
            defaultModelId = "gpt-3.5-turbo",
            defaultModels = listOf(
                model("gpt-4o", ModelCapability.VISION, ModelCapability.TOOL_USE),
                model("gpt-4o-mini", ModelCapability.VISION, ModelCapability.TOOL_USE),
                model("gpt-4-turbo", ModelCapability.VISION, ModelCapability.TOOL_USE),
                model("gpt-3.5-turbo", ModelCapability.TOOL_USE)
            ),
            aliases = setOf("openai-compatible")
        )
    }

    private fun openAIResponses(): ProviderDefinition {
        return ProviderDefinition(
            id = "openai-responses",
            displayName = "OpenAI Responses",
            apiStyle = ProviderApiStyle.OPENAI_RESPONSES,
            defaultEndpoint = "https://api.openai.com/v1",
            defaultChatPath = "/responses",
            defaultModelsPath = "/models",
            defaultImagesPath = "/images/generations",
            defaultModelId = "gpt-4.1-mini",
            defaultModels = listOf(
                model("gpt-4.1", ModelCapability.VISION, ModelCapability.TOOL_USE),
                model("gpt-4.1-mini", ModelCapability.VISION, ModelCapability.TOOL_USE),
                model("gpt-4o", ModelCapability.VISION, ModelCapability.TOOL_USE),
                model("gpt-4o-mini", ModelCapability.VISION, ModelCapability.TOOL_USE)
            ),
            capabilities = setOf(
                ProviderCapability.CHAT,
                ProviderCapability.STREAMING,
                ProviderCapability.VISION,
                ProviderCapability.CUSTOM_ENDPOINT,
                ProviderCapability.MODEL_LIST
            ),
            aliases = setOf("openai_responses", "responses"),
            createProvider = { config ->
                OpenAIResponsesProvider(
                    apiKey = config.apiKey,
                    baseUrl = config.baseUrl,
                    model = config.model,
                    customParams = config.customParams,
                    extraHeaders = config.extraHeaders,
                    responsesPath = config.chatPath,
                    authHeaderName = config.authHeaderName,
                    authHeaderValue = config.authHeaderValue
                )
            }
        )
    }

    private fun anthropic(): ProviderDefinition {
        return ProviderDefinition(
            id = "anthropic",
            displayName = "Anthropic Claude",
            apiStyle = ProviderApiStyle.ANTHROPIC_MESSAGES,
            defaultEndpoint = "https://api.anthropic.com",
            defaultChatPath = "/messages",
            defaultModelsPath = "/models",
            defaultModelId = "claude-sonnet-4-20250514",
            defaultModels = listOf(
                model("claude-sonnet-4-20250514", ModelCapability.VISION, ModelCapability.TOOL_USE),
                model("claude-3-5-sonnet-20241022", ModelCapability.VISION, ModelCapability.TOOL_USE),
                model("claude-3-5-haiku-20241022", ModelCapability.VISION, ModelCapability.TOOL_USE),
                model("claude-3-opus-20240229", ModelCapability.VISION, ModelCapability.TOOL_USE)
            ),
            capabilities = setOf(
                ProviderCapability.CHAT,
                ProviderCapability.STREAMING,
                ProviderCapability.TOOLS,
                ProviderCapability.VISION,
                ProviderCapability.CUSTOM_ENDPOINT,
                ProviderCapability.MODEL_LIST
            ),
            aliases = setOf("claude"),
            createProvider = { config ->
                AnthropicProvider(
                    apiKey = config.apiKey,
                    baseUrl = config.baseUrl,
                    model = config.model,
                    maxTokens = config.customParams?.maxTokens ?: 8192,
                    customParams = config.customParams
                )
            }
        )
    }

    private fun gemini(): ProviderDefinition {
        return ProviderDefinition(
            id = "gemini",
            displayName = "Google Gemini",
            apiStyle = ProviderApiStyle.GEMINI_GENERATE_CONTENT,
            defaultEndpoint = "https://generativelanguage.googleapis.com/v1",
            defaultChatPath = "/models/{model}:streamGenerateContent",
            defaultModelsPath = "/models",
            defaultModelId = "gemini-pro",
            defaultModels = listOf(
                model("gemini-2.0-flash-exp", ModelCapability.VISION, ModelCapability.VIDEO_INPUT, ModelCapability.TOOL_USE),
                model("gemini-1.5-pro", ModelCapability.VISION, ModelCapability.VIDEO_INPUT, ModelCapability.TOOL_USE),
                model("gemini-1.5-flash", ModelCapability.VISION, ModelCapability.VIDEO_INPUT, ModelCapability.TOOL_USE),
                model("gemini-pro", ModelCapability.TOOL_USE)
            ),
            capabilities = setOf(
                ProviderCapability.CHAT,
                ProviderCapability.STREAMING,
                ProviderCapability.TOOLS,
                ProviderCapability.VISION,
                ProviderCapability.VIDEO_INPUT,
                ProviderCapability.CUSTOM_ENDPOINT,
                ProviderCapability.MODEL_LIST
            ),
            aliases = setOf("google", "google-gemini"),
            createProvider = { config ->
                GeminiProvider(
                    apiKey = config.apiKey,
                    baseUrl = config.baseUrl,
                    model = config.model,
                    customParams = config.customParams
                )
            }
        )
    }

    private fun deepSeek(): ProviderDefinition {
        return openAICompatible(
            id = "deepseek",
            displayName = "DeepSeek",
            defaultEndpoint = "https://api.deepseek.com/v1",
            defaultModelId = "deepseek-chat",
            defaultImagesPath = "",
            defaultModels = listOf(
                model("deepseek-chat", ModelCapability.TOOL_USE),
                model("deepseek-reasoner", ModelCapability.REASONING)
            )
        )
    }

    private fun openRouter(): ProviderDefinition {
        return openAICompatible(
            id = "openrouter",
            displayName = "OpenRouter",
            defaultEndpoint = "https://openrouter.ai/api/v1",
            defaultModelId = "openai/gpt-4o-mini",
            defaultImagesPath = "",
            defaultModels = listOf(
                model("openai/gpt-4o-mini", ModelCapability.VISION, ModelCapability.TOOL_USE),
                model("anthropic/claude-3.5-sonnet", ModelCapability.VISION, ModelCapability.TOOL_USE),
                model("google/gemini-flash-1.5", ModelCapability.VISION, ModelCapability.TOOL_USE)
            )
        )
    }

    private fun ollama(): ProviderDefinition {
        return openAICompatible(
            id = "ollama",
            displayName = "Ollama / LM Studio",
            defaultEndpoint = "http://127.0.0.1:11434/v1",
            defaultModelId = "llama3.1",
            defaultImagesPath = "",
            defaultModels = listOf(
                model("llama3.1"),
                model("qwen2.5"),
                model("deepseek-r1", ModelCapability.REASONING)
            ),
            aliases = setOf("lmstudio", "lm-studio")
        )
    }

    private fun naapiTChat(): ProviderDefinition {
        return openAICompatible(
            id = "naapi-tchat",
            displayName = "TChat 官方服务",
            defaultEndpoint = "https://t.naapi.cc/v1",
            defaultModelId = "gpt-4o-mini",
            defaultModels = listOf(
                model("gpt-4o-mini", ModelCapability.VISION, ModelCapability.TOOL_USE),
                model("gpt-4o", ModelCapability.VISION, ModelCapability.TOOL_USE),
                model("deepseek-chat", ModelCapability.TOOL_USE)
            ),
            aliases = setOf("naapi", "naapi_tchat", "tchat-official")
        )
    }

    private fun openAICompatible(
        id: String,
        displayName: String,
        defaultEndpoint: String,
        defaultModelId: String,
        defaultModels: List<ModelDefinition>,
        defaultImagesPath: String = "/images/generations",
        aliases: Set<String> = emptySet()
    ): ProviderDefinition {
        val providerCapabilities = buildSet {
            add(ProviderCapability.CHAT)
            add(ProviderCapability.STREAMING)
            add(ProviderCapability.TOOLS)
            add(ProviderCapability.CUSTOM_ENDPOINT)
            add(ProviderCapability.MODEL_LIST)
            if (defaultImagesPath.isNotBlank()) {
                add(ProviderCapability.IMAGE_GENERATION)
            }
            if (defaultModels.any { ModelCapability.VISION in it.capabilities }) {
                add(ProviderCapability.VISION)
            }
        }

        return ProviderDefinition(
            id = id,
            displayName = displayName,
            apiStyle = ProviderApiStyle.OPENAI_CHAT_COMPLETIONS,
            defaultEndpoint = defaultEndpoint,
            defaultChatPath = "/chat/completions",
            defaultModelsPath = "/models",
            defaultImagesPath = defaultImagesPath,
            defaultModelId = defaultModelId,
            defaultModels = defaultModels,
            capabilities = providerCapabilities,
            aliases = aliases,
            createProvider = { config ->
                OpenAIProvider(
                    apiKey = config.apiKey,
                    baseUrl = config.baseUrl,
                    model = config.model,
                    customParams = config.customParams,
                    extraHeaders = config.extraHeaders,
                    chatPath = config.chatPath,
                    imagesPath = config.imagesPath,
                    authHeaderName = config.authHeaderName,
                    authHeaderValue = config.authHeaderValue
                )
            }
        )
    }

    private fun model(
        id: String,
        vararg extraCapabilities: ModelCapability,
        contextWindow: Int? = null,
        maxOutputTokens: Int? = null
    ): ModelDefinition {
        return ModelDefinition(
            id = id,
            capabilities = setOf(ModelCapability.TEXT) + extraCapabilities.toSet(),
            contextWindow = contextWindow,
            maxOutputTokens = maxOutputTokens
        )
    }
}
