package com.safegap.alert

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import com.safegap.core.model.AlertLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plays short alert tones using [ToneGenerator].
 *
 * - WARNING: single short beep
 * - CRITICAL: rapid double beep
 * - SAFE: silence
 *
 * Uses STREAM_ALARM to bypass Do Not Disturb for safety-critical alerts.
 */
@Singleton
class AudioAlertPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "SafeGap.Audio"
        private const val TONE_DURATION_MS = 150
        private const val MIN_INTERVAL_MS = 800L
        private const val DEFAULT_VOLUME = 90
    }

    private var toneGenerator: ToneGenerator? = null
    private var currentVolume: Int = DEFAULT_VOLUME
    @Volatile
    private var lastPlayedMs = 0L

    fun initialize() {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, currentVolume)
            Log.i(TAG, "AudioAlertPlayer initialized (volume=$currentVolume)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ToneGenerator", e)
        }
    }

    /**
     * Update the alert volume. Recreates the ToneGenerator with the new volume.
     * @param volume Volume level in range [0, 100].
     */
    fun updateVolume(volume: Int) {
        currentVolume = volume.coerceIn(0, 100)
        try {
            toneGenerator?.release()
            toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, currentVolume)
            Log.d(TAG, "Volume updated to $currentVolume")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to recreate ToneGenerator with volume $currentVolume", e)
        }
    }

    /**
     * Play an alert tone appropriate for the given level.
     * Respects [MIN_INTERVAL_MS] to avoid spamming.
     */
    fun play(level: AlertLevel) {
        if (level == AlertLevel.SAFE) return

        val now = System.currentTimeMillis()
        val interval = if (level == AlertLevel.CRITICAL) MIN_INTERVAL_MS / 2 else MIN_INTERVAL_MS
        if (now - lastPlayedMs < interval) return

        val tone = when (level) {
            AlertLevel.WARNING -> ToneGenerator.TONE_PROP_BEEP
            AlertLevel.CRITICAL -> ToneGenerator.TONE_PROP_BEEP2
            else -> return
        }

        try {
            toneGenerator?.startTone(tone, TONE_DURATION_MS)
            lastPlayedMs = now
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play tone", e)
        }
    }

    fun release() {
        toneGenerator?.release()
        toneGenerator = null
    }
}
