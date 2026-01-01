package com.tchat.wanxiaot.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * 存储权限管理工具
 * 
 * 处理不同 Android 版本的存储权限：
 * - Android 11+ (API 30+): 需要 MANAGE_EXTERNAL_STORAGE 或跳转系统设置
 * - Android 10 及以下: 使用传统的 READ/WRITE_EXTERNAL_STORAGE 权限
 */
object StoragePermissionHelper {
    
    /**
     * 检查是否有存储权限
     */
    fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+
            Environment.isExternalStorageManager()
        } else {
            // Android 10 及以下
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 获取需要请求的权限列表（仅适用于 Android 10 及以下）
     */
    fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 不需要传统权限，需要通过设置页面授权
            emptyArray()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10 只需要读取权限
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            // Android 9 及以下需要读写权限
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }

    /**
     * 创建跳转到"所有文件访问权限"设置页面的 Intent（Android 11+）
     */
    fun createManageStorageIntent(context: Context): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            null
        }
    }

    /**
     * 是否需要跳转到设置页面授权（Android 11+）
     */
    fun needsSettingsPage(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }

    /**
     * 获取权限状态描述
     */
    fun getPermissionStatusText(context: Context): String {
        return when {
            hasStoragePermission(context) -> "已授权"
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> "需要在设置中授权"
            else -> "未授权"
        }
    }
}
