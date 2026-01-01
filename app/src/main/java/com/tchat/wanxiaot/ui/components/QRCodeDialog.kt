package com.tchat.wanxiaot.ui.components

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.tchat.wanxiaot.settings.ProviderConfig
import com.tchat.wanxiaot.util.QRCodeGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 二维码分享弹窗 - Material You BottomSheet 风格
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRCodeDialog(
    provider: ProviderConfig,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 导出选项
    var includeModels by remember { mutableStateOf(true) }

    // 生成二维码
    val qrBitmap = remember(provider, includeModels) {
        val json = QRCodeGenerator.providerToJson(provider, includeModels)
        QRCodeGenerator.generateQRCode(json, 512)
    }

    // 计算预计大小
    val estimatedSize = remember(provider, includeModels) {
        val json = QRCodeGenerator.providerToJson(provider, includeModels)
        "${json.length}B"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "共享你的LLM模型",
                    style = MaterialTheme.typography.titleLarge
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = {
                            scope.launch {
                                saveQRCodeToGallery(context, qrBitmap, provider.name)
                            }
                        }
                    ) {
                        Icon(
                            Icons.Outlined.Download,
                            contentDescription = "保存",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                shareQRCode(context, qrBitmap, provider.name)
                            }
                        }
                    ) {
                        Icon(
                            Icons.Outlined.Share,
                            contentDescription = "分享",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 导出选项
            Text(
                text = "导出选项",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 包含模型列表选项
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = includeModels,
                    onCheckedChange = { includeModels = it }
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "包含模型列表（${provider.availableModels.size} 个模型）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // 预计大小
            Row(
                modifier = Modifier.padding(start = 12.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "预计大小：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = estimatedSize,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 二维码卡片
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 服务商名称
                    Text(
                        text = provider.name.ifEmpty { provider.providerType.displayName },
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // API 端点
                    Text(
                        text = provider.endpoint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // 二维码
                    Box(
                        modifier = Modifier
                            .size(280.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surface,
                                shape = MaterialTheme.shapes.medium
                            )
                            .padding(8.dp)
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "二维码",
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 署名
                    Text(
                        text = "由 Tchat - By wanxiaoT 生成",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * 保存二维码到相册
 */
private suspend fun saveQRCodeToGallery(
    context: Context,
    bitmap: Bitmap,
    providerName: String
) = withContext(Dispatchers.IO) {
    try {
        val fileName = "TChat_${providerName.replace(" ", "_")}_${System.currentTimeMillis()}.png"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/TChat")
            }

            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )

            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
            }
        } else {
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val tchatDir = File(picturesDir, "TChat")
            if (!tchatDir.exists()) tchatDir.mkdirs()

            val file = File(tchatDir, fileName)
            FileOutputStream(file).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
        }

        withContext(Dispatchers.Main) {
            Toast.makeText(context, "已保存到相册", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * 分享二维码
 */
private suspend fun shareQRCode(
    context: Context,
    bitmap: Bitmap,
    providerName: String
) = withContext(Dispatchers.IO) {
    try {
        val cacheDir = File(context.cacheDir, "share")
        if (!cacheDir.exists()) cacheDir.mkdirs()

        val fileName = "TChat_QRCode.png"
        val file = File(cacheDir, fileName)
        FileOutputStream(file).use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "TChat 服务商配置: $providerName")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        withContext(Dispatchers.Main) {
            context.startActivity(Intent.createChooser(shareIntent, "分享二维码"))
        }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "分享失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
