package com.tchat.wanxiaot.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.tchat.wanxiaot.util.ExportData
import com.tchat.wanxiaot.util.QRCodeUtils
import java.util.concurrent.Executors

/**
 * 通用二维码扫描器（用于导入功能）
 * 支持扫描相机二维码或从相册选择图片
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRCodeScannerForImport(
    onBack: () -> Unit,
    onDataScanned: (ExportData) -> Unit,
    password: String? = null
) {
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    var isScanning by remember { mutableStateOf(true) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var scannedEncryptedData by remember { mutableStateOf<String?>(null) }
    var inputPassword by remember { mutableStateOf("") }

    // 权限请求
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            Toast.makeText(context, "需要相机权限才能扫描二维码", Toast.LENGTH_SHORT).show()
        }
    }

    // 从相册选择图片
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                val qrContent = QRCodeUtils.decodeQRCode(bitmap)
                if (qrContent != null) {
                    val trimmed = qrContent.trim()
                    val exportData = runCatching { ExportData.fromJson(trimmed) }.getOrNull()
                    if (exportData != null) {
                        onDataScanned(exportData)
                    } else if (password != null) {
                        val decrypted = runCatching { com.tchat.wanxiaot.util.EncryptionUtils.decrypt(trimmed, password) }.getOrNull()
                        val decryptedExportData = decrypted?.let { runCatching { ExportData.fromJson(it) }.getOrNull() }
                        if (decryptedExportData != null) {
                            onDataScanned(decryptedExportData)
                        } else {
                            Toast.makeText(context, "无法解析二维码（可能密码错误）", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        scannedEncryptedData = trimmed
                        showPasswordDialog = true
                    }
                } else {
                    Toast.makeText(context, "无法识别二维码", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "读取图片失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    BackHandler {
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("扫描二维码") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                        Icon(
                            Icons.Default.Photo,
                            contentDescription = "从相册选择"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            if (!hasCameraPermission) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("需要相机权限才能扫描二维码")
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("授予权限")
                    }
                }
            } else {
                CameraPreviewWithQRScanner(
                    isScanning = isScanning,
                    onCodeScanned = { code ->
                        if (isScanning) {
                            isScanning = false
                            val trimmed = code.trim()
                            val exportData = runCatching { ExportData.fromJson(trimmed) }.getOrNull()
                            if (exportData != null) {
                                onDataScanned(exportData)
                            } else if (password != null) {
                                val decrypted = runCatching { com.tchat.wanxiaot.util.EncryptionUtils.decrypt(trimmed, password) }.getOrNull()
                                val decryptedExportData = decrypted?.let { runCatching { ExportData.fromJson(it) }.getOrNull() }
                                if (decryptedExportData != null) {
                                    onDataScanned(decryptedExportData)
                                } else {
                                    Toast.makeText(context, "无法解析二维码（可能密码错误）", Toast.LENGTH_SHORT).show()
                                    isScanning = true
                                }
                            } else {
                                scannedEncryptedData = trimmed
                                showPasswordDialog = true
                            }
                        }
                    }
                )

                // 扫描框
                Box(
                    modifier = Modifier
                        .size(250.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Transparent)
                ) {
                    ScannerOverlay()
                }

                Text(
                    text = "将二维码放入框内扫描",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 100.dp)
                )
            }
        }
    }

    // 密码输入对话框
    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = {
                showPasswordDialog = false
                isScanning = true
                scannedEncryptedData = null
            },
            title = { Text("输入解密密码") },
            text = {
                OutlinedTextField(
                    value = inputPassword,
                    onValueChange = { inputPassword = it },
                    label = { Text("密码") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scannedEncryptedData?.let { encrypted ->
                            try {
                                val decrypted = com.tchat.wanxiaot.util.EncryptionUtils.decrypt(encrypted, inputPassword)
                                val exportData = ExportData.fromJson(decrypted)
                                showPasswordDialog = false
                                onDataScanned(exportData)
                            } catch (e: Exception) {
                                Toast.makeText(context, "密码错误或数据损坏", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPasswordDialog = false
                        isScanning = true
                        scannedEncryptedData = null
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun CameraPreviewWithQRScanner(
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
                        processImageProxy(imageProxy, onCodeScanned)
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
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

private fun buildLuminanceSource(imageProxy: ImageProxy): com.google.zxing.LuminanceSource? {
    val width = imageProxy.width
    val height = imageProxy.height
    if (width <= 0 || height <= 0 || imageProxy.planes.isEmpty()) return null

    val yPlane = imageProxy.planes[0]
    val buffer = yPlane.buffer
    val rowStride = yPlane.rowStride
    val pixelStride = yPlane.pixelStride
    if (rowStride <= 0 || pixelStride <= 0) return null

    val yBytes = ByteArray(width * height)

    return try {
        buffer.rewind()

        if (pixelStride == 1 && rowStride == width) {
            if (buffer.remaining() < yBytes.size) return null
            buffer.get(yBytes, 0, yBytes.size)
        } else {
            val rowData = ByteArray(rowStride)
            var outputOffset = 0
            for (row in 0 until height) {
                if (buffer.remaining() < rowStride) return null
                buffer.get(rowData, 0, rowStride)

                if (pixelStride == 1) {
                    System.arraycopy(rowData, 0, yBytes, outputOffset, width)
                    outputOffset += width
                } else {
                    var inputOffset = 0
                    for (col in 0 until width) {
                        yBytes[outputOffset++] = rowData[inputOffset]
                        inputOffset += pixelStride
                    }
                }
            }
        }

        com.google.zxing.PlanarYUVLuminanceSource(
            yBytes,
            width,
            height,
            0,
            0,
            width,
            height,
            false
        )
    } catch (e: Exception) {
        null
    }
}

private fun rotateLuminanceSource(
    source: com.google.zxing.LuminanceSource,
    rotationDegrees: Int
): com.google.zxing.LuminanceSource {
    if (!source.isRotateSupported) return source

    val rotations = when (rotationDegrees) {
        90 -> 3
        180 -> 2
        270 -> 1
        else -> 0
    }

    var rotatedSource = source
    repeat(rotations) {
        rotatedSource = rotatedSource.rotateCounterClockwise()
    }
    return rotatedSource
}

private fun processImageProxy(imageProxy: ImageProxy, onCodeScanned: (String) -> Unit) {
    try {
        val source = buildLuminanceSource(imageProxy) ?: return
        val rotated = rotateLuminanceSource(source, imageProxy.imageInfo.rotationDegrees)
        val binaryBitmap = BinaryBitmap(HybridBinarizer(rotated))
        val reader = com.google.zxing.MultiFormatReader()

        val hints = hashMapOf<DecodeHintType, Any>(
            DecodeHintType.POSSIBLE_FORMATS to arrayListOf(BarcodeFormat.QR_CODE),
            DecodeHintType.CHARACTER_SET to "UTF-8",
            DecodeHintType.TRY_HARDER to true
        )

        val result = reader.decode(binaryBitmap, hints)
        onCodeScanned(result.text)
    } catch (e: Exception) {
        // 忽略解码失败（持续扫描）
    }
}

@Composable
private fun ScannerOverlay() {
    val cornerLength = 40.dp
    val cornerWidth = 4.dp
    val color = MaterialTheme.colorScheme.primary

    Box(modifier = Modifier.fillMaxSize()) {
        // 左上角
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .width(cornerLength)
                .height(cornerWidth),
            color = color
        ) {}
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .width(cornerWidth)
                .height(cornerLength),
            color = color
        ) {}

        // 右上角
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .width(cornerLength)
                .height(cornerWidth),
            color = color
        ) {}
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .width(cornerWidth)
                .height(cornerLength),
            color = color
        ) {}

        // 左下角
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .width(cornerLength)
                .height(cornerWidth),
            color = color
        ) {}
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .width(cornerWidth)
                .height(cornerLength),
            color = color
        ) {}

        // 右下角
        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .width(cornerLength)
                .height(cornerWidth),
            color = color
        ) {}
        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .width(cornerWidth)
                .height(cornerLength),
            color = color
        ) {}
    }
}
