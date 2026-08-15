package com.citymemory.ui.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * The four states a runtime permission can actually be in.
 *
 * Android's own API only tells you two of them — granted or not — and treating
 * "not granted" as "ask again" is the standard way to ship a dead button. From
 * API 30 the system auto-refuses a second request **without showing anything**,
 * so the result callback returns instantly and the button appears to do nothing
 * at all, forever, with no way for the user to discover why.
 */
enum class LocationPermissionState {
    /** Already granted. Go straight to the fix. */
    GRANTED,

    /** Never asked. Asking will show the system dialog. */
    ASKABLE,

    /** Refused once. Asking again will show the dialog, with an explanation owed. */
    NEEDS_RATIONALE,

    /**
     * Refused twice, or refused with "don't ask again". Asking does nothing
     * visible, so the only honest move is to stop offering and point at
     * Settings.
     */
    PERMANENTLY_DENIED,
}

val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

/**
 * Works out which of the four states applies.
 *
 * [asked] is whether this install has ever put the dialog up, and it has to be
 * remembered by the caller rather than here — the add-a-place form lives inside
 * an `AnimatedVisibility`, so closing it removes this from composition, and an
 * `asked` flag stored at this level would reset to false and quietly re-arm the
 * dead button on the next add.
 */
fun locationPermissionState(context: Context, asked: Boolean): LocationPermissionState {
    val granted = LOCATION_PERMISSIONS.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    if (granted) return LocationPermissionState.GRANTED

    val activity = context.findActivity() ?: return LocationPermissionState.ASKABLE
    val rationale = LOCATION_PERMISSIONS.any {
        ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
    }
    return when {
        rationale -> LocationPermissionState.NEEDS_RATIONALE
        // Never asked and no rationale means the dialog has simply not been
        // shown yet. Asked, and still no rationale, means the system has
        // stopped showing it.
        asked -> LocationPermissionState.PERMANENTLY_DENIED
        else -> LocationPermissionState.ASKABLE
    }
}

/** Opens this app's page in Settings, where the permission can be turned back on. */
fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

/** Opens the system location toggle, for when location itself is switched off. */
fun openLocationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

/**
 * The Activity behind a composable's Context.
 *
 * Compose hands out a `ContextThemeWrapper`, not the Activity, and
 * `shouldShowRequestPermissionRationale` needs the real one. Unwrapping is the
 * dependency-free way to get it.
 */
private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
