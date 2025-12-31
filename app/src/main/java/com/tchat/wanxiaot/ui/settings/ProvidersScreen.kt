package com.tchat.wanxiaot.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tchat.wanxiaot.settings.ProviderConfig
import com.tchat.wanxiaot.settings.SettingsManager
import com.tchat.wanxiaot.ui.components.QRCodeScannerScreen

/**
 * 服务商页面状态
 */
private sealed class ProvidersPageState {
    data object List : ProvidersPageState()
    data class Edit(val provider: ProviderConfig?) : ProvidersPageState()
    data object Scan : ProvidersPageState()
}

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
    var pageState by remember { mutableStateOf<ProvidersPageState>(ProvidersPageState.List) }

    // 处理系统返回键
    BackHandler {
        when (pageState) {
            is ProvidersPageState.List -> onBack()
            else -> pageState = ProvidersPageState.List
        }
    }

    AnimatedContent(
        targetState = pageState,
        transitionSpec = {
            val animationDuration = 200
            when {
                targetState is ProvidersPageState.List -> {
                    // 返回列表页面：从左边滑入，旧页面向右滑出
                    slideInHorizontally(
                        animationSpec = tween(animationDuration),
                        initialOffsetX = { -it }
                    ) togetherWith slideOutHorizontally(
                        animationSpec = tween(animationDuration),
                        targetOffsetX = { it }
                    )
                }
                else -> {
                    // 进入子页面：从右边滑入，旧页面向左滑出
                    slideInHorizontally(
                        animationSpec = tween(animationDuration),
                        initialOffsetX = { it }
                    ) togetherWith slideOutHorizontally(
                        animationSpec = tween(animationDuration),
                        targetOffsetX = { -it }
                    )
                }
            }
        },
        label = "providers_page_transition"
    ) { state ->
        when (state) {
            is ProvidersPageState.List -> {
                ProvidersListContent(
                    settings = settings,
                    onBack = onBack,
                    onAddNew = { pageState = ProvidersPageState.Edit(null) },
                    onScan = { pageState = ProvidersPageState.Scan },
                    onEditProvider = { provider -> pageState = ProvidersPageState.Edit(provider) }
                )
            }
            is ProvidersPageState.Edit -> {
                ProviderEditScreen(
                    provider = state.provider,
                    settingsManager = settingsManager,
                    onBack = { pageState = ProvidersPageState.List },
                    onSave = { provider ->
                        if (state.provider != null) {
                            settingsManager.updateProvider(provider)
                        } else {
                            settingsManager.addProvider(provider)
                        }
                        pageState = ProvidersPageState.List
                    },
                    onDelete = if (state.provider != null) {
                        { settingsManager.deleteProvider(state.provider.id) }
                    } else null
                )
            }
            is ProvidersPageState.Scan -> {
                QRCodeScannerScreen(
                    onBack = { pageState = ProvidersPageState.List },
                    onProviderScanned = { provider ->
                        settingsManager.addProvider(provider)
                        settingsManager.setCurrentProvider(provider.id)
                        pageState = ProvidersPageState.List
                    }
                )
            }
        }
    }
}

/**
 * 服务商列表内容
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProvidersListContent(
    settings: com.tchat.wanxiaot.settings.AppSettings,
    onBack: () -> Unit,
    onAddNew: () -> Unit,
    onScan: () -> Unit,
    onEditProvider: (ProviderConfig) -> Unit
) {
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
                    IconButton(onClick = onScan) {
                        Icon(Icons.Outlined.QrCodeScanner, contentDescription = "扫码导入")
                    }
                    IconButton(onClick = onAddNew) {
                        Icon(Icons.Default.Add, contentDescription = "添加服务商")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (settings.providers.isEmpty()) {
            // 空状态 - 居中显示
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "还没有添加服务商",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "点击右上角 + 添加你的第一个服务商",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FilledTonalButton(onClick = onAddNew) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("添加服务商")
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
            ) {
                // 分组标题
                Text(
                    "我的服务商",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )

                settings.providers.forEach { provider ->
                    val isCurrentProvider = provider.id == settings.currentProviderId

                    ProviderListItem(
                        provider = provider,
                        isCurrentProvider = isCurrentProvider,
                        onClick = { onEditProvider(provider) }
                    )
                }
            }
        }
    }
}

/**
 * 服务商列表项 - Material 3 设计
 */
@Composable
private fun ProviderListItem(
    provider: ProviderConfig,
    isCurrentProvider: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (isCurrentProvider) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 提供商图标
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (isCurrentProvider) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = provider.providerType.icon(),
                        contentDescription = provider.providerType.displayName,
                        tint = if (isCurrentProvider) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = provider.name.ifEmpty { "未命名" },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isCurrentProvider) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = provider.providerType.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isCurrentProvider) {
                            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    if (provider.selectedModel.isNotEmpty()) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isCurrentProvider) {
                                MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            }
                        )
                        Text(
                            text = provider.selectedModel,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isCurrentProvider) {
                                MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            if (isCurrentProvider) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "当前使用",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }

    // 分隔线
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}
