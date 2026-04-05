package com.safegap.alert

import android.graphics.RectF
import com.safegap.core.model.AlertLevel
import com.safegap.core.model.RawDetection
import com.safegap.core.model.TrackedObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AlertEngineTest {

    private lateinit var engine: AlertEngine

    @Before
    fun setUp() {
        engine = AlertEngine()
    }

    private fun obj(
        distance: Float? = null,
        speed: Float? = null,
        ttc: Float? = null,
        className: String = "car",
        trackId: Int = 1,
    ) = TrackedObject(
        trackId = trackId,
        detection = RawDetection(
            boundingBox = RectF(0.1f, 0.1f, 0.3f, 0.3f),
            classId = 0,
            className = className,
            confidence = 0.9f,
            timestampMs = 0L,
        ),
        distanceMeters = distance,
        speedMps = speed,
        ttcSeconds = ttc,
    )

    // --- Threshold tests ---

    @Test
    fun `no objects returns SAFE`() {
        val result = engine.evaluate(emptyList())
        assertEquals(AlertLevel.SAFE, result.level)
        assertNull(result.closestThreat)
    }

    @Test
    fun `far object returns SAFE`() {
        val result = engine.evaluate(listOf(obj(distance = 50f)))
        assertEquals(AlertLevel.SAFE, result.level)
    }

    @Test
    fun `object at 10m returns WARNING`() {
        val result = engine.evaluate(listOf(obj(distance = 10f)))
        assertEquals(AlertLevel.WARNING, result.level)
    }

    @Test
    fun `object at 3m returns CRITICAL`() {
        val result = engine.evaluate(listOf(obj(distance = 3f)))
        assertEquals(AlertLevel.CRITICAL, result.level)
    }

    @Test
    fun `car with TTC 1_5s returns CRITICAL`() {
        val result = engine.evaluate(listOf(obj(distance = 30f, ttc = 1.5f)))
        assertEquals(AlertLevel.CRITICAL, result.level)
    }

    @Test
    fun `car with TTC 3s returns WARNING`() {
        val result = engine.evaluate(listOf(obj(distance = 30f, ttc = 3.0f)))
        assertEquals(AlertLevel.WARNING, result.level)
    }

    @Test
    fun `person with TTC 3s returns CRITICAL`() {
        // Person has elevated TTC threshold (4.0s instead of 2.0s)
        val result = engine.evaluate(listOf(obj(distance = 30f, ttc = 3.0f, className = "person")))
        assertEquals(AlertLevel.CRITICAL, result.level)
    }

    @Test
    fun `object with no distance returns SAFE`() {
        val result = engine.evaluate(listOf(obj(distance = null)))
        assertEquals(AlertLevel.SAFE, result.level)
    }

    // --- Debounce tests ---

    @Test
    fun `level rises immediately`() {
        engine.evaluate(listOf(obj(distance = 50f))) // SAFE
        val result = engine.evaluate(listOf(obj(distance = 3f))) // CRITICAL
        assertEquals(AlertLevel.CRITICAL, result.level)
    }

    @Test
    fun `level drops only after 3 consecutive lower frames`() {
        // Rise to CRITICAL
        engine.evaluate(listOf(obj(distance = 3f)))

        // 1st SAFE frame — still CRITICAL (debounce)
        val r1 = engine.evaluate(listOf(obj(distance = 50f)))
        assertEquals(AlertLevel.CRITICAL, r1.level)

        // 2nd SAFE frame — still CRITICAL
        val r2 = engine.evaluate(listOf(obj(distance = 50f)))
        assertEquals(AlertLevel.CRITICAL, r2.level)

        // 3rd SAFE frame — drops to SAFE
        val r3 = engine.evaluate(listOf(obj(distance = 50f)))
        assertEquals(AlertLevel.SAFE, r3.level)
    }

    @Test
    fun `debounce resets if level rises again`() {
        engine.evaluate(listOf(obj(distance = 3f))) // CRITICAL

        engine.evaluate(listOf(obj(distance = 50f))) // 1st SAFE
        engine.evaluate(listOf(obj(distance = 50f))) // 2nd SAFE

        // Spike back to CRITICAL before debounce completes
        engine.evaluate(listOf(obj(distance = 3f)))

        // Should be CRITICAL again, debounce counter reset
        val r = engine.evaluate(listOf(obj(distance = 50f))) // 1st SAFE again
        assertEquals(AlertLevel.CRITICAL, r.level)
    }

    // --- Closest threat selection ---

    @Test
    fun `closest threat is the most dangerous object`() {
        val result = engine.evaluate(listOf(
            obj(distance = 50f, trackId = 1), // SAFE
            obj(distance = 3f, trackId = 2),  // CRITICAL
            obj(distance = 10f, trackId = 3), // WARNING
        ))

        assertEquals(AlertLevel.CRITICAL, result.level)
        assertNotNull(result.closestThreat)
        assertEquals(2, result.closestThreat!!.trackId)
    }

    @Test
    fun `among same-level threats picks the closer one`() {
        val result = engine.evaluate(listOf(
            obj(distance = 12f, trackId = 1), // WARNING
            obj(distance = 8f, trackId = 2),  // WARNING
        ))

        assertEquals(AlertLevel.WARNING, result.level)
        assertEquals(2, result.closestThreat!!.trackId)
    }
}
