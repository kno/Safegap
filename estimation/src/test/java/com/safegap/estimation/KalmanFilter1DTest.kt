package com.safegap.estimation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KalmanFilter1DTest {

    @Test
    fun `constant distance converges to true value`() {
        val filter = KalmanFilter1D(initialDistance = 10f)
        var t = 0L

        repeat(20) {
            t += 66 // ~15fps
            filter.update(10f, t)
        }

        assertEquals(10f, filter.distance, 0.1f)
        assertEquals(0f, filter.velocity, 0.5f)
    }

    @Test
    fun `approaching object shows positive velocity`() {
        val filter = KalmanFilter1D(initialDistance = 50f)
        var t = 0L

        // Object closing at 10 m/s → distance decreases 0.66m per frame at 15fps
        for (i in 0 until 30) {
            t += 66
            val dist = 50f - (i * 0.66f)
            filter.update(dist, t)
        }

        assertTrue(
            "Velocity should be positive (approaching): ${filter.velocity}",
            filter.velocity > 0f,
        )
    }

    @Test
    fun `noisy measurements are smoothed`() {
        val trueDistance = 20f
        val filter = KalmanFilter1D(initialDistance = trueDistance)
        var t = 0L
        val noise = floatArrayOf(2f, -1.5f, 3f, -2f, 1f, -3f, 2.5f, -1f, 0.5f, -2.5f)

        for (n in noise) {
            t += 66
            filter.update(trueDistance + n, t)
        }

        // Filtered result should be closer to true distance than raw noise
        val error = kotlin.math.abs(filter.distance - trueDistance)
        assertTrue(
            "Filtered error ($error) should be < 2.0",
            error < 2.0f,
        )
    }

    @Test
    fun `receding object shows negative velocity`() {
        val filter = KalmanFilter1D(initialDistance = 10f)
        var t = 0L

        // Object moving away: distance increases
        for (i in 0 until 30) {
            t += 66
            val dist = 10f + (i * 0.5f)
            filter.update(dist, t)
        }

        assertTrue(
            "Velocity should be negative (receding): ${filter.velocity}",
            filter.velocity < 0f,
        )
    }

    @Test
    fun `large initial error corrects over time`() {
        val filter = KalmanFilter1D(initialDistance = 100f) // wrong initial
        var t = 0L

        repeat(30) {
            t += 66
            filter.update(20f, t) // true distance is 20
        }

        assertEquals(20f, filter.distance, 1.0f)
    }

    // --- Edge case: duplicate timestamp (dt <= 0) ---

    @Test
    fun `duplicate timestamp does not corrupt state`() {
        val filter = KalmanFilter1D(initialDistance = 10f)

        filter.update(10f, 100L)
        val distBefore = filter.distance
        val velBefore = filter.velocity

        // Same timestamp → dt = 0, should return current state unchanged
        val result = filter.update(10f, 100L)

        assertEquals(distBefore, result, 0.001f)
        assertEquals(distBefore, filter.distance, 0.001f)
        assertEquals(velBefore, filter.velocity, 0.001f)
    }

    @Test
    fun `earlier timestamp does not corrupt state`() {
        val filter = KalmanFilter1D(initialDistance = 10f)

        filter.update(10f, 200L)
        val distBefore = filter.distance
        val velBefore = filter.velocity

        // Earlier timestamp → dt < 0, should return current state unchanged
        val result = filter.update(10f, 100L)

        assertEquals(distBefore, result, 0.001f)
        assertEquals(velBefore, filter.velocity, 0.001f)
    }

    // --- Edge case: negative distance ---

    @Test
    fun `negative distance measurement is handled gracefully`() {
        val filter = KalmanFilter1D(initialDistance = 5f)

        filter.update(5f, 100L)
        filter.update(-2f, 200L) // physically impossible, but should not crash

        // Filter should still have a finite, non-NaN distance
        assertTrue(
            "Distance should be finite: ${filter.distance}",
            filter.distance.isFinite(),
        )
    }

    // --- Edge case: cold-start with large dt gap ---

    @Test
    fun `cold-start with 500ms gap does not produce wild velocity`() {
        val filter = KalmanFilter1D(initialDistance = 20f)

        // First update establishes baseline
        filter.update(20f, 0L)

        // Second update with 500ms gap (large for a tracker at 15fps)
        filter.update(19f, 500L)

        // Velocity should be reasonable — object moved 1m in 0.5s = ~2 m/s
        // Should not produce a wildly large velocity
        assertTrue(
            "Velocity should be reasonable after cold-start gap: ${filter.velocity}",
            kotlin.math.abs(filter.velocity) < 10f,
        )
    }

    @Test
    fun `filter remains stable through large dt then normal dt`() {
        val filter = KalmanFilter1D(initialDistance = 30f)

        filter.update(30f, 0L)
        filter.update(29f, 500L) // 500ms gap

        // Now resume normal 66ms intervals
        var t = 500L
        repeat(10) {
            t += 66
            filter.update(28f, t)
        }

        // Should converge toward 28
        assertEquals(28f, filter.distance, 1.5f)
        assertTrue(
            "Velocity should be finite: ${filter.velocity}",
            filter.velocity.isFinite(),
        )
    }
}
