package com.tchat.wanxiaot.junglehelper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Base64
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tchat.network.ocr.VisionOcrService
import com.tchat.wanxiaot.R
import com.tchat.wanxiaot.ocr.OcrCredentialExtractor
import com.tchat.wanxiaot.ocr.OcrResultBus
import com.tchat.wanxiaot.settings.OcrModel
import com.tchat.wanxiaot.settings.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.math.abs

/**
 * 打野助手悬浮窗口服务
 * 管理ImGui悬浮窗口的生命周期
 */
class JungleHelperService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "jungle_helper_channel"

        const val ACTION_SHOW = "com.tchat.wanxiaot.junglehelper.SHOW"
        const val ACTION_HIDE = "com.tchat.wanxiaot.junglehelper.HIDE"
        const val ACTION_TOGGLE = "com.tchat.wanxiaot.junglehelper.TOGGLE"
        const val ACTION_STOP = "com.tchat.wanxiaot.junglehelper.STOP"
        const val ACTION_OCR_SELECT = "com.tchat.wanxiaot.junglehelper.OCR_SELECT"
        const val ACTION_MEDIA_PROJECTION_RESULT = "com.tchat.wanxiaot.junglehelper.MEDIA_PROJECTION_RESULT"

        const val EXTRA_MEDIA_PROJECTION_RESULT_CODE = "extra_media_projection_result_code"
        const val EXTRA_MEDIA_PROJECTION_DATA = "extra_media_projection_data"
        const val EXTRA_START_OCR_AFTER_PERMISSION = "extra_start_ocr_after_permission"

        var isRunning = false
            private set
    }

    private var windowManager: WindowManager? = null
    private var imguiView: ImGuiSurfaceView? = null
    private var isViewAdded = false
    private var windowParams: WindowManager.LayoutParams? = null
    private val resizeHandler = Handler(Looper.getMainLooper())
    private var resizeRunnable: Runnable? = null
    private var ocrPollRunnable: Runnable? = null
    private var autoResizeFastUntilUptimeMs = 0L

    private lateinit var settingsManager: SettingsManager
    private val ocrScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ocrSelectionView: OcrSelectionOverlayView? = null
    private var isOcrInProgress = false

    private var mediaProjectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private var mediaProjectionCallback: MediaProjection.Callback? = null
    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null
    private var captureVirtualDisplay: VirtualDisplay? = null
    private var captureImageReader: ImageReader? = null
    private var captureWidth = 0
    private var captureHeight = 0
    private var captureDensityDpi = 0
    private val captureVirtualDisplayCallback = object : VirtualDisplay.Callback() {
        override fun onStopped() {
            releaseCaptureResources()
        }
    }

    // 窗口参数
    private val windowWidth = 400
    private val windowHeight = 500

    // 拖动相关
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        settingsManager = SettingsManager(applicationContext)
        mediaProjectionManager = getSystemService(MediaProjectionManager::class.java)
        captureThread = HandlerThread("ocr_capture").apply { start() }
        captureHandler = Handler(captureThread!!.looper)
        createNotificationChannel()
        startForegroundWithTypes(includeMediaProjection = false)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> showWindow()
            ACTION_HIDE -> hideWindow()
            ACTION_TOGGLE -> toggleWindow()
            ACTION_OCR_SELECT -> beginOcrSelection()
            ACTION_MEDIA_PROJECTION_RESULT -> handleMediaProjectionResult(intent)
            ACTION_STOP -> {
                hideWindow()
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        dismissOcrSelectionOverlay()
        stopMediaProjection()
        ocrScope.cancel()
        captureThread?.quitSafely()
        captureThread = null
        captureHandler = null
        hideWindow()
        isRunning = false
        super.onDestroy()
    }

    private fun showWindow() {
        if (isViewAdded) return

        val density = resources.displayMetrics.density
        val params = WindowManager.LayoutParams(
            (windowWidth * density).toInt(),
            (windowHeight * density).toInt(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }

        imguiView = ImGuiSurfaceView(this).apply {
            onRequestClose = {
                hideWindow()
            }
            // 添加拖动支持
            setOnTouchListener(createDragTouchListener(params))
        }

        try {
            windowManager?.addView(imguiView, params)
            isViewAdded = true
            windowParams = params
            startAutoResize()
            startOcrPolling()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun hideWindow() {
        if (!isViewAdded) return

        try {
            stopAutoResize()
            stopOcrPolling()
            imguiView?.shutdown()
            windowManager?.removeView(imguiView)
            imguiView = null
            isViewAdded = false
            windowParams = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun toggleWindow() {
        if (isViewAdded) {
            hideWindow()
        } else {
            showWindow()
        }
    }

    private fun beginOcrSelection() {
        if (isOcrInProgress) return

        if (mediaProjection == null) {
            try {
                startActivity(
                    Intent(this, OcrPermissionActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(EXTRA_START_OCR_AFTER_PERMISSION, true)
                )
            } catch (e: Exception) {
                showToast("无法请求录屏权限：${e.message}")
            }
            return
        }

        showOcrSelectionOverlay()
    }

    private fun requestMediaProjectionPermission() {
        if (mediaProjection != null) {
            showToast("已获得录屏权限")
            return
        }

        try {
            startActivity(
                Intent(this, OcrPermissionActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(EXTRA_START_OCR_AFTER_PERMISSION, false)
            )
        } catch (e: Exception) {
            showToast("无法请求录屏权限：${e.message}")
        }
    }

    private fun handleMediaProjectionResult(intent: Intent) {
        val resultCode = intent.getIntExtra(EXTRA_MEDIA_PROJECTION_RESULT_CODE, android.app.Activity.RESULT_CANCELED)
        val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_MEDIA_PROJECTION_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_MEDIA_PROJECTION_DATA)
        }
        val startAfter = intent.getBooleanExtra(EXTRA_START_OCR_AFTER_PERMISSION, false)

        if (resultCode != android.app.Activity.RESULT_OK || data == null) {
            showToast("录屏权限被拒绝")
            return
        }

        val mgr = mediaProjectionManager
        if (mgr == null) {
            showToast("无法获取 MediaProjectionManager")
            return
        }

        stopMediaProjection()

        // Android 14+ / targetSdk 34+：在获取 MediaProjection 之前就必须处于 mediaProjection 类型的前台服务中
        try {
            startForegroundWithTypes(includeMediaProjection = true)
        } catch (e: SecurityException) {
            showToast("无法启动录屏前台服务：${e.message}")
            startForegroundWithTypes(includeMediaProjection = false)
            return
        } catch (e: Exception) {
            showToast("无法启动录屏前台服务：${e.message}")
            startForegroundWithTypes(includeMediaProjection = false)
            return
        }

        mediaProjection = try {
            mgr.getMediaProjection(resultCode, data)
        } catch (e: SecurityException) {
            showToast("无法获取录屏权限：${e.message}")
            startForegroundWithTypes(includeMediaProjection = false)
            return
        } catch (e: Exception) {
            showToast("无法获取录屏权限：${e.message}")
            startForegroundWithTypes(includeMediaProjection = false)
            return
        }

        val handler = captureHandler
        mediaProjectionCallback = object : MediaProjection.Callback() {
            override fun onStop() {
                stopMediaProjection()
            }
        }.also { callback ->
            mediaProjection?.registerCallback(callback, handler)
        }

        // 预热 VirtualDisplay，避免部分 ROM 在授权后延迟使用导致 token 过期，
        // 同时确保后续截图不需要重复 createVirtualDisplay（某些系统会拒绝重复调用）。
        try {
            ensureCaptureResources(getRealDisplayMetrics())
        } catch (e: SecurityException) {
            showToast("录屏初始化失败：${e.message}")
            stopMediaProjection()
            return
        } catch (e: Exception) {
            showToast("录屏初始化失败：${e.message}")
            stopMediaProjection()
            return
        }

        showToast("已授权录屏权限")
        if (startAfter) {
            beginOcrSelection()
        }
    }

    private fun showOcrSelectionOverlay() {
        if (ocrSelectionView != null) return
        val wm = windowManager ?: return

        val view = OcrSelectionOverlayView(
            context = this,
            onSelected = { rect ->
                dismissOcrSelectionOverlay()
                startOcrForRect(rect)
            },
            onCancel = {
                dismissOcrSelectionOverlay()
            }
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        try {
            wm.addView(view, params)
            ocrSelectionView = view
        } catch (e: Exception) {
            showToast("无法显示 OCR 框选层：${e.message}")
        }
    }

    private fun dismissOcrSelectionOverlay() {
        val view = ocrSelectionView ?: return
        ocrSelectionView = null
        try {
            windowManager?.removeView(view)
        } catch (_: Exception) {
            // ignore
        }
    }

    private fun stopMediaProjection() {
        releaseCaptureResources()

        try {
            mediaProjectionCallback?.let { callback ->
                mediaProjection?.unregisterCallback(callback)
            }
        } catch (_: Exception) {
            // ignore
        } finally {
            mediaProjectionCallback = null
        }

        try {
            mediaProjection?.stop()
        } catch (_: Exception) {
            // ignore
        } finally {
            mediaProjection = null
        }

        startForegroundWithTypes(includeMediaProjection = false)
    }

    private fun startForegroundWithTypes(includeMediaProjection: Boolean) {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val types = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                (if (includeMediaProjection) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0)
            startForeground(NOTIFICATION_ID, notification, types)
            return
        }

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun releaseCaptureResources() {
        val handler = captureHandler
        val reader = captureImageReader
        if (handler != null && reader != null) {
            try {
                handler.post { runCatching { reader.setOnImageAvailableListener(null, null) } }
            } catch (_: Exception) {
                // ignore
            }
        } else if (reader != null) {
            try {
                reader.setOnImageAvailableListener(null, null)
            } catch (_: Exception) {
                // ignore
            }
        }

        try {
            captureVirtualDisplay?.release()
        } catch (_: Exception) {
            // ignore
        } finally {
            captureVirtualDisplay = null
        }

        try {
            captureImageReader?.close()
        } catch (_: Exception) {
            // ignore
        } finally {
            captureImageReader = null
            captureWidth = 0
            captureHeight = 0
            captureDensityDpi = 0
        }
    }

    private fun ensureCaptureResources(dm: DisplayMetrics) {
        val projection = mediaProjection ?: throw IllegalStateException("MediaProjection not ready")
        val handler = captureHandler ?: throw IllegalStateException("Capture thread not ready")

        val width = dm.widthPixels.coerceAtLeast(1)
        val height = dm.heightPixels.coerceAtLeast(1)
        val densityDpi = dm.densityDpi.coerceAtLeast(1)

        val existingDisplay = captureVirtualDisplay
        val existingReader = captureImageReader
        if (existingDisplay != null &&
            existingReader != null &&
            captureWidth == width &&
            captureHeight == height &&
            captureDensityDpi == densityDpi
        ) {
            return
        }

        if (existingDisplay != null) {
            val newReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            try {
                existingDisplay.setSurface(newReader.surface)
                try {
                    existingDisplay.resize(width, height, densityDpi)
                } catch (_: Throwable) {
                    // ignore: resize isn't available on all API/ROMs
                }
                captureImageReader?.close()
                captureImageReader = newReader
                captureWidth = width
                captureHeight = height
                captureDensityDpi = densityDpi
                return
            } catch (e: Exception) {
                newReader.close()
                throw e
            }
        }

        releaseCaptureResources()

        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        val display = projection.createVirtualDisplay(
            "ocr_capture",
            width,
            height,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            captureVirtualDisplayCallback,
            handler
        )

        if (display == null) {
            reader.close()
            throw IllegalStateException("createVirtualDisplay returned null")
        }

        captureVirtualDisplay = display
        captureImageReader = reader
        captureWidth = width
        captureHeight = height
        captureDensityDpi = densityDpi
    }

    private fun startOcrForRect(rect: Rect) {
        if (isOcrInProgress) return
        isOcrInProgress = true
        showToast("正在识别...")

        val overlayView = imguiView
        val overlayOldAlpha = overlayView?.alpha ?: 1f
        val overlayOldVisibility = overlayView?.visibility ?: View.VISIBLE

        ocrScope.launch {
            try {
                resizeHandler.post {
                    overlayView?.alpha = 0f
                    overlayView?.visibility = View.INVISIBLE
                }

                delay(80L)

                val fullBitmap = captureScreenBitmap()
                if (fullBitmap == null) {
                    showToast("截图失败（请确认已授权录屏权限）")
                    return@launch
                }

                val cropped = try {
                    cropBitmap(fullBitmap, rect)
                } finally {
                    fullBitmap.recycle()
                }

                val ocrText = try {
                    recognizeText(cropped)
                } finally {
                    cropped.recycle()
                }

                val extracted = OcrCredentialExtractor.extract(ocrText)
                OcrResultBus.tryEmit(extracted)

                val clipText = buildString {
                    extracted.baseUrl?.let {
                        append("URL: ")
                        append(it)
                        append('\n')
                    }
                    extracted.apiKey?.let {
                        append("API Key: ")
                        append(it)
                        append('\n')
                    }
                    if (isEmpty()) {
                        append(extracted.rawText)
                    }
                }.trim()

                copyToClipboard("OCR", clipText)

                val maskedKey = extracted.apiKey?.let { maskApiKey(it) }
                val toastText = when {
                    extracted.baseUrl != null && maskedKey != null -> "识别完成，已复制\nURL: ${extracted.baseUrl}\nKey: $maskedKey"
                    extracted.baseUrl != null -> "识别完成，已复制\nURL: ${extracted.baseUrl}"
                    maskedKey != null -> "识别完成，已复制\nKey: $maskedKey"
                    else -> "识别完成（未检测到 URL/Key，已复制原文）"
                }
                showToast(toastText)
            } catch (e: Exception) {
                showToast("OCR 失败：${e.message}")
            } finally {
                resizeHandler.post {
                    overlayView?.alpha = overlayOldAlpha
                    overlayView?.visibility = overlayOldVisibility
                }
                isOcrInProgress = false
            }
        }
    }

    private fun cropBitmap(bitmap: Bitmap, rect: Rect): Bitmap {
        val left = rect.left.coerceIn(0, bitmap.width - 1)
        val top = rect.top.coerceIn(0, bitmap.height - 1)
        val right = rect.right.coerceIn(left + 1, bitmap.width)
        val bottom = rect.bottom.coerceIn(top + 1, bitmap.height)
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    private suspend fun recognizeText(bitmap: Bitmap): String {
        val ocrSettings = settingsManager.settings.value.ocrSettings
        val model = OcrModel.fromName(ocrSettings.model)

        return when (model) {
            OcrModel.MLKIT_LATIN -> recognizeWithMlKit(bitmap, TextRecognizerOptions.DEFAULT_OPTIONS)
            OcrModel.MLKIT_CHINESE -> recognizeWithMlKit(bitmap, ChineseTextRecognizerOptions.Builder().build())
            OcrModel.AI_VISION -> recognizeWithAiVision(bitmap, ocrSettings)
        }
    }

    private fun recognizeWithMlKit(bitmap: Bitmap, options: Any): String {
        val recognizer = when (options) {
            is TextRecognizerOptions -> TextRecognition.getClient(options)
            is ChineseTextRecognizerOptions -> TextRecognition.getClient(options)
            else -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        }

        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = Tasks.await(recognizer.process(image))
            result.text
        } finally {
            recognizer.close()
        }
    }

    private suspend fun recognizeWithAiVision(
        bitmap: Bitmap,
        ocrSettings: com.tchat.wanxiaot.settings.OcrSettings
    ): String {
        val settings = settingsManager.settings.value
        val provider = settings.providers.find { it.id == ocrSettings.aiProviderId }
            ?: throw IllegalStateException("未找到配置的 AI 提供商，请在 OCR 设置中选择提供商")

        // 将 Bitmap 转换为 Base64
        val base64Image = bitmapToBase64(bitmap)

        // 创建 VisionOcrService
        val visionService = VisionOcrService(
            providerType = provider.providerType.name.lowercase(),
            apiKey = provider.apiKey,
            baseUrl = provider.endpoint,
            model = ocrSettings.aiModel.ifEmpty { provider.selectedModel }
        )

        return withContext(Dispatchers.IO) {
            visionService.recognizeText(
                imageBase64 = base64Image,
                mimeType = "image/png",
                prompt = ocrSettings.customPrompt
            )
        }
    }

    /**
     * 将 Bitmap 转换为 Base64 字符串
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    private suspend fun captureScreenBitmap(): Bitmap? {
        if (mediaProjection == null) return null
        val handler = captureHandler ?: return null

        val dm = getRealDisplayMetrics()
        ensureCaptureResources(dm)
        val reader = captureImageReader ?: return null

        return suspendCancellableCoroutine { cont ->
            var done = false

            fun cleanup() {
                try {
                    reader.setOnImageAvailableListener(null, null)
                } catch (_: Exception) {
                    // ignore
                }
            }

            val timeoutRunnable = Runnable {
                if (done) return@Runnable
                done = true
                cleanup()
                cont.resume(null)
            }

            handler.postDelayed(timeoutRunnable, 1500L)

            // 清理旧帧，避免拿到延迟的画面（比如还包含悬浮窗）
            try {
                while (true) {
                    val old = reader.acquireLatestImage() ?: break
                    old.close()
                }
            } catch (_: Exception) {
                // ignore
            }

            reader.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                if (done) {
                    image.close()
                    return@setOnImageAvailableListener
                }
                done = true
                handler.removeCallbacks(timeoutRunnable)
                try {
                    cont.resume(imageToBitmap(image))
                } catch (e: Exception) {
                    cont.resume(null)
                } finally {
                    image.close()
                    cleanup()
                }
            }, handler)

            cont.invokeOnCancellation {
                if (done) return@invokeOnCancellation
                done = true
                handler.removeCallbacks(timeoutRunnable)
                cleanup()
            }
        }
    }

    private fun imageToBitmap(image: android.media.Image): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer.apply { rewind() }
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val paddedBitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        paddedBitmap.copyPixelsFromBuffer(buffer)

        return Bitmap.createBitmap(paddedBitmap, 0, 0, image.width, image.height).also {
            paddedBitmap.recycle()
        }
    }

    private fun getRealDisplayMetrics(): DisplayMetrics {
        val dm = DisplayMetrics()
        val wm = windowManager ?: (getSystemService(Context.WINDOW_SERVICE) as WindowManager)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            dm.widthPixels = bounds.width()
            dm.heightPixels = bounds.height()
            dm.densityDpi = resources.displayMetrics.densityDpi
        } else {
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
        }
        if (dm.widthPixels <= 0 || dm.heightPixels <= 0) {
            val fallback = resources.displayMetrics
            dm.widthPixels = fallback.widthPixels
            dm.heightPixels = fallback.heightPixels
        }
        if (dm.densityDpi == 0) {
            dm.densityDpi = resources.displayMetrics.densityDpi
        }
        return dm
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    private fun maskApiKey(value: String): String {
        if (value.length <= 10) return value
        return value.take(4) + "…" + value.takeLast(4)
    }

    private fun showToast(message: String) {
        resizeHandler.post {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun createDragTouchListener(params: WindowManager.LayoutParams): View.OnTouchListener {
        var isDragging = false
        var initialDownY = 0f

        val density = resources.displayMetrics.density
        val dragHandleHeightPx = 28f * density
        val dragStartThresholdPx = 8f * density

        return View.OnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    initialDownY = event.y
                    isDragging = false
                    boostAutoResize(kickNow = true)
                    false // 让事件继续传递给ImGui
                }
                MotionEvent.ACTION_MOVE -> {
                    boostAutoResize(kickNow = false)
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY

                    // 如果移动超过阈值，开始拖动
                    if (!isDragging && (abs(dx) > dragStartThresholdPx || abs(dy) > dragStartThresholdPx)) {
                        // 仅允许从标题栏区域拖动，避免和滑动条等控件手势冲突
                        if (initialDownY < dragHandleHeightPx) {
                            isDragging = true
                        }
                    }

                    if (isDragging) {
                        params.x = initialX + dx.toInt()
                        params.y = initialY + dy.toInt()
                        windowManager?.updateViewLayout(view, params)
                        true // 消费事件，不传递给ImGui
                    } else {
                        false // 让ImGui处理
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    false
                }
                else -> false
            }
        }
    }

    private fun boostAutoResize(kickNow: Boolean) {
        autoResizeFastUntilUptimeMs = SystemClock.uptimeMillis() + 600L
        if (!kickNow) return
        val runnable = resizeRunnable ?: return
        resizeHandler.removeCallbacks(runnable)
        resizeHandler.post(runnable)
    }

    private fun startAutoResize() {
        stopAutoResize()
        val view = imguiView ?: return
        val params = windowParams ?: return

        val density = resources.displayMetrics.density
        val minWidthPx = (200 * density).toInt()
        val minHeightPx = (160 * density).toInt()
        val paddingPx = (16 * density).toInt()
        val maxWidthPx = (resources.displayMetrics.widthPixels * 0.9f).toInt()
        val maxHeightPx = (resources.displayMetrics.heightPixels * 0.9f).toInt()

        val refreshRateHz = (view.display?.refreshRate ?: run {
            @Suppress("DEPRECATION")
            windowManager?.defaultDisplay?.refreshRate
        } ?: 60f).takeIf { it.isFinite() && it >= 30f } ?: 60f
        // 144Hz ≈ 7ms, 120Hz ≈ 8ms, 90Hz ≈ 11ms, 60Hz ≈ 16ms
        val fastPollMs = ((1000f / refreshRateHz) + 0.5f).toLong().coerceIn(7L, 16L)
        val slowPollMs = 200L
        val stableThresholdMs = 500L
        val stableThreshold =
            (((stableThresholdMs + fastPollMs - 1) / fastPollMs).toInt()).coerceAtLeast(30)

        var stableCount = 0
        resizeRunnable = object : Runnable {
            override fun run() {
                if (!isViewAdded || imguiView == null || windowParams == null) return

                val packedSize = try {
                    ImGuiBridge.nativeGetMainWindowSize()
                } catch (_: Exception) {
                    0L
                }

                val contentWidthPx = (packedSize ushr 32).toInt()
                val contentHeightPx = (packedSize and 0xFFFFFFFFL).toInt()

                var layoutChanged = false
                if (contentWidthPx > 0 && contentHeightPx > 0) {
                    val targetWidthPx = (contentWidthPx + paddingPx)
                        .coerceAtLeast(minWidthPx)
                        .coerceAtMost(maxWidthPx)
                    val targetHeightPx = (contentHeightPx + paddingPx)
                        .coerceAtLeast(minHeightPx)
                        .coerceAtMost(maxHeightPx)

                    if (params.width != targetWidthPx || params.height != targetHeightPx) {
                        params.width = targetWidthPx
                        params.height = targetHeightPx
                        try {
                            windowManager?.updateViewLayout(view, params)
                        } catch (_: Exception) {
                            // ignore
                        }
                        stableCount = 0
                        layoutChanged = true
                    } else {
                        stableCount++
                    }
                } else {
                    stableCount = 0
                }

                // 尺寸变化时提高轮询频率（拖动右下角缩放更顺滑），稳定后降频以减少开销
                val forceFast = SystemClock.uptimeMillis() < autoResizeFastUntilUptimeMs
                val nextDelayMs =
                    if (forceFast || layoutChanged || stableCount < stableThreshold) fastPollMs else slowPollMs
                resizeHandler.postDelayed(this, nextDelayMs)
            }
        }

        resizeHandler.post(resizeRunnable!!)
    }

    private fun stopAutoResize() {
        resizeRunnable?.let { resizeHandler.removeCallbacks(it) }
        resizeRunnable = null
    }

    private fun startOcrPolling() {
        stopOcrPolling()
        if (!ImGuiBridge.isLibraryLoaded) return

        ocrPollRunnable = object : Runnable {
            override fun run() {
                if (!isViewAdded || imguiView == null) return

                // 检查权限申请请求
                val permissionRequested = try {
                    ImGuiBridge.nativeConsumePermissionRequest()
                } catch (_: Exception) {
                    false
                }
                if (permissionRequested) {
                    requestMediaProjectionPermission()
                }

                if (!isOcrInProgress && ocrSelectionView == null) {
                    val requested = try {
                        ImGuiBridge.nativeConsumeOcrRequest()
                    } catch (_: Exception) {
                        false
                    }
                    if (requested) {
                        beginOcrSelection()
                    }
                }

                resizeHandler.postDelayed(this, 200L)
            }
        }

        resizeHandler.post(ocrPollRunnable!!)
    }

    private fun stopOcrPolling() {
        ocrPollRunnable?.let { resizeHandler.removeCallbacks(it) }
        ocrPollRunnable = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "打野助手",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "打野助手悬浮窗口服务"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        // 停止服务的PendingIntent
        val stopIntent = Intent(this, JungleHelperService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 切换显示的PendingIntent
        val toggleIntent = Intent(this, JungleHelperService::class.java).apply {
            action = ACTION_TOGGLE
        }
        val togglePendingIntent = PendingIntent.getService(
            this, 1, toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val ocrIntent = Intent(this, JungleHelperService::class.java).apply {
            action = ACTION_OCR_SELECT
        }
        val ocrPendingIntent = PendingIntent.getService(
            this, 2, ocrIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("打野助手")
            .setContentText("正在运行")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(0, "显示/隐藏", togglePendingIntent)
            .addAction(0, "OCR", ocrPendingIntent)
            .addAction(0, "停止", stopPendingIntent)
            .build()
    }
}
