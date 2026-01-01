package com.tchat.data.knowledge

/**
 * 文档加载器接口
 */
interface DocumentLoader {
    /**
     * 加载文档内容
     * @return 文档内容
     */
    suspend fun load(): String
}

/**
 * 文档加载结果
 */
data class DocumentContent(
    val content: String,
    val metadata: Map<String, String> = emptyMap()
)
