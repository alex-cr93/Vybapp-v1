package com.example.core

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Thread-safe in-memory LRU cache and background decoder for inline Base64 data URIs.
 * Prevents main-thread frame drops while scrolling posts, stories, and avatars in the feed.
 */
object BitmapMemoryCache {
    private const val MAX_CACHE_ENTRIES = 64

    private val cache = object : LruCache<String, ImageBitmap>(MAX_CACHE_ENTRIES) {}

    private fun cacheKey(dataUri: String, maxDim: Int): String =
        "${maxDim}_${dataUri.length}_${dataUri.hashCode()}"

    fun getCached(dataUri: String?, maxDim: Int = 720): ImageBitmap? {
        if (dataUri.isNullOrBlank() || !dataUri.startsWith("data:image")) return null
        return cache.get(cacheKey(dataUri, maxDim))
    }

    suspend fun decodeDataUriCached(dataUri: String?, maxDim: Int = 720): ImageBitmap? {
        if (dataUri.isNullOrBlank() || !dataUri.startsWith("data:image")) return null
        val key = cacheKey(dataUri, maxDim)
        cache.get(key)?.let { return it }

        return withContext(Dispatchers.Default) {
            cache.get(key)?.let { return@withContext it }
            val bytes = MediaUtils.decodeBase64DataUri(dataUri) ?: return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

            var sampleSize = 1
            val largest = max(bounds.outWidth, bounds.outHeight)
            if (largest > maxDim && maxDim > 0) {
                while (largest / sampleSize > maxDim) {
                    sampleSize *= 2
                }
            }

            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
            }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
            if (decoded != null) {
                cache.put(key, decoded)
            }
            decoded
        }
    }
}
