package com.safegap.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safegap.core.model.AlertLevel
import com.safegap.core.model.TrackedObject
import com.safegap.ui.theme.CriticalRed
import com.safegap.ui.theme.SafeGreen
import com.safegap.ui.theme.WarningAmber

/**
 * Color-coded banner at the top of the HUD.
 * Shows minimal text: alert status + closest object info.
 * Animates in/out when alert state changes.
 */
@Composable
fun AlertBanner(
    alertLevel: AlertLevel,
    closestThreat: TrackedObject?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = alertLevel != AlertLevel.SAFE,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier,
    ) {
        val backgroundColor by animateColorAsState(
            targetValue = when (alertLevel) {
                AlertLevel.WARNING -> WarningAmber
                AlertLevel.CRITICAL -> CriticalRed
                else -> SafeGreen
            },
            animationSpec = tween(200),
            label = "alertColor",
        )

        val text = buildAlertText(alertLevel, closestThreat)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Assertive }
                .background(backgroundColor.copy(alpha = 0.85f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

private fun buildAlertText(level: AlertLevel, threat: TrackedObject?): String {
    if (threat == null) return ""

    val className = threat.detection.className.uppercase()
    val distance = threat.distanceMeters?.let { "%.0f".format(it) + "m" } ?: ""

    return when (level) {
        AlertLevel.CRITICAL -> "$className CERCA — $distance"
        AlertLevel.WARNING -> "$className — $distance"
        else -> ""
    }
}
