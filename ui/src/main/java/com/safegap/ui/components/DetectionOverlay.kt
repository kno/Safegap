package com.safegap.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import com.safegap.core.model.AlertLevel
import com.safegap.core.model.TrackedObject
import com.safegap.ui.theme.CriticalRed
import com.safegap.ui.theme.SafeGreen
import com.safegap.ui.theme.WarningAmber

/**
 * Canvas overlay drawing bounding boxes with distance labels
 * for each tracked object. Uses native Android Canvas for
 * text rendering performance.
 */
@Composable
fun DetectionOverlay(
    trackedObjects: List<TrackedObject>,
    alertLevel: AlertLevel,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        val boxPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }

        val textPaint = Paint().apply {
            textSize = 36f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }

        val bgPaint = Paint().apply {
            style = Paint.Style.FILL
        }

        for (obj in trackedObjects) {
            val bbox = obj.detection.boundingBox
            val left = bbox.left * canvasWidth
            val top = bbox.top * canvasHeight
            val right = bbox.right * canvasWidth
            val bottom = bbox.bottom * canvasHeight

            // Color based on object-level distance/TTC
            val color = when {
                obj.distanceMeters != null && obj.distanceMeters!! < 5f -> CriticalRed
                obj.distanceMeters != null && obj.distanceMeters!! < 15f -> WarningAmber
                else -> SafeGreen
            }
            val androidColor = color.hashCode()

            boxPaint.color = androidColor
            bgPaint.color = androidColor

            drawContext.canvas.nativeCanvas.apply {
                // Bounding box
                drawRect(left, top, right, bottom, boxPaint)

                // Label: "car 12.3m"
                val distance = obj.distanceMeters
                val label = if (distance != null) {
                    "${obj.detection.className} ${"%.1f".format(distance)}m"
                } else {
                    obj.detection.className
                }

                textPaint.color = androidColor
                val textWidth = textPaint.measureText(label)
                val textHeight = textPaint.textSize

                // Background rect for text readability
                bgPaint.alpha = 160
                drawRect(
                    left,
                    top - textHeight - 4f,
                    left + textWidth + 8f,
                    top,
                    bgPaint,
                )

                // Text
                textPaint.color = android.graphics.Color.WHITE
                drawText(label, left + 4f, top - 6f, textPaint)
            }
        }
    }
}
