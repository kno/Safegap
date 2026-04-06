package com.safegap.core

import android.graphics.RectF

/**
 * Value-type replacement for [RectF] in display state.
 * Unlike [RectF], this is a data class with proper structural equality,
 * which allows [DisplayState] equality checks to work correctly.
 */
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    fun toRectF(): RectF = RectF(left, top, right, bottom)
}

/**
 * Smoothed, display-ready representation of a tracked object.
 * Produced by [DisplayStateManager] from raw [TrackedObject] pipeline data.
 */
data class DisplayState(
    val trackId: Int,
    val className: String,
    /** Smoothed bounding box in [0,1] normalized coordinates. */
    val smoothedBox: NormalizedRect,
    /** Smoothed distance for display (meters), null if not yet reliable. */
    val displayDistanceM: Float?,
    /** Smoothed speed for display (m/s, positive = approaching). */
    val displaySpeedMps: Float?,
    /** TTC from latest estimation (seconds). */
    val ttcSeconds: Float?,
    /** Confidence for display purposes, 1.0 = fully visible, decays during grace period. */
    val displayConfidence: Float,
)
