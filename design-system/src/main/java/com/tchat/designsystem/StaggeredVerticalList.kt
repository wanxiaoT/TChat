package com.tchat.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun <T> StaggeredVerticalList(
    items: List<T>,
    modifier: Modifier = Modifier,
    stableKey: (T) -> Any,
    isFirstRender: Boolean,
    content: @Composable LazyItemScope.(T) -> Unit
) {
    val reducedMotion = LocalReducedMotion.current
    val seenKeys = remember { mutableStateListOf<Any>() }

    LazyColumn(modifier = modifier) {
        itemsIndexed(items, key = { _, item -> stableKey(item) }) { index, item ->
            val key = stableKey(item)
            val shouldAnimate = isFirstRender && key !in seenKeys && !reducedMotion
            val alpha = remember(key) { Animatable(if (shouldAnimate) 0f else 1f) }
            val translationY = remember(key) { Animatable(if (shouldAnimate) 30f else 0f) }

            LaunchedEffect(key, reducedMotion) {
                if (shouldAnimate) {
                    delay(index * 40L)
                    launch { alpha.animateTo(1f, Motion.listItemEnter()) }
                    translationY.animateTo(0f, Motion.listItemEnter())
                    seenKeys.add(key)
                } else {
                    alpha.snapTo(1f)
                    translationY.snapTo(0f)
                    if (key !in seenKeys) {
                        seenKeys.add(key)
                    }
                }
            }

            Box(
                modifier = Modifier.graphicsLayer {
                    this.alpha = alpha.value
                    this.translationY = translationY.value
                }
            ) {
                content(item)
            }
        }
    }
}
