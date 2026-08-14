package com.citymemory.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The palette is built around one idea: an unlit city is cold and near-black,
 * and every place you explore adds warm light to it.
 *
 * - Warm amber  = explored / lit
 * - Cool cyan   = wishlisted / "not yet lit, but wanted"
 * - Dim slate   = undiscovered
 */

// Ground
val CityNight = Color(0xFF06070C)
val CitySurface = Color(0xFF0E1018)
val CitySurfaceElevated = Color(0xFF161A26)
val CityOutline = Color(0xFF232838)
val CityOutlineSoft = Color(0xFF1A1E2B)

// Explored — warm light
val GlowCore = Color(0xFFFFD9A3)
val GlowAmber = Color(0xFFFFB765)
val GlowEmber = Color(0xFFFF8A3D)

// Wishlisted — cool light
val WishCyan = Color(0xFF5FD0E8)
val WishCyanSoft = Color(0xFFA8E9F7)

// Undiscovered
val DimSlate = Color(0xFF39415A)
val DimSlateDeep = Color(0xFF232A3C)

// Type
val TextPrimary = Color(0xFFF0F2F8)
val TextSecondary = Color(0xFF9AA3BC)
val TextTertiary = Color(0xFF636C86)

// Feedback
val ErrorRed = Color(0xFFFF6B6B)
