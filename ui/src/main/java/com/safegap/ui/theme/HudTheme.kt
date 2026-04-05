package com.safegap.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val SafeGreen = Color(0xFF4CAF50)
val WarningAmber = Color(0xFFFFC107)
val CriticalRed = Color(0xFFF44336)

private val HudColorScheme = darkColorScheme(
    primary = SafeGreen,
    secondary = WarningAmber,
    error = CriticalRed,
    background = Color.Black,
    surface = Color(0xFF121212),
    onBackground = Color.White,
    onSurface = Color.White,
)

private val HudTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
    ),
)

@Composable
fun HudTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HudColorScheme,
        typography = HudTypography,
        content = content,
    )
}
