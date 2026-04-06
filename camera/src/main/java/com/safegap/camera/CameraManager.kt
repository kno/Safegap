package com.safegap.camera

import android.content.Context
import android.util.Log
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Info about available zoom range for current camera.
 */
data class CameraZoomState(
    val minZoomRatio: Float = 1f,
    val maxZoomRatio: Float = 1f,
    val currentZoomRatio: Float = 1f,
    val availableLensCount: Int = 1,
)

@Singleton
class CameraManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val frameProducer: FrameProducer,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var previewView: PreviewView? = null
    private var currentLensIndex = 0
    private var backCameraInfos: List<CameraInfo> = emptyList()

    private val _zoomState = MutableStateFlow(CameraZoomState())
    val zoomState: StateFlow<CameraZoomState> = _zoomState.asStateFlow()

    companion object {
        private const val TAG = "SafeGap.Camera"
    }

    fun start(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        this.lifecycleOwner = lifecycleOwner
        this.previewView = previewView

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            cameraProvider = provider

            // Discover back-facing cameras
            backCameraInfos = provider.availableCameraInfos.filter { info ->
                val lensFacing = info.lensFacing
                lensFacing == CameraSelector.LENS_FACING_BACK
            }
            Log.i(TAG, "Found ${backCameraInfos.size} back camera(s)")

            bindCamera(provider, lifecycleOwner, previewView)
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Set the zoom ratio for the current camera.
     */
    fun setZoomRatio(ratio: Float) {
        val cam = camera ?: return
        val clamped = ratio.coerceIn(
            cam.cameraInfo.zoomState.value?.minZoomRatio ?: 1f,
            cam.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f,
        )
        cam.cameraControl.setZoomRatio(clamped)
        _zoomState.value = _zoomState.value.copy(currentZoomRatio = clamped)
    }

    /**
     * Switch to the next available back camera lens (wide, main, tele).
     * Returns true if switched, false if only one lens.
     */
    fun switchLens(): Boolean {
        if (backCameraInfos.size <= 1) return false
        currentLensIndex = (currentLensIndex + 1) % backCameraInfos.size

        val provider = cameraProvider ?: return false
        val owner = lifecycleOwner ?: return false
        val preview = previewView ?: return false

        bindCamera(provider, owner, preview)
        return true
    }

    fun stop() {
        cameraProvider?.unbindAll()
        camera = null
        Log.i(TAG, "Camera stopped")
    }

    private fun bindCamera(
        provider: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
    ) {
        val preview = Preview.Builder()
            .build()
            .also { it.surfaceProvider = previewView.surfaceProvider }

        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(640, 480))
            .setBackpressureStrategy(STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(
                    ContextCompat.getMainExecutor(context),
                ) { imageProxy ->
                    scope.launch {
                        try {
                            frameProducer.emit(imageProxy)
                        } finally {
                            imageProxy.close()
                        }
                    }
                }
            }

        // Select camera by index into available back cameras
        val cameraSelector = if (backCameraInfos.isNotEmpty()) {
            val targetInfo = backCameraInfos[currentLensIndex]
            provider.availableCameraInfos
                .indexOf(targetInfo)
                .let { idx ->
                    if (idx >= 0) {
                        CameraSelector.Builder()
                            .addCameraFilter { cameras -> cameras.filter { it == targetInfo } }
                            .build()
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }
                }
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis,
            )

            // Observe zoom state
            camera?.cameraInfo?.zoomState?.observeForever { zoom ->
                _zoomState.value = CameraZoomState(
                    minZoomRatio = zoom.minZoomRatio,
                    maxZoomRatio = zoom.maxZoomRatio,
                    currentZoomRatio = zoom.zoomRatio,
                    availableLensCount = backCameraInfos.size,
                )
            }

            Log.i(TAG, "Camera bound (lens index=$currentLensIndex)")
        } catch (e: Exception) {
            Log.e(TAG, "Camera binding failed", e)
        }
    }
}
