package com.tchat.wanxiaot.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.tchat.designsystem.DesignSystemTheme
import com.tchat.wanxiaot.i18n.Language
import com.tchat.wanxiaot.i18n.ProvideStrings

@Composable
fun TChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    language: Language = Language.ZH_CN,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorSchemeOverride = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> null
    }
    val view = LocalView.current

    ProvideStrings(language = language) {
        DesignSystemTheme(
            darkTheme = darkTheme,
            colorSchemeOverride = colorSchemeOverride
        ) {
            val colorScheme = MaterialTheme.colorScheme
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as Activity).window
                    window.statusBarColor = Color.Transparent.toArgb()
                    window.navigationBarColor = colorScheme.surface
                        .copy(alpha = if (darkTheme) 0.9f else 0.96f)
                        .toArgb()
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = !darkTheme
                        isAppearanceLightNavigationBars = !darkTheme
                    }
                }
            }
            content()
        }
    }
}
