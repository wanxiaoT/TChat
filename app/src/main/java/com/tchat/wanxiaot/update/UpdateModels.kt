package com.tchat.wanxiaot.update

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrls: DownloadUrls,
    val releaseNotes: String,
    val minSupportedVersion: Int,
    val forceUpdate: Boolean,
    val fileSize: Long,
    val publishTime: String
)

data class DownloadUrls(
    val china: String,
    val global: String
)

data class UpdateResponse(
    val success: Boolean,
    val hasUpdate: Boolean,
    val data: UpdateInfo?
)

enum class DownloadSource {
    CHINA,
    GLOBAL
}
