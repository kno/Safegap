package com.safegap.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safegap.core.HudRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HudViewModel @Inject constructor(
    hudRepository: HudRepository,
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
}
