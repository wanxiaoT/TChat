package com.tchat.feature.chat

import android.graphics.BitmapFactory
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
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
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

                // 输入框
                TextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            inputHint,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    maxLines = 4,
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                        unfocusedIndicatorColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )

                // 发送按钮
                FilledIconButton(
                    onClick = onSend,
                    enabled = text.isNotBlank() || mediaParts.isNotEmpty(),
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
