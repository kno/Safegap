package com.safegap.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.safegap.core.DisplayState
import com.safegap.core.model.AlertLevel
import com.safegap.ui.theme.CriticalRed
import com.safegap.ui.theme.SafeGreen
import com.safegap.ui.theme.WarningAmber
import kotlin.math.abs

/**
 * Canvas overlay drawing smoothed bounding boxes with distance labels
 * for each display state. Uses displayConfidence as alpha so tracks
 * fade out gracefully during the grace period.
 */
@Composable
fun DetectionOverlay(
    displayStates: List<DisplayState>,
    alertLevel: AlertLevel,
    threatTrackId: Int? = null,
    modifier: Modifier = Modifier,
) {
    val boxPaint = remember {
        Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }
    }

    val threatBoxPaint = remember {
        Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
            isAntiAlias = true
        }
    }

    val textPaint = remember {
        Paint().apply {
            textSize = 36f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
    }

    val bgPaint = remember {
        Paint().apply {
            style = Paint.Style.FILL
        }
    }

    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        for (ds in displayStates) {
            val left = ds.smoothedBox.left * canvasWidth
            val top = ds.smoothedBox.top * canvasHeight
            val right = ds.smoothedBox.right * canvasWidth
            val bottom = ds.smoothedBox.bottom * canvasHeight

            val isThreat = threatTrackId != null && ds.trackId == threatTrackId

            // Alpha from displayConfidence (fades during grace period)
            val alpha = (ds.displayConfidence * 255).toInt().coerceIn(0, 255)

            // Color based on object-level distance
            val dist = ds.displayDistanceM
            val color = when {
                dist != null && dist < 5f -> CriticalRed
                dist != null && dist < 15f -> WarningAmber
                else -> SafeGreen
            }
            val androidColor = color.toArgb()

            val currentBoxPaint = if (isThreat) threatBoxPaint else boxPaint
            currentBoxPaint.color = androidColor
            currentBoxPaint.alpha = alpha
            bgPaint.color = androidColor

            drawContext.canvas.nativeCanvas.apply {
                // Bounding box (thicker for the threat object)
                drawRect(left, top, right, bottom, currentBoxPaint)

                // Label: "car 12.3m -25km/h"
                // Speed sign: negative = approaching, positive = moving away
                val label = buildString {
                    append(ds.className)
                    ds.displayDistanceM?.let { append(" ${"%.1f".format(it)}m") }
                    ds.displaySpeedMps?.let { speed ->
                        if (abs(speed) >= 0.5f) {
                            // speedMps positive = approaching → display as negative
                            val displayKmh = speed * 3.6f
                            val sign = if (displayKmh > 0f) "-" else "+"
                            append(" $sign${"%.0f".format(abs(displayKmh))}km/h")
                        }
                    }
                }

                val textWidth = textPaint.measureText(label)
                val textHeight = textPaint.textSize

                // Background rect for text readability
                bgPaint.alpha = (160 * ds.displayConfidence).toInt().coerceIn(0, 255)
                drawRect(
                    left,
                    top - textHeight - 4f,
                    left + textWidth + 8f,
                    top,
                    bgPaint,
                )

                // Text
                textPaint.color = android.graphics.Color.WHITE
                textPaint.alpha = alpha
                drawText(label, left + 4f, top - 6f, textPaint)
            }
        }
    }
}
