package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val CuePanDarkColorScheme = darkColorScheme(
    primary = ClickCyan,
    onPrimary = StudioBg,
    primaryContainer = StripElevated,
    onPrimaryContainer = ClickCyan,
    secondary = CueAmber,
    onSecondary = StudioBg,
    secondaryContainer = StripElevated,
    onSecondaryContainer = CueAmber,
    tertiary = BackingIndigo,
    onTertiary = TextPrimary,
    background = StudioBg,
    onBackground = TextPrimary,
    surface = ConsoleSurface,
    onSurface = TextPrimary,
    surfaceVariant = StripSurface,
    onSurfaceVariant = TextSecondary,
    outline = ConsoleBorder,
    error = ClipRed,
    onError = TextPrimary
)

@Composable
fun CuePanTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CuePanDarkColorScheme,
        typography = CuePanTypography,
        content = content
    )
}
