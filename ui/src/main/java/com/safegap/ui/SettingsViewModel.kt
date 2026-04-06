package com.safegap.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safegap.core.SafeGapSettings
import com.safegap.core.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<SafeGapSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SafeGapSettings())

    fun updateCriticalTtc(value: Float) = updateAlert {
        it.copy(criticalTtcS = value
            .coerceAtLeast(0.5f)
            .coerceAtMost(it.warningTtcS - 0.5f))
    }

    fun updateCriticalDistance(value: Float) = updateAlert {
        it.copy(criticalDistanceM = value
            .coerceAtLeast(1f)
            .coerceAtMost(it.warningDistanceM - 1f))
    }

    fun updateWarningTtc(value: Float) = updateAlert {
        it.copy(warningTtcS = value.coerceAtLeast(it.criticalTtcS + 0.5f))
    }

    fun updateWarningDistance(value: Float) = updateAlert {
        it.copy(warningDistanceM = value.coerceAtLeast(it.criticalDistanceM + 1f))
    }

    fun updateSmoothingWindow(value: Int) {
        viewModelScope.launch {
            settingsRepository.updateSmoothingWindowSize(value)
        }
    }

    fun updateCameraHeight(value: Float) = updateCamera { it.copy(cameraHeightM = value) }
    fun updateFocalLength(value: Float) = updateCamera { it.copy(focalLengthMm = value) }
    fun updateSensorHeight(value: Float) = updateCamera { it.copy(sensorHeightMm = value) }

    private fun updateAlert(transform: (SafeGapSettings) -> SafeGapSettings) {
        viewModelScope.launch {
            val updated = transform(settings.value)
            settingsRepository.updateAlertThresholds(
                criticalTtcS = updated.criticalTtcS,
                criticalDistanceM = updated.criticalDistanceM,
                warningTtcS = updated.warningTtcS,
                warningDistanceM = updated.warningDistanceM,
            )
        }
    }

    private fun updateCamera(transform: (SafeGapSettings) -> SafeGapSettings) {
        viewModelScope.launch {
            val updated = transform(settings.value)
            settingsRepository.updateCameraParams(
                cameraHeightM = updated.cameraHeightM,
                focalLengthMm = updated.focalLengthMm,
                sensorHeightMm = updated.sensorHeightMm,
            )
        }
    }
}
