package com.tchat.feature.chat

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import android.view.WindowManager

@Suppress("DEPRECATION")
internal val SoftInputAdjustResize: Int = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE

@Suppress("DEPRECATION")
internal val SoftInputAdjustNothing: Int = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING

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
            val preservedState = originalMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_STATE
            window.setSoftInputMode(preservedState or softInputMode)
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
