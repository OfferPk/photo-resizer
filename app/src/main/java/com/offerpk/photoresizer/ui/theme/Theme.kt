package com.offerpk.photoresizer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Brand = Color(0xFF0B5CAD)
val Accent = Color(0xFFFF7A00)

private val Light = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = Color(0xFF001B3F),
    secondary = Accent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDCC2),
    onSecondaryContainer = Color(0xFF2E1500),
    tertiary = Color(0xFF00897B),
    tertiaryContainer = Color(0xFFB2F1E8),
    onTertiaryContainer = Color(0xFF00201C),
    background = Color(0xFFF7F9FC),
    surface = Color(0xFFF7F9FC),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F4F9),
    surfaceContainer = Color(0xFFEBEFF5),
    surfaceContainerHigh = Color(0xFFE5E9F0),
    surfaceVariant = Color(0xFFE0E6EF),
    outlineVariant = Color(0xFFC5CCD6),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFA9C7FF),
    onPrimary = Color(0xFF003063),
    primaryContainer = Color(0xFF00468C),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFFFFB77C),
    onSecondary = Color(0xFF4C2700),
    secondaryContainer = Color(0xFF6C3A00),
    onSecondaryContainer = Color(0xFFFFDCC2),
    tertiary = Color(0xFF80D5CA),
    tertiaryContainer = Color(0xFF005048),
    onTertiaryContainer = Color(0xFFB2F1E8),
    background = Color(0xFF0F1418),
    surface = Color(0xFF0F1418),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 0.1.sp),
    )
}

@Composable
fun PhotoResizerTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, content = content)
}
