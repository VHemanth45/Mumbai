package com.citymemory.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.citymemory.util.NavigationLauncher

/**
 * Launching an external maps app is a UI-layer concern (it needs an Activity
 * context), so it is provided down the tree rather than injected into a
 * ViewModel. Also lets a preview or a UI test substitute a no-op launcher.
 */
val LocalNavigationLauncher = staticCompositionLocalOf<NavigationLauncher> {
    error("No NavigationLauncher provided. Wrap the tree in CityMemoryTheme from MainActivity.")
}
