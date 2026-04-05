package com.safegap.core

import com.safegap.core.model.AlertLevel
import com.safegap.core.model.TrackedObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared state holder bridging the detection pipeline (DrivingService)
 * and the UI layer (HudViewModel).
 *
 * Lives in :core so both :app and :ui can access it without circular deps.
 */
@Singleton
class HudRepository @Inject constructor() {

    private val _hudData = MutableStateFlow(HudData())
    val hudData: StateFlow<HudData> = _hudData.asStateFlow()

    fun update(
        alertLevel: AlertLevel,
        displayStates: List<DisplayState>,
        closestThreat: TrackedObject?,
        fps: Float = 0f,
        thermalThrottled: Boolean = false,
    ) {
        _hudData.value = HudData(
            alertLevel = alertLevel,
            displayStates = displayStates,
            closestThreat = closestThreat,
            fps = fps,
            thermalThrottled = thermalThrottled,
        )
    }

    fun reset() {
        _hudData.value = HudData()
    }
}

data class HudData(
    val alertLevel: AlertLevel = AlertLevel.SAFE,
    val displayStates: List<DisplayState> = emptyList(),
    val closestThreat: TrackedObject? = null,
    val fps: Float = 0f,
    val thermalThrottled: Boolean = false,
)
