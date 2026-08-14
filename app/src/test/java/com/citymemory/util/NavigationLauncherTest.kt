package com.citymemory.util

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Verifies the hand-off chain, including the rungs that only matter on devices
 * the developer does not have: no Google Maps, and no maps app at all.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NavigationLauncherTest {

    private lateinit var application: Application
    private val launcher = AndroidNavigationLauncher()

    private val latitude = 18.9220
    private val longitude = 72.8347

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        // Without this, Robolectric happily "starts" any intent, and the
        // fallback chain would never be exercised.
        shadowOf(application).checkActivities(true)
    }

    private fun installHandler(packageName: String, scheme: String) {
        val component = ComponentName(packageName, "$packageName.MapsActivity")
        val packageManager = shadowOf(application.packageManager)
        packageManager.addActivityIfNotPresent(component)
        packageManager.addIntentFilterForActivity(
            component,
            IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addDataScheme(scheme)
            },
        )
    }

    @Test
    fun `prefers turn-by-turn directions when google maps is installed`() {
        installHandler("com.google.android.apps.maps", "google.navigation")

        val result = launcher.navigateTo(application, latitude, longitude, "Gateway of India")

        assertEquals(
            NavigationResult.Launched(NavigationResult.Target.GOOGLE_MAPS_TURN_BY_TURN),
            result,
        )
        val started = shadowOf(application).nextStartedActivity
        assertEquals("com.google.android.apps.maps", started.`package`)
        assertEquals("google.navigation:q=18.922000,72.834700", started.data.toString())
    }

    @Test
    fun `falls back to a generic geo uri when google maps is absent`() {
        installHandler("net.osmand", "geo")

        val result = launcher.navigateTo(application, latitude, longitude, "Marine Drive")

        assertEquals(NavigationResult.Launched(NavigationResult.Target.GEO_URI), result)
        val started = shadowOf(application).nextStartedActivity
        assertTrue(started.data.toString().startsWith("geo:18.922000,72.834700?q="))
        // The label is passed through so the receiving app can name the pin.
        assertTrue(started.data.toString().contains("Marine%20Drive"))
    }

    @Test
    fun `falls back to the web when no maps app is installed`() {
        installHandler("com.android.browser", "https")

        val result = launcher.navigateTo(application, latitude, longitude, "Juhu Beach")

        assertEquals(NavigationResult.Launched(NavigationResult.Target.WEB_FALLBACK), result)
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&destination=18.922000,72.834700",
            shadowOf(application).nextStartedActivity.data.toString(),
        )
    }

    @Test
    fun `reports no handler when the device can open nothing at all`() {
        val result = launcher.navigateTo(application, latitude, longitude, "Elephanta Caves")

        assertEquals(NavigationResult.NoHandler, result)
    }

    @Test
    fun `coordinates are formatted with a decimal point regardless of locale`() {
        val defaultLocale = java.util.Locale.getDefault()
        try {
            // A locale that formats decimals with a comma would otherwise emit
            // "18,922000" and produce a URI no maps app can parse.
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            installHandler("net.osmand", "geo")

            launcher.navigateTo(application, latitude, longitude, "Kala Ghoda")

            assertTrue(
                shadowOf(application).nextStartedActivity.data.toString()
                    .startsWith("geo:18.922000,72.834700"),
            )
        } finally {
            java.util.Locale.setDefault(defaultLocale)
        }
    }
}
