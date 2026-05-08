package com.tchat.wanxiaot.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Swords
import com.composables.icons.lucide.Wrench
import com.tchat.data.model.ChatToolbarItem
import com.tchat.data.model.ChatToolbarItemConfig
import com.tchat.data.model.ChatToolbarSettings
import com.tchat.wanxiaot.i18n.strings
import com.tchat.wanxiaot.settings.SettingsManager
import com.tchat.wanxiaot.ui.components.AppHeroCard
import com.tchat.wanxiaot.ui.components.AppPageScaffold
import com.tchat.wanxiaot.ui.components.AppPill
import com.tchat.wanxiaot.ui.components.AppSectionCard
import com.tchat.wanxiaot.ui.components.AppSectionSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplaySettingsScreen(
    settingsManager: SettingsManager,
    onBack: () -> Unit,
    showTopBar: Boolean = true
) {
    val settings by settingsManager.settings.collectAsStateWithLifecycle()
    val toolbarSettings = settings.chatToolbarSettings.normalized()

    fun updateToolbarSettings(update: (ChatToolbarSettings) -> ChatToolbarSettings) {
        settingsManager.updateChatToolbarSettings(update(toolbarSettings))
    }

    AppPageScaffold(
        title = strings.settingsDisplay,
        eyebrow = "Display",
        subtitle = strings.settingsDisplayDesc,
        showTopBar = showTopBar,
        onBack = onBack
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                AppHeroCard(
                    eyebrow = "Toolbar Layout",
                    title = "把对话工具栏整理成更顺手的工作顺序",
                    description = "你可以决定哪些入口显示，以及它们在聊天页中的排列位置。",
                    icon = Icons.Default.Psychology,
                    trailing = {
                        AppPill(text = "${toolbarSettings.items.count { it.visible }} 项显示中")
                    }
                )
            }

            item {
                AppSectionCard(
                    title = "聊天工具栏",
                    description = "打开或关闭具体入口，并通过上下箭头调整顺序。"
                ) {
                    toolbarSettings.items.forEachIndexed { index, config ->
                        val isFirst = index == 0
                        val isLast = index == toolbarSettings.items.lastIndex

                        AppSectionSurface {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = getToolbarItemIcon(config.item),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 14.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = getToolbarItemTitle(config.item),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    AppPill(
                                        text = if (config.visible) "已显示" else "已隐藏",
                                        containerColor = if (config.visible) {
                                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                        },
                                        contentColor = if (config.visible) {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                                Box(contentAlignment = Alignment.CenterEnd) {
                                    Column(
                                        horizontalAlignment = Alignment.End,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Switch(
                                            checked = config.visible,
                                            onCheckedChange = { checked ->
                                                updateToolbarSettings { current ->
                                                    current.copy(
                                                        items = current.items.map {
                                                            if (it.item == config.item) it.copy(visible = checked) else it
                                                        }
                                                    )
                                                }
                                            }
                                        )
                                        RowButtons(
                                            moveUpEnabled = !isFirst,
                                            moveDownEnabled = !isLast,
                                            onMoveUp = {
                                                updateToolbarSettings { current ->
                                                    current.copy(items = move(current.items, index, index - 1))
                                                }
                                            },
                                            onMoveDown = {
                                                updateToolbarSettings { current ->
                                                    current.copy(items = move(current.items, index, index + 1))
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowButtons(
    moveUpEnabled: Boolean,
    moveDownEnabled: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onMoveUp, enabled = moveUpEnabled) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = null
            )
        }
        IconButton(onClick = onMoveDown, enabled = moveDownEnabled) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null
            )
        }
    }
}

private fun move(
    items: List<ChatToolbarItemConfig>,
    fromIndex: Int,
    toIndex: Int
): List<ChatToolbarItemConfig> {
    if (fromIndex !in items.indices || toIndex !in items.indices || fromIndex == toIndex) return items
    val mutable = items.toMutableList()
    val item = mutable.removeAt(fromIndex)
    mutable.add(toIndex, item)
    return mutable
}

@Composable
private fun getToolbarItemTitle(item: ChatToolbarItem): String {
    return when (item) {
        ChatToolbarItem.MODEL -> strings.chatModel
        ChatToolbarItem.TOOLS -> strings.chatTools
        ChatToolbarItem.DEEP_RESEARCH -> strings.chatDeepResearch
        ChatToolbarItem.JUNGLE_HELPER -> strings.chatJungleHelper
    }
}

@Composable
private fun getToolbarItemIcon(item: ChatToolbarItem) = when (item) {
    ChatToolbarItem.MODEL -> Lucide.Sparkles
    ChatToolbarItem.TOOLS -> Lucide.Wrench
    ChatToolbarItem.DEEP_RESEARCH -> Icons.Default.Psychology
    ChatToolbarItem.JUNGLE_HELPER -> Lucide.Swords
}
