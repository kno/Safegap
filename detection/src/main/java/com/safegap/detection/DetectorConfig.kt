package com.safegap.detection

object DetectorConfig {
    const val MODEL_FILE = "efficientdet_lite0.tflite"
    const val MAX_RESULTS = 10
    const val CONFIDENCE_THRESHOLD = 0.5f
    const val NUM_THREADS = 2

    /** COCO classes relevant for driving safety. */
    val RELEVANT_CLASSES = setOf(
        "car", "truck", "bus", "person", "bicycle", "motorcycle",
    )
}
