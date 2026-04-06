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
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
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
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
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
    // Singleton-scoped: lives for the process lifetime, no shutdown needed.
    // Recreating per start/stop cycle would add complexity with no practical benefit.
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var previewView: PreviewView? = null
    private var currentLensIndex = 0
    private var backCameraInfos: List<CameraInfo> = emptyList()

    private var zoomObserver: androidx.lifecycle.Observer<androidx.camera.core.ZoomState>? = null
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
        zoomObserver?.let { camera?.cameraInfo?.zoomState?.removeObserver(it) }
        zoomObserver = null
        scope.coroutineContext.cancelChildren()
        cameraProvider?.unbindAll()
        camera = null
        lifecycleOwner = null
        previewView = null
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
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(640, 480),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                        ),
                    ).build(),
            )
            .setBackpressureStrategy(STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    val bitmap = imageProxy.toBitmap()
                    val timestamp = imageProxy.imageInfo.timestamp / 1_000_000
                    val rotation = imageProxy.imageInfo.rotationDegrees
                    imageProxy.close() // release buffer immediately on analyzer thread
                    scope.launch {
                        frameProducer.emit(bitmap, timestamp, rotation)
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
            // Remove old zoom observer from OLD camera before unbinding
            zoomObserver?.let { obs ->
                camera?.cameraInfo?.zoomState?.removeObserver(obs)
            }

            provider.unbindAll()
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis,
            )

            // Track zoom state with a stored observer to avoid leaks on lens switch
            val obs = androidx.lifecycle.Observer<androidx.camera.core.ZoomState> { zoom ->
                _zoomState.value = CameraZoomState(
                    minZoomRatio = zoom.minZoomRatio,
                    maxZoomRatio = zoom.maxZoomRatio,
                    currentZoomRatio = zoom.zoomRatio,
                    availableLensCount = backCameraInfos.size,
                )
            }
            zoomObserver = obs
            camera?.cameraInfo?.zoomState?.observeForever(obs)

            Log.i(TAG, "Camera bound (lens index=$currentLensIndex)")
        } catch (e: Exception) {
            Log.e(TAG, "Camera binding failed", e)
        }
    }
}
