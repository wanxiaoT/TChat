package com.tchat.wanxiaot.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplaySettingsScreen(
    settingsManager: SettingsManager,
    onBack: () -> Unit,
    showTopBar: Boolean = true
) {
    val settings by settingsManager.settings.collectAsState()
    val toolbarSettings = settings.chatToolbarSettings.normalized()

    fun updateToolbarSettings(update: (ChatToolbarSettings) -> ChatToolbarSettings) {
        settingsManager.updateChatToolbarSettings(update(toolbarSettings))
    }

    Scaffold(
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = { Text(strings.settingsDisplay) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = strings.back
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = strings.settingsDisplayDesc,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    itemsIndexed(toolbarSettings.items, key = { _, config -> config.item.name }) { index, config ->
                        val isFirst = index == 0
                        val isLast = index == toolbarSettings.items.lastIndex

                        ListItem(
                            leadingContent = {
                                Icon(
                                    imageVector = getToolbarItemIcon(config.item),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            headlineContent = {
                                Text(getToolbarItemTitle(config.item))
                            },
                            trailingContent = {
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
                        )
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
    androidx.compose.foundation.layout.Row(
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
