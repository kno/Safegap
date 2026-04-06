package com.safegap.detection

import android.util.Log
import com.safegap.camera.BitmapPool
import com.safegap.camera.CameraFrame
import com.safegap.core.model.TrackedObject
import com.safegap.detection.tracking.IoUTracker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

data class PipelineResult(
    val trackedObjects: List<TrackedObject>,
    val imageHeightPx: Int,
)

@Singleton
class DetectionPipeline @Inject constructor(
    private val objectDetector: ObjectDetector,
    private val ioUTracker: IoUTracker,
    private val bitmapPool: BitmapPool,
) {
    companion object {
        private const val TAG = "SafeGap.Pipeline"
    }

    /**
     * Collects frames from [frameFlow], runs detection + tracking,
     * and emits [PipelineResult] on [Dispatchers.Default].
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

                emit(PipelineResult(tracked, imageHeight))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error processing frame", e)
            }
        }
    }.flowOn(Dispatchers.Default)
}
