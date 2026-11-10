package com.pik.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val PikDarkColorScheme = darkColorScheme(
    primary = PikColors.Lime,
    onPrimary = PikColors.OnLime,
    primaryContainer = PikColors.SurfaceRaised,
    onPrimaryContainer = PikColors.TextPrimary,
    secondary = PikColors.Blue,
    onSecondary = PikColors.OnLime,
    secondaryContainer = PikColors.SurfaceRaised,
    onSecondaryContainer = PikColors.TextPrimary,
    tertiary = PikColors.Amber,
    onTertiary = PikColors.OnLime,
    background = PikColors.Background,
    onBackground = PikColors.TextPrimary,
    surface = PikColors.Surface,
    onSurface = PikColors.TextPrimary,
    surfaceVariant = PikColors.SurfaceRaised,
    onSurfaceVariant = PikColors.TextSecondary,
    surfaceTint = PikColors.Lime,
    surfaceContainerLowest = PikColors.Background,
    surfaceContainerLow = PikColors.Surface,
    surfaceContainer = PikColors.Surface,
    surfaceContainerHigh = PikColors.SurfaceRaised,
    surfaceContainerHighest = PikColors.SurfaceRaised,
    outline = PikColors.TextSecondary,
    outlineVariant = PikColors.Outline,
    error = PikColors.Coral,
    onError = PikColors.OnLime,
)

/** Dark theme only for v1. */
@Composable
fun PikTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PikDarkColorScheme,
        typography = PikTypography,
    ) {
        Surface(
            color = PikColors.Background,
            contentColor = PikColors.TextPrimary,
            content = content,
        )
    }
}
