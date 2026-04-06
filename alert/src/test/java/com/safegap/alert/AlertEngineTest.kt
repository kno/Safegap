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

    // --- Threshold tests (sustained 500ms+ for debounce) ---

    /** Helper: evaluate the same objects twice, 600ms apart, to pass debounce. */
    private fun evaluateSustained(objects: List<TrackedObject>): AlertResult {
        engine.evaluate(objects, nowMs = 1000L)
        return engine.evaluate(objects, nowMs = 1600L)
    }

    @Test
    fun `no objects returns SAFE`() {
        val result = engine.evaluate(emptyList(), nowMs = 1000L)
        assertEquals(AlertLevel.SAFE, result.level)
        assertNull(result.closestThreat)
    }

    @Test
    fun `far object returns SAFE`() {
        val result = evaluateSustained(listOf(obj(distance = 50f)))
        assertEquals(AlertLevel.SAFE, result.level)
    }

    @Test
    fun `object at 10m returns WARNING`() {
        val result = evaluateSustained(listOf(obj(distance = 10f)))
        assertEquals(AlertLevel.WARNING, result.level)
    }

    @Test
    fun `object at 3m returns CRITICAL`() {
        val result = evaluateSustained(listOf(obj(distance = 3f)))
        assertEquals(AlertLevel.CRITICAL, result.level)
    }

    @Test
    fun `car with TTC 1_5s returns CRITICAL`() {
        val result = evaluateSustained(listOf(obj(distance = 30f, ttc = 1.5f)))
        assertEquals(AlertLevel.CRITICAL, result.level)
    }

    @Test
    fun `car with TTC 3s returns WARNING`() {
        val result = evaluateSustained(listOf(obj(distance = 30f, ttc = 3.0f)))
        assertEquals(AlertLevel.WARNING, result.level)
    }

    @Test
    fun `person with TTC 3s returns CRITICAL`() {
        // Person has elevated TTC threshold (4.0s instead of 2.0s)
        val result = evaluateSustained(listOf(obj(distance = 30f, ttc = 3.0f, className = "person")))
        assertEquals(AlertLevel.CRITICAL, result.level)
    }

    @Test
    fun `object with no distance returns SAFE`() {
        val result = evaluateSustained(listOf(obj(distance = null)))
        assertEquals(AlertLevel.SAFE, result.level)
    }

    // --- Time-based debounce tests (500ms sustain) ---

    @Test
    fun `level does not rise before 500ms sustained`() {
        val t0 = 1000L
        engine.evaluate(listOf(obj(distance = 50f)), nowMs = t0)       // SAFE
        val r = engine.evaluate(listOf(obj(distance = 3f)), nowMs = t0 + 200) // CRITICAL for 200ms
        assertEquals(AlertLevel.SAFE, r.level) // Not sustained long enough
    }

    @Test
    fun `level rises after 500ms sustained`() {
        val t0 = 1000L
        engine.evaluate(listOf(obj(distance = 50f)), nowMs = t0)
        engine.evaluate(listOf(obj(distance = 3f)), nowMs = t0 + 100)  // start pending
        engine.evaluate(listOf(obj(distance = 3f)), nowMs = t0 + 300)  // still pending
        val r = engine.evaluate(listOf(obj(distance = 3f)), nowMs = t0 + 600) // 500ms elapsed
        assertEquals(AlertLevel.CRITICAL, r.level)
    }

    @Test
    fun `level drops after 500ms sustained lower`() {
        val t0 = 1000L
        // Establish CRITICAL
        engine.evaluate(listOf(obj(distance = 3f)), nowMs = t0)
        engine.evaluate(listOf(obj(distance = 3f)), nowMs = t0 + 600) // sustained → CRITICAL

        // Start dropping
        engine.evaluate(listOf(obj(distance = 50f)), nowMs = t0 + 700)  // SAFE pending
        val r1 = engine.evaluate(listOf(obj(distance = 50f)), nowMs = t0 + 900) // 200ms
        assertEquals(AlertLevel.CRITICAL, r1.level) // Not yet

        val r2 = engine.evaluate(listOf(obj(distance = 50f)), nowMs = t0 + 1300) // 600ms
        assertEquals(AlertLevel.SAFE, r2.level) // Now drops
    }

    @Test
    fun `debounce resets if level changes during sustain`() {
        val t0 = 1000L
        engine.evaluate(listOf(obj(distance = 50f)), nowMs = t0) // SAFE

        // Start pending CRITICAL
        engine.evaluate(listOf(obj(distance = 3f)), nowMs = t0 + 100)
        engine.evaluate(listOf(obj(distance = 3f)), nowMs = t0 + 400) // 300ms into sustain

        // Spike back to SAFE — resets pending
        engine.evaluate(listOf(obj(distance = 50f)), nowMs = t0 + 450)

        // CRITICAL again — timer restarts
        engine.evaluate(listOf(obj(distance = 3f)), nowMs = t0 + 500)
        val r = engine.evaluate(listOf(obj(distance = 3f)), nowMs = t0 + 800) // only 300ms
        assertEquals(AlertLevel.SAFE, r.level) // Not sustained long enough from reset
    }

    // --- Closest threat selection ---

    @Test
    fun `closest threat is the most dangerous object`() {
        val objects = listOf(
            obj(distance = 50f, trackId = 1), // SAFE
            obj(distance = 3f, trackId = 2),  // CRITICAL
            obj(distance = 10f, trackId = 3), // WARNING
        )
        engine.evaluate(objects, nowMs = 1000L)
        val result = engine.evaluate(objects, nowMs = 1600L)

        assertEquals(AlertLevel.CRITICAL, result.level)
        assertNotNull(result.closestThreat)
        assertEquals(2, result.closestThreat!!.trackId)
    }

    @Test
    fun `debounce does not allow early escalation after bounce back`() {
        val t0 = 1000L

        // Start at SAFE
        engine.evaluate(listOf(obj(distance = 50f)), nowMs = t0)

        // Send WARNING for 400ms (under SUSTAIN_MS)
        engine.evaluate(listOf(obj(distance = 10f)), nowMs = t0 + 100)
        engine.evaluate(listOf(obj(distance = 10f)), nowMs = t0 + 300)
        val r1 = engine.evaluate(listOf(obj(distance = 10f)), nowMs = t0 + 400)
        assertEquals(AlertLevel.SAFE, r1.level) // Not sustained long enough

        // Bounce back to SAFE briefly
        engine.evaluate(listOf(obj(distance = 50f)), nowMs = t0 + 450)

        // Return to WARNING — timer must restart from scratch
        engine.evaluate(listOf(obj(distance = 10f)), nowMs = t0 + 500)
        val r2 = engine.evaluate(listOf(obj(distance = 10f)), nowMs = t0 + 800)
        assertEquals(AlertLevel.SAFE, r2.level) // Only 300ms since restart, not enough

        // After full 500ms from the restart, it should escalate
        val r3 = engine.evaluate(listOf(obj(distance = 10f)), nowMs = t0 + 1100)
        assertEquals(AlertLevel.WARNING, r3.level)
    }

    @Test
    fun `among same-level threats picks the closer one`() {
        val objects = listOf(
            obj(distance = 12f, trackId = 1), // WARNING
            obj(distance = 8f, trackId = 2),  // WARNING
        )
        engine.evaluate(objects, nowMs = 1000L)
        val result = engine.evaluate(objects, nowMs = 1600L)

        assertEquals(AlertLevel.WARNING, result.level)
        assertEquals(2, result.closestThreat!!.trackId)
    }
}
