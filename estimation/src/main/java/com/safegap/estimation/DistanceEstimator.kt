package com.safegap.estimation

import com.safegap.core.model.RawDetection
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Estimates distance to a detected object using the apparent-size method:
 *
 * `distance_m = (real_height_m × focal_length_px) / bbox_height_px`
 *
 * Stateless — each call is independent.
 */
@Singleton
class DistanceEstimator @Inject constructor() {

    companion object {
        /** Discard if bbox top is within this fraction of the frame top (clipped object). */
        private const val TOP_CLIP_THRESHOLD = 0.05f

        /** Minimum confidence to trust the estimation. */
        private const val MIN_CONFIDENCE = 0.55f
    }

    private var intrinsics = CameraIntrinsics()

    fun updateIntrinsics(intrinsics: CameraIntrinsics) {
        this.intrinsics = intrinsics
    }

    /**
     * Estimate distance for a detection with a normalized bounding box ([0, 1]).
     *
     * @param detection Detection with bbox in normalized coordinates.
     * @param imageHeightPx Height of the source image in pixels.
     * @return Distance in meters, or null if the estimation is unreliable.
     */
    fun estimate(detection: RawDetection, imageHeightPx: Int): Float? {
        if (detection.confidence < MIN_CONFIDENCE) return null

        // Reject objects clipped by the top edge — bbox height is unreliable
        if (detection.boundingBox.top < TOP_CLIP_THRESHOLD) return null

        val realHeightM = KnownObjectHeights.forClass(detection.className) ?: return null

        val bboxHeightPx = (detection.boundingBox.bottom - detection.boundingBox.top) * imageHeightPx
        if (bboxHeightPx <= 0f) return null

        val focalPx = intrinsics.focalLengthPx(imageHeightPx)
        val distance = (realHeightM * focalPx) / bboxHeightPx

        // Sanity check: reject implausible distances
        if (distance < 0.5f || distance > 200f) return null

        return distance
    }
}
