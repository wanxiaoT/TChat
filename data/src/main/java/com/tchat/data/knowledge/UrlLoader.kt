package com.tchat.data.knowledge

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * URL网页内容加载器
 * 抓取指定URL的网页内容并提取文本
 */
class UrlLoader(
    private val url: String,
    private val timeout: Int = 30
) : DocumentLoader {

    override suspend fun load(): String = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = timeout * 1000
            connection.readTimeout = timeout * 1000
            connection.setRequestProperty("User-Agent", "TChat/1.0 KnowledgeBase")
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP错误: $responseCode")
            }

            val htmlContent = connection.inputStream.bufferedReader().use { it.readText() }

            // 提取纯文本内容
            extractTextFromHtml(htmlContent)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * 从HTML中提取纯文本
     */
    private fun extractTextFromHtml(html: String): String {
        var text = html

        // 移除script和style标签及其内容
        text = text.replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
        text = text.replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")

        // 移除注释
        text = text.replace(Regex("<!--[\\s\\S]*?-->"), "")

        // 将常见块级元素转换为换行
        text = text.replace(Regex("<(br|p|div|h[1-6]|li|tr)[^>]*>", RegexOption.IGNORE_CASE), "\n")
        text = text.replace(Regex("</(p|div|h[1-6]|li|tr)>", RegexOption.IGNORE_CASE), "\n")

        // 移除所有HTML标签
        text = text.replace(Regex("<[^>]+>"), "")

        // 解码HTML实体
        text = decodeHtmlEntities(text)

        // 清理多余空白
        text = text.replace(Regex("[ \\t]+"), " ")
        text = text.replace(Regex("\\n{3,}"), "\n\n")
        text = text.trim()

        return text
    }

    /**
     * 解码常见HTML实体
     */
    private fun decodeHtmlEntities(text: String): String {
        return text
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&hellip;", "...")
            .replace("&copy;", "©")
            .replace("&reg;", "®")
            .replace("&trade;", "™")
            .replace(Regex("&#(\\d+);")) { matchResult ->
                val code = matchResult.groupValues[1].toIntOrNull() ?: return@replace matchResult.value
                if (code in 0..0x10FFFF) {
                    code.toChar().toString()
                } else {
                    matchResult.value
                }
            }
            .replace(Regex("&#x([0-9a-fA-F]+);")) { matchResult ->
                val code = matchResult.groupValues[1].toIntOrNull(16) ?: return@replace matchResult.value
                if (code in 0..0x10FFFF) {
                    code.toChar().toString()
                } else {
                    matchResult.value
                }
            }
    }
}
