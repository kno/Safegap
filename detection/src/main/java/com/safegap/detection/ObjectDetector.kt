package com.safegap.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.safegap.core.model.RawDetection
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.vision.detector.ObjectDetector as TfLiteObjectDetector
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObjectDetector @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "SafeGap.Detector"
    }

    private val _ready = CompletableDeferred<Unit>()

    private var detector: TfLiteObjectDetector? = null

    /**
     * Initialize the TFLite interpreter. Call once from a background thread
     * before the pipeline starts collecting frames.
     */
    suspend fun initialize() = withContext(Dispatchers.Default) {
        if (detector != null) return@withContext

        // INT8 quantized model is not compatible with GPU delegate — use NNAPI
        // with CPU fallback. NNAPI accelerates INT8 via DSP/NPU on supported devices.
        val baseOptions = BaseOptions.builder()
            .setNumThreads(DetectorConfig.NUM_THREADS)
            .useNnapi()
            .build()

        detector = try {
            val options = TfLiteObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(baseOptions)
                .setMaxResults(DetectorConfig.MAX_RESULTS)
                .setScoreThreshold(DetectorConfig.CONFIDENCE_THRESHOLD)
                .build()
            TfLiteObjectDetector.createFromFileAndOptions(
                context,
                DetectorConfig.MODEL_FILE,
                options,
            ).also { Log.i(TAG, "ObjectDetector initialized with NNAPI") }
        } catch (e: Exception) {
            Log.w(TAG, "NNAPI not available, falling back to CPU", e)
            val cpuOptions = TfLiteObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setNumThreads(DetectorConfig.NUM_THREADS)
                        .build(),
                )
                .setMaxResults(DetectorConfig.MAX_RESULTS)
                .setScoreThreshold(DetectorConfig.CONFIDENCE_THRESHOLD)
                .build()
            TfLiteObjectDetector.createFromFileAndOptions(
                context,
                DetectorConfig.MODEL_FILE,
                cpuOptions,
            ).also { Log.i(TAG, "ObjectDetector initialized with CPU") }
        }
        _ready.complete(Unit)
    }

    /** Block until the detector is ready. */
    suspend fun awaitReady() = _ready.await()

    /**
     * Run inference on a single frame.
     * Returns only detections for [DetectorConfig.RELEVANT_CLASSES].
     */
    fun detect(bitmap: Bitmap, timestampMs: Long): List<RawDetection> {
        val det = detector ?: throw IllegalStateException("ObjectDetector not initialized")

        val tensorImage = TensorImage.fromBitmap(bitmap)
        val results = det.detect(tensorImage)

        val imageWidth = bitmap.width.toFloat()
        val imageHeight = bitmap.height.toFloat()

        return results
            .flatMap { detection ->
                detection.categories.map { category -> detection.boundingBox to category }
            }
            .filter { (_, category) ->
                category.label in DetectorConfig.RELEVANT_CLASSES
            }
            .map { (bbox, category) ->
                // Normalize bounding box to [0, 1] relative coordinates
                RawDetection(
                    boundingBox = RectF(
                        bbox.left / imageWidth,
                        bbox.top / imageHeight,
                        bbox.right / imageWidth,
                        bbox.bottom / imageHeight,
                    ),
                    classId = category.index,
                    className = category.label,
                    confidence = category.score,
                    timestampMs = timestampMs,
                )
            }
    }

    fun close() {
        detector?.close()
        detector = null
    }
}
