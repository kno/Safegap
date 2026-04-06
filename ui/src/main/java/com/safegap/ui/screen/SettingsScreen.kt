package com.safegap.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import kotlin.math.roundToInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safegap.core.SafeGapSettings
import com.safegap.ui.SettingsViewModel

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "Ajustes",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // --- Alert thresholds ---
        SectionHeader("Umbrales de alerta")

        SettingSlider(
            label = "TTC critico",
            value = settings.criticalTtcS,
            range = 0.5f..(settings.warningTtcS - 0.5f).coerceAtLeast(1.0f),
            unit = "s",
            onValueChange = { viewModel.updateCriticalTtc(it) },
        )

        SettingSlider(
            label = "Distancia critica",
            value = settings.criticalDistanceM,
            range = 1f..(settings.warningDistanceM - 1f).coerceAtLeast(2f),
            unit = "m",
            onValueChange = { viewModel.updateCriticalDistance(it) },
        )

        SettingSlider(
            label = "TTC aviso",
            value = settings.warningTtcS,
            range = 1f..10f,
            unit = "s",
            onValueChange = { viewModel.updateWarningTtc(it) },
        )

        SettingSlider(
            label = "Distancia aviso",
            value = settings.warningDistanceM,
            range = 5f..40f,
            unit = "m",
            onValueChange = { viewModel.updateWarningDistance(it) },
        )

        Spacer(modifier = Modifier.height(16.dp))

        // --- Stabilization ---
        SectionHeader("Estabilizacion")

        IntSettingSlider(
            label = "Ventana de suavizado",
            value = settings.smoothingWindowSize,
            range = 1..10,
            unit = "muestras",
            onValueChange = { viewModel.updateSmoothingWindow(it) },
        )

        Spacer(modifier = Modifier.height(16.dp))

        // --- Camera calibration ---
        SectionHeader("Calibracion de camara")

        SettingSlider(
            label = "Altura de montaje",
            value = settings.cameraHeightM,
            range = 0.5f..2.5f,
            unit = "m",
            onValueChange = { viewModel.updateCameraHeight(it) },
        )

        SettingSlider(
            label = "Longitud focal",
            value = settings.focalLengthMm,
            range = 1.5f..8.0f,
            unit = "mm",
            onValueChange = { viewModel.updateFocalLength(it) },
        )

        SettingSlider(
            label = "Altura del sensor",
            value = settings.sensorHeightMm,
            range = 2.0f..7.0f,
            unit = "mm",
            onValueChange = { viewModel.updateSensorHeight(it) },
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Volver")
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "${"%.1f".format(value)} $unit",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
        )
    }
}

@Composable
private fun IntSettingSlider(
    label: String,
    value: Int,
    range: IntRange,
    unit: String,
    onValueChange: (Int) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "$value $unit",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = range.last - range.first - 1,
        )
    }
}
