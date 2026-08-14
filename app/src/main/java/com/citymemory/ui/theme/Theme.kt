package com.citymemory.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * City Memory is deliberately dark-only. The whole product metaphor is
 * "dark city that lights up as you explore it" — a light theme would erase it.
 * So there is no light color scheme and no dynamic color.
 */
private val CityDarkColorScheme = darkColorScheme(
    primary = GlowAmber,
    onPrimary = CityNight,
    primaryContainer = GlowEmber,
    onPrimaryContainer = CityNight,

    secondary = WishCyan,
    onSecondary = CityNight,
    secondaryContainer = CitySurfaceElevated,
    onSecondaryContainer = WishCyanSoft,

    tertiary = GlowCore,
    onTertiary = CityNight,

    background = CityNight,
    onBackground = TextPrimary,

    surface = CitySurface,
    onSurface = TextPrimary,
    surfaceVariant = CitySurfaceElevated,
    onSurfaceVariant = TextSecondary,

    surfaceContainer = CitySurfaceElevated,
    surfaceContainerHigh = CityOutline,
    surfaceContainerLow = CitySurface,

    outline = CityOutline,
    outlineVariant = CityOutlineSoft,

    // Snackbars are drawn from the *inverse* roles. Leaving these at their
    // defaults renders a white sheet with purple actions over the dark city.
    inverseSurface = CitySurfaceElevated,
    inverseOnSurface = TextPrimary,
    inversePrimary = GlowAmber,

    scrim = CityNight,

    error = ErrorRed,
    onError = CityNight,
)

@Composable
fun CityMemoryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CityDarkColorScheme,
        typography = CityTypography,
        content = content,
    )
}
