package com.safegap.ui

import com.safegap.core.model.AlertLevel
import com.safegap.core.model.TrackedObject

data class HudState(
    val isCameraActive: Boolean = false,
    val alertLevel: AlertLevel = AlertLevel.SAFE,
    val trackedObjects: List<TrackedObject> = emptyList(),
    val closestThreat: TrackedObject? = null,
)
