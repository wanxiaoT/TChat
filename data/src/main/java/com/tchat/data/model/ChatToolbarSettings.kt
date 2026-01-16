package com.tchat.data.model

/**
 * 聊天输入框上方的工具栏按钮类型
 */
enum class ChatToolbarItem {
    MODEL,
    TOOLS,
    DEEP_RESEARCH,
    JUNGLE_HELPER
}

data class ChatToolbarItemConfig(
    val item: ChatToolbarItem,
    val visible: Boolean = true
)

/**
 * 聊天输入框上方工具栏的显示与顺序配置
 */
data class ChatToolbarSettings(
    val items: List<ChatToolbarItemConfig> = defaultItems()
) {
    companion object {
        private val DEFAULT_ORDER = listOf(
            ChatToolbarItem.MODEL,
            ChatToolbarItem.DEEP_RESEARCH,
            ChatToolbarItem.TOOLS,
            ChatToolbarItem.JUNGLE_HELPER
        )

        fun defaultItems(): List<ChatToolbarItemConfig> {
            val order = DEFAULT_ORDER + ChatToolbarItem.entries.filterNot(DEFAULT_ORDER::contains)
            return order.map { ChatToolbarItemConfig(it, true) }
        }
    }

    /**
     * 保证包含所有已知按钮，去重并补全缺失项（按默认顺序追加）
     */
    fun normalized(): ChatToolbarSettings {
        val seen = mutableSetOf<ChatToolbarItem>()
        val normalizedItems = mutableListOf<ChatToolbarItemConfig>()

        for (config in items) {
            if (seen.add(config.item)) {
                normalizedItems.add(config)
            }
        }

        val defaultOrder = DEFAULT_ORDER + ChatToolbarItem.entries.filterNot(DEFAULT_ORDER::contains)
        for (item in defaultOrder) {
            if (seen.add(item)) {
                normalizedItems.add(ChatToolbarItemConfig(item, true))
            }
        }

        return if (normalizedItems == items) this else ChatToolbarSettings(normalizedItems)
    }
}
