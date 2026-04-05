package com.safegap.estimation

/**
 * Camera intrinsic parameters needed for distance estimation.
 *
 * Primary source: `CameraCharacteristics` via Camera2Interop.
 * Fallback: typical smartphone rear camera defaults.
 */
data class CameraIntrinsics(
    val focalLengthMm: Float = DEFAULT_FOCAL_LENGTH_MM,
    val sensorHeightMm: Float = DEFAULT_SENSOR_HEIGHT_MM,
) {
    companion object {
        const val DEFAULT_FOCAL_LENGTH_MM = 3.6f
        const val DEFAULT_SENSOR_HEIGHT_MM = 4.55f
    }

    /**
     * Convert focal length from mm to pixels for a given image height.
     *
     * `focal_px = (image_height_px × focal_mm) / sensor_height_mm`
     */
    fun focalLengthPx(imageHeightPx: Int): Float =
        (imageHeightPx * focalLengthMm) / sensorHeightMm
}
