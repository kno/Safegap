package com.safegap.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safegap.camera.CameraZoomState

/**
 * Lateral panel with quick-access sliders for zoom, camera height, and focal length.
 * Slides in from the right edge. Auto-dismissed after 5s of inactivity by the caller.
 */
@Composable
fun QuickSettingsPanel(
    visible: Boolean,
    zoomState: CameraZoomState,
    cameraHeightM: Float,
    focalLengthMm: Float,
    onZoomChange: (Float) -> Unit,
    onSwitchLens: () -> Unit,
    onCameraHeightChange: (Float) -> Unit,
    onFocalLengthChange: (Float) -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { it }),
        exit = slideOutHorizontally(targetOffsetX = { it }),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(220.dp)
                .background(
                    Color.Black.copy(alpha = 0.75f),
                    RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                )
                .padding(horizontal = 12.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Zoom slider
            QuickSlider(
                label = "Zoom",
                value = zoomState.currentZoomRatio,
                range = zoomState.minZoomRatio..zoomState.maxZoomRatio,
                valueFormat = { "${"%.1f".format(it)}x" },
                onValueChange = {
                    onInteraction()
                    onZoomChange(it)
                },
            )

            // Switch lens button (only if multiple cameras)
            if (zoomState.availableLensCount > 1) {
                TextButton(onClick = {
                    onInteraction()
                    onSwitchLens()
                }) {
                    Text(
                        text = "Cambiar lente",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Camera height slider
            QuickSlider(
                label = "Altura",
                value = cameraHeightM,
                range = 0.5f..2.5f,
                valueFormat = { "${"%.2f".format(it)} m" },
                onValueChange = {
                    onInteraction()
                    onCameraHeightChange(it)
                },
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Focal length slider
            QuickSlider(
                label = "Focal",
                value = focalLengthMm,
                range = 1.5f..8.0f,
                valueFormat = { "${"%.1f".format(it)} mm" },
                onValueChange = {
                    onInteraction()
                    onFocalLengthChange(it)
                },
            )
        }
    }
}

@Composable
private fun QuickSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueFormat: (Float) -> String,
    onValueChange: (Float) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.width(196.dp),
        ) {
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
            )
            Text(
                text = valueFormat(value),
                color = Color.White,
                fontSize = 14.sp,
                textAlign = TextAlign.End,
            )
        }
        Slider(
            value = value.coerceIn(range),
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.width(196.dp),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f),
            ),
        )
    }
}
