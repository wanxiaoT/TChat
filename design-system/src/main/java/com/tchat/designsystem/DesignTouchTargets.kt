package com.tchat.designsystem

import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

fun Modifier.tchatTouchTarget(): Modifier =
    sizeIn(minWidth = 48.dp, minHeight = 48.dp)
