package com.safegap.estimation

/** Real-world heights (meters) for COCO classes relevant to driving. */
object KnownObjectHeights {

    private val heights = mapOf(
        "car" to 1.5f,
        "truck" to 3.5f,
        "bus" to 3.2f,
        "person" to 1.7f,
        "bicycle" to 1.1f,
        "motorcycle" to 1.2f,
    )

    /** Returns the known height in meters, or null for unsupported classes. */
    fun forClass(className: String): Float? = heights[className]
}
