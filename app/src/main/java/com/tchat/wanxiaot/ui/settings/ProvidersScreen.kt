package com.tchat.wanxiaot.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.BrainCircuit
import com.composables.icons.lucide.Lucide
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
                    slideInHorizontally(
                        animationSpec = tween(animationDuration),
                        initialOffsetX = { -it }
                    ) togetherWith slideOutHorizontally(
                        animationSpec = tween(animationDuration),
                        targetOffsetX = { it }
                    )
                }
                else -> {
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
 * 服务商列表内容 - Material You 设计
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onScan) {
                        Icon(Icons.Outlined.QrCodeScanner, contentDescription = "扫码导入")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddNew,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("添加") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { innerPadding ->
        if (settings.providers.isEmpty()) {
            // 空状态
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
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(80.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Lucide.BrainCircuit,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Text(
                        "还没有添加服务商",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "添加一个 AI 服务商开始聊天",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "已配置 ${settings.providers.size} 个服务商",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                items(settings.providers, key = { it.id }) { provider ->
                    val isCurrentProvider = provider.id == settings.currentProviderId

                    ProviderCard(
                        provider = provider,
                        isCurrentProvider = isCurrentProvider,
                        onClick = { onEditProvider(provider) }
                    )
                }

                // 底部留空给 FAB
                item {
                    Spacer(modifier = Modifier.height(72.dp))
                }
            }
        }
    }
}

/**
 * 服务商卡片 - Material You 设计
 */
@Composable
private fun ProviderCard(
    provider: ProviderConfig,
    isCurrentProvider: Boolean,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isCurrentProvider)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 提供商图标
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (isCurrentProvider)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .padding(12.dp)
                ) {
                    Icon(
                        imageVector = provider.providerType.icon(),
                        contentDescription = provider.providerType.displayName,
                        tint = if (isCurrentProvider)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = provider.name.ifEmpty { "未命名" },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isCurrentProvider)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 服务商类型标签
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (isCurrentProvider)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        else
                            MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = provider.providerType.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isCurrentProvider)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    // 模型数量标签
                    if (provider.availableModels.isNotEmpty()) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (isCurrentProvider)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            else
                                MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                text = "${provider.availableModels.size} 个模型",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isCurrentProvider)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // 当前使用的模型
                if (provider.selectedModel.isNotEmpty()) {
                    Text(
                        text = "当前: ${provider.selectedModel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isCurrentProvider)
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isCurrentProvider) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "当前使用",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .size(28.dp)
                            .padding(4.dp)
                    )
                }
            }
        }
    }
}
