package com.tchat.wanxiaot.update

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class UpdateManager(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "UpdateManager"
        private const val UPDATE_API_URL = "https://tchatupdate.wanxiaot.com/api/check_update.php"
        private const val DOWNLOAD_DIR = "downloads"
        private const val APK_FILE_NAME = "TChat-update.apk"
    }

    /**
     * 检查更新
     * @param currentVersionCode 当前应用版本号
     * @return UpdateResponse 更新信息响应
     */
    suspend fun checkUpdate(currentVersionCode: Int): Result<UpdateResponse> = withContext(Dispatchers.IO) {
        try {
            val url = "$UPDATE_API_URL?version=$currentVersionCode"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val responseBody = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))

            val jsonObject = JSONObject(responseBody)
            val success = jsonObject.getBoolean("success")

            if (!success) {
                val message = jsonObject.optString("message", "Unknown error")
                return@withContext Result.failure(Exception(message))
            }

            val hasUpdate = jsonObject.getBoolean("hasUpdate")
            val data = jsonObject.optJSONObject("data")

            val updateInfo = data?.let {
                val downloadUrlsJson = it.getJSONObject("downloadUrls")
                UpdateInfo(
                    versionCode = it.getInt("versionCode"),
                    versionName = it.getString("versionName"),
                    downloadUrls = DownloadUrls(
                        china = downloadUrlsJson.getString("china"),
                        global = downloadUrlsJson.getString("global")
                    ),
                    releaseNotes = it.getString("releaseNotes"),
                    minSupportedVersion = it.getInt("minSupportedVersion"),
                    forceUpdate = it.getBoolean("forceUpdate"),
                    fileSize = it.getLong("fileSize"),
                    publishTime = it.getString("publishTime")
                )
            }

            Result.success(UpdateResponse(success, hasUpdate, updateInfo))
        } catch (e: Exception) {
            Log.e(TAG, "Check update failed", e)
            Result.failure(e)
        }
    }

    /**
     * 下载APK
     * @param url 下载链接
     * @param onProgress 下载进度回调 (已下载字节, 总字节)
     * @return 下载的APK文件
     */
    suspend fun downloadApk(
        url: String,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Download failed: HTTP ${response.code}"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Empty response body"))
            val totalBytes = body.contentLength()

            val downloadDir = File(context.getExternalFilesDir(null), DOWNLOAD_DIR)
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }

            val apkFile = File(downloadDir, APK_FILE_NAME)
            if (apkFile.exists()) {
                apkFile.delete()
            }

            var downloadedBytes = 0L

            body.byteStream().use { inputStream ->
                FileOutputStream(apkFile).use { outputStream ->
                    val buffer = ByteArray(8192)
                    var bytes: Int

                    while (inputStream.read(buffer).also { bytes = it } != -1) {
                        outputStream.write(buffer, 0, bytes)
                        downloadedBytes += bytes
                        onProgress(downloadedBytes, totalBytes)
                    }
                }
            }

            Result.success(apkFile)
        } catch (e: Exception) {
            Log.e(TAG, "Download APK failed", e)
            Result.failure(e)
        }
    }

    /**
     * 清理下载的APK文件
     */
    fun cleanupDownloads() {
        try {
            val downloadDir = File(context.getExternalFilesDir(null), DOWNLOAD_DIR)
            downloadDir.listFiles()?.forEach { it.delete() }
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup downloads failed", e)
        }
    }
}
