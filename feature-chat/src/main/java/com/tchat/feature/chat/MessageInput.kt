package com.tchat.feature.chat

import android.graphics.BitmapFactory
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.tchat.data.model.MessagePart

@Composable
fun MessageInput(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    mediaParts: List<MessagePart> = emptyList(),
    onPickMedia: (() -> Unit)? = null,
    onRemoveMedia: ((MessagePart) -> Unit)? = null,
    onGenerateImage: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    // i18n strings
    inputHint: String = "输入消息...",
    sendContentDescription: String = "发送",
    generateImageContentDescription: String = "生成图片",
    attachContentDescription: String = "添加图片/视频"
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val sendEnabled = text.isNotBlank() || mediaParts.isNotEmpty()

        if (mediaParts.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                mediaParts.forEach { part ->
                    when (part) {
                        is MessagePart.Image -> {
                            MediaThumb(
                                label = part.fileName ?: "图片",
                                bitmap = remember(part.filePath) {
                                    runCatching { BitmapFactory.decodeFile(part.filePath) }.getOrNull()
                                },
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
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (onPickMedia != null) {
                FilledTonalIconButton(
                    onClick = onPickMedia,
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape
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
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = generateImageContentDescription
                    )
                }
            }

            val inputShape = MaterialTheme.shapes.extraLarge
            val inputInteractionSource = remember { MutableInteractionSource() }
            val isInputFocused = inputInteractionSource.collectIsFocusedAsState().value

            val inputBorderWidth = animateDpAsState(
                targetValue = if (isInputFocused) 1.5.dp else 1.dp,
                label = "messageInputBorderWidth"
            ).value
            val inputBorderColor = animateColorAsState(
                targetValue = when {
                    isInputFocused -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                    sendEnabled -> MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                    else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                },
                label = "messageInputBorderColor"
            ).value
            val inputContainerColor = animateColorAsState(
                targetValue = when {
                    isInputFocused -> MaterialTheme.colorScheme.surface
                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                },
                label = "messageInputContainerColor"
            ).value
            val inputShadowElevation = animateDpAsState(
                targetValue = when {
                    isInputFocused -> 10.dp
                    sendEnabled -> 6.dp
                    else -> 2.dp
                },
                label = "messageInputShadowElevation"
            ).value

            // 输入框（高级感：柔和阴影 + 动态描边 + 平滑高度变化）
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .animateContentSize(
                        animationSpec = spring(
                            stiffness = Spring.StiffnessMediumLow,
                            dampingRatio = Spring.DampingRatioNoBouncy
                        )
                    ),
                shape = inputShape,
                color = inputContainerColor,
                tonalElevation = 0.dp,
                shadowElevation = inputShadowElevation,
                border = BorderStroke(inputBorderWidth, inputBorderColor)
            ) {
                TextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = inputHint,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    minLines = 1,
                    maxLines = 4,
                    interactionSource = inputInteractionSource,
                    shape = inputShape,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            // 发送按钮
            val sendShadowElevation = animateDpAsState(
                targetValue = if (sendEnabled) 10.dp else 0.dp,
                label = "messageSendShadowElevation"
            ).value
            FilledIconButton(
                onClick = onSend,
                enabled = sendEnabled,
                modifier = Modifier.shadow(sendShadowElevation, CircleShape, clip = false),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
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
            .size(56.dp)
            .clip(MaterialTheme.shapes.medium)
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
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
