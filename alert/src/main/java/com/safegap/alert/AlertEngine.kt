package com.safegap.alert

import com.safegap.core.SafeGapSettings
import com.safegap.core.model.AlertLevel
import com.safegap.core.model.TrackedObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Evaluates tracked objects and determines the overall alert level.
 *
 * Thresholds are configurable via [updateSettings].
 *
 * Debounce: a new alert level must be sustained for [SUSTAIN_MS] milliseconds
 * before it becomes active. This prevents transient spikes from triggering alerts.
 * Dropping to a lower level also requires [SUSTAIN_MS] of sustained lower readings.
 */
@Singleton
class AlertEngine @Inject constructor() {

    companion object {
        /** Alert condition must hold for this duration before firing. */
        private const val SUSTAIN_MS = 500L
        private const val PERSON_TTC_MULTIPLIER = 2.0f
    }

    private data class Thresholds(
        val criticalTtcS: Float,
        val criticalDistanceM: Float,
        val warningTtcS: Float,
        val warningDistanceM: Float,
    )

    @Volatile
    private var thresholds = Thresholds(
        criticalTtcS = SafeGapSettings.DEFAULT_CRITICAL_TTC_S,
        criticalDistanceM = SafeGapSettings.DEFAULT_CRITICAL_DISTANCE_M,
        warningTtcS = SafeGapSettings.DEFAULT_WARNING_TTC_S,
        warningDistanceM = SafeGapSettings.DEFAULT_WARNING_DISTANCE_M,
    )

    private var currentLevel = AlertLevel.SAFE
    /** The raw level being sustained, waiting to become active. */
    private var pendingLevel = AlertLevel.SAFE
    /** Timestamp (ms) when pendingLevel was first seen continuously. */
    private var pendingSinceMs = 0L

    fun updateSettings(settings: SafeGapSettings) {
        thresholds = Thresholds(
            criticalTtcS = settings.criticalTtcS,
            criticalDistanceM = settings.criticalDistanceM,
            warningTtcS = settings.warningTtcS,
            warningDistanceM = settings.warningDistanceM,
        )
    }

    /**
     * Evaluate a frame of tracked objects and return the debounced alert level,
     * the most threatening object (if any), and its per-object level.
     *
     * @param nowMs current time in milliseconds (injectable for testing).
     */
    fun evaluate(objects: List<TrackedObject>, nowMs: Long = System.currentTimeMillis()): AlertResult {
        var worstLevel = AlertLevel.SAFE
        var closestThreat: TrackedObject? = null

        for (obj in objects) {
            val objLevel = classifyObject(obj)
            if (objLevel.severity > worstLevel.severity) {
                worstLevel = objLevel
                closestThreat = obj
            } else if (objLevel == worstLevel && closestThreat != null) {
                val objDist = obj.distanceMeters ?: Float.MAX_VALUE
                val currentDist = closestThreat.distanceMeters ?: Float.MAX_VALUE
                if (objDist < currentDist) {
                    closestThreat = obj
                }
            }
        }

        currentLevel = debounce(worstLevel, nowMs)

        return AlertResult(
            level = currentLevel,
            closestThreat = closestThreat,
        )
    }

    fun reset() {
        currentLevel = AlertLevel.SAFE
        pendingLevel = AlertLevel.SAFE
        pendingSinceMs = 0L
    }

    private fun classifyObject(obj: TrackedObject): AlertLevel {
        val distance = obj.distanceMeters ?: return AlertLevel.SAFE
        val ttc = obj.ttcSeconds
        val isPerson = obj.detection.className == "person"

        // Snapshot thresholds atomically to prevent mixed old/new reads
        val t = thresholds

        // CRITICAL check — person has elevated TTC threshold
        val critTtc = if (isPerson) t.criticalTtcS * PERSON_TTC_MULTIPLIER else t.criticalTtcS
        if ((ttc != null && ttc < critTtc) || distance < t.criticalDistanceM) {
            return AlertLevel.CRITICAL
        }

        // WARNING check
        if ((ttc != null && ttc < t.warningTtcS) || distance < t.warningDistanceM) {
            return AlertLevel.WARNING
        }

        return AlertLevel.SAFE
    }

    private fun debounce(rawLevel: AlertLevel, now: Long): AlertLevel {

        if (rawLevel == currentLevel) {
            // Stable at current level — don't touch pending state.
            // Resetting pendingSinceMs here would allow a brief bounce-back
            // followed by a return to restart the timer, enabling early escalation.
            pendingLevel = rawLevel
            return currentLevel
        }

        if (rawLevel != pendingLevel) {
            // New candidate level — start timing
            pendingLevel = rawLevel
            pendingSinceMs = now
            return currentLevel
        }

        // Same pending level — check if sustained long enough
        return if (now - pendingSinceMs >= SUSTAIN_MS) {
            pendingSinceMs = now
            rawLevel
        } else {
            currentLevel
        }
    }
}

data class AlertResult(
    val level: AlertLevel,
    val closestThreat: TrackedObject?,
)
