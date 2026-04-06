package com.safegap.alert

import android.content.Context
import android.media.AudioAttributes
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
 * Uses STREAM_NOTIFICATION to coexist with navigation/music audio.
 */
@Singleton
class AudioAlertPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "SafeGap.Audio"
        private const val TONE_DURATION_MS = 150
        private const val MIN_INTERVAL_MS = 800L
    }

    private var toneGenerator: ToneGenerator? = null
    @Volatile
    private var lastPlayedMs = 0L

    fun initialize() {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            Log.i(TAG, "AudioAlertPlayer initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ToneGenerator", e)
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
