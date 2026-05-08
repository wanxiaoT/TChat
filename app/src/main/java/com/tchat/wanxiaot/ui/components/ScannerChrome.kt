package com.tchat.wanxiaot.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ScannerTopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    AppSectionSurface(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回"
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                content = actions
            )
        }
    }
}

@Composable
fun ScannerHintCard(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    AppSectionSurface(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = trailing
            )
        }
    }
}

@Composable
fun ScannerFrame(
    modifier: Modifier = Modifier,
    size: Dp = 272.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.24f),
                shape = RoundedCornerShape(28.dp)
            )
    ) {
        ScannerCornerOverlay()
    }
}

@Composable
private fun ScannerCornerOverlay() {
    val cornerLength = 38.dp
    val cornerWidth = 4.dp
    val color = MaterialTheme.colorScheme.primary

    Box(modifier = Modifier.fillMaxSize()) {
        Corner(color, cornerLength, cornerWidth, Alignment.TopStart)
        Corner(color, cornerLength, cornerWidth, Alignment.TopEnd)
        Corner(color, cornerLength, cornerWidth, Alignment.BottomStart)
        Corner(color, cornerLength, cornerWidth, Alignment.BottomEnd)
    }
}

@Composable
private fun BoxScope.Corner(
    color: Color,
    cornerLength: Dp,
    cornerWidth: Dp,
    alignment: Alignment
) {
    Box(
        modifier = Modifier
            .align(alignment)
            .size(cornerLength)
    ) {
        Surface(
            modifier = Modifier
                .align(
                    when (alignment) {
                        Alignment.TopStart, Alignment.TopEnd -> Alignment.TopStart
                        Alignment.BottomStart, Alignment.BottomEnd -> Alignment.BottomStart
                        else -> Alignment.TopStart
                    }
                )
                .width(cornerLength)
                .height(cornerWidth),
            color = color
        ) {}
        Surface(
            modifier = Modifier
                .align(
                    when (alignment) {
                        Alignment.TopStart, Alignment.BottomStart -> Alignment.TopStart
                        Alignment.TopEnd, Alignment.BottomEnd -> Alignment.TopEnd
                        else -> Alignment.TopStart
                    }
                )
                .width(cornerWidth)
                .height(cornerLength),
            color = color
        ) {}
    }
}
