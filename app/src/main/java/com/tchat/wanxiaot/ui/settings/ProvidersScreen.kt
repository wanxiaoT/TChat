package com.tchat.wanxiaot.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tchat.wanxiaot.settings.ProviderConfig
import com.tchat.wanxiaot.settings.SettingsManager

/**
 * 服务商管理页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersScreen(
    settingsManager: SettingsManager,
    onBack: () -> Unit
) {
    val settings by settingsManager.settings.collectAsState()
    var editingProvider by remember { mutableStateOf<ProviderConfig?>(null) }
    var isAddingNew by remember { mutableStateOf(false) }

    // 编辑或添加服务商
    if (editingProvider != null || isAddingNew) {
        ProviderEditScreen(
            provider = editingProvider,
            settingsManager = settingsManager,
            onBack = {
                editingProvider = null
                isAddingNew = false
            },
            onSave = { provider ->
                if (editingProvider != null) {
                    settingsManager.updateProvider(provider)
                } else {
                    settingsManager.addProvider(provider)
                }
                editingProvider = null
                isAddingNew = false
            },
            onDelete = if (editingProvider != null) {
                { settingsManager.deleteProvider(editingProvider!!.id) }
            } else null
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("服务商") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    FilledTonalIconButton(onClick = { isAddingNew = true }) {
                        Icon(Icons.Default.Add, contentDescription = "添加服务商")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (settings.providers.isEmpty()) {
                // 没有服务商时显示提示 - 使用 ElevatedCard
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "还没有添加服务商",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "点击右上角 + 添加你的第一个服务商",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        FilledTonalButton(onClick = { isAddingNew = true }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("添加服务商")
                        }
                    }
                }
            } else {
                Text(
                    "我的服务商",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                settings.providers.forEach { provider ->
                    val isCurrentProvider = provider.id == settings.currentProviderId

                    // 使用 OutlinedCard 或 ElevatedCard
                    if (isCurrentProvider) {
                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { editingProvider = provider },
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            ProviderCardContent(provider, isCurrentProvider)
                        }
                    } else {
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { editingProvider = provider },
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            ProviderCardContent(provider, isCurrentProvider)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderCardContent(
    provider: ProviderConfig,
    isCurrentProvider: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = provider.name.ifEmpty { "未命名" },
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrentProvider)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = provider.providerType.displayName,
                style = MaterialTheme.typography.bodySmall,
                color = if (isCurrentProvider)
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (provider.selectedModel.isNotEmpty()) {
                Text(
                    text = "模型: ${provider.selectedModel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isCurrentProvider)
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (isCurrentProvider) {
            Icon(
                Icons.Default.Check,
                contentDescription = "当前使用",
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
