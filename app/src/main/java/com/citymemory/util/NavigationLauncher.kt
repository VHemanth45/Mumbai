package com.citymemory.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import java.util.Locale

/**
 * City Memory does not do navigation. It hands off to whatever the device
 * already has.
 *
 * Kept behind an interface so the hand-off can be retargeted (a different maps
 * provider, an in-app preview, a "copy coordinates" affordance) without
 * touching any screen, and so it can be faked in tests.
 */
interface NavigationLauncher {

    /**
     * Opens an external maps app at the given coordinates.
     *
     * @param placeName shown as the pin label where the receiving app supports it.
     */
    fun navigateTo(
        context: Context,
        latitude: Double,
        longitude: Double,
        placeName: String,
    ): NavigationResult
}

sealed interface NavigationResult {
    /** Something handled the intent. [target] records which rung of the chain. */
    data class Launched(val target: Target) : NavigationResult

    /** Nothing on the device could handle a map, not even a browser. */
    data object NoHandler : NavigationResult

    enum class Target { GOOGLE_MAPS_TURN_BY_TURN, GEO_URI, WEB_FALLBACK }
}

/**
 * Tries three increasingly generic hand-offs and takes the first that resolves:
 *
 *  1. `google.navigation:` — turn-by-turn directions in Google Maps.
 *  2. `geo:` — any installed maps app, including offline ones like OsmAnd.
 *  3. `https://google.com/maps/dir/` — a browser, or Maps via app links.
 *
 * Availability is discovered by catching [ActivityNotFoundException] rather than
 * by querying the package manager: `startActivity` is not subject to package
 * visibility filtering, so this works without declaring who we might talk to.
 */
class AndroidNavigationLauncher : NavigationLauncher {

    override fun navigateTo(
        context: Context,
        latitude: Double,
        longitude: Double,
        placeName: String,
    ): NavigationResult {
        val coordinates = String.format(Locale.US, "%f,%f", latitude, longitude)
        val label = Uri.encode(placeName)

        val attempts = listOf(
            NavigationResult.Target.GOOGLE_MAPS_TURN_BY_TURN to
                Intent(Intent.ACTION_VIEW, "google.navigation:q=$coordinates".toUri())
                    .setPackage(GOOGLE_MAPS_PACKAGE),

            NavigationResult.Target.GEO_URI to
                Intent(Intent.ACTION_VIEW, "geo:$coordinates?q=$coordinates($label)".toUri()),

            NavigationResult.Target.WEB_FALLBACK to
                Intent(
                    Intent.ACTION_VIEW,
                    "https://www.google.com/maps/dir/?api=1&destination=$coordinates".toUri(),
                ),
        )

        for ((target, intent) in attempts) {
            // Callers may pass an application context (e.g. from a preview or a
            // service); without NEW_TASK that would throw at startActivity.
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return NavigationResult.Launched(target)
            } catch (_: ActivityNotFoundException) {
                // Try the next, more generic, hand-off.
            }
        }
        return NavigationResult.NoHandler
    }

    private companion object {
        const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"
    }
}
