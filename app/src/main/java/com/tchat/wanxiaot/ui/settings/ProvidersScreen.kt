package com.tchat.wanxiaot.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.BrainCircuit
import com.composables.icons.lucide.Lucide
import com.tchat.wanxiaot.settings.ProviderConfig
import com.tchat.wanxiaot.settings.SettingsManager
import com.tchat.wanxiaot.ui.components.AppEmptyState
import com.tchat.wanxiaot.ui.components.AppHeroCard
import com.tchat.wanxiaot.ui.components.AppIconTile
import com.tchat.wanxiaot.ui.components.AppPageScaffold
import com.tchat.wanxiaot.ui.components.AppPill
import com.tchat.wanxiaot.ui.components.AppSectionSurface
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
 * 卡片大小（用于旧的网格布局，保留以防需要）
 */
private enum class CardSize(val minWidth: Int) {
    SMALL(120),
    MEDIUM(160),
    LARGE(200)
}

/**
 * 服务商管理页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersScreen(
    settingsManager: SettingsManager,
    onBack: () -> Unit,
    showTopBar: Boolean = true
) {
    val settings by settingsManager.settings.collectAsStateWithLifecycle()
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
                    settingsManager = settingsManager,
                    settings = settings,
                    onBack = onBack,
                    onAddNew = { pageState = ProvidersPageState.Edit(null) },
                    onScan = { pageState = ProvidersPageState.Scan },
                    onEditProvider = { provider -> pageState = ProvidersPageState.Edit(provider) },
                    showTopBar = showTopBar
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
    settingsManager: SettingsManager,
    settings: com.tchat.wanxiaot.settings.AppSettings,
    onBack: () -> Unit,
    onAddNew: () -> Unit,
    onScan: () -> Unit,
    onEditProvider: (ProviderConfig) -> Unit,
    showTopBar: Boolean = true
) {
    var showColumnMenu by remember { mutableStateOf(false) }

    AppPageScaffold(
        title = "服务商",
        eyebrow = "Provider Network",
        subtitle = if (settings.providers.isEmpty()) "连接模型供应商，建立稳定的调用层" else "已配置 ${settings.providers.size} 个服务商",
        showTopBar = showTopBar,
        onBack = onBack,
        actions = {
            Box {
                IconButton(onClick = { showColumnMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "布局选项")
                }

                DropdownMenu(
                    expanded = showColumnMenu,
                    onDismissRequest = { showColumnMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("单列显示") },
                        onClick = {
                            settingsManager.updateProviderGridColumnCount(1)
                            showColumnMenu = false
                        },
                        leadingIcon = {
                            if (settings.providerGridColumnCount == 1) {
                                Icon(Icons.Default.Check, contentDescription = null)
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("双列显示") },
                        onClick = {
                            settingsManager.updateProviderGridColumnCount(2)
                            showColumnMenu = false
                        },
                        leadingIcon = {
                            if (settings.providerGridColumnCount == 2) {
                                Icon(Icons.Default.Check, contentDescription = null)
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("三列显示") },
                        onClick = {
                            settingsManager.updateProviderGridColumnCount(3)
                            showColumnMenu = false
                        },
                        leadingIcon = {
                            if (settings.providerGridColumnCount == 3) {
                                Icon(Icons.Default.Check, contentDescription = null)
                            }
                        }
                    )
                }
            }

            IconButton(onClick = onScan) {
                Icon(Icons.Outlined.QrCodeScanner, contentDescription = "扫码导入")
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddNew,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("添加服务商") }
            )
        }
    ) { innerPadding ->
        if (settings.providers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    AppHeroCard(
                        eyebrow = "Connection Layer",
                        title = "先把供应商连接整理清楚，再谈模型体验",
                        description = "服务商层决定模型、端点、Key 和参数策略，是整个应用的基础设施。",
                        icon = Lucide.BrainCircuit
                    )
                    AppEmptyState(
                        icon = Lucide.BrainCircuit,
                        title = "还没有添加服务商",
                        description = "添加一个 AI 服务商，聊天、知识库和深度研究功能才会真正工作起来。"
                    )
                }
            }
        } else {
            // 当列数为1时使用列表布局，否则使用网格布局
            if (settings.providerGridColumnCount == 1) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "已配置 ${settings.providers.size} 个服务商",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            // 平板模式下显示操作按钮
                            if (!showTopBar) {
                                Row {
                                    // 列数选择按钮
                                    Box {
                                        IconButton(onClick = { showColumnMenu = true }) {
                                            Icon(Icons.Default.MoreVert, contentDescription = "布局选项")
                                        }

                                        DropdownMenu(
                                            expanded = showColumnMenu,
                                            onDismissRequest = { showColumnMenu = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("单列显示") },
                                                onClick = {
                                                    settingsManager.updateProviderGridColumnCount(1)
                                                    showColumnMenu = false
                                                },
                                                leadingIcon = {
                                                    if (settings.providerGridColumnCount == 1) {
                                                        Icon(Icons.Default.Check, contentDescription = null)
                                                    }
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("双列显示") },
                                                onClick = {
                                                    settingsManager.updateProviderGridColumnCount(2)
                                                    showColumnMenu = false
                                                },
                                                leadingIcon = {
                                                    if (settings.providerGridColumnCount == 2) {
                                                        Icon(Icons.Default.Check, contentDescription = null)
                                                    }
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("三列显示") },
                                                onClick = {
                                                    settingsManager.updateProviderGridColumnCount(3)
                                                    showColumnMenu = false
                                                },
                                                leadingIcon = {
                                                    if (settings.providerGridColumnCount == 3) {
                                                        Icon(Icons.Default.Check, contentDescription = null)
                                                    }
                                                }
                                            )
                                        }
                                    }

                                    IconButton(onClick = onScan) {
                                        Icon(Icons.Outlined.QrCodeScanner, contentDescription = "扫码导入")
                                    }
                                }
                            }
                        }
                    }

                    items(settings.providers, key = { it.id }) { provider ->
                        val isCurrentProvider = provider.id == settings.currentProviderId

                        ProviderListItem(
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
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(settings.providerGridColumnCount),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(settings.providerGridColumnCount) }) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "已配置 ${settings.providers.size} 个服务商",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            // 平板模式下显示操作按钮
                            if (!showTopBar) {
                                Row {
                                    // 列数选择按钮
                                    Box {
                                        IconButton(onClick = { showColumnMenu = true }) {
                                            Icon(Icons.Default.MoreVert, contentDescription = "布局选项")
                                        }

                                        DropdownMenu(
                                            expanded = showColumnMenu,
                                            onDismissRequest = { showColumnMenu = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("单列显示") },
                                                onClick = {
                                                    settingsManager.updateProviderGridColumnCount(1)
                                                    showColumnMenu = false
                                                },
                                                leadingIcon = {
                                                    if (settings.providerGridColumnCount == 1) {
                                                        Icon(Icons.Default.Check, contentDescription = null)
                                                    }
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("双列显示") },
                                                onClick = {
                                                    settingsManager.updateProviderGridColumnCount(2)
                                                    showColumnMenu = false
                                                },
                                                leadingIcon = {
                                                    if (settings.providerGridColumnCount == 2) {
                                                        Icon(Icons.Default.Check, contentDescription = null)
                                                    }
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("三列显示") },
                                                onClick = {
                                                    settingsManager.updateProviderGridColumnCount(3)
                                                    showColumnMenu = false
                                                },
                                                leadingIcon = {
                                                    if (settings.providerGridColumnCount == 3) {
                                                        Icon(Icons.Default.Check, contentDescription = null)
                                                    }
                                                }
                                            )
                                        }
                                    }

                                    IconButton(onClick = onScan) {
                                        Icon(Icons.Outlined.QrCodeScanner, contentDescription = "扫码导入")
                                    }
                                }
                            }
                        }
                    }

                    items(settings.providers, key = { it.id }) { provider ->
                        val isCurrentProvider = provider.id == settings.currentProviderId

                        // 根据列数选择合适的卡片大小 - 双列和三列都使用SMALL以保持紧凑
                        val cardSize = when (settings.providerGridColumnCount) {
                            2 -> CardSize.SMALL
                            3 -> CardSize.SMALL
                            else -> CardSize.LARGE
                        }

                        ProviderCard(
                            provider = provider,
                            isCurrentProvider = isCurrentProvider,
                            onClick = { onEditProvider(provider) },
                            onSetCurrent = { settingsManager.setCurrentProvider(provider.id) },
                            onDelete = { settingsManager.deleteProvider(provider.id) },
                            cardSize = cardSize
                        )
                    }

                    // 底部留空给 FAB
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(settings.providerGridColumnCount) }) {
                        Spacer(modifier = Modifier.height(72.dp))
                    }
                }
            }
        }
    }
}

/**
 * 服务商列表项 - Material You 列表设计
 */
