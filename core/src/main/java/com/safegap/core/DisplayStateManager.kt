package com.safegap.core

import android.graphics.RectF
import com.safegap.core.model.TrackedObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Smooths raw pipeline output into stable, display-ready state.
 *
 * Strategies:
 * 1. **Birth threshold**: tracks must be detected for [BIRTH_FRAMES] consecutive
 *    frames before appearing on screen.
 * 2. **EMA smoothing**: bounding box coordinates, distance, and speed are smoothed
 *    with exponential moving average to eliminate frame-to-frame jitter.
 * 3. **Grace period with dead-reckoning**: when a track loses detection, the bbox
 *    is projected forward using the last known velocity. Display confidence
 *    decays linearly to zero over [GRACE_FRAMES].
 * 4. **History buffer**: last [HISTORY_SIZE] distance samples per track, used to
 *    compute median distance (robust to single-frame outliers).
 */
@Singleton
class DisplayStateManager @Inject constructor() {

    companion object {
        /** Frames before a new track is shown on screen. */
        private const val BIRTH_FRAMES = 3

        /** Frames a track persists after losing detection (with decay). */
        private const val GRACE_FRAMES = 8

        /** EMA alpha for bounding box smoothing (0 = no update, 1 = no smoothing). */
        private const val BBOX_ALPHA = 0.4f

        /** EMA alpha for distance/speed display smoothing. */
        private const val VALUE_ALPHA = 0.35f

        /** Number of distance samples to keep for median calculation. */
        private const val HISTORY_SIZE = 8
    }

    private val tracks = mutableMapOf<Int, InternalTrack>()

    /**
     * Process a frame of tracked objects and return stable display states.
     */
    fun update(objects: List<TrackedObject>): List<DisplayState> {
        val activeIds = objects.map { it.trackId }.toSet()

        // Update existing tracks or create new ones
        for (obj in objects) {
            val existing = tracks[obj.trackId]
            if (existing != null) {
                existing.updateWith(obj)
            } else {
                tracks[obj.trackId] = InternalTrack(obj)
            }
        }

        // Advance grace period for tracks not seen this frame
        for ((id, track) in tracks) {
            if (id !in activeIds) {
                track.markMissed()
            }
        }

        // Remove expired tracks
        tracks.entries.removeAll { it.value.isExpired() }

        // Build display states, filtering out tracks below birth threshold
        return tracks.values
            .filter { it.isVisible() }
            .map { it.toDisplayState() }
    }

    fun reset() {
        tracks.clear()
    }

    private class InternalTrack(initial: TrackedObject) {
        val trackId = initial.trackId
        var className = initial.detection.className

        // Birth counting
        var consecutiveDetections = 1
        var born = false

        // Smoothed bbox
        var smoothBox = RectF(initial.detection.boundingBox)

        // Smoothed display values
        var smoothDistance: Float? = initial.distanceMeters
        var smoothSpeed: Float? = initial.speedMps
        var latestTtc: Float? = initial.ttcSeconds

        // Grace period
        var missedFrames = 0
        var lastSpeedMps: Float = initial.speedMps ?: 0f

        // Ring buffer for median distance (avoids boxing and per-frame allocation)
        val distanceHistory = FloatArray(HISTORY_SIZE)
        var historyCount = 0
        var historyIndex = 0

        init {
            initial.distanceMeters?.let { addToHistory(it) }
        }

        fun updateWith(obj: TrackedObject) {
            className = obj.detection.className
            missedFrames = 0
            consecutiveDetections++
            if (consecutiveDetections >= BIRTH_FRAMES) born = true

            // Smooth bbox with EMA
            val raw = obj.detection.boundingBox
            smoothBox = RectF(
                ema(smoothBox.left, raw.left, BBOX_ALPHA),
                ema(smoothBox.top, raw.top, BBOX_ALPHA),
                ema(smoothBox.right, raw.right, BBOX_ALPHA),
                ema(smoothBox.bottom, raw.bottom, BBOX_ALPHA),
            )

            // Smooth distance — use median from history as base, EMA on top
            obj.distanceMeters?.let { rawDist ->
                addToHistory(rawDist)
                val median = medianDistance()
                smoothDistance = if (smoothDistance != null) {
                    ema(smoothDistance!!, median, VALUE_ALPHA)
                } else {
                    median
                }
            }

            // Smooth speed
            obj.speedMps?.let { rawSpeed ->
                smoothSpeed = if (smoothSpeed != null) {
                    ema(smoothSpeed!!, rawSpeed, VALUE_ALPHA)
                } else {
                    rawSpeed
                }
                lastSpeedMps = smoothSpeed ?: 0f
            }

            latestTtc = obj.ttcSeconds
        }

        fun markMissed() {
            missedFrames++
            consecutiveDetections = 0

            // Dead-reckoning: project bbox forward using last known speed
            if (lastSpeedMps != 0f && smoothDistance != null) {
                // Approximate: if object approaches, bbox grows (we shift bottom down slightly)
                val dt = 1f / 15f // assume ~15fps
                val bboxHeight = smoothBox.bottom - smoothBox.top
                val distChange = lastSpeedMps * dt
                val newDist = (smoothDistance!! - distChange).coerceAtLeast(0.5f)
                val scale = if (newDist > 0f) smoothDistance!! / newDist else 1f
                val heightDelta = bboxHeight * (scale - 1f) * 0.5f

                smoothBox = RectF(
                    smoothBox.left,
                    (smoothBox.top - heightDelta).coerceAtLeast(0f),
                    smoothBox.right,
                    (smoothBox.bottom + heightDelta).coerceAtMost(1f),
                )
                smoothDistance = newDist
            }
        }

        fun isExpired(): Boolean = missedFrames > GRACE_FRAMES

        fun isVisible(): Boolean = born

        fun toDisplayState(): DisplayState {
            val confidence = if (missedFrames == 0) {
                1.0f
            } else {
                (1f - missedFrames.toFloat() / GRACE_FRAMES).coerceAtLeast(0f)
            }

            return DisplayState(
                trackId = trackId,
                className = className,
                smoothedBox = NormalizedRect(
                    left = smoothBox.left,
                    top = smoothBox.top,
                    right = smoothBox.right,
                    bottom = smoothBox.bottom,
                ),
                displayDistanceM = smoothDistance,
                displaySpeedMps = smoothSpeed,
                ttcSeconds = latestTtc,
                displayConfidence = confidence,
            )
        }

        private fun addToHistory(value: Float) {
            distanceHistory[historyIndex] = value
            historyIndex = (historyIndex + 1) % HISTORY_SIZE
            if (historyCount < HISTORY_SIZE) historyCount++
        }

        private fun medianDistance(): Float {
            val arr = distanceHistory.copyOf(historyCount)
            arr.sort()
            val n = arr.size
            return if (n % 2 == 0) (arr[n / 2 - 1] + arr[n / 2]) / 2f else arr[n / 2]
        }

        private fun ema(old: Float, new: Float, alpha: Float): Float =
            alpha * new + (1f - alpha) * old
    }
}
