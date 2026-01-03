package com.tchat.wanxiaot.update

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

object ApkInstaller {
    private const val TAG = "ApkInstaller"

    /**
     * 安装APK
     * @param activity 当前Activity
     * @param apkFile APK文件
     */
    fun installApk(activity: Activity, apkFile: File) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

            val apkUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Android 7.0+ 使用FileProvider
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                FileProvider.getUriForFile(
                    activity,
                    "${activity.packageName}.fileprovider",
                    apkFile
                )
            } else {
                Uri.fromFile(apkFile)
            }

            intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
            activity.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Install APK failed", e)
        }
    }
}
