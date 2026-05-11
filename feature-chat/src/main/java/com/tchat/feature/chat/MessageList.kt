package com.tchat.feature.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.tchat.data.model.Message
import com.tchat.designsystem.LocalReducedMotion
import com.tchat.designsystem.Motion
import com.tchat.designsystem.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MessageList(
    messages: List<Message>,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    streamingMessage: Message? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    providerIcon: ImageVector? = null,
    modelName: String = "",
    isLoadingHistory: Boolean = false,
    onRegenerate: ((userMessageId: String, aiMessageId: String) -> Unit)? = null,
    onSelectVariant: ((messageId: String, variantIndex: Int) -> Unit)? = null,
    onCopy: ((content: String) -> Unit)? = null,
    onSpeak: ((content: String) -> Unit)? = null,
    onShare: ((content: String) -> Unit)? = null,
    onDelete: ((messageId: String) -> Unit)? = null,
    onToggleBookmark: ((Message) -> Unit)? = null,
    onReply: ((Message) -> Unit)? = null,
    onCreateBranch: ((Message) -> Unit)? = null,
    onQuoteClick: ((String) -> Unit)? = null
) {
    val trailingStreamingMessage = streamingMessage?.takeIf { candidate ->
        messages.none { it.id == candidate.id }
    }
    val reducedMotion = LocalReducedMotion.current
    val seenMessageKeys = remember { mutableStateListOf<String>() }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        if (isLoadingHistory) {
            item(
                key = "history_loading",
                contentType = "history_loading"
            ) {
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
            key = { _, message -> message.id },
            contentType = { _, message ->
                val renderedMessage = streamingMessage?.takeIf { it.id == message.id } ?: message
                renderedMessage.listContentType()
            }
        ) { index, message ->
            val renderedMessage = streamingMessage?.takeIf { it.id == message.id } ?: message
            val shouldAnimate = message.id !in seenMessageKeys && !reducedMotion
            val alpha = remember(message.id) { Animatable(if (shouldAnimate) 0f else 1f) }
            val translationY = remember(message.id) { Animatable(if (shouldAnimate) 30f else 0f) }
            // 获取上一条消息（用于判断是否显示重新生成按钮）
            val previousMessage = messages.getOrNull(index - 1)?.let { previous ->
                streamingMessage?.takeIf { it.id == previous.id } ?: previous
            }

            LaunchedEffect(message.id, reducedMotion) {
                if (shouldAnimate) {
                    delay(index.coerceAtMost(MAX_STAGGERED_ENTRIES) * ENTER_STAGGER_MS)
                    launch { alpha.animateTo(1f, Motion.listItemEnter()) }
                    translationY.animateTo(0f, Motion.listItemEnter())
                    seenMessageKeys.add(message.id)
                } else {
                    alpha.snapTo(1f)
                    translationY.snapTo(0f)
                    if (message.id !in seenMessageKeys) {
                        seenMessageKeys.add(message.id)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        this.alpha = alpha.value
                        this.translationY = translationY.value
                    },
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
                    onDelete = onDelete,
                    onToggleBookmark = onToggleBookmark,
                    onReply = onReply,
                    onCreateBranch = onCreateBranch,
                    onQuoteClick = onQuoteClick
                )
            }
        }

        if (trailingStreamingMessage != null) {
            item(
                key = "streaming_${trailingStreamingMessage.id}",
                contentType = trailingStreamingMessage.listContentType()
            ) {
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
                        onDelete = onDelete,
                        onToggleBookmark = onToggleBookmark,
                        onReply = onReply,
                        onCreateBranch = onCreateBranch,
                        onQuoteClick = onQuoteClick
                    )
                }
            }
        }
    }
}

private fun Message.listContentType(): String {
    return when {
        isStreaming -> "streaming_${role.name.lowercase()}"
        else -> role.name.lowercase()
    }
}

private const val ENTER_STAGGER_MS = 24L
private const val MAX_STAGGERED_ENTRIES = 8
