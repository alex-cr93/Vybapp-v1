package com.example.core

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "CallSoundManager"

class CallSoundManager(private val context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var ringtone: Ringtone? = null
    private var toneGenerator: ToneGenerator? = null
    private var ringbackJob: Job? = null
    private var endToneJob: Job? = null

    private fun audioManager(): AudioManager? =
        runCatching { appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }.getOrNull()

    private fun vibrator(): Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()

    fun startIncomingRingtone(allowSound: Boolean, allowVibrate: Boolean) {
        stopAll()
        val ringerMode = audioManager()?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL

        if (allowSound && ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            runCatching {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val r = RingtoneManager.getRingtone(appContext, uri)
                if (r != null) {
                    r.audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        r.isLooping = true
                    }
                    r.play()
                    ringtone = r
                }
            }.onFailure { e ->
                Log.w(TAG, "Unable to play incoming ringtone: ${e.message}")
            }
        }

        if (allowVibrate && ringerMode != AudioManager.RINGER_MODE_SILENT) {
            runCatching {
                val vib = vibrator()
                if (vib?.hasVibrator() == true) {
                    val pattern = longArrayOf(0L, 550L, 950L)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vib.vibrate(VibrationEffect.createWaveform(pattern, 0))
                    } else {
                        @Suppress("DEPRECATION")
                        vib.vibrate(pattern, 0)
                    }
                }
            }.onFailure { e ->
                Log.w(TAG, "Unable to start call vibration: ${e.message}")
            }
        }
    }

    fun startOutgoingRingback(allowSound: Boolean) {
        stopAll()
        if (!allowSound) return
        val ringerMode = audioManager()?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
        if (ringerMode == AudioManager.RINGER_MODE_SILENT) return

        ringbackJob = scope.launch {
            val tg = runCatching { ToneGenerator(AudioManager.STREAM_VOICE_CALL, 55) }.getOrNull()
                ?: return@launch
            toneGenerator = tg
            try {
                while (isActive) {
                    runCatching { tg.startTone(ToneGenerator.TONE_SUP_RINGTONE, 1400) }
                    delay(3400L)
                }
            } finally {
                runCatching {
                    tg.stopTone()
                    tg.release()
                }
                if (toneGenerator === tg) {
                    toneGenerator = null
                }
            }
        }
    }

    fun playEndTone(allowSound: Boolean) {
        stopAll()
        if (!allowSound) return
        val ringerMode = audioManager()?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
        if (ringerMode == AudioManager.RINGER_MODE_SILENT) return

        endToneJob = scope.launch {
            val tg = runCatching { ToneGenerator(AudioManager.STREAM_VOICE_CALL, 50) }.getOrNull()
                ?: return@launch
            try {
                runCatching { tg.startTone(ToneGenerator.TONE_PROP_BEEP, 240) }
                delay(280L)
            } finally {
                runCatching {
                    tg.stopTone()
                    tg.release()
                }
            }
        }
    }

    fun stopAll() {
        ringbackJob?.cancel()
        ringbackJob = null
        endToneJob?.cancel()
        endToneJob = null

        runCatching {
            ringtone?.stop()
        }
        ringtone = null

        runCatching {
            toneGenerator?.stopTone()
            toneGenerator?.release()
        }
        toneGenerator = null

        runCatching {
            vibrator()?.cancel()
        }
    }
}
