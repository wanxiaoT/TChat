package com.tchat.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

private val ComposerDockShape = RoundedCornerShape(28.dp)

@Composable
internal fun ChatBackdrop(modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    val baseTop = lerp(colorScheme.background, colorScheme.surface, 0.18f)
    val baseMiddle = colorScheme.surfaceColorAtElevation(0.dp)
    val baseBottom = colorScheme.surfaceColorAtElevation(1.dp)
    val topSheen = Color.White.copy(alpha = 0.12f)
    val bottomDepth = lerp(colorScheme.surfaceContainerLow, colorScheme.surface, 0.82f).copy(alpha = 0.42f)
    val sideFade = colorScheme.primary.copy(alpha = 0.028f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        baseTop,
                        baseMiddle,
                        baseBottom
                    )
                )
            )
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(topSheen, Color.Transparent),
                        startY = 0f,
                        endY = size.height * 0.22f
                    )
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, bottomDepth),
                        startY = size.height * 0.58f,
                        endY = size.height
                    )
                )
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(sideFade, Color.Transparent, sideFade),
                        startX = 0f,
                        endX = size.width
                    )
                )
            }
    )
}

@Composable
internal fun ChatComposerDock(
    modifier: Modifier = Modifier,
    imeVisible: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val containerColor = colorScheme.surface.copy(alpha = 0.94f)
    val borderColor = colorScheme.outlineVariant.copy(alpha = 0.42f)
    val overlayTop = Color.White.copy(alpha = 0.22f)
    val overlayBottom = colorScheme.surfaceColorAtElevation(2.dp).copy(alpha = 0.76f)
    val outerTopPadding = if (imeVisible) 4.dp else 8.dp
    val outerBottomPadding = if (imeVisible) 2.dp else 8.dp
    val contentVerticalPadding = if (imeVisible) 4.dp else 6.dp
    val itemSpacing = if (imeVisible) 2.dp else 4.dp

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = 10.dp,
                top = outerTopPadding,
                end = 10.dp,
                bottom = outerBottomPadding
            ),
        shape = ComposerDockShape,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier.background(
                Brush.verticalGradient(
                    colors = listOf(
                        overlayTop,
                        overlayBottom
                    )
                )
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = contentVerticalPadding),
                verticalArrangement = Arrangement.spacedBy(itemSpacing),
                content = content
            )
        }
    }
}
