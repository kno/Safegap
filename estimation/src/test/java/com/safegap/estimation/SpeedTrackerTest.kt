package com.safegap.estimation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SpeedTrackerTest {

    private lateinit var tracker: SpeedTracker

    @Before
    fun setUp() {
        tracker = SpeedTracker()
    }

    @Test
    fun `first sample has zero speed`() {
        val result = tracker.update(trackId = 1, rawDistance = 30f, timestampMs = 1000)

        assertEquals(0f, result.speedMps, 0.1f)
        assertNull(result.ttcSeconds) // no meaningful speed yet
    }

    @Test
    fun `approaching object has positive speed`() {
        var t = 1000L
        // Object closing at ~10 m/s
        for (i in 0 until 10) {
            t += 100 // 10fps
            tracker.update(1, rawDistance = 50f - i * 1f, timestampMs = t)
        }

        val result = tracker.update(1, rawDistance = 39f, timestampMs = t + 100)

        assertTrue(
            "Speed should be positive (approaching): ${result.speedMps}",
            result.speedMps > 0f,
        )
    }

    @Test
    fun `approaching object has valid TTC`() {
        var t = 1000L
        // Object at 20m, closing at ~5 m/s
        for (i in 0 until 10) {
            t += 100
            tracker.update(1, rawDistance = 20f - i * 0.5f, timestampMs = t)
        }

        val result = tracker.update(1, rawDistance = 14.5f, timestampMs = t + 100)

        assertNotNull("TTC should not be null for approaching object", result.ttcSeconds)
        assertTrue("TTC should be positive: ${result.ttcSeconds}", result.ttcSeconds!! > 0f)
    }

    @Test
    fun `receding object has no TTC`() {
        var t = 1000L
        // Object moving away
        for (i in 0 until 10) {
            t += 100
            tracker.update(1, rawDistance = 10f + i * 1f, timestampMs = t)
        }

        val result = tracker.update(1, rawDistance = 21f, timestampMs = t + 100)

        assertNull("TTC should be null for receding object", result.ttcSeconds)
    }

    @Test
    fun `independent tracks do not interfere`() {
        val t = 1000L

        val r1 = tracker.update(1, rawDistance = 10f, timestampMs = t)
        val r2 = tracker.update(2, rawDistance = 50f, timestampMs = t)

        // Different distances
        assertTrue(
            kotlin.math.abs(r1.filteredDistanceM - r2.filteredDistanceM) > 10f,
        )
    }

    @Test
    fun `pruneExcept removes old tracks`() {
        tracker.update(1, rawDistance = 10f, timestampMs = 1000)
        tracker.update(2, rawDistance = 20f, timestampMs = 1000)
        tracker.update(3, rawDistance = 30f, timestampMs = 1000)

        tracker.pruneExcept(setOf(1, 3))

        // Track 2 was pruned; updating it starts fresh
        val result = tracker.update(2, rawDistance = 20f, timestampMs = 2000)
        assertEquals(0f, result.speedMps, 0.1f) // fresh track, no history
    }

    @Test
    fun `reset clears all state`() {
        tracker.update(1, rawDistance = 10f, timestampMs = 1000)
        tracker.update(1, rawDistance = 9f, timestampMs = 1100)

        tracker.reset()

        val result = tracker.update(1, rawDistance = 10f, timestampMs = 2000)
        assertEquals(0f, result.speedMps, 0.1f) // fresh start
    }

    @Test
    fun `filtered distance is smoother than raw input`() {
        var t = 1000L
        val rawDistances = mutableListOf<Float>()
        val filteredDistances = mutableListOf<Float>()
        val trueDistance = 25f

        val noise = floatArrayOf(3f, -2f, 4f, -3f, 2f, -4f, 3f, -1f, 2f, -3f)
        for (n in noise) {
            t += 100
            val raw = trueDistance + n
            rawDistances.add(raw)
            val result = tracker.update(1, raw, t)
            filteredDistances.add(result.filteredDistanceM)
        }

        // Variance of filtered should be less than variance of raw
        val rawVariance = variance(rawDistances)
        val filteredVariance = variance(filteredDistances.drop(3)) // skip warmup

        assertTrue(
            "Filtered variance ($filteredVariance) should be < raw ($rawVariance)",
            filteredVariance < rawVariance,
        )
    }

    private fun variance(values: List<Float>): Float {
        val mean = values.average().toFloat()
        return values.map { (it - mean) * (it - mean) }.average().toFloat()
    }
}
