package com.tchat.wanxiaot.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.*
import com.tchat.wanxiaot.settings.R2Settings
import com.tchat.wanxiaot.settings.SettingsManager
import com.tchat.wanxiaot.ui.components.AppHeroCard
import com.tchat.wanxiaot.ui.components.AppPageScaffold
import com.tchat.wanxiaot.ui.components.AppPill
import com.tchat.wanxiaot.ui.components.AppSectionCard
import com.tchat.wanxiaot.ui.components.AppSectionSurface
import com.tchat.wanxiaot.util.CloudBackupManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun R2SettingsScreen(
    settingsManager: SettingsManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by settingsManager.settings.collectAsStateWithLifecycle()
    val r2Settings = settings.r2Settings

    // 本地编辑状态
    var accountId by remember(r2Settings) { mutableStateOf(r2Settings.accountId) }
    var accessKeyId by remember(r2Settings) { mutableStateOf(r2Settings.accessKeyId) }
    var secretAccessKey by remember(r2Settings) { mutableStateOf(r2Settings.secretAccessKey) }
    var bucketName by remember(r2Settings) { mutableStateOf(r2Settings.bucketName) }
    var customEndpoint by remember(r2Settings) { mutableStateOf(r2Settings.customEndpoint) }

    var showSecretKey by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(customEndpoint.isNotEmpty()) }
    var isTestingConnection by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testSuccess by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    // 检查是否有未保存的更改
    val hasChanges = accountId != r2Settings.accountId ||
            accessKeyId != r2Settings.accessKeyId ||
            secretAccessKey != r2Settings.secretAccessKey ||
            bucketName != r2Settings.bucketName ||
            customEndpoint != r2Settings.customEndpoint

    // 保存设置
    fun saveSettings() {
        val newSettings = R2Settings(
            enabled = true,
            accountId = accountId.trim(),
            accessKeyId = accessKeyId.trim(),
            secretAccessKey = secretAccessKey.trim(),
            bucketName = bucketName.trim(),
            customEndpoint = customEndpoint.trim()
        )
        settingsManager.updateR2Settings(newSettings)
        scope.launch {
            snackbarHostState.showSnackbar("设置已保存")
        }
    }

    // 测试连接
    fun testConnection() {
        // 先保存当前设置
        val newSettings = R2Settings(
            enabled = true,
            accountId = accountId.trim(),
            accessKeyId = accessKeyId.trim(),
            secretAccessKey = secretAccessKey.trim(),
            bucketName = bucketName.trim(),
            customEndpoint = customEndpoint.trim()
        )
        settingsManager.updateR2Settings(newSettings)

        isTestingConnection = true
        testResult = null

        scope.launch {
            val cloudBackupManager = CloudBackupManager(context, settingsManager)
            val result = cloudBackupManager.testConnection()

            isTestingConnection = false
            testSuccess = result.isSuccess
            testResult = if (result.isSuccess) {
                "连接成功！"
            } else {
                result.exceptionOrNull()?.message ?: "连接失败"
            }
        }
    }

    AppPageScaffold(
        title = "云备份设置",
        eyebrow = "Cloud Backup",
        subtitle = "Cloudflare R2 连接与凭证管理",
        onBack = onBack,
        actions = {
            if (hasChanges) {
                TextButton(onClick = { saveSettings() }) {
                    Text("保存")
                }
            }
        }
    ) { paddingValues ->
        SnackbarHost(hostState = snackbarHostState)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AppHeroCard(
                eyebrow = "Remote Storage",
                title = "把备份链路配置成一个稳定的恢复通道",
                description = "R2 适合承载数据库备份文件，但账号、桶和凭证必须清晰可验证。",
                icon = Lucide.Cloud,
                trailing = {
                    if (hasChanges) {
                        AppPill(text = "未保存更改")
                    }
                }
            )

            AppSectionCard(
                title = "R2 配置",
                description = "填写账户、Key 和目标 Bucket。高级设置只在需要自定义端点时开启。"
            ) {
                    OutlinedTextField(
                        value = accountId,
                        onValueChange = { accountId = it },
                        label = { Text("Account ID") },
                        placeholder = { Text("Cloudflare 账户 ID") },
                        supportingText = { Text("在 Cloudflare Dashboard 右侧栏可以找到") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = accessKeyId,
                        onValueChange = { accessKeyId = it },
                        label = { Text("Access Key ID") },
                        placeholder = { Text("R2 API 令牌的 Access Key ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = secretAccessKey,
                        onValueChange = { secretAccessKey = it },
                        label = { Text("Secret Access Key") },
                        placeholder = { Text("R2 API 令牌的 Secret Access Key") },
                        singleLine = true,
                        visualTransformation = if (showSecretKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showSecretKey = !showSecretKey }) {
                                Icon(
                                    imageVector = if (showSecretKey) Lucide.EyeOff else Lucide.Eye,
                                    contentDescription = if (showSecretKey) "隐藏" else "显示"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = bucketName,
                        onValueChange = { bucketName = it },
                        label = { Text("Bucket 名称") },
                        placeholder = { Text("例如: tchat-backup") },
                        supportingText = { Text("需要先在 R2 控制台创建 Bucket") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 高级设置
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "高级设置",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = showAdvanced,
                            onCheckedChange = { showAdvanced = it }
                        )
                    }

                    if (showAdvanced) {
                        OutlinedTextField(
                            value = customEndpoint,
                            onValueChange = { customEndpoint = it },
                            label = { Text("自定义端点 (可选)") },
                            placeholder = { Text("留空使用默认端点") },
                            supportingText = {
                                Text(
                                    if (customEndpoint.isEmpty() && accountId.isNotEmpty()) {
                                        "默认: https://$accountId.r2.cloudflarestorage.com"
                                    } else {
                                        "仅在使用自定义域名时需要填写"
                                    }
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
            }

            val isConfigComplete = accountId.isNotBlank() &&
                    accessKeyId.isNotBlank() &&
                    secretAccessKey.isNotBlank() &&
                    bucketName.isNotBlank()

            Button(
                onClick = { testConnection() },
                enabled = isConfigComplete && !isTestingConnection,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isTestingConnection) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("测试中...")
                } else {
                    Icon(
                        imageVector = Lucide.Wifi,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("测试连接")
                }
            }

            // 测试结果
            testResult?.let { result ->
                AppSectionSurface {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (testSuccess) Lucide.CircleCheck else Lucide.CircleX,
                            contentDescription = null,
                            tint = if (testSuccess) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            }
                        )
                        Text(
                            text = result,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (testSuccess) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            }
                        )
                    }
                }
            }

            AppSectionCard(title = "帮助") {
                AppSectionSurface {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "如何获取 R2 凭证？",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "点击查看 Cloudflare R2 API 令牌创建指南",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://developers.cloudflare.com/r2/api/s3/tokens/"))
                                context.startActivity(intent)
                            }
                        ) {
                            Icon(
                                imageVector = Lucide.ExternalLink,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            AppSectionCard(title = "安全建议") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Lucide.ShieldCheck,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column {
                        Text(
                            text = "建议创建仅具有单个 Bucket 读写权限的 API 令牌，以最小化安全风险。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
