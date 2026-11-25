package com.example.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Base64
import android.webkit.MimeTypeMap
import androidx.annotation.OptIn
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.roundToInt

data class EncodedMediaPayload(
    val mimeType: String,
    val previewDataUri: String?,
    val fullBytes: ByteArray,
    val aspectRatio: Double,
    val isVideo: Boolean,
    val isAudio: Boolean = false
)

data class EncodedDocumentPayload(
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val fullBytes: ByteArray
)

data class ResolvedMediaInfo(
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val isVideo: Boolean
)

object MediaUtils {
    const val CHUNK_SIZE_BYTES = 450_000 // ~600KB base64, safely under 1MB Firestore doc limit
    const val MAX_MEDIA_BYTES = 15 * 1024 * 1024 // 15 MB
    const val TARGET_VIDEO_BUDGET_BYTES = 13L * 1024L * 1024L // Leave safety headroom below 15 MB
    const val DIRECT_VIDEO_PASSTHROUGH_BYTES = 7L * 1024L * 1024L
    const val MAX_PREVIEW_DATA_URI_CHARS = 180_000 // Safely below 300,000 rule limit
    const val MAX_AVATAR_DATA_URI_CHARS = 160_000 // Safely below 200,000 rule limit

    fun createTempCameraUri(context: Context): Uri {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "vyb_capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun createTempCameraVideoUri(context: Context): Uri {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "vyb_video_${System.currentTimeMillis()}.mp4")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun inferMimeFromSignature(header: ByteArray): String? {
        if (header.size >= 12) {
            if (header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte()) {
                return "image/jpeg"
            }
            if (header[0] == 0x89.toByte() && header[1] == 0x50.toByte() && header[2] == 0x4E.toByte() && header[3] == 0x47.toByte()) {
                return "image/png"
            }
            if (header[0] == 'G'.code.toByte() && header[1] == 'I'.code.toByte() && header[2] == 'F'.code.toByte()) {
                return "image/gif"
            }
            if (header[0] == 'R'.code.toByte() && header[1] == 'I'.code.toByte() &&
                header[2] == 'F'.code.toByte() && header[3] == 'F'.code.toByte() &&
                header[8] == 'W'.code.toByte() && header[9] == 'E'.code.toByte() &&
                header[10] == 'B'.code.toByte() && header[11] == 'P'.code.toByte()
            ) {
                return "image/webp"
            }
            if (header[4] == 'f'.code.toByte() && header[5] == 't'.code.toByte() &&
                header[6] == 'y'.code.toByte() && header[7] == 'p'.code.toByte()
            ) {
                val brand = String(header.copyOfRange(8, 12), Charsets.US_ASCII).lowercase(Locale.ROOT)
                return when {
                    brand.startsWith("hei") || brand.startsWith("mif") || brand == "hevc" || brand == "hevx" -> "image/heic"
                    brand.startsWith("avif") -> "image/avif"
                    brand.startsWith("qt") -> "video/quicktime"
                    else -> "video/mp4"
                }
            }
            // EBML header for MKV / WebM
            if (header[0] == 0x1A.toByte() && header[1] == 0x45.toByte() &&
                header[2] == 0xDF.toByte() && header[3] == 0xA3.toByte()
            ) {
                return "video/x-matroska"
            }
        }
        return null
    }

    fun inferIsVideoMime(mimeType: String, fileName: String? = null): Boolean {
        val lowerMime = mimeType.lowercase(Locale.ROOT)
        if (lowerMime.startsWith("video/")) return true
        if (lowerMime.startsWith("image/")) return false
        val ext = fileName?.substringAfterLast('.', "")?.lowercase(Locale.ROOT).orEmpty()
        return ext in setOf("mp4", "mov", "mkv", "webm", "3gp", "3gpp", "m4v", "avi", "ts", "mts")
    }

    fun calculateTargetVideoBitrate(durationMs: Long, maxBytes: Long = TARGET_VIDEO_BUDGET_BYTES): Int {
        val safeSec = (durationMs.coerceAtLeast(1000L) / 1000.0).coerceAtLeast(1.0)
        val totalBits = maxBytes.toDouble() * 8.0 * 0.88 // Reserve room for container overhead & bitrate spikes
        val audioBitsPerSec = 96_000.0
        val availableVideoBps = ((totalBits / safeSec) - audioBitsPerSec).roundToInt()
        return availableVideoBps.coerceIn(380_000, 2_400_000)
    }

