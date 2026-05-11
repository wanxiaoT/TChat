package com.tchat.feature.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
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
        val cacheKey = "$filePath#$resolvedTargetSize"
        BitmapMemoryCache.get(cacheKey)?.let { cached ->
            value = cached
            return@produceState
        }

        value = withContext(Dispatchers.IO) {
            decodeSampledBitmap(filePath, resolvedTargetSize)?.also { bitmap ->
                BitmapMemoryCache.put(cacheKey, bitmap)
            }
        }
    }
    return bitmapState
}

private object BitmapMemoryCache {
    private const val MAX_CACHE_BYTES = 24 * 1024 * 1024

    private val cache = object : LruCache<String, Bitmap>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount
        }
    }

    fun get(key: String): Bitmap? = cache.get(key)

    fun put(key: String, bitmap: Bitmap) {
        if (bitmap.byteCount <= MAX_CACHE_BYTES) {
            cache.put(key, bitmap)
        }
    }
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
