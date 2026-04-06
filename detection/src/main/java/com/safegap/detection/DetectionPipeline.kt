package com.safegap.detection

import android.util.Log
import com.safegap.camera.BitmapPool
import com.safegap.camera.CameraFrame
import com.safegap.core.model.TrackedObject
import com.safegap.detection.tracking.IoUTracker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.Closeable
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

data class PipelineResult(
    val trackedObjects: List<TrackedObject>,
    val imageHeightPx: Int,
)

@Singleton
class DetectionPipeline @Inject constructor(
    private val objectDetector: ObjectDetectorApi,
    private val ioUTracker: IoUTracker,
    private val bitmapPool: BitmapPool,
) : Closeable {
    companion object {
        private const val TAG = "SafeGap.Pipeline"
        private const val FRAME_BUDGET_MS = 66L
        private const val OVERBUDGET_THRESHOLD = 10
    }

    private val pipelineDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "safegap-pipeline").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    private var consecutiveOverBudget = 0

    /**
     * Collects frames from [frameFlow], runs detection + tracking,
     * and emits [PipelineResult] on a dedicated single-thread dispatcher.
     */
    fun process(frameFlow: SharedFlow<CameraFrame>): Flow<PipelineResult> = flow {
        objectDetector.awaitReady()
        Log.i(TAG, "Detection pipeline started")

        frameFlow.collect { frame ->
            try {
                val startMs = System.currentTimeMillis()

                val rawDetections = objectDetector.detect(frame.bitmap, frame.timestampMs)
                val imageHeight = frame.bitmap.height
                bitmapPool.release(frame.bitmap)

                val tracked = ioUTracker.update(rawDetections)

                val elapsed = System.currentTimeMillis() - startMs
                Log.d(
                    TAG,
                    "Frame processed in ${elapsed}ms — " +
                        "${rawDetections.size} detections, ${tracked.size} tracked",
                )

                // Frame budget monitoring
                if (elapsed > FRAME_BUDGET_MS) {
                    consecutiveOverBudget++
                    if (consecutiveOverBudget > OVERBUDGET_THRESHOLD) {
                        Log.w(TAG, "Consistently over frame budget: ${elapsed}ms avg")
                    }
                } else {
                    consecutiveOverBudget = 0
                }

                emit(PipelineResult(tracked, imageHeight))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error processing frame", e)
            }
        }
    }.flowOn(pipelineDispatcher)

    override fun close() {
        pipelineDispatcher.close()
    }
}
