package com.safegap.core

import android.graphics.RectF
import com.safegap.core.model.RawDetection
import com.safegap.core.model.TrackedObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DisplayStateManagerTest {

    private lateinit var manager: DisplayStateManager

    @Before
    fun setUp() {
        manager = DisplayStateManager()
    }

    private fun trackedObject(
        trackId: Int = 1,
        className: String = "car",
        left: Float = 0.1f,
        top: Float = 0.1f,
        right: Float = 0.3f,
        bottom: Float = 0.3f,
        distanceMeters: Float? = 10f,
        speedMps: Float? = 2f,
        ttcSeconds: Float? = 5f,
    ) = TrackedObject(
        trackId = trackId,
        detection = RawDetection(
            boundingBox = RectF(left, top, right, bottom),
            classId = 0,
            className = className,
            confidence = 0.9f,
            timestampMs = 0L,
        ),
        distanceMeters = distanceMeters,
        speedMps = speedMps,
        ttcSeconds = ttcSeconds,
    )

    // --- Birth threshold ---

    @Test
    fun `objects below BIRTH_FRAMES threshold are not visible`() {
        // BIRTH_FRAMES = 3, so 1st and 2nd frames should not produce display state
        val obj = trackedObject(trackId = 1)

        val result1 = manager.update(listOf(obj))
        assertTrue("Frame 1: should not be visible", result1.isEmpty())

        val result2 = manager.update(listOf(obj))
        assertTrue("Frame 2: should not be visible", result2.isEmpty())
    }

    @Test
    fun `objects appear after BIRTH_FRAMES consecutive detections`() {
        val obj = trackedObject(trackId = 1)

        manager.update(listOf(obj)) // frame 1
        manager.update(listOf(obj)) // frame 2
        val result3 = manager.update(listOf(obj)) // frame 3 — should be born

        assertEquals("Frame 3: should be visible", 1, result3.size)
        assertEquals(1, result3[0].trackId)
    }

    // --- Grace period expiry ---

    @Test
    fun `track removed after GRACE_FRAMES missed frames`() {
        val obj = trackedObject(trackId = 1)

        // Birth the object (3 frames)
        repeat(3) { manager.update(listOf(obj)) }

        // Miss frames: GRACE_FRAMES = 8, track should persist through frame 8
        for (i in 1..8) {
            val result = manager.update(emptyList())
            assertEquals("Grace frame $i: should still be visible", 1, result.size)
        }

        // Frame 9 without detection: should expire (missedFrames = 9 > 8)
        val expired = manager.update(emptyList())
        assertTrue("After grace period: track should be removed", expired.isEmpty())
    }

    @Test
    fun `display confidence decays linearly during grace period`() {
        val obj = trackedObject(trackId = 1)

        // Birth the object
        repeat(3) { manager.update(listOf(obj)) }

        // Active frame should have confidence 1.0
        val active = manager.update(listOf(obj))
        assertEquals(1.0f, active[0].displayConfidence, 0.01f)

        // First missed frame: confidence = 1 - 1/8 = 0.875
        val missed1 = manager.update(emptyList())
        assertEquals(0.875f, missed1[0].displayConfidence, 0.01f)

        // Fourth missed frame: confidence = 1 - 4/8 = 0.5
        manager.update(emptyList()) // 2nd miss
        manager.update(emptyList()) // 3rd miss
        val missed4 = manager.update(emptyList()) // 4th miss
        assertEquals(0.5f, missed4[0].displayConfidence, 0.01f)
    }

    // --- Dead-reckoning ---

    @Test
    fun `dead-reckoning projects bbox forward when object approaches`() {
        val obj = trackedObject(
            trackId = 1,
            top = 0.2f,
            bottom = 0.4f,
            distanceMeters = 10f,
            speedMps = 5f, // approaching at 5 m/s
        )

        // Birth the object
        repeat(3) { manager.update(listOf(obj)) }

        // Get bbox before grace
        val beforeGrace = manager.update(listOf(obj))
        val boxBefore = beforeGrace[0].smoothedBox

        // Miss one frame — dead-reckoning should expand bbox (object approaches)
        val afterMiss = manager.update(emptyList())
        val boxAfter = afterMiss[0].smoothedBox

        // When approaching, bbox should grow (bottom moves down or top moves up)
        val heightBefore = boxBefore.bottom - boxBefore.top
        val heightAfter = boxAfter.bottom - boxAfter.top
        assertTrue(
            "Bbox should grow as object approaches: before=$heightBefore, after=$heightAfter",
            heightAfter > heightBefore,
        )
    }

    @Test
    fun `dead-reckoning does not project when speed is zero`() {
        val obj = trackedObject(
            trackId = 1,
            distanceMeters = 10f,
            speedMps = 0f,
        )

        // Birth the object
        repeat(3) { manager.update(listOf(obj)) }
        val before = manager.update(listOf(obj))
        val boxBefore = before[0].smoothedBox

        // Miss frame — no dead-reckoning since speed = 0
        val after = manager.update(emptyList())
        val boxAfter = after[0].smoothedBox

        assertEquals(boxBefore.left, boxAfter.left, 0.001f)
        assertEquals(boxBefore.right, boxAfter.right, 0.001f)
    }

    // --- Median robustness ---

    @Test
    fun `single outlier distance does not spike display`() {
        val obj = trackedObject(trackId = 1, distanceMeters = 10f)

        // Birth and stabilize with consistent distance
        repeat(5) { manager.update(listOf(obj)) }

        val stableResult = manager.update(listOf(obj))
        val stableDistance = stableResult[0].displayDistanceM!!

        // Inject a single outlier (100m)
        val outlier = trackedObject(trackId = 1, distanceMeters = 100f)
        val afterOutlier = manager.update(listOf(outlier))
        val outlierDistance = afterOutlier[0].displayDistanceM!!

        // Due to median + EMA, the display distance should not jump to 100
        // It should remain much closer to 10 than to 100
        assertTrue(
            "Outlier should be dampened: stable=$stableDistance, afterOutlier=$outlierDistance",
            outlierDistance < 30f,
        )
    }

    // --- EMA smoothing ---

    @Test
    fun `EMA smoothing produces gradual transitions in distance`() {
        val obj = trackedObject(trackId = 1, distanceMeters = 20f)

        // Birth and stabilize with enough frames to fill history buffer
        repeat(10) { manager.update(listOf(obj)) }

        val stableResult = manager.update(listOf(obj))
        val stableDist = stableResult[0].displayDistanceM!!

        // Now send multiple frames at 30m to shift median and observe EMA lag
        val jumped = trackedObject(trackId = 1, distanceMeters = 30f)
        // Fill history with new values (HISTORY_SIZE = 8)
        repeat(8) { manager.update(listOf(jumped)) }
        val result = manager.update(listOf(jumped))
        val smoothedDist = result[0].displayDistanceM!!

        // After 9 frames at 30m, EMA should be approaching 30 but still lagging
        // The smoothed distance should show gradual transition, not immediate jump
        // It should be closer to 30 than to 20 after 9 frames, but not exactly 30
        assertTrue(
            "Smoothed distance ($smoothedDist) should be between stable ($stableDist) and 30",
            smoothedDist > stableDist && smoothedDist < 30f,
        )
    }

    @Test
    fun `EMA smoothing produces gradual transitions in bbox`() {
        val obj1 = trackedObject(trackId = 1, left = 0.1f, right = 0.3f)

        // Birth and stabilize
        repeat(5) { manager.update(listOf(obj1)) }
        val before = manager.update(listOf(obj1))

        // Sudden jump in bbox position
        val obj2 = trackedObject(trackId = 1, left = 0.5f, right = 0.7f)
        val after = manager.update(listOf(obj2))

        // Smoothed left should be between old (0.1) and new (0.5)
        val smoothedLeft = after[0].smoothedBox.left
        assertTrue(
            "Smoothed left ($smoothedLeft) should be between old and new",
            smoothedLeft > 0.1f && smoothedLeft < 0.5f,
        )
    }

    @Test
    fun `EMA smoothing converges to stable value over time`() {
        val obj = trackedObject(trackId = 1, distanceMeters = 15f)

        // Birth
        repeat(3) { manager.update(listOf(obj)) }

        // Many frames at consistent distance
        var lastDistance = 0f
        repeat(20) {
            val result = manager.update(listOf(obj))
            lastDistance = result[0].displayDistanceM!!
        }

        assertEquals(15f, lastDistance, 0.5f)
    }

    // --- Reset ---

    @Test
    fun `reset clears all tracks`() {
        val obj = trackedObject(trackId = 1)
        repeat(5) { manager.update(listOf(obj)) }

        manager.reset()

        val result = manager.update(listOf(obj))
        assertTrue("After reset, object should not be born yet", result.isEmpty())
    }

    // --- Multiple tracks ---

    @Test
    fun `multiple tracks are managed independently`() {
        val obj1 = trackedObject(trackId = 1, distanceMeters = 10f)
        val obj2 = trackedObject(trackId = 2, distanceMeters = 20f, left = 0.5f, right = 0.7f)

        // Birth both
        repeat(3) {
            manager.update(listOf(obj1, obj2))
        }

        val result = manager.update(listOf(obj1, obj2))
        assertEquals(2, result.size)

        // Remove obj1, keep obj2
        val partial = manager.update(listOf(obj2))
        assertEquals(2, partial.size) // obj1 in grace period + obj2

        // obj2 should still have distance near 20
        val track2 = partial.first { it.trackId == 2 }
        assertTrue(track2.displayDistanceM!! > 15f)
    }
}
