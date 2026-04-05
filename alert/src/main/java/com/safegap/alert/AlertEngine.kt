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
 * Debounce: level rises immediately, drops only after [DEBOUNCE_FRAMES]
 * consecutive frames at a lower level.
 */
@Singleton
class AlertEngine @Inject constructor() {

    companion object {
        private const val DEBOUNCE_FRAMES = 3
        private const val PERSON_TTC_MULTIPLIER = 2.0f
    }

    private var criticalTtcS = SafeGapSettings.DEFAULT_CRITICAL_TTC_S
    private var criticalDistanceM = SafeGapSettings.DEFAULT_CRITICAL_DISTANCE_M
    private var warningTtcS = SafeGapSettings.DEFAULT_WARNING_TTC_S
    private var warningDistanceM = SafeGapSettings.DEFAULT_WARNING_DISTANCE_M

    private var currentLevel = AlertLevel.SAFE
    private var lowerFrameCount = 0

    fun updateSettings(settings: SafeGapSettings) {
        criticalTtcS = settings.criticalTtcS
        criticalDistanceM = settings.criticalDistanceM
        warningTtcS = settings.warningTtcS
        warningDistanceM = settings.warningDistanceM
    }

    /**
     * Evaluate a frame of tracked objects and return the debounced alert level,
     * the most threatening object (if any), and its per-object level.
     */
    fun evaluate(objects: List<TrackedObject>): AlertResult {
        var worstLevel = AlertLevel.SAFE
        var closestThreat: TrackedObject? = null

        for (obj in objects) {
            val objLevel = classifyObject(obj)
            if (objLevel > worstLevel) {
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

        currentLevel = debounce(worstLevel)

        return AlertResult(
            level = currentLevel,
            closestThreat = closestThreat,
        )
    }

    fun reset() {
        currentLevel = AlertLevel.SAFE
        lowerFrameCount = 0
    }

    private fun classifyObject(obj: TrackedObject): AlertLevel {
        val distance = obj.distanceMeters ?: return AlertLevel.SAFE
        val ttc = obj.ttcSeconds
        val isPerson = obj.detection.className == "person"

        // CRITICAL check — person has elevated TTC threshold
        val critTtc = if (isPerson) criticalTtcS * PERSON_TTC_MULTIPLIER else criticalTtcS
        if ((ttc != null && ttc < critTtc) || distance < criticalDistanceM) {
            return AlertLevel.CRITICAL
        }

        // WARNING check
        if ((ttc != null && ttc < warningTtcS) || distance < warningDistanceM) {
            return AlertLevel.WARNING
        }

        return AlertLevel.SAFE
    }

    private fun debounce(rawLevel: AlertLevel): AlertLevel {
        if (rawLevel >= currentLevel) {
            lowerFrameCount = 0
            return rawLevel
        }

        lowerFrameCount++
        return if (lowerFrameCount >= DEBOUNCE_FRAMES) {
            lowerFrameCount = 0
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
