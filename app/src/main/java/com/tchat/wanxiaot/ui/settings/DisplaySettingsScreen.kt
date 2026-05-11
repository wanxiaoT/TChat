package com.tchat.wanxiaot.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.draw.scale
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
import com.tchat.wanxiaot.ui.components.AppPageScaffold
import com.tchat.wanxiaot.ui.components.AppPill
import com.tchat.wanxiaot.ui.components.SettingsGroupCard
import com.tchat.wanxiaot.ui.components.SettingsSurface

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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            item {
                SettingsGroupCard(
                    title = "聊天工具栏",
                    description = "打开或关闭具体入口，并通过上下箭头调整顺序。",
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    toolbarSettings.items.forEachIndexed { index, config ->
                        val isFirst = index == 0
                        val isLast = index == toolbarSettings.items.lastIndex

                        SettingsSurface {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = getToolbarItemIcon(config.item),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
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
                                        },
                                        modifier = Modifier.scale(0.86f)
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

@Composable
private fun RowButtons(
    moveUpEnabled: Boolean,
    moveDownEnabled: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onMoveUp,
            enabled = moveUpEnabled,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }
        IconButton(
            onClick = onMoveDown,
            enabled = moveDownEnabled,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
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
