package com.tchat.wanxiaot.junglehelper

import android.content.res.AssetManager
import android.util.Log

/**
 * ImGui JNI桥接类
 * 提供与C++层ImGui的通信接口
 */
object ImGuiBridge {

    private const val TAG = "ImGuiBridge"
    var isLibraryLoaded = false
        private set

    init {
        try {
            System.loadLibrary("junglehelper")
            isLibraryLoaded = true
            Log.i(TAG, "Native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library: ${e.message}")
            isLibraryLoaded = false
        }
    }

    /**
     * 初始化ImGui
     * @param width 显示宽度
     * @param height 显示高度
     * @param density 屏幕密度
     */
    external fun nativeInit(width: Int, height: Int, density: Float)

    /**
     * 设置AssetManager（用于从assets加载字体等资源）
     * 需在nativeInit之前调用。
     */
    external fun nativeSetAssetManager(assetManager: AssetManager)

    /**
     * 关闭ImGui
     */
    external fun nativeShutdown()

    /**
     * 调整显示大小
     */
    external fun nativeResize(width: Int, height: Int)

    /**
     * 渲染一帧
     */
    external fun nativeRender()

    /**
     * 处理触摸事件
     * @param action 触摸动作 (ACTION_DOWN=0, ACTION_UP=1, ACTION_MOVE=2)
     * @param x X坐标
     * @param y Y坐标
     * @param pointerId 触摸点ID
     */
    external fun nativeOnTouch(action: Int, x: Float, y: Float, pointerId: Int)

    /**
     * 检查ImGui是否想要捕获鼠标事件
     */
    external fun nativeWantCaptureMouse(): Boolean

    /**
     * 消费一次 OCR 触发请求（来自 ImGui 按钮）。
     * @return true 表示用户在 ImGui 中点击了 OCR 按钮
     */
    external fun nativeConsumeOcrRequest(): Boolean

    /**
     * 消费一次权限申请请求（来自 ImGui 按钮）。
     * @return true 表示用户在 ImGui 中点击了申请权限按钮
     */
    external fun nativeConsumePermissionRequest(): Boolean

    /**
     * 设置UI可见性
     */
    external fun nativeSetVisible(visible: Boolean)

    /**
     * 获取UI可见性
     */
    external fun nativeIsVisible(): Boolean

    /**
     * 获取主窗口像素尺寸（高32位=width，低32位=height）
     * 若尚未初始化/渲染过将返回0。
     */
    external fun nativeGetMainWindowSize(): Long
}
