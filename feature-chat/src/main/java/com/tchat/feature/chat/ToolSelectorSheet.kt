package com.tchat.feature.chat

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Globe
import com.composables.icons.lucide.HardDrive
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ShieldCheck
import com.tchat.data.model.LocalToolOption
import com.tchat.data.model.LocalToolOption.Companion.description
import com.tchat.data.model.LocalToolOption.Companion.displayName

/**
 * 工具选择抽屉
 *
 * 允许用户选择开启或关闭哪些本地工具
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolSelectorSheet(
    sheetState: SheetState,
    enabledTools: Set<LocalToolOption>,
    onToolToggle: (LocalToolOption, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val allTools = LocalToolOption.allOptions()
    
    // 检查存储权限状态
    var hasStoragePermission by remember { 
        mutableStateOf(checkStoragePermission(context)) 
    }
    
    // 权限请求 launcher（Android 10 及以下）
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        hasStoragePermission = allGranted
        if (allGranted) {
            Toast.makeText(context, "存储权限已授权", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "需要存储权限才能使用文件系统工具", Toast.LENGTH_SHORT).show()
        }
    }
    
    // 设置页面 launcher（Android 11+）
    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        // 从设置页面返回后重新检查权限
        hasStoragePermission = checkStoragePermission(context)
        if (hasStoragePermission) {
            Toast.makeText(context, "存储权限已授权", Toast.LENGTH_SHORT).show()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // 标题
            Text(
                text = "本地工具",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "启用工具后，AI可以在对话中调用这些功能",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            // 工具列表
            allTools.forEach { tool ->
                val isEnabled = enabledTools.contains(tool)
                val isFileSystem = tool is LocalToolOption.FileSystem

                ToolCard(
                    tool = tool,
                    isEnabled = isEnabled,
                    onToggle = { enabled -> onToolToggle(tool, enabled) },
                    showPermissionButton = isFileSystem,
                    hasPermission = hasStoragePermission,
                    onRequestPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            // Android 11+ 跳转到设置页面
                            val intent = createManageStorageIntent(context)
                            if (intent != null) {
                                settingsLauncher.launch(intent)
                            }
                        } else {
                            // Android 10 及以下使用权限请求
                            val permissions = getRequiredPermissions()
                            if (permissions.isNotEmpty()) {
                                permissionLauncher.launch(permissions)
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 已启用数量提示
            if (enabledTools.isNotEmpty()) {
                Text(
                    text = "已启用 ${enabledTools.size} 个工具",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

/**
 * 工具卡片
 */
@Composable
private fun ToolCard(
    tool: LocalToolOption,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    showPermissionButton: Boolean = false,
    hasPermission: Boolean = true,
    onRequestPermission: () -> Unit = {}
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = if (isEnabled) 
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else 
            MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = { 
            if (!showPermissionButton || hasPermission) {
                onToggle(!isEnabled) 
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 工具图标
                Icon(
                    imageVector = getToolIcon(tool),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (isEnabled) 
                        MaterialTheme.colorScheme.primary
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 工具信息
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tool.displayName(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = tool.description(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 开关
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { 
                        if (!showPermissionButton || hasPermission) {
                            onToggle(it) 
                        }
                    },
                    enabled = !showPermissionButton || hasPermission
                )
            }
            
            // 文件系统工具的权限按钮（只在未授权时显示）
            if (showPermissionButton && !hasPermission) {
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 权限状态
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Lucide.ShieldCheck,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "需要文件访问权限",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    
                    // 授权按钮
                    Button(
                        onClick = onRequestPermission,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            text = "授权",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}

/**
 * 获取工具对应的图标
 */
private fun getToolIcon(tool: LocalToolOption): ImageVector = when (tool) {
    is LocalToolOption.FileSystem -> Lucide.FileText
    is LocalToolOption.WebFetch -> Lucide.Globe
    is LocalToolOption.SystemInfo -> Lucide.HardDrive
}

// ========== 权限帮助函数 ==========

/**
 * 检查是否有存储权限
 */
private fun checkStoragePermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        val readPermission = context.checkSelfPermission(
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10 只需要读取权限（Scoped Storage）
            readPermission
        } else {
            val writePermission = context.checkSelfPermission(
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            readPermission && writePermission
        }
    }
}

/**
 * 获取需要请求的权限列表
 */
private fun getRequiredPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        emptyArray()
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
    } else {
        arrayOf(
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }
}

/**
 * 创建跳转到"所有文件访问权限"设置页面的 Intent
 */
private fun createManageStorageIntent(context: Context): Intent? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
        }
    } else {
        null
    }
}
