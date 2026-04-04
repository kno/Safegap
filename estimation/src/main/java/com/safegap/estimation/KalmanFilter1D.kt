package com.safegap.estimation

/**
 * 1D Kalman filter for smoothing noisy distance estimates per tracked object.
 *
 * State vector: `x = [distance, velocity]^T`
 * Transition:   `F = [[1, -dt], [0, 1]]`  (velocity is closing speed)
 * Observation:  `H = [1, 0]`              (we only observe distance)
 *
 * Measurement noise R scales with distance (farther = noisier).
 */
class KalmanFilter1D(
    initialDistance: Float,
    private val processNoise: Float = 0.5f,
    private val baseR: Float = 1.0f,
) {
    // State: [distance, velocity]
    private var x0 = initialDistance  // distance
    private var x1 = 0f              // velocity (m/s, positive = closing)

    // Covariance matrix (2x2, stored as 4 values)
    private var p00 = 10f
    private var p01 = 0f
    private var p10 = 0f
    private var p11 = 10f

    private var lastTimestampMs: Long = 0L

    /** Filtered distance in meters. */
    val distance: Float get() = x0

    /** Filtered closing velocity in m/s (positive = approaching). */
    val velocity: Float get() = x1

    /**
     * Update the filter with a new distance measurement.
     * @return Filtered distance.
     */
    fun update(measuredDistance: Float, timestampMs: Long): Float {
        val dt = if (lastTimestampMs == 0L) {
            0.066f // ~15fps default
        } else {
            (timestampMs - lastTimestampMs) / 1000f
        }
        lastTimestampMs = timestampMs

        if (dt <= 0f) return x0

        // --- Predict ---
        // x_pred = F * x
        val predX0 = x0 - x1 * dt  // distance decreases when object approaches
        val predX1 = x1

        // P_pred = F * P * F^T + Q
        val pp00 = p00 - dt * p10 - dt * (p01 - dt * p11) + processNoise
        val pp01 = p01 - dt * p11
        val pp10 = p10 - dt * p11
        val pp11 = p11 + processNoise

        // --- Update ---
        // Measurement noise scales with distance
        val r = baseR * (1f + predX0 / 20f).coerceAtLeast(1f)

        // Innovation: y = z - H * x_pred
        val y = measuredDistance - predX0

        // Innovation covariance: S = H * P_pred * H^T + R = pp00 + R
        val s = pp00 + r

        // Kalman gain: K = P_pred * H^T / S
        val k0 = pp00 / s
        val k1 = pp10 / s

        // Update state
        x0 = predX0 + k0 * y
        x1 = predX1 + k1 * y

        // Update covariance: P = (I - K*H) * P_pred
        p00 = (1f - k0) * pp00
        p01 = (1f - k0) * pp01
        p10 = pp10 - k1 * pp00
        p11 = pp11 - k1 * pp01

        return x0
    }
}
