package com.safegap.estimation

import android.graphics.RectF
import com.safegap.core.model.RawDetection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DistanceEstimatorTest {

    private lateinit var estimator: DistanceEstimator

    // Default intrinsics: focal 3.6mm, sensor height 4.55mm
    // For 480px image: focal_px = 480 * 3.6 / 4.55 ≈ 379.56
    private val imageHeight = 480

    @Before
    fun setUp() {
        estimator = DistanceEstimator()
    }

    private fun det(
        top: Float, bottom: Float,
        className: String = "car",
        confidence: Float = 0.9f,
    ) = RawDetection(
        boundingBox = RectF(0.2f, top, 0.8f, bottom),
        classId = 0,
        className = className,
        confidence = confidence,
        timestampMs = 0L,
    )

    @Test
    fun `car at known distance gives correct estimate`() {
        // Car (1.5m real height) with bbox covering 20% of frame height
        // bbox_height_px = 0.2 * 480 = 96px
        // distance = (1.5 * 379.56) / 96 ≈ 5.93m
        val detection = det(top = 0.4f, bottom = 0.6f)
        val distance = estimator.estimate(detection, imageHeight)

        assertNotNull(distance)
        assertEquals(5.93f, distance!!, 0.5f)
    }

    @Test
    fun `farther car has larger distance`() {
        // Smaller bbox = farther object
        val near = det(top = 0.3f, bottom = 0.7f)  // 40% of frame
        val far = det(top = 0.4f, bottom = 0.5f)    // 10% of frame

        val nearDist = estimator.estimate(near, imageHeight)!!
        val farDist = estimator.estimate(far, imageHeight)!!

        assert(farDist > nearDist) { "Far ($farDist) should be > near ($nearDist)" }
    }

    @Test
    fun `truck is estimated with truck height`() {
        // Truck (3.5m) vs car (1.5m) with same bbox
        val car = det(top = 0.3f, bottom = 0.5f, className = "car")
        val truck = det(top = 0.3f, bottom = 0.5f, className = "truck")

        val carDist = estimator.estimate(car, imageHeight)!!
        val truckDist = estimator.estimate(truck, imageHeight)!!

        // Same bbox but truck is taller → must be farther away
        assert(truckDist > carDist)
        assertEquals(truckDist / carDist, 3.5f / 1.5f, 0.01f)
    }

    @Test
    fun `person uses person height`() {
        val person = det(top = 0.3f, bottom = 0.5f, className = "person")
        val distance = estimator.estimate(person, imageHeight)

        assertNotNull(distance)
    }

    @Test
    fun `low confidence returns null`() {
        val detection = det(top = 0.4f, bottom = 0.6f, confidence = 0.4f)
        assertNull(estimator.estimate(detection, imageHeight))
    }

    @Test
    fun `clipped top bbox returns null`() {
        // Top edge within 5% of frame top → clipped
        val detection = det(top = 0.03f, bottom = 0.6f)
        assertNull(estimator.estimate(detection, imageHeight))
    }

    @Test
    fun `unknown class returns null`() {
        val detection = det(top = 0.3f, bottom = 0.5f, className = "cat")
        assertNull(estimator.estimate(detection, imageHeight))
    }

    @Test
    fun `zero height bbox returns null`() {
        val detection = det(top = 0.5f, bottom = 0.5f)
        assertNull(estimator.estimate(detection, imageHeight))
    }

    @Test
    fun `custom intrinsics change result`() {
        val detection = det(top = 0.4f, bottom = 0.6f)

        val defaultDist = estimator.estimate(detection, imageHeight)!!

        estimator.updateIntrinsics(CameraIntrinsics(
            focalLengthMm = 4.5f,
            sensorHeightMm = 5.0f,
        ))
        val customDist = estimator.estimate(detection, imageHeight)!!

        // Different intrinsics → different distance
        assert(defaultDist != customDist)
    }
}
