package com.tchat.feature.chat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.tchat.data.model.MessagePart
import com.tchat.designsystem.LocalReducedMotion
import com.tchat.designsystem.Motion
import com.tchat.designsystem.Spacing

@Composable
fun MessageInput(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    mediaParts: List<MessagePart> = emptyList(),
    onPickMedia: (() -> Unit)? = null,
    onRemoveMedia: ((MessagePart) -> Unit)? = null,
    onGenerateImage: (() -> Unit)? = null,
    replyPreview: String? = null,
    onClearReply: (() -> Unit)? = null,
    // i18n strings
    inputHint: String = "输入消息...",
    sendContentDescription: String = "发送",
    generateImageContentDescription: String = "生成图片",
    attachContentDescription: String = "添加图片/视频"
) {
    val thumbSizePx = with(LocalDensity.current) { 72.dp.roundToPx() }
    var isInputFocused by remember { mutableStateOf(false) }
    val reducedMotion = LocalReducedMotion.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        val sendEnabled = text.isNotBlank() || mediaParts.isNotEmpty()
        val utilityButtonColors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (!replyPreview.isNullOrBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)),
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Icon(
                        imageVector = Icons.Filled.FormatQuote,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = replyPreview,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                    if (onClearReply != null) {
                        IconButton(
                            onClick = onClearReply,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "取消引用",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        if (mediaParts.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                mediaParts.forEach { part ->
                    when (part) {
                        is MessagePart.Image -> {
                            MediaThumb(
                                label = part.fileName ?: "图片",
                                bitmap = rememberScaledBitmap(part.filePath, thumbSizePx),
                                onRemove = if (onRemoveMedia != null) {
                                    { onRemoveMedia(part) }
                                } else {
                                    null
                                }
                            )
                        }
                        is MessagePart.Video -> {
                            MediaChip(
                                icon = Icons.Outlined.Videocam,
                                label = part.fileName ?: "视频",
                                onRemove = if (onRemoveMedia != null) {
                                    { onRemoveMedia(part) }
                                } else {
                                    null
                                }
                            )
                        }
                        else -> Unit
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            if (onPickMedia != null) {
                FilledTonalIconButton(
                    onClick = onPickMedia,
                    modifier = Modifier.size(38.dp),
                    shape = CircleShape,
                    colors = utilityButtonColors
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AddPhotoAlternate,
                        contentDescription = attachContentDescription
                    )
                }
            }

            if (onGenerateImage != null) {
                FilledTonalIconButton(
                    onClick = onGenerateImage,
                    enabled = text.isNotBlank() && mediaParts.isEmpty(),
                    modifier = Modifier.size(38.dp),
                    shape = CircleShape,
                    colors = utilityButtonColors
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = generateImageContentDescription
                    )
                }
            }

            val inputShape = MaterialTheme.shapes.extraLarge

            val inputBorderWidth = animateDpAsState(
                targetValue = if (isInputFocused) 1.25.dp else 1.dp,
                label = "messageInputBorderWidth"
            ).value
            val inputBorderColor = animateColorAsState(
                targetValue = when {
                    isInputFocused -> MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
                    sendEnabled -> MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)
                    else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f)
                },
                label = "messageInputBorderColor"
            ).value
            val inputContainerColor = animateColorAsState(
                targetValue = when {
                    isInputFocused -> MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                    else -> MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp).copy(alpha = 0.78f)
                },
                label = "messageInputContainerColor"
            ).value

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .animateContentSize(
                        animationSpec = Motion.snapIfReduced(reducedMotion, Motion.sheetTransition())
                    ),
                shape = inputShape,
                color = inputContainerColor,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                border = BorderStroke(inputBorderWidth, inputBorderColor)
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 42.dp, max = 128.dp)
                        .onFocusChanged { isInputFocused = it.isFocused }
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    minLines = 1,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (sendEnabled) onSend()
                        }
                    ),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (text.isEmpty()) {
                                Text(
                                    text = inputHint,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }

            val sendInteractionSource = remember { MutableInteractionSource() }
            val sendPressed by sendInteractionSource.collectIsPressedAsState()
            val sendScale by animateFloatAsState(
                targetValue = if (sendPressed && sendEnabled) 0.96f else 1f,
                animationSpec = Motion.snapIfReduced(reducedMotion, Motion.pressFeedback()),
                label = "send_button_scale"
            )
            FilledIconButton(
                onClick = onSend,
                enabled = sendEnabled,
                modifier = Modifier
                    .size(40.dp)
                    .graphicsLayer {
                        scaleX = sendScale
                        scaleY = sendScale
                    },
                shape = CircleShape,
                interactionSource = sendInteractionSource,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = sendContentDescription
                )
            }
        }
    }
}

@Composable
private fun MediaThumb(
    label: String,
    bitmap: android.graphics.Bitmap?,
    onRemove: (() -> Unit)?
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(MaterialTheme.shapes.medium)
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                MaterialTheme.shapes.medium
            )
    ) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = label,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxSize()
            ) {}
        }

        if (onRemove != null) {
            FilledIconButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(20.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "移除",
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun MediaChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onRemove: (() -> Unit)?
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text = label, style = MaterialTheme.typography.bodySmall)
            if (onRemove != null) {
                IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = "移除", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
