package com.tchat.designsystem

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlin.math.absoluteValue

enum class AvatarSize(val dp: Dp) {
    SMALL(40.dp),
    MEDIUM(48.dp),
    LARGE(64.dp)
}

enum class StatusIndicator {
    ONLINE,
    TYPING,
    MUTED,
    NONE
}

@Composable
fun ChatAvatar(
    name: String,
    avatarUrl: String?,
    modifier: Modifier = Modifier,
    size: AvatarSize = AvatarSize.MEDIUM,
    status: StatusIndicator = StatusIndicator.NONE,
    onClick: (() -> Unit)? = null
) {
    val reducedMotion = LocalReducedMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && onClick != null) 0.96f else 1f,
        animationSpec = Motion.snapIfReduced(reducedMotion, Motion.pressFeedback()),
        label = "avatar_press"
    )
    val avatarColor = remember(name) { avatarColorFor(name) }
    val initials = remember(name) { name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "T" }

    val container = @Composable {
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(avatarColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                textAlign = TextAlign.Center
            )
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .matchParentSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
            StatusDot(
                status = status,
                size = size,
                reducedMotion = reducedMotion,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier
                .size(size.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            shape = CircleShape,
            color = Color.Transparent,
            interactionSource = interactionSource,
            tonalElevation = 0.dp
        ) {
            container()
        }
    } else {
        Box(
            modifier = modifier
                .size(size.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
        ) {
            container()
        }
    }
}

@Composable
private fun StatusDot(
    status: StatusIndicator,
    size: AvatarSize,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    if (status == StatusIndicator.NONE) return

    val dotSize = when (size) {
        AvatarSize.SMALL -> 8.dp
        AvatarSize.MEDIUM -> 10.dp
        AvatarSize.LARGE -> 12.dp
    }
    val color = when (status) {
        StatusIndicator.ONLINE -> Color(0xFF2E7D32)
        StatusIndicator.TYPING -> MaterialTheme.colorScheme.primary
        StatusIndicator.MUTED -> MaterialTheme.colorScheme.outline
        StatusIndicator.NONE -> Color.Transparent
    }
    val transition = rememberInfiniteTransition(label = "avatar_status")
    val alpha by transition.animateFloat(
        initialValue = 0.62f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "avatar_status_alpha"
    )

    Box(
        modifier = modifier
            .size(dotSize)
            .border(BorderStroke(1.5.dp, MaterialTheme.colorScheme.surface), CircleShape)
            .clip(CircleShape)
            .background(color.copy(alpha = if (reducedMotion) 1f else alpha))
    )
}

private fun avatarColorFor(name: String): Color {
    val palette = listOf(
        Color(0xFF1565C0),
        Color(0xFF2E7D32),
        Color(0xFF6B5778),
        Color(0xFF546E7A),
        Color(0xFF5E35B1),
        Color(0xFF00838F)
    )
    return palette[name.hashCode().absoluteValue % palette.size]
}
