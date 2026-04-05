package com.safegap.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.safegap.alert.AlertEngine
import com.safegap.alert.AudioAlertPlayer
import com.safegap.app.R
import com.safegap.camera.FrameProducer
import com.safegap.core.Constants
import com.safegap.core.DisplayStateManager
import com.safegap.core.HudRepository
import com.safegap.core.SettingsRepository
import com.safegap.detection.DetectionPipeline
import com.safegap.detection.ObjectDetector
import com.safegap.estimation.CameraIntrinsics
import com.safegap.estimation.DistanceEstimator
import com.safegap.estimation.SpeedTracker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DrivingService : LifecycleService() {

    companion object {
        private const val TAG = "SafeGap.Service"
    }

    @Inject lateinit var objectDetector: ObjectDetector
    @Inject lateinit var detectionPipeline: DetectionPipeline
    @Inject lateinit var frameProducer: FrameProducer
    @Inject lateinit var distanceEstimator: DistanceEstimator
    @Inject lateinit var speedTracker: SpeedTracker
    @Inject lateinit var alertEngine: AlertEngine
    @Inject lateinit var audioAlertPlayer: AudioAlertPlayer
    @Inject lateinit var displayStateManager: DisplayStateManager
    @Inject lateinit var hudRepository: HudRepository
    @Inject lateinit var settingsRepository: SettingsRepository

    private var thermalThrottled = false
    private var frameCount = 0
    private var fpsWindowStartMs = 0L
    private var currentFps = 0f

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                Constants.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA,
            )
        } else {
            startForeground(Constants.NOTIFICATION_ID, notification)
        }
        Log.i(TAG, "DrivingService started")

        audioAlertPlayer.initialize()
        registerThermalCallback()
        observeSettings()
        startPipeline()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() {
        objectDetector.close()
        audioAlertPlayer.release()
        displayStateManager.reset()
        hudRepository.reset()
        super.onDestroy()
        Log.i(TAG, "DrivingService stopped")
    }

    private fun observeSettings() {
        lifecycleScope.launch {
            settingsRepository.settings.collect { settings ->
                alertEngine.updateSettings(settings)
                distanceEstimator.updateIntrinsics(
                    CameraIntrinsics(
                        focalLengthMm = settings.focalLengthMm,
                        sensorHeightMm = settings.sensorHeightMm,
                    ),
                )
                Log.d(TAG, "Settings updated: $settings")
            }
        }
    }

    private fun registerThermalCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val powerManager = getSystemService(PowerManager::class.java)
            powerManager.addThermalStatusListener { status ->
                val wasThermal = thermalThrottled
                thermalThrottled = status >= PowerManager.THERMAL_STATUS_SEVERE
                if (thermalThrottled != wasThermal) {
                    Log.w(TAG, "Thermal status changed: throttled=$thermalThrottled (status=$status)")
                }
            }
        }
    }

    private fun startPipeline() {
        lifecycleScope.launch {
            objectDetector.initialize()
        }

        lifecycleScope.launch {
            detectionPipeline.process(frameProducer.frames).collect { result ->
                // FPS tracking
                updateFps()

                // Skip frames under thermal throttling (process 1 of every 2)
                if (thermalThrottled && frameCount % 2 != 0) return@collect

                // Estimation
                val enriched = result.trackedObjects.map { obj ->
                    val rawDist = distanceEstimator.estimate(
                        obj.detection,
                        result.imageHeightPx,
                    )
                    if (rawDist != null) {
                        val estimate = speedTracker.update(
                            obj.trackId,
                            rawDist,
                            obj.detection.timestampMs,
                        )
                        obj.copy(
                            distanceMeters = estimate.filteredDistanceM,
                            speedMps = estimate.speedMps,
                            ttcSeconds = estimate.ttcSeconds,
                        )
                    } else {
                        obj
                    }
                }

                speedTracker.pruneExcept(enriched.map { it.trackId }.toSet())

                // Alert evaluation
                val alertResult = alertEngine.evaluate(enriched)

                // Audio
                audioAlertPlayer.play(alertResult.level)

                // Stabilize display output
                val displayStates = displayStateManager.update(enriched)

                // Push to UI
                hudRepository.update(
                    alertLevel = alertResult.level,
                    displayStates = displayStates,
                    closestThreat = alertResult.closestThreat,
                    fps = currentFps,
                    thermalThrottled = thermalThrottled,
                )
            }
        }
    }

    private fun updateFps() {
        val now = System.currentTimeMillis()
        frameCount++
        if (fpsWindowStartMs == 0L) {
            fpsWindowStartMs = now
            return
        }
        val elapsed = now - fpsWindowStartMs
        if (elapsed >= 1000L) {
            currentFps = frameCount * 1000f / elapsed
            frameCount = 0
            fpsWindowStartMs = now
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
