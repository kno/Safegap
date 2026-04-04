package com.safegap.estimation

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks per-object speed and TTC using a sliding window of distance samples
 * filtered through a Kalman filter.
 *
 * Positive speed = object approaching (closing).
 */
@Singleton
class SpeedTracker @Inject constructor() {

    companion object {
        private const val WINDOW_SIZE = 5
    }

    private val tracks = mutableMapOf<Int, TrackState>()

    /**
     * Update with a new raw distance measurement for a tracked object.
     * @return [SpeedEstimate] with filtered distance, speed, and TTC.
     */
    fun update(trackId: Int, rawDistance: Float, timestampMs: Long): SpeedEstimate {
        val state = tracks.getOrPut(trackId) {
            TrackState(
                kalman = KalmanFilter1D(rawDistance),
                samples = ArrayDeque(WINDOW_SIZE),
            )
        }

        val filteredDistance = state.kalman.update(rawDistance, timestampMs)
        state.samples.addLast(DistanceSample(filteredDistance, timestampMs))
        if (state.samples.size > WINDOW_SIZE) {
            state.samples.removeFirst()
        }

        val speed = computeSpeed(state.samples)
        val ttc = computeTtc(filteredDistance, speed)

        return SpeedEstimate(
            filteredDistanceM = filteredDistance,
            speedMps = speed,
            ttcSeconds = ttc,
        )
    }

    /** Remove tracks that no longer exist. */
    fun pruneExcept(activeTrackIds: Set<Int>) {
        tracks.keys.retainAll(activeTrackIds)
    }

    fun reset() {
        tracks.clear()
    }

    /**
     * Speed from sliding window: `(oldest_distance - newest_distance) / dt`.
     * Positive = approaching.
     */
    private fun computeSpeed(samples: ArrayDeque<DistanceSample>): Float {
        if (samples.size < 2) return 0f

        val oldest = samples.first()
        val newest = samples.last()
        val dtSeconds = (newest.timestampMs - oldest.timestampMs) / 1000f

        if (dtSeconds <= 0f) return 0f

        return (oldest.distanceM - newest.distanceM) / dtSeconds
    }

    /**
     * Time-to-collision: `distance / closing_speed`.
     * Only meaningful when the object is approaching (speed > 0).
     */
    private fun computeTtc(distance: Float, speed: Float): Float? {
        if (speed <= 0.5f) return null  // not approaching or too slow to matter
        return distance / speed
    }

    private data class TrackState(
        val kalman: KalmanFilter1D,
        val samples: ArrayDeque<DistanceSample>,
    )
}

data class DistanceSample(
    val distanceM: Float,
    val timestampMs: Long,
)

data class SpeedEstimate(
    val filteredDistanceM: Float,
    val speedMps: Float,
    val ttcSeconds: Float?,
)
