package com.tchat.network.provider

/**
 * Embedding 服务提供者接口
 * 用于将文本转换为向量嵌入
 */
interface EmbeddingProvider {
    /**
     * 批量生成文本嵌入向量
     * @param texts 要嵌入的文本列表
     * @param model 嵌入模型名称
     * @return 每个文本对应的嵌入向量列表
     */
    suspend fun embed(texts: List<String>, model: String): List<FloatArray>

    /**
     * 生成单个文本的嵌入向量
     * @param text 要嵌入的文本
     * @param model 嵌入模型名称
     * @return 文本的嵌入向量
     */
    suspend fun embed(text: String, model: String): FloatArray {
        return embed(listOf(text), model).first()
    }

    /**
     * 取消当前请求
     */
    fun cancel()
}

/**
 * Embedding API 异常
 */
sealed class EmbeddingException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class AuthenticationError(message: String) : EmbeddingException(message)
    class RateLimitError(message: String) : EmbeddingException(message)
    class InvalidRequestError(message: String) : EmbeddingException(message)
    class ServiceUnavailableError(message: String) : EmbeddingException(message)
    class NetworkError(message: String, cause: Throwable? = null) : EmbeddingException(message, cause)
    class UnknownError(message: String, cause: Throwable? = null) : EmbeddingException(message, cause)
}
