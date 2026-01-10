package com.tchat.wanxiaot.util

import android.content.Context
import com.tchat.wanxiaot.settings.R2Settings
import com.tchat.wanxiaot.settings.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.w3c.dom.Element
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 云备份信息
 */
data class CloudBackupInfo(
    val key: String,           // 文件名/路径
    val size: Long,            // 文件大小
    val lastModified: Long,    // 最后修改时间
    val etag: String           // ETag
)

/**
 * Cloudflare R2 云备份管理器
 * 使用 S3 兼容 API 实现备份文件的上传、下载、列表和删除
 */
class CloudBackupManager(
    private val context: Context,
    private val settingsManager: SettingsManager
) {
    companion object {
        private const val BACKUP_PREFIX = "TChat_Backup_"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)  // 大文件下载需要更长时间
        .writeTimeout(300, TimeUnit.SECONDS)  // 大文件上传需要更长时间
        .build()

    private val r2Settings: R2Settings
        get() = settingsManager.settings.value.r2Settings

    /**
     * 检查 R2 是否已配置
     */
    fun isConfigured(): Boolean = r2Settings.isConfigured

    /**
     * 测试连接
     */
    suspend fun testConnection(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!r2Settings.isConfigured) {
                return@withContext Result.failure(IllegalStateException("R2 未配置"))
            }

            // 尝试列出 bucket 内容来测试连接
            val url = "${r2Settings.endpoint}/${r2Settings.bucketName}?max-keys=1"

            val headers = AwsSignatureV4.signRequest(
                method = "GET",
                url = url,
                headers = emptyMap(),
                payload = null,
                accessKeyId = r2Settings.accessKeyId,
                secretAccessKey = r2Settings.secretAccessKey
            )

            val request = Request.Builder()
                .url(url)
                .get()
                .apply {
                    headers.forEach { (key, value) ->
                        addHeader(key, value)
                    }
                }
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                val errorBody = response.body?.string() ?: "Unknown error"
                Result.failure(Exception("连接失败: ${response.code} - $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("连接失败: ${e.message}"))
        }
    }

    /**
     * 上传备份文件到 R2
     * 使用流式上传，避免大文件占用过多内存
     */
    suspend fun uploadBackup(localFile: File): Result<CloudBackupInfo> = withContext(Dispatchers.IO) {
        try {
            if (!r2Settings.isConfigured) {
                return@withContext Result.failure(IllegalStateException("R2 未配置"))
            }

            if (!localFile.exists()) {
                return@withContext Result.failure(IllegalArgumentException("本地文件不存在"))
            }

            val key = localFile.name
            val url = "${r2Settings.endpoint}/${r2Settings.bucketName}/$key"
            val fileSize = localFile.length()

            // 使用流式签名，避免将整个文件加载到内存
            val headers = AwsSignatureV4.signRequestForStreaming(
                method = "PUT",
                url = url,
                headers = mapOf(
                    "Content-Type" to "application/zip",
                    "Content-Length" to fileSize.toString()
                ),
                accessKeyId = r2Settings.accessKeyId,
                secretAccessKey = r2Settings.secretAccessKey
            )

            // 使用 asRequestBody 直接从文件流式读取，不加载到内存
            val requestBody = localFile.asRequestBody("application/zip".toMediaType())

            val request = Request.Builder()
                .url(url)
                .put(requestBody)
                .apply {
                    headers.forEach { (k, v) ->
                        addHeader(k, v)
                    }
                    addHeader("Content-Type", "application/zip")
                    addHeader("Content-Length", fileSize.toString())
                }
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val etag = response.header("ETag") ?: ""
                Result.success(
                    CloudBackupInfo(
                        key = key,
                        size = fileSize,
                        lastModified = System.currentTimeMillis(),
                        etag = etag.trim('"')
                    )
                )
            } else {
                val errorBody = response.body?.string() ?: "Unknown error"
                Result.failure(Exception("上传失败: ${response.code} - $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("上传失败: ${e.message}"))
        }
    }

    /**
     * 从 R2 下载备份文件
     */
    suspend fun downloadBackup(key: String, outputFile: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (!r2Settings.isConfigured) {
                return@withContext Result.failure(IllegalStateException("R2 未配置"))
            }

            val url = "${r2Settings.endpoint}/${r2Settings.bucketName}/$key"

            val headers = AwsSignatureV4.signRequest(
                method = "GET",
                url = url,
                headers = emptyMap(),
                payload = null,
                accessKeyId = r2Settings.accessKeyId,
                secretAccessKey = r2Settings.secretAccessKey
            )

            val request = Request.Builder()
                .url(url)
                .get()
                .apply {
                    headers.forEach { (k, v) ->
                        addHeader(k, v)
                    }
                }
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                response.body?.byteStream()?.use { input ->
                    outputFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Result.success(outputFile)
            } else {
                val errorBody = response.body?.string() ?: "Unknown error"
                Result.failure(Exception("下载失败: ${response.code} - $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("下载失败: ${e.message}"))
        }
    }

    /**
     * 列出 R2 中的所有备份
     */
    suspend fun listBackups(): Result<List<CloudBackupInfo>> = withContext(Dispatchers.IO) {
        try {
            if (!r2Settings.isConfigured) {
                return@withContext Result.failure(IllegalStateException("R2 未配置"))
            }

            val url = "${r2Settings.endpoint}/${r2Settings.bucketName}?prefix=$BACKUP_PREFIX"

            val headers = AwsSignatureV4.signRequest(
                method = "GET",
                url = url,
                headers = emptyMap(),
                payload = null,
                accessKeyId = r2Settings.accessKeyId,
                secretAccessKey = r2Settings.secretAccessKey
            )

            val request = Request.Builder()
                .url(url)
                .get()
                .apply {
                    headers.forEach { (k, v) ->
                        addHeader(k, v)
                    }
                }
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val xmlBody = response.body?.string() ?: ""
                val backups = parseListBucketResult(xmlBody)
                Result.success(backups.sortedByDescending { it.lastModified })
            } else {
                val errorBody = response.body?.string() ?: "Unknown error"
                Result.failure(Exception("获取列表失败: ${response.code} - $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("获取列表失败: ${e.message}"))
        }
    }

    /**
     * 删除 R2 中的备份
     */
    suspend fun deleteBackup(key: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!r2Settings.isConfigured) {
                return@withContext Result.failure(IllegalStateException("R2 未配置"))
            }

            val url = "${r2Settings.endpoint}/${r2Settings.bucketName}/$key"

            val headers = AwsSignatureV4.signRequest(
                method = "DELETE",
                url = url,
                headers = emptyMap(),
                payload = null,
                accessKeyId = r2Settings.accessKeyId,
                secretAccessKey = r2Settings.secretAccessKey
            )

            val request = Request.Builder()
                .url(url)
                .delete()
                .apply {
                    headers.forEach { (k, v) ->
                        addHeader(k, v)
                    }
                }
                .build()

            val response = client.newCall(request).execute()

            // S3 DELETE 返回 204 No Content 表示成功
            if (response.isSuccessful || response.code == 204) {
                Result.success(Unit)
            } else {
                val errorBody = response.body?.string() ?: "Unknown error"
                Result.failure(Exception("删除失败: ${response.code} - $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("删除失败: ${e.message}"))
        }
    }

    /**
     * 解析 S3 ListBucket XML 响应
     */
    private fun parseListBucketResult(xml: String): List<CloudBackupInfo> {
        val backups = mutableListOf<CloudBackupInfo>()

        try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val document = builder.parse(xml.byteInputStream())

            val contents = document.getElementsByTagName("Contents")

            for (i in 0 until contents.length) {
                val element = contents.item(i) as Element

                val key = element.getElementsByTagName("Key").item(0)?.textContent ?: continue
                val size = element.getElementsByTagName("Size").item(0)?.textContent?.toLongOrNull() ?: 0
                val lastModifiedStr = element.getElementsByTagName("LastModified").item(0)?.textContent ?: ""
                val etag = element.getElementsByTagName("ETag").item(0)?.textContent?.trim('"') ?: ""

                val lastModified = parseIso8601Date(lastModifiedStr)

                backups.add(
                    CloudBackupInfo(
                        key = key,
                        size = size,
                        lastModified = lastModified,
                        etag = etag
                    )
                )
            }
        } catch (e: Exception) {
            // 解析失败返回空列表
        }

        return backups
    }

    /**
     * 解析 ISO 8601 日期
     */
    private fun parseIso8601Date(dateStr: String): Long {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            format.parse(dateStr)?.time ?: 0
        } catch (e: Exception) {
            try {
                val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                format.parse(dateStr)?.time ?: 0
            } catch (e2: Exception) {
                0
            }
        }
    }

    /**
     * 格式化文件大小
     */
    fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
            else -> "${size / (1024 * 1024 * 1024)} GB"
        }
    }

    /**
     * 格式化日期
     */
    fun formatDate(timestamp: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return format.format(Date(timestamp))
    }
}
