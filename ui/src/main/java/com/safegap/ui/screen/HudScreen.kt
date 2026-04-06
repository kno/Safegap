package com.safegap.ui.screen

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import androidx.camera.view.PreviewView
import com.safegap.ui.HudViewModel
import com.safegap.ui.R
import com.safegap.ui.components.AlertBanner
import com.safegap.ui.components.CameraPreviewSurface
import com.safegap.ui.components.DebugOverlay
import com.safegap.ui.components.DetectionOverlay
import com.safegap.ui.components.QuickSettingsPanel
import kotlinx.coroutines.delay

private const val AUTO_DISMISS_MS = 5000L

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HudScreen(
    onPreviewViewReady: (PreviewView) -> Unit,
    onSettingsClick: () -> Unit,
    isDebug: Boolean = false,
    modifier: Modifier = Modifier,
    viewModel: HudViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val zoomState by viewModel.zoomState.collectAsStateWithLifecycle()
    val cameraHeightM by viewModel.cameraHeightM.collectAsStateWithLifecycle()
    val focalLengthMm by viewModel.focalLengthMm.collectAsStateWithLifecycle()
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    var showQuickSettings by remember { mutableStateOf(false) }
    var lastInteractionMs by remember { mutableLongStateOf(0L) }

    // Auto-dismiss after 5 seconds of inactivity
    LaunchedEffect(showQuickSettings, lastInteractionMs) {
        if (showQuickSettings) {
            delay(AUTO_DISMISS_MS)
            showQuickSettings = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (cameraPermissionState.status.isGranted) {
            // Camera preview (bottom layer)
            CameraPreviewSurface(
                modifier = Modifier.fillMaxSize(),
                onPreviewViewReady = onPreviewViewReady,
            )

            // Tap area to toggle quick settings
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(showQuickSettings) {
                        detectTapGestures {
                            showQuickSettings = !showQuickSettings
                            if (showQuickSettings) {
                                lastInteractionMs = System.currentTimeMillis()
                            }
                        }
                    },
            )

            // Detection bounding boxes overlay
            DetectionOverlay(
                displayStates = state.displayStates,
                alertLevel = state.alertLevel,
                threatTrackId = state.closestThreat?.trackId,
                modifier = Modifier.fillMaxSize(),
            )

            // Alert banner (top)
            AlertBanner(
                alertLevel = state.alertLevel,
                closestThreat = state.closestThreat,
                modifier = Modifier.align(Alignment.TopCenter),
            )

            // Quick settings panel (right edge)
            QuickSettingsPanel(
                visible = showQuickSettings,
                zoomState = zoomState,
                cameraHeightM = cameraHeightM,
                focalLengthMm = focalLengthMm,
                onZoomChange = { viewModel.setZoomRatio(it) },
                onSwitchLens = { viewModel.switchLens() },
                onCameraHeightChange = { viewModel.updateCameraHeight(it) },
                onFocalLengthChange = { viewModel.updateFocalLength(it) },
                onInteraction = { lastInteractionMs = System.currentTimeMillis() },
                modifier = Modifier.align(Alignment.CenterEnd),
            )

            // Settings button (bottom-left)
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = "Ajustes",
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(28.dp),
                )
            }

            // Debug overlay (top-left, debug builds only)
            if (isDebug) {
                DebugOverlay(
                    fps = state.fps,
                    thermalThrottled = state.thermalThrottled,
                    objectCount = state.displayStates.size,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                )
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                val text = if (cameraPermissionState.status.shouldShowRationale) {
                    "SafeGap necesita acceso a la camara para detectar objetos y medir distancias. Concede el permiso para continuar."
                } else {
                    "Permiso de camara requerido"
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(32.dp),
                )
            }

            LaunchedEffect(Unit) {
                cameraPermissionState.launchPermissionRequest()
            }
        }
    }
}
