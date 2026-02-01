package com.tchat.feature.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tchat.data.model.MessagePart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

internal object ChatMediaUtils {

    private const val MAX_IMAGE_BYTES: Long = 10L * 1024L * 1024L // 10MB
    private const val MAX_VIDEO_BYTES: Long = 20L * 1024L * 1024L // 20MB

    suspend fun importMediaPart(context: Context, uri: Uri): MessagePart = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri)?.trim().orEmpty()
        val fileName = queryDisplayName(resolver = resolver, uri = uri)
        val inferredMimeType = mimeType.ifBlank { guessMimeTypeFromName(fileName) }

        val kind = when {
            inferredMimeType.startsWith("image/") -> MediaKind.IMAGE
            inferredMimeType.startsWith("video/") -> MediaKind.VIDEO
            else -> throw IllegalArgumentException("不支持的文件类型: ${inferredMimeType.ifBlank { "unknown" }}")
        }

        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("无法读取文件内容")

        when (kind) {
            MediaKind.IMAGE -> {
                if (bytes.size.toLong() > MAX_IMAGE_BYTES) {
                    throw IllegalArgumentException("图片过大（>${MAX_IMAGE_BYTES / 1024 / 1024}MB），请压缩后再发送")
                }
            }
            MediaKind.VIDEO -> {
                if (bytes.size.toLong() > MAX_VIDEO_BYTES) {
                    throw IllegalArgumentException("视频过大（>${MAX_VIDEO_BYTES / 1024 / 1024}MB），请裁剪后再发送")
                }
            }
        }

        val outDir = File(context.filesDir, "chat_media/uploads").apply { mkdirs() }
        val ext = fileName.substringAfterLast('.', "").takeIf { it.isNotBlank() }
            ?: extensionFromMimeType(inferredMimeType)

        val safeBaseName = fileName.substringBeforeLast('.', fileName).takeIf { it.isNotBlank() } ?: "media"
        val outFileName = "${safeBaseName}_${UUID.randomUUID()}.$ext"
        val outFile = File(outDir, outFileName)

        FileOutputStream(outFile).use { it.write(bytes) }

        when (kind) {
            MediaKind.IMAGE -> MessagePart.Image(
                filePath = outFile.absolutePath,
                mimeType = inferredMimeType.ifBlank { "image/png" },
                fileName = fileName.ifBlank { outFile.name },
                sizeBytes = bytes.size.toLong()
            )
            MediaKind.VIDEO -> MessagePart.Video(
                filePath = outFile.absolutePath,
                mimeType = inferredMimeType.ifBlank { "video/mp4" },
                fileName = fileName.ifBlank { outFile.name },
                sizeBytes = bytes.size.toLong()
            )
        }
    }

    private enum class MediaKind { IMAGE, VIDEO }

    private fun queryDisplayName(resolver: android.content.ContentResolver, uri: Uri): String {
        return runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) {
                    cursor.getString(index).orEmpty()
                } else {
                    ""
                }
            } ?: ""
        }.getOrDefault("")
    }

    private fun guessMimeTypeFromName(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            else -> ""
        }
    }

    private fun extensionFromMimeType(mimeType: String): String {
        return when {
            mimeType.contains("png", ignoreCase = true) -> "png"
            mimeType.contains("jpeg", ignoreCase = true) || mimeType.contains("jpg", ignoreCase = true) -> "jpg"
            mimeType.contains("webp", ignoreCase = true) -> "webp"
            mimeType.contains("gif", ignoreCase = true) -> "gif"
            mimeType.contains("mp4", ignoreCase = true) -> "mp4"
            mimeType.contains("webm", ignoreCase = true) -> "webm"
            else -> "bin"
        }
    }
}

