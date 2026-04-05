package com.safegap.ui.screen

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import androidx.camera.view.PreviewView
import com.safegap.ui.HudViewModel
import com.safegap.ui.components.AlertBanner
import com.safegap.ui.components.CameraPreviewSurface
import com.safegap.ui.components.DetectionOverlay
import com.safegap.ui.components.SpeedBadge

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HudScreen(
    onPreviewViewReady: (PreviewView) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HudViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

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

            // Detection bounding boxes overlay
            DetectionOverlay(
                trackedObjects = state.trackedObjects,
                alertLevel = state.alertLevel,
                modifier = Modifier.fillMaxSize(),
            )

            // Alert banner (top)
            AlertBanner(
                alertLevel = state.alertLevel,
                closestThreat = state.closestThreat,
                modifier = Modifier.align(Alignment.TopCenter),
            )

            // Speed badge (bottom-right)
            SpeedBadge(
                closestThreat = state.closestThreat,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            )
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

            androidx.compose.runtime.LaunchedEffect(Unit) {
                cameraPermissionState.launchPermissionRequest()
            }
        }
    }
}
