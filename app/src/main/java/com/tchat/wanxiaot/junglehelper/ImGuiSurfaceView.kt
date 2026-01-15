package com.tchat.wanxiaot.junglehelper

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * ImGui渲染视图
 * 使用OpenGL ES 3.0渲染ImGui界面
 */
class ImGuiSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    companion object {
        private const val TAG = "ImGuiSurfaceView"
    }

    private var initialized = false
    private val density: Float = context.resources.displayMetrics.density

    var onRequestClose: (() -> Unit)? = null

    @Volatile
    private var closeRequested = false

    init {
        if (ImGuiBridge.isLibraryLoaded) {
            try {
                ImGuiBridge.nativeSetAssetManager(context.assets)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set AssetManager: ${e.message}")
            }
            // 设置OpenGL ES 3.0
            setEGLContextClientVersion(3)

            // 设置透明背景
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            holder.setFormat(PixelFormat.TRANSLUCENT)
            setZOrderOnTop(true)

            // 设置渲染器
            setRenderer(ImGuiRenderer())

            // 连续渲染模式（用于动画和计时器更新）
            renderMode = RENDERMODE_CONTINUOUSLY
        } else {
            Log.e(TAG, "Native library not loaded, ImGuiSurfaceView will not render")
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!initialized || !ImGuiBridge.isLibraryLoaded) return false

        val action = event.actionMasked
        val x = event.x
        val y = event.y
        val pointerId = event.getPointerId(event.actionIndex)

        // 将触摸事件发送到ImGui
        queueEvent {
            try {
                ImGuiBridge.nativeOnTouch(action, x, y, pointerId)
            } catch (e: Exception) {
                Log.e(TAG, "Error in nativeOnTouch: ${e.message}")
            }
        }

        // 如果ImGui想要捕获鼠标，则消费事件
        return try {
            ImGuiBridge.nativeWantCaptureMouse() || super.onTouchEvent(event)
        } catch (e: Exception) {
            Log.e(TAG, "Error in nativeWantCaptureMouse: ${e.message}")
            super.onTouchEvent(event)
        }
    }

    fun shutdown() {
        if (!ImGuiBridge.isLibraryLoaded) return
        closeRequested = true
        queueEvent {
            try {
                ImGuiBridge.nativeShutdown()
            } catch (e: Exception) {
                Log.e(TAG, "Error in nativeShutdown: ${e.message}")
            }
            initialized = false
        }
    }

    private inner class ImGuiRenderer : Renderer {
        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            // Surface创建时不初始化，等待onSurfaceChanged获取正确的尺寸
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            if (!ImGuiBridge.isLibraryLoaded) return

            try {
                if (!initialized) {
                    closeRequested = false
                    ImGuiBridge.nativeInit(width, height, density)
                    initialized = true
                    Log.i(TAG, "ImGui initialized: ${width}x${height}, density=$density")
                } else {
                    ImGuiBridge.nativeResize(width, height)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in surface changed: ${e.message}")
            }
        }

        override fun onDrawFrame(gl: GL10?) {
            if (!initialized || !ImGuiBridge.isLibraryLoaded) return

            try {
                ImGuiBridge.nativeRender()
                if (!closeRequested) {
                    val isVisible = try {
                        ImGuiBridge.nativeIsVisible()
                    } catch (_: Exception) {
                        true
                    }
                    if (!isVisible) {
                        closeRequested = true
                        post { onRequestClose?.invoke() }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in nativeRender: ${e.message}")
            }
        }
    }
}
