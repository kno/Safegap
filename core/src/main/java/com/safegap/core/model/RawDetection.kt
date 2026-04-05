package com.safegap.core.model

import android.graphics.RectF

data class RawDetection(
    val boundingBox: RectF,
    val classId: Int,
    val className: String,
    val confidence: Float,
    val timestampMs: Long,
)
