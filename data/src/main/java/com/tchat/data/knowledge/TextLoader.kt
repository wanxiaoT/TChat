package com.tchat.data.knowledge

/**
 * 文本内容加载器
 * 直接返回给定的文本内容
 */
class TextLoader(
    private val content: String
) : DocumentLoader {

    override suspend fun load(): String {
        return content
    }
}
