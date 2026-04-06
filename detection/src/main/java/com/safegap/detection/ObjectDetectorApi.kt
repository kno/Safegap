package com.safegap.detection

import android.graphics.Bitmap
import com.safegap.core.model.RawDetection

/**
 * Abstraction over object detection for testability.
 * Production implementation: [ObjectDetector] (TFLite).
 */
interface ObjectDetectorApi {
    /** Block until the detector is ready. */
    suspend fun awaitReady()

    /** Run inference on a single frame. */
    fun detect(bitmap: Bitmap, timestampMs: Long): List<RawDetection>
}
