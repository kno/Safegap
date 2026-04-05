package com.safegap.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Debug overlay showing FPS and thermal status.
 * Only shown in debug builds.
 */
@Composable
fun DebugOverlay(
    fps: Float,
    thermalThrottled: Boolean,
    objectCount: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(
                color = Color.Black.copy(alpha = 0.5f),
                shape = RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = "${"%.1f".format(fps)} FPS",
            color = if (fps < 10f) Color.Red else Color.Green,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = "$objectCount obj",
            color = Color.White,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )
        if (thermalThrottled) {
            Text(
                text = "THERMAL",
                color = Color.Red,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}
