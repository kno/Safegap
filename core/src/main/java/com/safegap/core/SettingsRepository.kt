package com.safegap.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "safegap_settings")

/**
 * User-configurable settings persisted via DataStore.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val CRITICAL_TTC = floatPreferencesKey("critical_ttc_s")
        val CRITICAL_DISTANCE = floatPreferencesKey("critical_distance_m")
        val WARNING_TTC = floatPreferencesKey("warning_ttc_s")
        val WARNING_DISTANCE = floatPreferencesKey("warning_distance_m")
        val CAMERA_HEIGHT = floatPreferencesKey("camera_height_m")
        val FOCAL_LENGTH = floatPreferencesKey("focal_length_mm")
        val SENSOR_HEIGHT = floatPreferencesKey("sensor_height_mm")
    }

    val settings: Flow<SafeGapSettings> = context.dataStore.data.map { prefs ->
        SafeGapSettings(
            criticalTtcS = prefs[Keys.CRITICAL_TTC] ?: SafeGapSettings.DEFAULT_CRITICAL_TTC_S,
            criticalDistanceM = prefs[Keys.CRITICAL_DISTANCE] ?: SafeGapSettings.DEFAULT_CRITICAL_DISTANCE_M,
            warningTtcS = prefs[Keys.WARNING_TTC] ?: SafeGapSettings.DEFAULT_WARNING_TTC_S,
            warningDistanceM = prefs[Keys.WARNING_DISTANCE] ?: SafeGapSettings.DEFAULT_WARNING_DISTANCE_M,
            cameraHeightM = prefs[Keys.CAMERA_HEIGHT] ?: SafeGapSettings.DEFAULT_CAMERA_HEIGHT_M,
            focalLengthMm = prefs[Keys.FOCAL_LENGTH] ?: SafeGapSettings.DEFAULT_FOCAL_LENGTH_MM,
            sensorHeightMm = prefs[Keys.SENSOR_HEIGHT] ?: SafeGapSettings.DEFAULT_SENSOR_HEIGHT_MM,
        )
    }

    suspend fun updateAlertThresholds(
        criticalTtcS: Float,
        criticalDistanceM: Float,
        warningTtcS: Float,
        warningDistanceM: Float,
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CRITICAL_TTC] = criticalTtcS
            prefs[Keys.CRITICAL_DISTANCE] = criticalDistanceM
            prefs[Keys.WARNING_TTC] = warningTtcS
            prefs[Keys.WARNING_DISTANCE] = warningDistanceM
        }
    }

    suspend fun updateCameraParams(
        cameraHeightM: Float,
        focalLengthMm: Float,
        sensorHeightMm: Float,
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CAMERA_HEIGHT] = cameraHeightM
            prefs[Keys.FOCAL_LENGTH] = focalLengthMm
            prefs[Keys.SENSOR_HEIGHT] = sensorHeightMm
        }
    }
}

data class SafeGapSettings(
    val criticalTtcS: Float = DEFAULT_CRITICAL_TTC_S,
    val criticalDistanceM: Float = DEFAULT_CRITICAL_DISTANCE_M,
    val warningTtcS: Float = DEFAULT_WARNING_TTC_S,
    val warningDistanceM: Float = DEFAULT_WARNING_DISTANCE_M,
    val cameraHeightM: Float = DEFAULT_CAMERA_HEIGHT_M,
    val focalLengthMm: Float = DEFAULT_FOCAL_LENGTH_MM,
    val sensorHeightMm: Float = DEFAULT_SENSOR_HEIGHT_MM,
) {
    companion object {
        const val DEFAULT_CRITICAL_TTC_S = 2.0f
        const val DEFAULT_CRITICAL_DISTANCE_M = 5.0f
        const val DEFAULT_WARNING_TTC_S = 4.0f
        const val DEFAULT_WARNING_DISTANCE_M = 15.0f
        const val DEFAULT_CAMERA_HEIGHT_M = 1.2f
        const val DEFAULT_FOCAL_LENGTH_MM = 3.6f
        const val DEFAULT_SENSOR_HEIGHT_MM = 4.55f
    }
}
