package com.safegap.detection.tracking

import android.graphics.RectF
import com.safegap.core.model.RawDetection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)

class IoUTrackerTest {

    private lateinit var tracker: IoUTracker

    @Before
    fun setUp() {
        tracker = IoUTracker()
    }

    private fun det(
        left: Float, top: Float, right: Float, bottom: Float,
        className: String = "car",
        confidence: Float = 0.9f,
        timestampMs: Long = 0L,
    ) = RawDetection(
        boundingBox = RectF(left, top, right, bottom),
        classId = 0,
        className = className,
        confidence = confidence,
        timestampMs = timestampMs,
    )

    // --- IoU computation ---

    @Test
    fun `computeIoU - identical boxes returns 1`() {
        val box = RectF(0.1f, 0.1f, 0.5f, 0.5f)
        assertEquals(1.0f, computeIoU(box, box), 0.001f)
    }

    @Test
    fun `computeIoU - no overlap returns 0`() {
        val a = RectF(0.0f, 0.0f, 0.2f, 0.2f)
        val b = RectF(0.5f, 0.5f, 0.8f, 0.8f)
        assertEquals(0.0f, computeIoU(a, b), 0.001f)
    }

    @Test
    fun `computeIoU - partial overlap`() {
        val a = RectF(0.0f, 0.0f, 0.4f, 0.4f)
        val b = RectF(0.2f, 0.2f, 0.6f, 0.6f)
        // Intersection: 0.2x0.2 = 0.04
        // Union: 0.16 + 0.16 - 0.04 = 0.28
        assertEquals(0.04f / 0.28f, computeIoU(a, b), 0.001f)
    }

    @Test
    fun `computeIoU - one box inside another`() {
        val outer = RectF(0.0f, 0.0f, 1.0f, 1.0f)
        val inner = RectF(0.2f, 0.2f, 0.4f, 0.4f)
        // Intersection = inner area = 0.04
        // Union = 1.0 + 0.04 - 0.04 = 1.0
        assertEquals(0.04f, computeIoU(outer, inner), 0.001f)
    }

    // --- Tracker behavior ---

    @Test
    fun `first frame assigns unique track IDs`() {
        val detections = listOf(
            det(0.1f, 0.1f, 0.3f, 0.3f, className = "car"),
            det(0.5f, 0.5f, 0.7f, 0.7f, className = "person"),
        )

        val tracked = tracker.update(detections)

        assertEquals(2, tracked.size)
        val ids = tracked.map { it.trackId }.toSet()
        assertEquals(2, ids.size) // unique IDs
    }

    @Test
    fun `same object in consecutive frames keeps track ID`() {
        val frame1 = listOf(det(0.1f, 0.1f, 0.3f, 0.3f))
        val result1 = tracker.update(frame1)

        // Slightly shifted bbox (high IoU)
        val frame2 = listOf(det(0.12f, 0.12f, 0.32f, 0.32f))
        val result2 = tracker.update(frame2)

        assertEquals(result1[0].trackId, result2[0].trackId)
    }

    @Test
    fun `different class objects are not matched`() {
        val frame1 = listOf(det(0.1f, 0.1f, 0.3f, 0.3f, className = "car"))
        val result1 = tracker.update(frame1)

        // Same bbox but different class
        val frame2 = listOf(det(0.1f, 0.1f, 0.3f, 0.3f, className = "person"))
        val result2 = tracker.update(frame2)

        // Should have different IDs (old car track in grace + new person track)
        assertTrue(result2.any { it.detection.className == "person" })
        val personTrack = result2.first { it.detection.className == "person" }
        assertTrue(personTrack.trackId != result1[0].trackId)
    }

    @Test
    fun `object disappearing enters grace period`() {
        val frame1 = listOf(det(0.1f, 0.1f, 0.3f, 0.3f))
        tracker.update(frame1)

        // No detections for 5 frames (grace period)
        for (i in 1..5) {
            val result = tracker.update(emptyList())
            assertEquals("Grace frame $i should keep track", 1, result.size)
        }

        // 6th frame without detection — should be removed
        val result = tracker.update(emptyList())
        assertEquals(0, result.size)
    }

    @Test
    fun `object reappearing within grace period keeps ID`() {
        val frame1 = listOf(det(0.1f, 0.1f, 0.3f, 0.3f))
        val result1 = tracker.update(frame1)
        val originalId = result1[0].trackId

        // Miss 3 frames
        tracker.update(emptyList())
        tracker.update(emptyList())
        tracker.update(emptyList())

        // Reappear at similar position
        val frame5 = listOf(det(0.12f, 0.12f, 0.32f, 0.32f))
        val result5 = tracker.update(frame5)

        assertEquals(1, result5.size)
        assertEquals(originalId, result5[0].trackId)
    }

    @Test
    fun `multiple objects tracked independently`() {
        val frame1 = listOf(
            det(0.0f, 0.0f, 0.2f, 0.2f, className = "car"),
            det(0.5f, 0.5f, 0.7f, 0.7f, className = "car"),
        )
        val result1 = tracker.update(frame1)
        val id1 = result1.first { it.detection.boundingBox.left < 0.3f }.trackId
        val id2 = result1.first { it.detection.boundingBox.left > 0.3f }.trackId

        // Both move slightly
        val frame2 = listOf(
            det(0.02f, 0.02f, 0.22f, 0.22f, className = "car"),
            det(0.52f, 0.52f, 0.72f, 0.72f, className = "car"),
        )
        val result2 = tracker.update(frame2)

        val newId1 = result2.first { it.detection.boundingBox.left < 0.3f }.trackId
        val newId2 = result2.first { it.detection.boundingBox.left > 0.3f }.trackId

        assertEquals(id1, newId1)
        assertEquals(id2, newId2)
    }

    @Test
    fun `low IoU creates new track`() {
        val frame1 = listOf(det(0.0f, 0.0f, 0.1f, 0.1f))
        val result1 = tracker.update(frame1)

        // Completely different position
        val frame2 = listOf(det(0.8f, 0.8f, 1.0f, 1.0f))
        val result2 = tracker.update(frame2)

        // Should be a new track (old one in grace period)
        val newTrack = result2.first { it.detection.boundingBox.left > 0.5f }
        assertTrue(newTrack.trackId != result1[0].trackId)
    }

    @Test
    fun `reset clears all tracks`() {
        tracker.update(listOf(det(0.1f, 0.1f, 0.3f, 0.3f)))

        tracker.reset()

        val result = tracker.update(listOf(det(0.1f, 0.1f, 0.3f, 0.3f)))
        assertEquals(1, result.size)
        assertEquals(1, result[0].trackId) // ID counter reset
    }
}
