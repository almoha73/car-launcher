package org.almoha.cockpit.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp

object CockpitColors {
    val Background = Color(0xFF05070A)
    val BackgroundTop = Color(0xFF0A1017)
    val Card = Color(0xFF0D131B)
    val CardHigh = Color(0xFF141C26)
    val Stroke = Color(0x14FFFFFF)
    val Text = Color(0xFFEAF0F6)
    val TextDim = Color(0xFF96A2B2)
    val TextMuted = Color(0xFF66717F)
    val Accent = Color(0xFF4DD0E1)
    val Nav = Color(0xFF4DD0E1)
    val Media = Color(0xFF1ED760)
    val Phone = Color(0xFF6FB6FF)
    val Assistant = Color(0xFFB99CFF)
    val Ev = Color(0xFFFFC46B)
    val Warning = Color(0xFFFF7A7A)
    val Charging = Color(0xFF7BE495)
}

private val CockpitTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        letterSpacing = (-1).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        letterSpacing = 0.4.sp,
    ),
)

@Composable
fun CockpitTheme(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val cappedDensity = Density(
        density = density.density,
        fontScale = density.fontScale.coerceAtMost(1.3f),
    )
    CompositionLocalProvider(LocalDensity provides cappedDensity) {
        MaterialTheme(
            colorScheme = darkColorScheme(
            primary = CockpitColors.Accent,
            onPrimary = Color.Black,
            secondary = CockpitColors.Media,
            background = CockpitColors.Background,
            surface = CockpitColors.Card,
            onSurface = CockpitColors.Text,
            onSurfaceVariant = CockpitColors.TextDim,
            surfaceVariant = CockpitColors.CardHigh,
            outline = CockpitColors.Stroke,
            error = CockpitColors.Warning,
        ),
            typography = CockpitTypography,
            content = content,
        )
    }
}
