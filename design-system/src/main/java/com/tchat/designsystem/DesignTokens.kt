package com.tchat.designsystem

import android.provider.Settings
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

object ChatColors {
    val userBubbleContainer: Color
        @Composable get() = MaterialTheme.colorScheme.secondaryContainer
    val onUserBubble: Color
        @Composable get() = MaterialTheme.colorScheme.onSecondaryContainer
    val aiBubbleSurface: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh
    val modelBadgeBackground: Color
        @Composable get() = MaterialTheme.colorScheme.tertiaryContainer
    val modelBadgeText: Color
        @Composable get() = MaterialTheme.colorScheme.onTertiaryContainer
}

object AppColors {
    val linkText: Color
        @Composable get() {
            val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
            return if (isDark) linkTextDark else linkTextLight
        }
}

object AppShapes {
    val avatarSmall = RoundedCornerShape(24.dp)
    val avatarLarge = RoundedCornerShape(40.dp)
    val sheetTop = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
}

val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }
}
