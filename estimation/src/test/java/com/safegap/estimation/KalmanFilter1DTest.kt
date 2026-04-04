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
}
