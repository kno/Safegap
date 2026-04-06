package com.safegap.detection

import android.graphics.Bitmap
import android.graphics.RectF
import com.safegap.camera.CameraFrame
import com.safegap.core.model.RawDetection
import com.safegap.detection.tracking.IoUTracker
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Integration test for DetectionPipeline: detect -> track -> emit.
 * Uses a fake ObjectDetector to avoid TFLite dependency.
 */
@RunWith(RobolectricTestRunner::class)
class DetectionPipelineTest {

    /** Fake detector that returns pre-configured detections without TFLite. */
    private class FakeObjectDetector : ObjectDetectorApi {
        @Volatile
        var detectionsToReturn: List<RawDetection> = emptyList()

        override suspend fun awaitReady() {
            // No-op: always ready
        }

        override fun detect(bitmap: Bitmap, timestampMs: Long): List<RawDetection> {
            return detectionsToReturn.map { it.copy(timestampMs = timestampMs) }
        }
    }

    private fun createFrameFlow() = MutableSharedFlow<CameraFrame>(
        replay = 1,
        extraBufferCapacity = 2,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    @Test
    fun `pipeline emits PipelineResult with tracked objects`() = runBlocking {
        val fakeDetector = FakeObjectDetector()
        fakeDetector.detectionsToReturn = listOf(
            RawDetection(
                boundingBox = RectF(0.1f, 0.1f, 0.3f, 0.3f),
                classId = 0,
                className = "car",
                confidence = 0.9f,
                timestampMs = 0L,
            ),
            RawDetection(
                boundingBox = RectF(0.5f, 0.5f, 0.7f, 0.7f),
                classId = 1,
                className = "person",
                confidence = 0.85f,
                timestampMs = 0L,
            ),
        )

        val tracker = IoUTracker()
        val pipeline = DetectionPipeline(fakeDetector, tracker)

        val frameFlow = createFrameFlow()

        // Emit frame before collecting (replay=1 ensures it's buffered)
        val bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)
        frameFlow.emit(CameraFrame(bitmap, timestampMs = 100L, rotationDegrees = 0))

        val result = withTimeout(5000L) {
            pipeline.process(frameFlow).first()
        }

        assertEquals(2, result.trackedObjects.size)
        assertTrue(result.trackedObjects.any { it.detection.className == "car" })
        assertTrue(result.trackedObjects.any { it.detection.className == "person" })
        assertTrue(result.imageHeightPx > 0)

        // Track IDs should be assigned
        val ids = result.trackedObjects.map { it.trackId }.toSet()
        assertEquals(2, ids.size)

        pipeline.close()
    }

    @Test
    fun `pipeline tracks objects across frames`() = runBlocking {
        val fakeDetector = FakeObjectDetector()
        val tracker = IoUTracker()
        val pipeline = DetectionPipeline(fakeDetector, tracker)

        val frameFlow = createFrameFlow()

        val carDetection = RawDetection(
            boundingBox = RectF(0.1f, 0.1f, 0.3f, 0.3f),
            classId = 0,
            className = "car",
            confidence = 0.9f,
            timestampMs = 0L,
        )

        fakeDetector.detectionsToReturn = listOf(carDetection)

        val bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)

        // Start collecting in background
        val results = mutableListOf<PipelineResult>()
        val job = launch {
            pipeline.process(frameFlow).take(2).toList(results)
        }

        // Give the collector time to subscribe
        yield()
        kotlinx.coroutines.delay(200)

        // Emit first frame
        frameFlow.emit(CameraFrame(bitmap, timestampMs = 100L, rotationDegrees = 0))

        // Wait a bit, then shift bbox slightly for second frame
        kotlinx.coroutines.delay(200)
        fakeDetector.detectionsToReturn = listOf(
            carDetection.copy(
                boundingBox = RectF(0.12f, 0.12f, 0.32f, 0.32f),
            ),
        )
        frameFlow.emit(CameraFrame(bitmap, timestampMs = 166L, rotationDegrees = 0))

        withTimeout(10000L) { job.join() }

        assertEquals(2, results.size)
        // Same object should keep same track ID across frames
        assertEquals(
            results[0].trackedObjects[0].trackId,
            results[1].trackedObjects[0].trackId,
        )

        pipeline.close()
    }
}
