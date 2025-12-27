package com.tchat.wanxiaot.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    currentSettings: AppSettings,
    onDismiss: () -> Unit,
    onSave: (AppSettings) -> Unit
) {
    var selectedProvider by remember { mutableStateOf(currentSettings.provider) }
    var apiKey by remember { mutableStateOf(currentSettings.apiKey) }
    var endpoint by remember { mutableStateOf(currentSettings.endpoint) }
    var availableModels by remember { mutableStateOf(currentSettings.availableModels.ifEmpty { selectedProvider.defaultModels }) }
    var selectedModel by remember { mutableStateOf(currentSettings.selectedModel.ifEmpty { availableModels.firstOrNull() ?: "" }) }

    var providerExpanded by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }
    var isFetchingModels by remember { mutableStateOf(false) }
    var fetchError by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val httpClient = remember { OkHttpClient() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI 设置") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 服务商选择
                ExposedDropdownMenuBox(
                    expanded = providerExpanded,
                    onExpandedChange = { providerExpanded = !providerExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedProvider.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("服务商") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = providerExpanded,
                        onDismissRequest = { providerExpanded = false }
                    ) {
                        AIProvider.entries.forEach { provider ->
                            DropdownMenuItem(
                                text = { Text(provider.displayName) },
                                onClick = {
                                    selectedProvider = provider
                                    endpoint = provider.defaultEndpoint
                                    availableModels = provider.defaultModels
                                    selectedModel = provider.defaultModels.firstOrNull() ?: ""
                                    providerExpanded = false
                                }
                            )
                        }
                    }
                }

                // API Key 输入
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    placeholder = { Text("sk-xxxxxxxxxxxxxxxxxxxxxxxx") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // 端点输入
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    label = { Text("API 端点") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // 拉取模型按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "模型列表 (${availableModels.size})",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isFetchingModels = true
                                fetchError = null
                                try {
                                    val models = fetchModelsFromApi(
                                        httpClient = httpClient,
                                        endpoint = endpoint,
                                        apiKey = apiKey,
                                        provider = selectedProvider
                                    )
                                    availableModels = models
                                    if (models.isNotEmpty()) {
                                        selectedModel = models.first()
                                    }
                                } catch (e: Exception) {
                                    fetchError = e.message ?: "拉取失败"
                                }
                                isFetchingModels = false
                            }
                        },
                        enabled = !isFetchingModels && apiKey.isNotBlank() && endpoint.isNotBlank()
                    ) {
                        if (isFetchingModels) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("拉取模型")
                    }
                }

                // 错误提示
                if (fetchError != null) {
                    Text(
                        text = fetchError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // 模型选择
                if (availableModels.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = modelExpanded,
                        onExpandedChange = { modelExpanded = !modelExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedModel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("选择模型") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = modelExpanded,
                            onDismissRequest = { modelExpanded = false }
                        ) {
                            availableModels.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model) },
                                    onClick = {
                                        selectedModel = model
                                        modelExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(AppSettings(
                        provider = selectedProvider,
                        apiKey = apiKey.trim(),
                        endpoint = endpoint.trim(),
                        selectedModel = selectedModel,
                        availableModels = availableModels
                    ))
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 从 API 拉取模型列表
 */
private suspend fun fetchModelsFromApi(
    httpClient: OkHttpClient,
    endpoint: String,
    apiKey: String,
    provider: AIProvider
): List<String> = withContext(Dispatchers.IO) {
    val url = when (provider) {
        AIProvider.GEMINI -> "$endpoint/models?key=$apiKey"
        else -> "${endpoint.trimEnd('/')}/models"
    }

    val requestBuilder = Request.Builder().url(url)

    // 根据不同 Provider 添加认证头
    when (provider) {
        AIProvider.OPENAI, AIProvider.ANTHROPIC -> {
            if (apiKey.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $apiKey")
            }
        }
        else -> {}
    }

    val response = httpClient.newCall(requestBuilder.build()).execute()

    if (!response.isSuccessful) {
        throw Exception("HTTP ${response.code}: ${response.message}")
    }

    val responseBody = response.body?.string() ?: throw Exception("空响应")

    // 解析 JSON 响应
    val jsonObject = JSONObject(responseBody)
    val dataArray = jsonObject.optJSONArray("data")
        ?: jsonObject.optJSONArray("models")
        ?: throw Exception("无效的响应格式")

    val models = mutableListOf<String>()
    for (i in 0 until dataArray.length()) {
        val modelObj = dataArray.getJSONObject(i)
        val modelId = modelObj.optString("id") ?: modelObj.optString("model") ?: continue

        // 过滤掉非聊天模型
        if (modelId.contains("embedding", ignoreCase = true) ||
            modelId.contains("whisper", ignoreCase = true) ||
            modelId.contains("tts", ignoreCase = true) ||
            modelId.contains("dall-e", ignoreCase = true)) {
            continue
        }

        models.add(modelId)
    }

    if (models.isEmpty()) {
        throw Exception("未找到可用模型")
    }

    models
}
