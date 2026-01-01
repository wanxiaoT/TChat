package com.tchat.data.util

/**
 * 文本分块工具
 */
object TextChunker {
    /**
     * 将文本分块
     * @param text 要分块的文本
     * @param maxChunkSize 每个块的最大字符数
     * @param overlap 块之间的重叠字符数
     * @return 文本块列表
     */
    fun chunkText(
        text: String,
        maxChunkSize: Int = 500,
        overlap: Int = 50
    ): List<String> {
        if (text.length <= maxChunkSize) {
            return listOf(text)
        }

        val chunks = mutableListOf<String>()
        var start = 0

        while (start < text.length) {
            val end = minOf(start + maxChunkSize, text.length)
            var chunk = text.substring(start, end)

            // 尝试在句子边界处分割
            if (end < text.length) {
                val lastPeriod = chunk.lastIndexOfAny(charArrayOf('.', '!', '?', '\n', '。', '!', '?'))
                if (lastPeriod > maxChunkSize / 2) {
                    chunk = chunk.substring(0, lastPeriod + 1)
                }
            }

            chunks.add(chunk.trim())

            // 计算下一个起始位置
            start += chunk.length - overlap
            if (start + maxChunkSize >= text.length && start < text.length) {
                // 最后一块
                start = maxOf(start, text.length - maxChunkSize)
            }
        }

        return chunks.distinct()
    }

    /**
     * 按段落分块
     */
    fun chunkByParagraphs(
        text: String,
        maxChunkSize: Int = 1000
    ): List<String> {
        val paragraphs = text.split(Regex("\n\n+"))
        val chunks = mutableListOf<String>()
        var currentChunk = StringBuilder()

        for (paragraph in paragraphs) {
            if (currentChunk.length + paragraph.length > maxChunkSize && currentChunk.isNotEmpty()) {
                chunks.add(currentChunk.toString().trim())
                currentChunk = StringBuilder()
            }
            currentChunk.append(paragraph).append("\n\n")
        }

        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString().trim())
        }

        return chunks.ifEmpty { listOf(text) }
    }

    /**
     * 按语义分块 (简单实现:按句子)
     */
    fun chunkBySentences(
        text: String,
        maxChunkSize: Int = 800
    ): List<String> {
        val sentences = text.split(Regex("(?<=[.!?。!?])\\s+"))
        val chunks = mutableListOf<String>()
        var currentChunk = StringBuilder()

        for (sentence in sentences) {
            if (currentChunk.length + sentence.length > maxChunkSize && currentChunk.isNotEmpty()) {
                chunks.add(currentChunk.toString().trim())
                currentChunk = StringBuilder()
            }
            currentChunk.append(sentence).append(" ")
        }

        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString().trim())
        }

        return chunks.ifEmpty { listOf(text) }
    }
}
