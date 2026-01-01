package com.tchat.data.knowledge

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 文件内容加载器
 * 支持 TXT、MD 等纯文本文件
 */
class FileLoader(
    private val file: File
) : DocumentLoader {

    override suspend fun load(): String = withContext(Dispatchers.IO) {
        if (!file.exists()) {
            throw Exception("文件不存在: ${file.absolutePath}")
        }

        if (!file.canRead()) {
            throw Exception("无法读取文件: ${file.absolutePath}")
        }

        val extension = file.extension.lowercase()
        when (extension) {
            "txt", "md", "markdown", "text" -> {
                file.readText(Charsets.UTF_8)
            }
            "json" -> {
                file.readText(Charsets.UTF_8)
            }
            else -> {
                // 尝试作为纯文本读取
                try {
                    file.readText(Charsets.UTF_8)
                } catch (e: Exception) {
                    throw Exception("不支持的文件类型: $extension")
                }
            }
        }
    }

    companion object {
        /**
         * 支持的文件扩展名
         */
        val SUPPORTED_EXTENSIONS = listOf("txt", "md", "markdown", "text", "json")

        /**
         * 检查文件是否支持
         */
        fun isSupported(file: File): Boolean {
            return file.extension.lowercase() in SUPPORTED_EXTENSIONS
        }

        /**
         * 检查文件扩展名是否支持
         */
        fun isExtensionSupported(extension: String): Boolean {
            return extension.lowercase() in SUPPORTED_EXTENSIONS
        }
    }
}
