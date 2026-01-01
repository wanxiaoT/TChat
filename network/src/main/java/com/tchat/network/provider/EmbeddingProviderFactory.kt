package com.tchat.network.provider

/**
 * Embedding Provider 工厂类
 * 根据服务商类型创建对应的 Embedding 提供者
 */
object EmbeddingProviderFactory {

    /**
     * 支持 Embedding 的服务商类型
     */
    enum class EmbeddingProviderType {
        OPENAI,
        GEMINI
    }

    /**
     * 创建 Embedding 提供者
     * @param type 服务商类型
     * @param apiKey API密钥
     * @param baseUrl API端点（可选）
     * @return EmbeddingProvider 实例
     */
    fun create(
        type: EmbeddingProviderType,
        apiKey: String,
        baseUrl: String? = null
    ): EmbeddingProvider {
        return when (type) {
            EmbeddingProviderType.OPENAI -> {
                OpenAIEmbeddingProvider(
                    apiKey = apiKey,
                    baseUrl = baseUrl ?: "https://api.openai.com/v1"
                )
            }
            EmbeddingProviderType.GEMINI -> {
                GeminiEmbeddingProvider(
                    apiKey = apiKey,
                    baseUrl = baseUrl ?: "https://generativelanguage.googleapis.com/v1"
                )
            }
        }
    }

    /**
     * 获取服务商的默认 Embedding 模型列表
     */
    fun getDefaultModels(type: EmbeddingProviderType): List<String> {
        return when (type) {
            EmbeddingProviderType.OPENAI -> OpenAIEmbeddingProvider.DEFAULT_MODELS
            EmbeddingProviderType.GEMINI -> GeminiEmbeddingProvider.DEFAULT_MODELS
        }
    }

    /**
     * 获取服务商的默认 Embedding 模型
     */
    fun getDefaultModel(type: EmbeddingProviderType): String {
        return getDefaultModels(type).firstOrNull() ?: when (type) {
            EmbeddingProviderType.OPENAI -> "text-embedding-3-small"
            EmbeddingProviderType.GEMINI -> "text-embedding-004"
        }
    }
}
