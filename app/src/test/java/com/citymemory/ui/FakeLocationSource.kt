package com.citymemory.ui

import android.content.Context
import com.citymemory.util.LocationFix
import com.citymemory.util.LocationSource

/**
 * A location that answers whatever the test needs it to.
 *
 * This is the payoff for [LocationSource] being an interface: the whole
 * "use my location" path — the permission gate, a fix outside Mumbai, a
 * timeout, and the camera being told where to go — is exercised on the JVM
 * without a device, a GPS receiver or a permission dialog.
 */
class FakeLocationSource(
    var fix: LocationFix = LocationFix.Unavailable,
    var granted: Boolean = true,
) : LocationSource {

    var calls: Int = 0
        private set

    override fun hasPermission(context: Context): Boolean = granted

    override suspend fun currentLocation(context: Context): LocationFix {
        calls++
        return if (granted) fix else LocationFix.PermissionDenied
    }

    /** Counted separately, so a test can tell the cheap path from the interactive one. */
    var samples: Int = 0
        private set

    override suspend fun recentLocation(context: Context, maxAgeMillis: Long): LocationFix {
        samples++
        return if (granted) fix else LocationFix.PermissionDenied
    }
}
