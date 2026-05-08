package com.tchat.feature.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.tchat.data.model.Message

@Composable
fun MessageList(
    messages: List<Message>,
    streamingMessage: Message? = null,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    providerIcon: ImageVector? = null,
    modelName: String = "",
    isLoadingHistory: Boolean = false,
    onRegenerate: ((userMessageId: String, aiMessageId: String) -> Unit)? = null,
    onSelectVariant: ((messageId: String, variantIndex: Int) -> Unit)? = null,
    onCopy: ((content: String) -> Unit)? = null,
    onSpeak: ((content: String) -> Unit)? = null,
    onShare: ((content: String) -> Unit)? = null,
    onDelete: ((messageId: String) -> Unit)? = null
) {
    val trailingStreamingMessage = streamingMessage?.takeIf { candidate ->
        messages.none { it.id == candidate.id }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isLoadingHistory) {
            item(key = "history_loading") {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        }

        itemsIndexed(
            items = messages,
            key = { _, message -> message.id }
        ) { index, message ->
            val renderedMessage = streamingMessage?.takeIf { it.id == message.id } ?: message
            // 获取上一条消息（用于判断是否显示重新生成按钮）
            val previousMessage = messages.getOrNull(index - 1)?.let { previous ->
                streamingMessage?.takeIf { it.id == previous.id } ?: previous
            }

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                MessageItem(
                    message = renderedMessage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 760.dp),
                    providerIcon = providerIcon,
                    modelName = modelName,
                    previousMessage = previousMessage,
                    onRegenerate = onRegenerate,
                    onSelectVariant = onSelectVariant,
                    onCopy = onCopy,
                    onSpeak = onSpeak,
                    onShare = onShare,
                    onDelete = onDelete
                )
            }
        }

        if (trailingStreamingMessage != null) {
            item(key = "streaming_${trailingStreamingMessage.id}") {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    MessageItem(
                        message = trailingStreamingMessage,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 760.dp),
                        providerIcon = providerIcon,
                        modelName = modelName,
                        previousMessage = messages.lastOrNull(),
                        onRegenerate = onRegenerate,
                        onSelectVariant = onSelectVariant,
                        onCopy = onCopy,
                        onSpeak = onSpeak,
                        onShare = onShare,
                        onDelete = onDelete
                    )
                }
            }
        }
    }
}
