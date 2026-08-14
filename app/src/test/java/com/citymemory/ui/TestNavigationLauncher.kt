package com.citymemory.ui

import android.content.Context
import com.citymemory.util.NavigationLauncher
import com.citymemory.util.NavigationResult

/** Records hand-offs instead of launching anything. */
class RecordingNavigationLauncher : NavigationLauncher {

    data class Call(val latitude: Double, val longitude: Double, val placeName: String)

    val calls = mutableListOf<Call>()

    override fun navigateTo(
        context: Context,
        latitude: Double,
        longitude: Double,
        placeName: String,
    ): NavigationResult {
        calls += Call(latitude, longitude, placeName)
        return NavigationResult.Launched(NavigationResult.Target.GEO_URI)
    }
}
