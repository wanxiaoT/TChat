package com.tchat.feature.chat

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

@Composable
internal fun SoftInputModeEffect(softInputMode: Int) {
    val view = LocalView.current
    DisposableEffect(view, softInputMode) {
        val activity = view.context.findActivity()
        if (activity == null) {
            onDispose { }
        } else {
            val window = activity.window
            val originalMode = window.attributes.softInputMode
            window.setSoftInputMode(softInputMode)
            onDispose {
                window.setSoftInputMode(originalMode)
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}

