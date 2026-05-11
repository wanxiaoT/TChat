package com.tchat.designsystem

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

object Motion {
    inline fun <reified T> pageTransition(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    inline fun <reified T> listItemEnter(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow
    )

    inline fun <reified T> pressFeedback(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh
    )

    inline fun <reified T> sheetTransition(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )

    val fadeTween: FiniteAnimationSpec<Float> = tween(
        durationMillis = 150,
        easing = FastOutSlowInEasing
    )

    inline fun <reified T> snapIfReduced(
        reducedMotion: Boolean,
        spec: FiniteAnimationSpec<T>
    ): FiniteAnimationSpec<T> = if (reducedMotion) snap() else spec

    fun fade(reducedMotion: Boolean): FiniteAnimationSpec<Float> =
        if (reducedMotion) snap() else fadeTween
}
