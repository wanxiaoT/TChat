package com.tchat.feature.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.tchat.data.model.Message

@Composable
fun MessageList(
    messages: List<Message>,
    modifier: Modifier = Modifier,
    providerIcon: ImageVector? = null,
    modelName: String = "",
    onRegenerate: ((userMessageId: String, aiMessageId: String) -> Unit)? = null,
    onSelectVariant: ((messageId: String, variantIndex: Int) -> Unit)? = null
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(
            items = messages,
            key = { _, message -> message.id }
        ) { index, message ->
            // 获取下一条消息（用于判断是否显示重新生成按钮）
            val nextMessage = messages.getOrNull(index + 1)

            MessageItem(
                message = message,
                providerIcon = providerIcon,
                modelName = modelName,
                nextMessage = nextMessage,
                onRegenerate = onRegenerate,
                onSelectVariant = onSelectVariant
            )
        }
    }
}
