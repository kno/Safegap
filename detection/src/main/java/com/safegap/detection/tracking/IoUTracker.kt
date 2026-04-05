package com.safegap.detection.tracking

import android.graphics.RectF
import com.safegap.core.model.RawDetection
import com.safegap.core.model.TrackedObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IoUTracker @Inject constructor() {

    companion object {
        private const val IOU_THRESHOLD = 0.3f
        private const val GRACE_PERIOD_FRAMES = 5
    }

    private var nextTrackId = 1
    private var activeTracks = mutableListOf<Track>()

    /**
     * Match new detections against existing tracks using greedy IoU.
     * Returns a list of [TrackedObject] with persistent track IDs.
     */
    fun update(detections: List<RawDetection>): List<TrackedObject> {
        // Group detections by class for class-restricted matching
        val detectionsByClass = detections.groupBy { it.className }
        val matched = mutableSetOf<Int>() // indices into detections
        val matchedTrackIds = mutableSetOf<Int>()

        val flatDetections = detections.toList()

        // For each class, compute IoU between existing tracks and new detections
        for ((className, classDetections) in detectionsByClass) {
            val classTracks = activeTracks.filter {
                it.detection.className == className && it.trackId !in matchedTrackIds
            }

            // Build IoU matrix: pairs of (track, detection, iou)
            val pairs = mutableListOf<Triple<Track, RawDetection, Float>>()
            for (track in classTracks) {
                for (det in classDetections) {
                    val iou = computeIoU(track.detection.boundingBox, det.boundingBox)
                    if (iou >= IOU_THRESHOLD) {
                        pairs.add(Triple(track, det, iou))
                    }
                }
            }

            // Greedy assignment: highest IoU first
            pairs.sortByDescending { it.third }
            val usedDetections = mutableSetOf<RawDetection>()
            val usedTracks = mutableSetOf<Int>()

            for ((track, det, _) in pairs) {
                if (track.trackId in usedTracks || det in usedDetections) continue
                // Update existing track
                track.detection = det
                track.missedFrames = 0
                usedDetections.add(det)
                usedTracks.add(track.trackId)
                matchedTrackIds.add(track.trackId)
                matched.add(flatDetections.indexOf(det))
            }
        }

        // Increment missed frames for unmatched tracks
        for (track in activeTracks) {
            if (track.trackId !in matchedTrackIds) {
                track.missedFrames++
            }
        }

        // Remove tracks that exceeded grace period
        activeTracks.removeAll { it.missedFrames > GRACE_PERIOD_FRAMES }

        // Create new tracks for unmatched detections
        for ((i, det) in flatDetections.withIndex()) {
            if (i !in matched) {
                activeTracks.add(Track(
                    trackId = nextTrackId++,
                    detection = det,
                    missedFrames = 0,
                ))
            }
        }

        // Return all active tracks (including those in grace period)
        return activeTracks.map { track ->
            TrackedObject(
                trackId = track.trackId,
                detection = track.detection,
            )
        }
    }

    fun reset() {
        activeTracks.clear()
        nextTrackId = 1
    }

    private data class Track(
        val trackId: Int,
        var detection: RawDetection,
        var missedFrames: Int,
    )
}

/** Compute Intersection-over-Union between two RectF in [0,1] normalized coords. */
internal fun computeIoU(a: RectF, b: RectF): Float {
    val interLeft = maxOf(a.left, b.left)
    val interTop = maxOf(a.top, b.top)
    val interRight = minOf(a.right, b.right)
    val interBottom = minOf(a.bottom, b.bottom)

    val interWidth = maxOf(0f, interRight - interLeft)
    val interHeight = maxOf(0f, interBottom - interTop)
    val interArea = interWidth * interHeight

    val areaA = (a.right - a.left) * (a.bottom - a.top)
    val areaB = (b.right - b.left) * (b.bottom - b.top)
    val unionArea = areaA + areaB - interArea

    return if (unionArea <= 0f) 0f else interArea / unionArea
}
