package com.safegap.core.model

data class TrackedObject(
    val trackId: Int,
    val detection: RawDetection,
    val distanceMeters: Float? = null,
    val speedMps: Float? = null,
    val ttcSeconds: Float? = null,
)
