package com.safegap.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safegap.core.model.TrackedObject
import kotlin.math.abs

/**
 * Badge showing relative speed of the closest threatening object.
 * Only visible when there's an object with known speed.
 */
@Composable
fun SpeedBadge(
    closestThreat: TrackedObject?,
    modifier: Modifier = Modifier,
) {
    val speed = closestThreat?.speedMps ?: return
    if (abs(speed) < 0.1f) return

    val direction = if (speed > 0) "acercandose" else "alejandose"
    val text = "${"%.0f".format(abs(speed * 3.6f))} km/h $direction"

    Text(
        text = text,
        color = Color.White,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier
            .background(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
