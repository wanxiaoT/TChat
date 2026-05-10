package com.tchat.network.provider

/**
 * Provider API 风格。
 *
 * 用来描述服务商的协议形态，后续 UI、能力判断和请求构建可以基于它做分流，
 * 避免继续依赖散落的字符串判断。
 */
enum class ProviderApiStyle {
    OPENAI_CHAT_COMPLETIONS,
    OPENAI_RESPONSES,
    ANTHROPIC_MESSAGES,
    GEMINI_GENERATE_CONTENT
}

enum class ProviderCapability {
    CHAT,
    STREAMING,
    TOOLS,
    VISION,
    VIDEO_INPUT,
    IMAGE_GENERATION,
    CUSTOM_ENDPOINT,
    MODEL_LIST
}

enum class ModelCapability {
    TEXT,
    VISION,
    VIDEO_INPUT,
    TOOL_USE,
    REASONING,
    IMAGE_GENERATION
}

data class ModelDefinition(
    val id: String,
    val displayName: String = id,
    val capabilities: Set<ModelCapability> = setOf(ModelCapability.TEXT),
    val contextWindow: Int? = null,
    val maxOutputTokens: Int? = null
)

data class ProviderCreateConfig(
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

data class ResolvedProviderConfig(
    val providerId: String,
    val apiStyle: ProviderApiStyle,
    val apiKey: String,
    val baseUrl: String,
    val model: String,
    val customParams: CustomParams?,
    val extraHeaders: Map<String, String>,
    val chatPath: String,
    val imagesPath: String,
    val authHeaderName: String,
    val authHeaderValue: String?
)

data class ProviderDefinition(
    val id: String,
    val displayName: String,
    val apiStyle: ProviderApiStyle,
    val defaultEndpoint: String,
    val defaultChatPath: String,
    val defaultModelsPath: String,
    val defaultImagesPath: String = "",
    val defaultModelId: String,
    val defaultModels: List<ModelDefinition>,
    val capabilities: Set<ProviderCapability>,
    val aliases: Set<String> = emptySet(),
    val createProvider: (ResolvedProviderConfig) -> AIProvider
) {
    init {
        require(id.isNotBlank()) { "Provider id must not be blank" }
        require(displayName.isNotBlank()) { "Provider displayName must not be blank" }
        require(defaultEndpoint.isNotBlank()) { "Provider defaultEndpoint must not be blank" }
        require(defaultModelId.isNotBlank()) { "Provider defaultModelId must not be blank" }
    }

    fun resolve(config: ProviderCreateConfig): ResolvedProviderConfig {
        return ResolvedProviderConfig(
            providerId = id,
            apiStyle = apiStyle,
            apiKey = config.apiKey,
            baseUrl = config.baseUrl.ifBlank { defaultEndpoint },
            model = config.model.ifBlank { defaultModelId },
            customParams = config.customParams,
            extraHeaders = config.extraHeaders,
            chatPath = config.chatPath.ifBlank { defaultChatPath },
            imagesPath = config.imagesPath.ifBlank { defaultImagesPath },
            authHeaderName = config.authHeaderName.ifBlank { "Authorization" },
            authHeaderValue = config.authHeaderValue
        )
    }

    fun create(config: ProviderCreateConfig): AIProvider {
        return createProvider(resolve(config))
    }

    fun findModel(modelId: String): ModelDefinition? {
        if (modelId.isBlank()) return null
        return defaultModels.firstOrNull { it.id.equals(modelId, ignoreCase = true) }
    }

    fun supports(capability: ProviderCapability): Boolean {
        return capability in capabilities
    }
}
