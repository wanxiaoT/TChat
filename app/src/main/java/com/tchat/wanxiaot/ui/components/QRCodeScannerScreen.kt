package com.tchat.wanxiaot.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.tchat.wanxiaot.settings.ProviderConfig
import com.tchat.wanxiaot.util.QRCodeGenerator
import java.util.concurrent.Executors

private const val QR_PROVIDER_SCANNER_TAG = "QRCodeScanner"

/**
 * 扫码导入服务商配置页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRCodeScannerScreen(
    onBack: () -> Unit,
    onProviderScanned: (ProviderConfig) -> Unit
) {
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    var isScanning by remember { mutableStateOf(true) }

    // 权限请求
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            Toast.makeText(context, "需要相机权限才能扫描二维码", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // 处理系统返回键
    BackHandler {
        onBack()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (!hasCameraPermission) {
            AppPageBackground()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                AppEmptyState(
                    title = "需要相机权限",
                    description = "允许相机访问后，才能扫描供应商配置二维码。",
                    icon = Icons.Default.Lock,
                    action = {
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("授予权限")
                        }
                    }
                )
            }
        } else {
            CameraPreviewWithScanner(
                isScanning = isScanning,
                onCodeScanned = { code ->
                    if (isScanning) {
                        isScanning = false
                        val provider = QRCodeGenerator.jsonToProvider(code)
                        if (provider != null) {
                            onProviderScanned(provider)
                        } else {
                            Toast.makeText(context, "无效的配置二维码", Toast.LENGTH_SHORT).show()
                            isScanning = true
                        }
                    }
                }
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 18.dp)
            ) {
                ScannerTopBar(
                    title = "扫码导入",
                    subtitle = "识别供应商配置二维码",
                    onBack = onBack,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 6.dp)
                )

                ScannerFrame(
                    modifier = Modifier.align(Alignment.Center)
                )

                ScannerHintCard(
                    title = if (isScanning) "对准二维码" else "正在解析",
                    description = "保持画面稳定，将二维码放入框内即可自动识别。",
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                ) {
                    AppPill(text = if (isScanning) "扫描中" else "处理中")
                }
            }
        }
    }
}

@Composable
private fun CameraPreviewWithScanner(
    isScanning: Boolean,
    onCodeScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    Size(1280, 720),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                                )
                            )
                            .build()
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    if (isScanning) {
                        val buffer = imageProxy.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)

                        val source = PlanarYUVLuminanceSource(
                            bytes,
                            imageProxy.width,
                            imageProxy.height,
                            0, 0,
                            imageProxy.width,
                            imageProxy.height,
                            false
                        )

                        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

                        try {
                            val reader = MultiFormatReader().apply {
                                setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
                            }
                            val result = reader.decode(binaryBitmap)
                            onCodeScanned(result.text)
                        } catch (e: NotFoundException) {
                            // 未找到二维码，继续扫描
                        } catch (e: Exception) {
                            // 其他错误
                        }
                    }
                    imageProxy.close()
                }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    Log.e(QR_PROVIDER_SCANNER_TAG, "Failed to bind camera use cases", e)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

