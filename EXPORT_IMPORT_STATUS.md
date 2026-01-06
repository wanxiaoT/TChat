# 导出/导入功能 - 完成度报告与剩余工作

## ✅ 已完成的核心功能

### 1. 架构层
- ✅ ExportImportManager 核心业务逻辑（100%）
  - 供应商配置导出/导入
  - 模型列表导出/导入
  - API配置导出/导入
  - 知识库导出/导入
  - 加密/解密支持

- ✅ ExportImportViewModel（100%）
  - UI状态管理
  - 供应商多选管理
  - 所有导出/导入方法封装

- ✅ QRCodeUtils 工具类（100%）
  - 二维码生成
  - 二维码解析
  - 加密二维码支持

### 2. UI层
- ✅ 设置页面集成（100%）
  - 主菜单入口
  - 路由配置（手机+平板布局）

- ✅ ExportImportScreenEnhanced（80%）
  - 文件选择器（导出）
  - 文件选择器（导入）
  - 多文件选择器
  - 供应商多选对话框
  - 二维码显示对话框
  - Loading状态

- ✅ QRCodeScannerForImport（100%）
  - 相机扫描
  - 相册选择
  - 加密二维码支持
  - 权限管理

## ⚠️ 待完成的工作

### 高优先级

#### 1. 完成 ExportImportScreenEnhanced 中的 TODO 项
**位置**: `app/src/main/java/com/tchat/wanxiaot/ui/settings/ExportImportScreenEnhanced.kt`

**需要修改的地方**:

```kotlin
// 当前状态管理需要扩展
var showQRScanner by remember { mutableStateOf(false) }
var showPasswordInput by remember { mutableStateOf(false) }
var selectedProviderId by remember { mutableStateOf<String?>(null) }
var selectedKnowledgeBaseId by remember { mutableStateOf<String?>(null) }

// 1. 集成二维码扫描器
onImportQRCode = {
    currentExportType = ExportType.PROVIDERS
    showQRScanner = true
}

// 2. 完成模型列表导出
ExportType.MODELS -> {
    selectedProviderId?.let { providerId ->
        val file = uriToFile(context, it, "models_export.json")
        viewModel.exportModelsToFile(
            providerId,
            file,
            useEncryption,
            encryptionPassword.takeIf { useEncryption }
        )
    }
}

// 3. 完成API配置导出
ExportType.API_CONFIG -> {
    selectedProviderId?.let { providerId ->
        val file = uriToFile(context, it, "api_config_export.json")
        viewModel.exportApiConfigToFile(
            providerId,
            file,
            true, // API配置默认加密
            encryptionPassword
        )
    }
}

// 4. 完成知识库导出
ExportType.KNOWLEDGE_BASE -> {
    selectedKnowledgeBaseId?.let { kbId ->
        val file = uriToFile(context, it, "knowledge_base_export.json")
        viewModel.exportKnowledgeBaseToFile(kbId, file)
    }
}
```

#### 2. 添加 Snackbar 消息显示
**位置**: 同上

```kotlin
val snackbarHostState = remember { SnackbarHostState() }

Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) }
) { ... }

// 在 LaunchedEffect 中处理消息
LaunchedEffect(uiState) {
    when (val state = uiState) {
        is ExportImportUiState.Success -> {
            snackbarHostState.showSnackbar(
                message = state.message,
                duration = SnackbarDuration.Short
            )
            viewModel.clearUiState()
        }
        is ExportImportUiState.Error -> {
            snackbarHostState.showSnackbar(
                message = state.message,
                duration = SnackbarDuration.Long
            )
            viewModel.clearUiState()
        }
        else -> {}
    }
}
```

#### 3. 添加二维码扫描器对话框
**位置**: 同上

```kotlin
// 在 Scaffold 外部添加
if (showQRScanner) {
    QRCodeScannerForImport(
        onBack = { showQRScanner = false },
        onDataScanned = { exportData ->
            showQRScanner = false
            when (currentExportType) {
                ExportType.PROVIDERS -> {
                    // ViewModel 已经有处理 ExportData 的方法
                    // 需要添加一个方法来处理通用的 ExportData
                    viewModel.importFromExportData(exportData, encryptionPassword)
                }
                else -> {}
            }
        },
        password = encryptionPassword.takeIf { it.isNotEmpty() }
    )
}
```

#### 4. 添加供应商/知识库选择对话框（用于导出）

```kotlin
// 供应商单选对话框（用于模型列表和API配置导出）
@Composable
fun ProviderSingleSelectionDialog(
    providers: List<ProviderConfig>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var selectedId by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择供应商") },
        text = {
            LazyColumn {
                items(providers) { provider ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedId = provider.id }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedId == provider.id,
                            onClick = { selectedId = provider.id }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(provider.name)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { selectedId?.let { onConfirm(it) } },
                enabled = selectedId != null
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
```

#### 5. 添加二维码分享功能

