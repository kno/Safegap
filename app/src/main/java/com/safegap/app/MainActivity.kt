package com.safegap.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.safegap.app.service.DrivingService
import com.safegap.camera.CameraManager
import com.safegap.ui.screen.HudScreen
import com.safegap.ui.screen.SettingsScreen
import com.safegap.ui.theme.HudTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var cameraManager: CameraManager

    private var previewBound = false
    private var savedPreviewView: PreviewView? = null
    private var showSettings by mutableStateOf(false)
    private var cameraPermissionGranted by mutableStateOf(false)

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        cameraPermissionGranted = granted
        if (granted) {
            startDrivingService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        hideSystemBars()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            HudTheme {
                if (showSettings) {
                    SettingsScreen(
                        onBack = { showSettings = false },
                    )
                } else {
                    HudScreen(
                        isCameraPermissionGranted = cameraPermissionGranted,
                        onPreviewViewReady = { previewView ->
                            savedPreviewView = previewView
                            bindCamera(previewView)
                        },
                        onSettingsClick = { showSettings = true },
                        isDebug = BuildConfig.DEBUG,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        cameraPermissionGranted = granted
        if (granted) {
            startDrivingService()
            savedPreviewView?.let { bindCamera(it) }
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startDrivingService() {
        startForegroundService(Intent(this, DrivingService::class.java))
    }

    override fun onPause() {
        super.onPause()
        cameraManager.stop()
        previewBound = false
        // Service intentionally NOT stopped here — it must survive
        // phone calls, screen locks, and brief app switches during driving.
    }

    /**
     * Explicitly stops the driving session and foreground service.
     * Call from UI (e.g., stop button) or when the activity is finishing.
     */
    fun stopDrivingSession() {
        stopService(Intent(this, DrivingService::class.java))
    }

    override fun onDestroy() {
        if (isFinishing) {
            stopDrivingSession()
        }
        super.onDestroy()
    }

    private fun bindCamera(previewView: PreviewView) {
        if (previewBound) return
        previewBound = true
        cameraManager.start(this, previewView)
    }

    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}