@Composable
private fun ProviderListItem(
    provider: ProviderConfig,
    isCurrentProvider: Boolean,
    onClick: () -> Unit
) {
    AppSectionSurface {
        Surface(
            onClick = onClick,
            color = if (isCurrentProvider) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.56f)
            } else {
                Color.Transparent
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIconTile(
                    icon = provider.providerType.icon(),
                    tint = if (isCurrentProvider) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    containerColor = if (isCurrentProvider) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = provider.name.ifEmpty { "未命名" },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = provider.providerType.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (provider.selectedModel.isNotEmpty()) {
                        Text(
                            text = provider.selectedModel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isCurrentProvider) {
                        AppPill(text = "当前使用")
                    }
                    AppPill(
                        text = "${provider.availableModels.size} 个模型",
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 服务商卡片 - Material You 网格设计（类似 Cherry Studio 风格）
 */
@Composable
private fun ProviderCard(
    provider: ProviderConfig,
    isCurrentProvider: Boolean,
    onClick: () -> Unit,
    onSetCurrent: () -> Unit,
    onDelete: () -> Unit,
    cardSize: CardSize = CardSize.MEDIUM
) {
    var showMenu by remember { mutableStateOf(false) }

    // 根据卡片大小调整各个元素的尺寸
    val iconSize = when (cardSize) {
        CardSize.SMALL -> 36.dp
        CardSize.MEDIUM -> 44.dp
        CardSize.LARGE -> 52.dp
    }

    val iconInnerSize = when (cardSize) {
        CardSize.SMALL -> 20.dp
        CardSize.MEDIUM -> 24.dp
        CardSize.LARGE -> 28.dp
    }

    val cardPadding = when (cardSize) {
        CardSize.SMALL -> 12.dp
        CardSize.MEDIUM -> 14.dp
        CardSize.LARGE -> 16.dp
    }

    AppSectionSurface {
        Surface(
            onClick = onClick,
            color = if (isCurrentProvider) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.52f)
            } else {
                Color.Transparent
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(cardPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
            // 顶部区域：图标 + 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // 提供商图标
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (isCurrentProvider)
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    else
                        MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(iconSize)
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = provider.providerType.icon(),
                            contentDescription = provider.providerType.displayName,
                            tint = if (isCurrentProvider)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(iconInnerSize)
                        )
                    }
                }

                // 操作按钮（三点菜单）
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "更多操作",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        if (!isCurrentProvider) {
                            DropdownMenuItem(
                                text = { Text("设为当前") },
                                onClick = {
                                    showMenu = false
                                    onSetCurrent()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("编辑") },
                            onClick = {
                                showMenu = false
                                onClick()
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        )
                    }
                }
            }

            // 提供商名称
            Text(
                text = provider.name.ifEmpty { "未命名" },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            // 描述信息（如果有的话，显示提供商类型）
            if (provider.providerType.displayName.isNotEmpty()) {
                Text(
                    text = provider.providerType.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 底部标签区域
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 启用/禁用状态标签
                AppPill(
                    text = if (isCurrentProvider) "启用" else "未设为当前",
                    containerColor = if (isCurrentProvider) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    contentColor = if (isCurrentProvider) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                AppPill(
                    text = "${provider.availableModels.size} 个模型",
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        }
    }
}
