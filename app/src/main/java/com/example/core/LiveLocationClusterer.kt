package com.example.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.data.model.LiveLocationShare
import kotlin.math.min

data class LiveLocationCluster(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val members: List<LiveLocationShare>
) {
    val isCluster: Boolean get() = members.size > 1
    val primaryMember: LiveLocationShare get() = members.first()
}

object LiveLocationClusterer {
    private val avatarMarkerCache = object : LruCache<String, ImageBitmap>(40) {}

    fun clusterRadiusMetersForZoom(zoomLevel: Float): Double {
        return when {
            zoomLevel >= 17.5f -> 20.0
            zoomLevel >= 16.0f -> 55.0
            zoomLevel >= 14.5f -> 150.0
            zoomLevel >= 13.0f -> 400.0
            zoomLevel >= 11.5f -> 1200.0
            zoomLevel >= 9.5f -> 4000.0
            else -> 12000.0
        }
    }

    fun clusterLiveLocations(
        shares: List<LiveLocationShare>,
        zoomLevel: Float,
        maxClusterRadiusMetersOverride: Double? = null
    ): List<LiveLocationCluster> {
        if (shares.isEmpty()) return emptyList()
        val thresholdMeters = maxClusterRadiusMetersOverride ?: clusterRadiusMetersForZoom(zoomLevel)
        val sorted = shares.sortedBy { it.userId }
        val visited = BooleanArray(sorted.size)
        val clusters = mutableListOf<LiveLocationCluster>()

        for (i in sorted.indices) {
            if (visited[i]) continue
            visited[i] = true
            val seed = sorted[i]
            val group = mutableListOf(seed)

            for (j in i + 1 until sorted.size) {
                if (visited[j]) continue
                val candidate = sorted[j]
                val dist = LocationHelper.distanceMetersBetween(
                    seed.latitude,
                    seed.longitude,
                    candidate.latitude,
                    candidate.longitude
                )
                if (dist <= thresholdMeters) {
                    visited[j] = true
                    group.add(candidate)
                }
            }

            val avgLat = group.sumOf { it.latitude } / group.size
            val avgLng = group.sumOf { it.longitude } / group.size
            val id = group.joinToString("_") { it.userId }
            clusters.add(
                LiveLocationCluster(
                    id = id,
                    latitude = avgLat,
                    longitude = avgLng,
                    members = group
                )
            )
        }
        return clusters
    }

    fun decodeOptimizedMarkerAvatar(
        avatarUrl: String?,
        targetSizePx: Int = 88
    ): ImageBitmap? {
        if (avatarUrl.isNullOrBlank()) return null
        val cacheKey = "${avatarUrl.hashCode()}_$targetSizePx"
        avatarMarkerCache.get(cacheKey)?.let { return it }

        val rawBytes = decodeDataUriOrBase64(avatarUrl) ?: return null
        return runCatching {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, boundsOpts)
            val srcW = boundsOpts.outWidth.coerceAtLeast(1)
            val srcH = boundsOpts.outHeight.coerceAtLeast(1)
            var sampleSize = 1
            while ((srcW / sampleSize) > targetSizePx * 2 && (srcH / sampleSize) > targetSizePx * 2) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
                ?: return null
            val side = min(decoded.width, decoded.height).coerceAtLeast(1)
            val xOff = ((decoded.width - side) / 2).coerceAtLeast(0)
            val yOff = ((decoded.height - side) / 2).coerceAtLeast(0)
            val squared = if (decoded.width != side || decoded.height != side) {
                Bitmap.createBitmap(decoded, xOff, yOff, side, side).also {
                    if (it !== decoded) decoded.recycle()
                }
            } else {
                decoded
            }
            val scaled = if (squared.width != targetSizePx || squared.height != targetSizePx) {
                Bitmap.createScaledBitmap(squared, targetSizePx, targetSizePx, true).also {
                    if (it !== squared) squared.recycle()
                }
            } else {
                squared
            }
            val imageBitmap = scaled.asImageBitmap()
            avatarMarkerCache.put(cacheKey, imageBitmap)
            imageBitmap
        }.getOrNull()
    }

    private fun decodeDataUriOrBase64(raw: String): ByteArray? {
        val clean = raw.trim()
        val base64Part = when {
            clean.startsWith("data:image", ignoreCase = true) ->
                clean.substringAfter("base64,", "")
            !clean.startsWith("http", ignoreCase = true) && clean.length > 48 ->
                clean
            else -> ""
        }
        if (base64Part.isBlank()) return null
        return runCatching { Base64.decode(base64Part, Base64.DEFAULT) }.getOrNull()
    }
}