```kotlin
// 在 QRCodeDisplayDialog 的 onShare 中实现
import android.content.Intent
import androidx.core.content.FileProvider

fun shareQRCode(context: Context, bitmap: Bitmap) {
    try {
        // 保存到临时文件
        val file = File(context.cacheDir, "qr_code_${System.currentTimeMillis()}.png")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        // 获取文件URI
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        // 创建分享Intent
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "分享二维码"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
```

### 中优先级

#### 6. 在 ViewModel 中添加通用导入方法

```kotlin
// ExportImportViewModel.kt
fun importFromExportData(exportData: ExportData, password: String?) {
    viewModelScope.launch {
        _uiState.value = ExportImportUiState.Loading("导入数据...")
        try {
            when (exportData.type) {
                ExportDataType.PROVIDERS -> {
                    val data = ProvidersExportData.fromJson(exportData.data)
                    // 处理导入...
                }
                ExportDataType.MODELS -> {
                    // 处理模型列表...
                }
                ExportDataType.API_CONFIG -> {
                    // 处理API配置...
                }
                ExportDataType.KNOWLEDGE_BASE -> {
                    // 处理知识库...
                }
            }
            _uiState.value = ExportImportUiState.Success("导入成功")
        } catch (e: Exception) {
            _uiState.value = ExportImportUiState.Error("导入失败: ${e.message}")
        }
    }
}
```

### 低优先级

#### 7. 添加批量导入进度提示

```kotlin
// 显示批量导入的进度
var importProgress by remember { mutableStateOf(0f) }
var importTotal by remember { mutableStateOf(0) }

if (importTotal > 0) {
    LinearProgressIndicator(
        progress = importProgress,
        modifier = Modifier.fillMaxWidth()
    )
    Text("正在导入 ${(importProgress * importTotal).toInt()} / $importTotal")
}
```

#### 8. 添加导入历史记录

```kotlin
// 可选：记录导入历史
data class ImportHistory(
    val timestamp: Long,
    val type: ExportDataType,
    val itemCount: Int,
    val source: String // "file" or "qrcode"
)
```

## 📋 完整的功能检查清单

### 供应商配置
- [x] 多选导出到文件
- [x] 导出到二维码
- [x] 从文件导入
- [ ] 从二维码导入（需要集成扫描器）
- [ ] 批量文件导入

### 模型列表
- [x] 后端支持
- [ ] 单选供应商对话框
- [ ] 导出到文件
- [ ] 导出到二维码
- [ ] 从文件导入
- [ ] 从二维码导入

### API配置
- [x] 后端支持（强制加密）
- [ ] 单选供应商对话框
- [ ] 导出到文件
- [ ] 导出到二维码
- [ ] 从文件导入
- [ ] 从二维码导入

### 知识库
- [x] 后端支持
- [ ] 知识库选择对话框
- [ ] 导出到文件
- [ ] 从文件导入

### UI/UX
- [ ] Snackbar 消息提示
- [ ] 二维码分享功能
- [ ] 批量导入进度条
- [ ] 错误处理优化
- [ ] Loading 状态优化

## 🔧 需要的 AndroidManifest.xml 权限

确保在 `app/src/main/AndroidManifest.xml` 中有以下权限：

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />

<!-- FileProvider for sharing -->
<application>
    <provider
        android:name="androidx.core.content.FileProvider"
        android:authorities="${applicationId}.fileprovider"
        android:exported="false"
        android:grantUriPermissions="true">
        <meta-data
            android:name="android.support.FILE_PROVIDER_PATHS"
            android:resource="@xml/file_paths" />
    </provider>
</application>
```

并创建 `res/xml/file_paths.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="cache" path="." />
    <files-path name="files" path="." />
</paths>
```

## 🎯 下一步建议

1. **先完成高优先级项目**：
   - 集成二维码扫描器到主界面
   - 添加 Snackbar 消息显示
   - 完成模型列表和API配置的UI连接

2. **测试核心流程**：
   - 供应商配置完整导出导入流程
   - 二维码扫描功能
   - 加密/解密功能

3. **优化用户体验**：
   - 添加更多的错误提示
   - 改进Loading状态显示
   - 添加导入成功后的统计信息

4. **扩展功能**：
   - 批量导入进度显示
   - 导入历史记录
   - 导出模板功能

## 📊 当前完成度

| 功能模块 | 完成度 | 说明 |
|---------|-------|------|
| 核心架构 | 100% | ExportImportManager + ViewModel |
| 供应商配置导出/导入 | 90% | 缺少二维码扫描集成 |
| 模型列表导出/导入 | 60% | 缺少UI连接 |
| API配置导出/导入 | 60% | 缺少UI连接 |
| 知识库导出/导入 | 60% | 缺少UI连接 |
| 二维码功能 | 95% | 缺少分享功能 |
| UI/UX | 75% | 缺少Snackbar和优化 |

**总体完成度**: 约 **80%**
