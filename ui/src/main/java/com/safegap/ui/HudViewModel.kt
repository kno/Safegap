package com.safegap.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safegap.camera.CameraManager
import com.safegap.camera.CameraZoomState
import com.safegap.core.HudRepository
import com.safegap.core.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HudViewModel @Inject constructor(
    hudRepository: HudRepository,
    private val cameraManager: CameraManager,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<HudState> = hudRepository.hudData
        .map { data ->
            HudState(
                isCameraActive = true,
                alertLevel = data.alertLevel,
                displayStates = data.displayStates,
                closestThreat = data.closestThreat,
                fps = data.fps,
                thermalThrottled = data.thermalThrottled,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HudState())

    val zoomState: StateFlow<CameraZoomState> = cameraManager.zoomState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CameraZoomState())

    val cameraHeightM: StateFlow<Float> = settingsRepository.settings
        .map { it.cameraHeightM }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.2f)

    val focalLengthMm: StateFlow<Float> = settingsRepository.settings
        .map { it.focalLengthMm }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 3.6f)

    fun setZoomRatio(ratio: Float) {
        cameraManager.setZoomRatio(ratio)
    }

    fun switchLens() {
        cameraManager.switchLens()
    }

    fun updateCameraHeight(value: Float) {
        viewModelScope.launch {
            settingsRepository.updateCameraParams(
                cameraHeightM = value,
                focalLengthMm = focalLengthMm.value,
                sensorHeightMm = settingsRepository.currentSensorHeightMm(),
            )
        }
    }

    fun updateFocalLength(value: Float) {
        viewModelScope.launch {
            settingsRepository.updateCameraParams(
                cameraHeightM = cameraHeightM.value,
                focalLengthMm = value,
                sensorHeightMm = settingsRepository.currentSensorHeightMm(),
            )
        }
    }
}
