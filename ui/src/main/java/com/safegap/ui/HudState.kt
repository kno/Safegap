package com.safegap.ui

import com.safegap.core.DisplayState
import com.safegap.core.model.AlertLevel
import com.safegap.core.model.TrackedObject

data class HudState(
    val isCameraActive: Boolean = false,
    val alertLevel: AlertLevel = AlertLevel.SAFE,
    val displayStates: List<DisplayState> = emptyList(),
    val closestThreat: TrackedObject? = null,
    val fps: Float = 0f,
    val thermalThrottled: Boolean = false,
)
