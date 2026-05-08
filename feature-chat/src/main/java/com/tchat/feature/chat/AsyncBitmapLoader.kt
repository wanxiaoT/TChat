package com.tchat.feature.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

@Composable
internal fun rememberScaledBitmap(
    filePath: String,
    targetSizePx: Int
): Bitmap? {
    val resolvedTargetSize = targetSizePx.coerceAtLeast(1)
    val bitmapState by produceState<Bitmap?>(initialValue = null, filePath, resolvedTargetSize) {
        value = withContext(Dispatchers.IO) {
            decodeSampledBitmap(filePath, resolvedTargetSize)
        }
    }
    return bitmapState
}

private fun decodeSampledBitmap(filePath: String, targetSizePx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeFile(filePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        return null
    }

    var sampleSize = 1
    while (max(bounds.outWidth / sampleSize, bounds.outHeight / sampleSize) > targetSizePx) {
        sampleSize *= 2
    }

    return BitmapFactory.decodeFile(
        filePath,
        BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
    )
}
