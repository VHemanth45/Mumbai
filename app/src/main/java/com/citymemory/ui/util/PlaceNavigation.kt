package com.citymemory.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.citymemory.domain.model.Place
import com.citymemory.ui.LocalNavigationLauncher
import com.citymemory.util.NavigationResult

/**
 * Wires the Navigate button to the external maps hand-off, and surfaces the one
 * failure worth reporting: a device with no maps app and no browser at all.
 */
@Composable
fun rememberPlaceNavigator(onUnavailable: (String) -> Unit): (Place) -> Unit {
    val context = LocalContext.current
    val launcher = LocalNavigationLauncher.current
    val currentOnUnavailable by rememberUpdatedState(onUnavailable)

    return remember(context, launcher) {
        { place ->
            val result = launcher.navigateTo(
                context = context,
                latitude = place.latitude,
                longitude = place.longitude,
                placeName = place.name,
            )
            if (result is NavigationResult.NoHandler) {
                currentOnUnavailable("No maps app available to open ${place.name}.")
            }
        }
    }
}
