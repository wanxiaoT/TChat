package com.tchat.wanxiaot.junglehelper

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * 打野助手管理器
 * 提供启动/停止悬浮窗口服务的API
 */
object JungleHelperManager {

    /**
     * 检查是否有悬浮窗口权限
     */
    fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * 请求悬浮窗口权限
     * 会打开系统设置页面
     */
    fun requestOverlayPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    /**
     * 启动打野助手服务并显示悬浮窗口
     * @return true 如果成功启动，false 如果没有权限
     */
    fun start(context: Context): Boolean {
        if (!hasOverlayPermission(context)) {
            return false
        }

        val intent = Intent(context, JungleHelperService::class.java).apply {
            action = JungleHelperService.ACTION_SHOW
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }

        return true
    }

    /**
     * 停止打野助手服务
     */
    fun stop(context: Context) {
        val intent = Intent(context, JungleHelperService::class.java).apply {
            action = JungleHelperService.ACTION_STOP
        }
        context.startService(intent)
    }

    /**
     * 切换悬浮窗口显示/隐藏
     */
    fun toggle(context: Context): Boolean {
        if (!hasOverlayPermission(context)) {
            return false
        }

        val intent = Intent(context, JungleHelperService::class.java).apply {
            action = JungleHelperService.ACTION_TOGGLE
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }

        return true
    }

    /**
     * 检查服务是否正在运行
     */
    fun isRunning(): Boolean {
        return JungleHelperService.isRunning
    }
}