    suspend fun inspectMediaUri(context: Context, uri: Uri): ResolvedMediaInfo =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            var displayName: String? = null
            var sizeBytes = -1L

            runCatching {
                resolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
                            displayName = cursor.getString(nameIdx)
                        }
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                            sizeBytes = cursor.getLong(sizeIdx)
                        }
                    }
                }
            }

            val cleanName = (displayName ?: uri.lastPathSegment ?: "multimedia")
                .trim()
                .ifBlank { "multimedia" }
                .take(180)
            val ext = cleanName.substringAfterLast('.', "").lowercase(Locale.ROOT)

            val rawMime = resolver.getType(uri)?.takeIf {
                it.isNotBlank() && it != "application/octet-stream" && it != "*/*"
            }
            val extMime = if (ext.isNotBlank()) {
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            } else null

            var resolvedMime = rawMime ?: extMime
            if (resolvedMime.isNullOrBlank()) {
                val header = ByteArray(16)
                val readCount = runCatching {
                    resolver.openInputStream(uri)?.use { it.read(header) } ?: 0
                }.getOrDefault(0)
                if (readCount >= 12) {
                    resolvedMime = inferMimeFromSignature(header.copyOf(readCount))
                }
            }

            if (resolvedMime.isNullOrBlank() || resolvedMime == "application/octet-stream") {
                val mmr = MediaMetadataRetriever()
                try {
                    mmr.setDataSource(context, uri)
                    val hasVideo = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
                    val mmrMime = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                    resolvedMime = when {
                        hasVideo.equals("yes", ignoreCase = true) -> mmrMime ?: "video/mp4"
                        !mmrMime.isNullOrBlank() -> mmrMime
                        else -> "image/jpeg"
                    }
                } catch (_: Exception) {
                    resolvedMime = "image/jpeg"
                } finally {
                    runCatching { mmr.release() }
                }
            }

            val finalMime = resolvedMime ?: "image/jpeg"
            val isVideo = inferIsVideoMime(finalMime, cleanName)
            ResolvedMediaInfo(
                displayName = cleanName,
                mimeType = finalMime,
                sizeBytes = sizeBytes,
                isVideo = isVideo
            )
        }

    private fun applyExifRotationIfNeeded(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.preScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return runCatching {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) bitmap.recycle()
            rotated
        }.getOrDefault(bitmap)
    }

    private fun scaleBitmapToMaxDim(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val largest = max(w, h)
        if (largest <= maxDim || w <= 0 || h <= 0) return bitmap
        val ratio = maxDim.toFloat() / largest.toFloat()
        val targetW = (w * ratio).roundToInt().coerceAtLeast(1)
        val targetH = (h * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }

    suspend fun decodeBitmapFromUri(context: Context, uri: Uri, maxDim: Int = 1440): Bitmap? =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val decodedWithImageDecoder = runCatching {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        val largest = max(info.size.width, info.size.height)
                        if (largest > maxDim && info.size.width > 0 && info.size.height > 0) {
                            val ratio = maxDim.toFloat() / largest.toFloat()
                            decoder.setTargetSize(
                                (info.size.width * ratio).roundToInt().coerceAtLeast(1),
                                (info.size.height * ratio).roundToInt().coerceAtLeast(1)
                            )
                        }
                    }
                }.getOrNull()
                if (decodedWithImageDecoder != null) {
                    return@withContext decodedWithImageDecoder
                }
            }

            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                val largest = max(bounds.outWidth, bounds.outHeight)
                var sampleSize = 1
                while (largest > 0 && largest / sampleSize > maxDim) {
                    sampleSize *= 2
                }
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val raw = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                } ?: return@withContext null
                val oriented = applyExifRotationIfNeeded(context, uri, raw)
                scaleBitmapToMaxDim(oriented, maxDim)
            } catch (_: Exception) {
                null
            }
        }

    private fun encodeBoundedDataUri(bitmap: Bitmap, maxBase64DataUriChars: Int, startMaxDim: Int): String {
        var currentDim = startMaxDim
        var candidate = scaleBitmapToMaxDim(bitmap, currentDim)
        val out = ByteArrayOutputStream()

        repeat(5) {
            var quality = 80
            while (quality >= 36) {
                out.reset()
                candidate.compress(Bitmap.CompressFormat.JPEG, quality, out)
                val dataUri = "data:image/jpeg;base64,${Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)}"
                if (dataUri.length <= maxBase64DataUriChars) {
                    return dataUri
                }
                quality -= 14
            }
            currentDim = (currentDim * 0.7f).roundToInt().coerceAtLeast(160)
            candidate = scaleBitmapToMaxDim(bitmap, currentDim)
        }

        out.reset()
        candidate.compress(Bitmap.CompressFormat.JPEG, 32, out)
        return "data:image/jpeg;base64,${Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)}"
    }

    suspend fun compressAvatarDataUri(context: Context, uri: Uri): String? =
        withContext(Dispatchers.IO) {
            val bitmap = decodeBitmapFromUri(context, uri, maxDim = 400) ?: return@withContext null
            encodeBoundedDataUri(bitmap, MAX_AVATAR_DATA_URI_CHARS, startMaxDim = 360)
        }

    suspend fun loadComposerPreviewBitmap(context: Context, uri: Uri): Bitmap? =
        withContext(Dispatchers.IO) {
            val info = inspectMediaUri(context, uri)
            if (info.isVideo) {
                extractVideoThumbnailBitmap(context, uri)
            } else {
                decodeBitmapFromUri(context, uri, maxDim = 720)
            }
        }

    private fun extractVideoThumbnailBitmap(context: Context, uri: Uri): Bitmap? {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(context, uri)
            val rawFrame = mmr.getFrameAtTime(500_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: mmr.getFrameAtTime(0L)
                ?: mmr.frameAtTime
            rawFrame?.let { scaleBitmapToMaxDim(it, 720) }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { mmr.release() }
        }
    }

    private fun copyUriToTempFile(context: Context, uri: Uri, extHint: String): File? {
        val tempDir = File(context.cacheDir, "media_work").apply { mkdirs() }
        val target = File(tempDir, "in_${UUID.randomUUID().toString().take(12)}.$extHint")
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                }
            } ?: return null
            if (target.length() > 0L) target else {
                target.delete()
                null
            }
        } catch (_: Exception) {
            target.delete()
            null
        }
    }

    private data class VideoInspection(
        val durationMs: Long,
        val width: Int,
        val height: Int,
        val aspectRatio: Double,
        val needsTranscoding: Boolean,
        val isHdr: Boolean
    )

    private fun inspectVideoDetails(context: Context, uri: Uri, mimeType: String, fileSize: Long): VideoInspection {
        var durationMs = 10_000L
        var width = 1280
        var height = 720
        var rotation = 0

        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(context, uri)
            durationMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 10_000L
            width = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1280
            height = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 720
            rotation = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        } catch (_: Exception) {
        } finally {
            runCatching { mmr.release() }
        }

        val effectiveW = if (rotation == 90 || rotation == 270) height else width
        val effectiveH = if (rotation == 90 || rotation == 270) width else height
        val aspect = if (effectiveW > 0 && effectiveH > 0) {
            (effectiveW.toDouble() / effectiveH.toDouble()).coerceIn(0.4, 2.8)
        } else 16.0 / 9.0

        var isHevcOrNonAvc = false
        var isHdr = false
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val trackMime = format.getString(MediaFormat.KEY_MIME)?.lowercase(Locale.ROOT).orEmpty()
                if (trackMime.startsWith("video/")) {
                    if (trackMime != MediaFormat.MIMETYPE_VIDEO_AVC.lowercase(Locale.ROOT)) {
                        isHevcOrNonAvc = true
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && format.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) {
                        val transfer = format.getInteger(MediaFormat.KEY_COLOR_TRANSFER)
                        if (transfer == MediaFormat.COLOR_TRANSFER_ST2084 || transfer == MediaFormat.COLOR_TRANSFER_HLG) {
                            isHdr = true
                        }
                    }
                    break
                }
            }
        } catch (_: Exception) {
        } finally {
            runCatching { extractor.release() }
        }

        val maxSide = max(effectiveW, effectiveH)
        val unknownOrLarge = fileSize <= 0L || fileSize > DIRECT_VIDEO_PASSTHROUGH_BYTES
        val nonMp4 = !mimeType.equals("video/mp4", ignoreCase = true)
        val needsTranscoding = unknownOrLarge || nonMp4 || isHevcOrNonAvc || isHdr || maxSide > 1280

        return VideoInspection(
            durationMs = durationMs,
            width = effectiveW,
            height = effectiveH,
            aspectRatio = aspect,
            needsTranscoding = needsTranscoding,
            isHdr = isHdr
        )
    }

    @OptIn(UnstableApi::class)
    private suspend fun transcodeVideoOnce(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        targetShortSide: Int,
        targetBitrate: Int,
        hdrMode: Int,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { cont ->
            val mainHandler = Handler(Looper.getMainLooper())
            var finished = false
            val progressHolder = ProgressHolder()
            lateinit var transformer: Transformer

            val progressPoller = object : Runnable {
                override fun run() {
                    if (finished || !cont.isActive) return
                    val state = runCatching { transformer.getProgress(progressHolder) }
                        .getOrDefault(Transformer.PROGRESS_STATE_NOT_STARTED)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress((progressHolder.progress / 100f).coerceIn(0.02f, 0.98f))
                    }
                    mainHandler.postDelayed(this, 220L)
                }
            }

            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (finished) return
                    finished = true
                    mainHandler.removeCallbacks(progressPoller)
                    onProgress(1f)
                    if (cont.isActive) cont.resume(Result.success(outputFile))
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    if (finished) return
                    finished = true
                    mainHandler.removeCallbacks(progressPoller)
                    if (cont.isActive) cont.resume(Result.failure(exportException))
                }
            }

            try {
                val mediaItem = MediaItem.fromUri(inputUri)
                val videoEffects = listOf(
                    Presentation.createForShortSide(targetShortSide)
                )
                val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                    .setEffects(Effects(emptyList(), videoEffects))
                    .build()

                val sequence = EditedMediaItemSequence.Builder(editedMediaItem).build()
                val composition = Composition.Builder(sequence)
                    .setHdrMode(hdrMode)
                    .build()

                val encoderFactory = DefaultEncoderFactory.Builder(context.applicationContext)
                    .setRequestedVideoEncoderSettings(
                        VideoEncoderSettings.Builder()
                            .setBitrate(targetBitrate)
                            .build()
                    )
                    .build()

                transformer = Transformer.Builder(context.applicationContext)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(encoderFactory)
                    .addListener(listener)
                    .build()

                cont.invokeOnCancellation {
                    finished = true
                    mainHandler.removeCallbacks(progressPoller)
                    mainHandler.post { runCatching { transformer.cancel() } }
                }

                transformer.start(composition, outputFile.absolutePath)
                mainHandler.postDelayed(progressPoller, 220L)
            } catch (e: Exception) {
                finished = true
                mainHandler.removeCallbacks(progressPoller)
                if (cont.isActive) cont.resume(Result.failure(e))
            }
        }
    }

    @OptIn(UnstableApi::class)
    private suspend fun compressVideoToHdMp4(
        context: Context,
        sourceUri: Uri,
        inspection: VideoInspection,
        onProgress: (Float) -> Unit
    ): Result<File> {
        val workDir = File(context.cacheDir, "media_work").apply { mkdirs() }
        val outputFile = File(workDir, "vyb_out_${UUID.randomUUID().toString().take(12)}.mp4")
        val targetShortSide = if (inspection.durationMs > 90_000L) 480 else 720
        val targetBitrate = calculateTargetVideoBitrate(inspection.durationMs)

        // Primary pass: OpenGL HDR -> SDR tone mapping (handles Samsung S25 HDR10+ / 4K / HEVC cleanly)
        val firstPass = transcodeVideoOnce(
            context = context,
            inputUri = sourceUri,
            outputFile = outputFile,
            targetShortSide = targetShortSide,
            targetBitrate = targetBitrate,
            hdrMode = Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL,
            onProgress = onProgress
        )
        if (firstPass.isSuccess && outputFile.exists() && outputFile.length() in 1L..MAX_MEDIA_BYTES.toLong()) {
            return firstPass
        }

        // Fallback pass: if output was still too big or OpenGL tone map failed, retry at 480p with tighter bitrate
        if (firstPass.isFailure || outputFile.length() > MAX_MEDIA_BYTES.toLong()) {
            outputFile.delete()
            val retryShortSide = if (outputFile.length() > MAX_MEDIA_BYTES.toLong()) 480 else targetShortSide
            val retryBitrate = (targetBitrate * 0.65).roundToInt().coerceAtLeast(320_000)
            val fallbackHdrMode = if (firstPass.isFailure) {
                Composition.HDR_MODE_EXPERIMENTAL_FORCE_INTERPRET_HDR_AS_SDR
            } else {
                Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL
            }
            val secondPass = transcodeVideoOnce(
                context = context,
                inputUri = sourceUri,
                outputFile = outputFile,
                targetShortSide = retryShortSide,
                targetBitrate = retryBitrate,
                hdrMode = fallbackHdrMode,
                onProgress = onProgress
            )
            if (secondPass.isSuccess && outputFile.exists() && outputFile.length() in 1L..MAX_MEDIA_BYTES.toLong()) {
                return secondPass
            }
            outputFile.delete()
            val errMsg = secondPass.exceptionOrNull()?.localizedMessage
                ?: firstPass.exceptionOrNull()?.localizedMessage
                ?: "El video sigue superando 15 MB incluso tras comprimirlo. Prueba con un clip más corto."
            return Result.failure(IllegalStateException(errMsg))
        }

        return firstPass
    }

    suspend fun prepareMediaPayload(
        context: Context,
        uri: Uri,
        onProgress: (Float) -> Unit = {}
    ): Result<EncodedMediaPayload> =
        withContext(Dispatchers.IO) {
            var tempInputFile: File? = null
            var tempOutputFile: File? = null
            try {
                onProgress(0.05f)
                val info = inspectMediaUri(context, uri)

                if (info.isVideo) {
                    val inspection = inspectVideoDetails(context, uri, info.mimeType, info.sizeBytes)
                    val thumbBitmap = extractVideoThumbnailBitmap(context, uri)
                    val previewUri = thumbBitmap?.let {
                        encodeBoundedDataUri(it, MAX_PREVIEW_DATA_URI_CHARS, startMaxDim = 640)
                    }

                    val finalBytes: ByteArray = if (!inspection.needsTranscoding &&
                        info.sizeBytes in 1L..DIRECT_VIDEO_PASSTHROUGH_BYTES
                    ) {
                        onProgress(0.8f)
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: return@withContext Result.failure(IllegalStateException("No se pudo leer el video."))
                    } else {
                        // Copy content URI to temp file if Photo Picker URI is non-seekable on some OEM extractors
                        val sourceForTransformer = uri
                        val compressed = compressVideoToHdMp4(
                            context = context,
                            sourceUri = sourceForTransformer,
                            inspection = inspection,
                            onProgress = { p -> onProgress(0.10f + p * 0.85f) }
                        )
                        val outFile = if (compressed.isSuccess) {
                            compressed.getOrThrow().also { tempOutputFile = it }
                        } else {
                            // Retry once from copied local file in case OEM Photo Picker FD caused extractor issues
                            val ext = resolveExtension(info.mimeType, info.displayName)
                            tempInputFile = copyUriToTempFile(context, uri, ext)
                            val retryRes = tempInputFile?.let { copied ->
                                compressVideoToHdMp4(
                                    context = context,
                                    sourceUri = Uri.fromFile(copied),
                                    inspection = inspection,
                                    onProgress = { p -> onProgress(0.10f + p * 0.85f) }
                                )
                            }
                            if (retryRes != null && retryRes.isSuccess) {
                                retryRes.getOrThrow().also { tempOutputFile = it }
                            } else {
                                // Last-resort passthrough only if the original file already fits <= MAX_MEDIA_BYTES
                                val safeCopy = tempInputFile ?: copyUriToTempFile(context, uri, ext)
                                    ?.also { tempInputFile = it }
                                if (safeCopy != null && safeCopy.length() in 1L..MAX_MEDIA_BYTES.toLong()) {
                                    safeCopy
                                } else {
                                    val reason = retryRes?.exceptionOrNull()?.localizedMessage
                                        ?: compressed.exceptionOrNull()?.localizedMessage
                                        ?: "No se pudo comprimir el video."
                                    return@withContext Result.failure(IllegalStateException(reason))
                                }
                            }
                        }

                        if (outFile.length() <= 0L) {
                            return@withContext Result.failure(IllegalStateException("El video procesado quedó vacío."))
                        }
                        if (outFile.length() > MAX_MEDIA_BYTES.toLong()) {
                            return@withContext Result.failure(
                                IllegalArgumentException("El video supera el límite de 15 MB incluso tras comprimirlo.")
                            )
                        }
                        outFile.readBytes()
                    }

                    if (finalBytes.isEmpty()) {
                        return@withContext Result.failure(IllegalStateException("No se pudo leer el video."))
                    }

                    onProgress(1f)
                    Result.success(
                        EncodedMediaPayload(
                            mimeType = if (tempOutputFile != null) "video/mp4" else info.mimeType,
                            previewDataUri = previewUri,
                            fullBytes = finalBytes,
                            aspectRatio = inspection.aspectRatio,
                            isVideo = true
                        )
                    )
                } else {
                    onProgress(0.25f)
                    val fullBitmap = decodeBitmapFromUri(context, uri, maxDim = 1600)
                        ?: return@withContext Result.failure(
                            IllegalStateException("No se pudo decodificar la imagen seleccionada (${info.mimeType}).")
                        )
                    val aspect = if (fullBitmap.height > 0) {
                        (fullBitmap.width.toDouble() / fullBitmap.height.toDouble()).coerceIn(0.4, 2.8)
                    } else 1.0

                    onProgress(0.55f)
                    val fullOut = ByteArrayOutputStream()
                    var jpegQuality = 86
                    fullBitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, fullOut)
                    while (fullOut.size() > 4 * 1024 * 1024 && jpegQuality > 64) {
                        fullOut.reset()
                        jpegQuality -= 8
                        fullBitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, fullOut)
                    }
                    val fullBytes = fullOut.toByteArray()
                    if (fullBytes.isEmpty()) {
                        return@withContext Result.failure(IllegalStateException("La imagen procesada está vacía."))
                    }
                    if (fullBytes.size > MAX_MEDIA_BYTES) {
                        return@withContext Result.failure(IllegalArgumentException("La imagen supera el límite de 15 MB."))
                    }

                    onProgress(0.8f)
                    val previewSource = scaleBitmapToMaxDim(fullBitmap, 720)
                    val previewUri = encodeBoundedDataUri(
                        bitmap = previewSource,
                        maxBase64DataUriChars = MAX_PREVIEW_DATA_URI_CHARS,
                        startMaxDim = 720
                    )

                    onProgress(1f)
                    Result.success(
                        EncodedMediaPayload(
                            mimeType = "image/jpeg",
                            previewDataUri = previewUri,
                            fullBytes = fullBytes,
                            aspectRatio = aspect,
                            isVideo = false
                        )
                    )
                }
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                runCatching { tempInputFile?.delete() }
                runCatching { tempOutputFile?.delete() }
            }
        }

    fun decodeBase64DataUri(dataUri: String?): ByteArray? {
        if (dataUri.isNullOrBlank()) return null
        return try {
            val base64Part = if (dataUri.contains(",")) dataUri.substringAfter(",") else dataUri
            Base64.decode(base64Part, Base64.DEFAULT)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun prepareAudioPayload(file: File): Result<EncodedMediaPayload> =
        withContext(Dispatchers.IO) {
            try {
                if (!file.exists() || file.length() <= 0L) {
                    return@withContext Result.failure(IllegalStateException("El audio grabado está vacío."))
                }
                if (file.length() > MAX_MEDIA_BYTES.toLong()) {
                    return@withContext Result.failure(IllegalArgumentException("El audio supera el límite de 15 MB."))
                }
                val rawBytes = file.readBytes()
                Result.success(
                    EncodedMediaPayload(
                        mimeType = "audio/mp4",
                        previewDataUri = null,
                        fullBytes = rawBytes,
                        aspectRatio = 1.0,
                        isVideo = false,
                        isAudio = true
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun prepareDocumentPayload(context: Context, uri: Uri): Result<EncodedDocumentPayload> =
        withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                var resolvedName: String? = null
                var queriedSize = -1L
                resolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIdx >= 0) resolvedName = cursor.getString(nameIdx)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                            queriedSize = cursor.getLong(sizeIdx)
                        }
                    }
                }
                if (queriedSize > MAX_MEDIA_BYTES) {
                    return@withContext Result.failure(
                        IllegalArgumentException("El documento supera el límite de 15 MB.")
                    )
                }
                val rawBytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@withContext Result.failure(
                        IllegalStateException("No se pudo leer el documento seleccionado.")
                    )
                if (rawBytes.isEmpty()) {
                    return@withContext Result.failure(
                        IllegalStateException("El documento seleccionado está vacío.")
                    )
                }
                if (rawBytes.size > MAX_MEDIA_BYTES) {
                    return@withContext Result.failure(
                        IllegalArgumentException("El documento supera el límite de 15 MB.")
                    )
                }
                val cleanName = (resolvedName ?: uri.lastPathSegment ?: "Documento_VYB")
                    .trim()
                    .ifBlank { "Documento_VYB" }
                    .take(200)
                val extFromName = cleanName.substringAfterLast('.', "").lowercase(Locale.ROOT)
                val mime = resolver.getType(uri)?.takeIf { it.isNotBlank() }
                    ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(extFromName)
                    ?: "application/octet-stream"

                Result.success(
                    EncodedDocumentPayload(
                        fileName = cleanName,
                        mimeType = mime.take(100),
                        sizeBytes = rawBytes.size.toLong(),
                        fullBytes = rawBytes
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun resolveExtension(mimeType: String, preferredName: String? = null): String {
        val nameExt = preferredName
            ?.substringAfterLast('.', "")
            ?.lowercase(Locale.ROOT)
            ?.replace(Regex("[^a-z0-9]"), "")
            ?.takeIf { it.isNotBlank() && it.length <= 8 && preferredName.contains('.') }
        if (nameExt != null) return nameExt

        val lowerMime = mimeType.lowercase(Locale.ROOT)
        return when {
            lowerMime.startsWith("audio/") -> "m4a"
            lowerMime.contains("mp4") -> "mp4"
            lowerMime.contains("webm") -> "webm"
            lowerMime.contains("quicktime") -> "mov"
            lowerMime.contains("matroska") || lowerMime.contains("mkv") -> "mkv"
            lowerMime.contains("heic") -> "heic"
            lowerMime.contains("heif") -> "heif"
            lowerMime.contains("avif") -> "avif"
            lowerMime.contains("webp") -> "webp"
            lowerMime.contains("png") -> "png"
            lowerMime.contains("pdf") -> "pdf"
            lowerMime == "text/plain" -> "txt"
            lowerMime.contains("csv") -> "csv"
            lowerMime.contains("wordprocessingml") -> "docx"
            lowerMime.contains("msword") -> "doc"
            lowerMime.contains("spreadsheetml") -> "xlsx"
            lowerMime.contains("ms-excel") -> "xls"
            lowerMime.contains("presentationml") -> "pptx"
            lowerMime.contains("ms-powerpoint") -> "ppt"
            lowerMime.contains("zip") -> "zip"
            lowerMime.startsWith("image/") -> "jpg"
            else -> MimeTypeMap.getSingleton().getExtensionFromMimeType(lowerMime)?.takeIf { it.isNotBlank() } ?: "bin"
        }
    }

    suspend fun writeTempMediaFile(
        context: Context,
        mediaId: String,
        mimeType: String,
        bytes: ByteArray,
        preferredName: String? = null
    ): File =
        withContext(Dispatchers.IO) {
            val ext = resolveExtension(mimeType, preferredName)
            val file = File(context.cacheDir, "vyb_media_${mediaId}.$ext")
            if (!file.exists() || file.length() != bytes.size.toLong()) {
                file.writeBytes(bytes)
            }
            file
        }

    suspend fun writeShareableDocumentFile(
        context: Context,
        mediaId: String,
        mimeType: String,
        bytes: ByteArray,
        preferredName: String?
    ): File = withContext(Dispatchers.IO) {
        val docsDir = File(context.cacheDir, "docs").apply { mkdirs() }
        val ext = resolveExtension(mimeType, preferredName)
        val safeBase = (preferredName ?: "documento_$mediaId")
            .substringBeforeLast('.')
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .take(48)
            .ifBlank { "documento_$mediaId" }
        val file = File(docsDir, "${safeBase}_${mediaId.takeLast(6)}.$ext")
        if (!file.exists() || file.length() != bytes.size.toLong()) {
            file.writeBytes(bytes)
        }
        file
    }

    fun formatByteSize(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.0f KB", kb)
        val mb = kb / 1024.0
        return String.format(Locale.US, "%.1f MB", mb)
    }
}
