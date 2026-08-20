package com.citymemory.ui.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * What still stands between the user and automatic logging.
 *
 * Three separate grants have to line up, and — this is the part that makes it
 * fiddly rather than tedious — **they cannot be asked for together.** From
 * Android 11 the system silently drops `ACCESS_BACKGROUND_LOCATION` out of any
 * request that also contains the foreground ones, returning "denied" for
 * everything without showing a dialog. An app that asks for all three at once
 * therefore has a toggle that can never be switched on, and no error anywhere
 * explaining why.
 *
 * So this reports *the next single thing to ask for*, and the UI asks for
 * exactly that, one step at a time, re-reading after each answer.
 */
enum class AutoLogStep {
    /** Everything is in place. The detector can be switched on. */
    READY,

    /** Ordinary foreground location. A normal runtime dialog. */
    NEED_FOREGROUND_LOCATION,

    /** Android 13+ only. Also a normal runtime dialog. */
    NEED_NOTIFICATIONS,

    /**
     * "Allow all the time".
     *
     * On Android 10 this can still be a dialog. From Android 11 the option does
     * not appear in any dialog the app can raise — the only place it exists is
     * the app's own settings page — so the honest move is to explain what is
     * needed and send the user there rather than firing a request that shows
     * nothing.
     */
    NEED_BACKGROUND_LOCATION,
}

object AutoLogPermissions {

    /** Whether background location must be granted through Settings rather than a dialog. */
    val backgroundNeedsSettings: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    fun nextStep(context: Context): AutoLogStep = when {
        !hasForegroundLocation(context) -> AutoLogStep.NEED_FOREGROUND_LOCATION
        !hasNotifications(context) -> AutoLogStep.NEED_NOTIFICATIONS
        !hasBackgroundLocation(context) -> AutoLogStep.NEED_BACKGROUND_LOCATION
        else -> AutoLogStep.READY
    }

    fun hasForegroundLocation(context: Context): Boolean = LOCATION_PERMISSIONS.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Notifications, which below Android 13 are on unless the user turned them
     * off in Settings — and there is no runtime permission to ask for there.
     */
    fun hasNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Background location.
     *
     * Before Android 10 there is no such permission and a foreground grant
     * already works from the background, so the foreground grant *is* the
     * answer — reporting otherwise would demand something the platform has no
     * way to give.
     */
    fun hasBackgroundLocation(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return hasForegroundLocation(context)
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
    }
}
