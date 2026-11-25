package com.example.core

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

private const val TAG = "VoiceAudioManager"

class VoiceRecorderSession(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAtMs: Long = 0L

    fun start(): Result<File> {
        stopInternal(deleteFile = true)
        return try {
            val voiceDir = File(context.cacheDir, "voice").apply { mkdirs() }
            val file = File(voiceDir, "vyb_voice_${System.currentTimeMillis()}.m4a")
            val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            rec.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(64000)
                setMaxDuration(120_000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = rec
            outputFile = file
            startedAtMs = System.currentTimeMillis()
            Result.success(file)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start voice recording", e)
            stopInternal(deleteFile = true)
            Result.failure(e)
        }
    }

    fun stopAndFinalize(): Pair<File, Int>? {
        val file = outputFile
        val elapsedSec = (((System.currentTimeMillis() - startedAtMs) + 500L) / 1000L)
            .toInt()
            .coerceIn(1, 300)
        val rec = recorder
        recorder = null
        outputFile = null
        if (rec != null) {
            try {
                rec.stop()
            } catch (e: Exception) {
                Log.w(TAG, "Recorder stop failed cleanly: ${e.message}")
            }
            try {
                rec.release()
            } catch (_: Exception) {
            }
        }
        return if (file != null && file.exists() && file.length() > 0L) {
            file to elapsedSec
        } else {
            runCatching { file?.delete() }
            null
        }
    }

    fun cancel() {
        stopInternal(deleteFile = true)
    }

    private fun stopInternal(deleteFile: Boolean) {
        val rec = recorder
        recorder = null
        if (rec != null) {
            try {
                rec.stop()
            } catch (_: Exception) {
            }
            try {
                rec.release()
            } catch (_: Exception) {
            }
        }
        if (deleteFile) {
            runCatching { outputFile?.delete() }
        }
        outputFile = null
    }
}

class VoicePlaybackManager {
    private var player: MediaPlayer? = null
    var activeKey: String? = null
        private set

    fun play(
        key: String,
        file: File,
        onPreparedDurationMs: (Int) -> Unit = {},
        onCompleted: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        stop()
        try {
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    stop()
                    onCompleted()
                }
                setOnErrorListener { _, _, _ ->
                    stop()
                    onError("No se pudo reproducir el audio.")
                    true
                }
                prepare()
                onPreparedDurationMs(duration.coerceAtLeast(1000))
                start()
            }
            player = mp
            activeKey = key
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play audio", e)
            stop()
            onError("No se pudo reproducir el audio.")
        }
    }

    fun currentPositionMs(): Int = try {
        player?.currentPosition ?: 0
    } catch (_: Exception) {
        0
    }

    fun durationMs(): Int = try {
        player?.duration ?: 0
    } catch (_: Exception) {
        0
    }

    fun isPlaying(): Boolean = try {
        player?.isPlaying == true
    } catch (_: Exception) {
        false
    }

    fun stop() {
        val mp = player
        player = null
        activeKey = null
        if (mp != null) {
            try {
                if (mp.isPlaying) mp.stop()
            } catch (_: Exception) {
            }
            try {
                mp.release()
            } catch (_: Exception) {
            }
        }
    }
}

fun formatVoiceDuration(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val mins = safe / 60
    val secs = safe % 60
    return "$mins:${secs.toString().padStart(2, '0')}"
}
