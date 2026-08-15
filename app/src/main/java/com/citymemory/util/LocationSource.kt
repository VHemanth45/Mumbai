package com.citymemory.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.citymemory.domain.model.GeoPoint
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Where the user is, when they are willing to say.
 *
 * Behind an interface for the same reason [NavigationLauncher] is: it keeps the
 * Android location APIs out of the view model, and it means the whole
 * add-a-place flow can be exercised in a JVM test with a fake that returns a
 * fix, a refusal, or nothing at all.
 */
interface LocationSource {

    /** Whether the user has already granted a location permission. */
    fun hasPermission(context: Context): Boolean

    /**
     * One fix, or the reason there isn't one.
     *
     * Suspends until a fix arrives or [FIX_TIMEOUT_MILLIS] passes. There is no
     * streaming variant on purpose: this is used to answer "where am I standing
     * right now", once, and a subscription would be a battery cost for a
     * question that has already been answered.
     */
    suspend fun currentLocation(context: Context): LocationFix

    companion object {
        /**
         * How long to wait for a satellite fix before giving up.
         *
         * A cold GPS fix indoors can take a minute or never arrive at all, and a
         * button that spins for a minute is a broken button. Twelve seconds is
         * long enough for a warm fix outdoors and short enough that giving up
         * still feels like an answer.
         */
        const val FIX_TIMEOUT_MILLIS = 12_000L
    }
}

sealed interface LocationFix {

    /** [accuracyMeters] is null when the provider did not say. */
    data class Found(val point: GeoPoint, val accuracyMeters: Float?) : LocationFix

    /** The permission has not been granted, or was refused. */
    data object PermissionDenied : LocationFix

    /** Location is switched off on the device, or no provider is usable. */
    data object Unavailable : LocationFix

    /** Everything was in order and no fix arrived in time. */
    data object TimedOut : LocationFix
}

/**
 * A fix from the framework's own [LocationManager].
 *
 * **Not** `FusedLocationProviderClient`. Fused is the better API — it blends
 * sensors and is easier on the battery — but it lives in Google Play services,
 * which this project does not depend on and which is not present on every
 * device. For a question asked once, while the user is standing at the place
 * they are adding, the platform GPS provider answers it, and it answers it
 * offline: satellites need no network, which is what lets this app keep having
 * no `INTERNET` permission at all.
 */
class AndroidLocationSource : LocationSource {

    override fun hasPermission(context: Context): Boolean =
        PERMISSIONS.any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    override suspend fun currentLocation(context: Context): LocationFix {
        if (!hasPermission(context)) return LocationFix.PermissionDenied

        val manager = ContextCompat.getSystemService(context, LocationManager::class.java)
            ?: return LocationFix.Unavailable
        if (!LocationManagerCompat.isLocationEnabled(manager)) return LocationFix.Unavailable

        // A last known fix is free and instant, and while it is standing in the
        // right building it is a far better answer than a spinner. Anything
        // stale enough to be in the wrong neighbourhood is ignored.
        lastKnownFix(manager)?.let { return it }

        val location = withTimeoutOrNull(LocationSource.FIX_TIMEOUT_MILLIS) {
            firstFix(context, manager)
        }
        return location?.let { LocationFix.Found(it.toGeoPoint(), it.accuracyOrNull()) }
            ?: LocationFix.TimedOut
    }

    /**
     * Whichever provider answers first, not whichever is listed first.
     *
     * This was a ranked list, GPS before network, and the ranking made the
     * second entry dead code: GPS is enabled on essentially every phone that
     * has location switched on, so it always won the pick — and then, in the
     * one scenario this whole feature exists for, burned the entire twelve
     * seconds. Standing inside the cafe you are adding is exactly where a cold
     * satellite fix does not arrive and where the network provider would have
     * answered in about a second, unasked.
     *
     * The losers are cancelled in the `finally`, which removes their listeners
     * and lets the radios go back to sleep.
     */
    private suspend fun firstFix(context: Context, manager: LocationManager): Location? =
        coroutineScope {
            val enabled = PROVIDERS.filter { provider ->
                runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
            }
            if (enabled.isEmpty()) return@coroutineScope null

            val winner = CompletableDeferred<Location?>()
            val outstanding = AtomicInteger(enabled.size)
            val jobs = enabled.map { provider ->
                launch {
                    val fix = awaitFix(context, manager, provider)
                    if (fix != null) {
                        winner.complete(fix)
                    } else if (outstanding.decrementAndGet() == 0) {
                        // Every provider gave up rather than timed out, so
                        // there is nothing left to wait for.
                        winner.complete(null)
                    }
                }
            }
            try {
                winner.await()
            } finally {
                jobs.forEach { it.cancel() }
            }
        }

    /**
     * A cached fix, if it is both recent and precise enough to be worth having.
     *
     * Age alone is not enough. A two-minute-old network fix can be accurate to
     * two kilometres, which drops the ring in the wrong suburb and says nothing
     * about it — worse than the second it would have cost to ask properly. So a
     * cached fix has to know how good it is, and be good.
     *
     * Suppressed: [currentLocation] returns before this on a missing permission.
     */
    @Suppress("MissingPermission")
    private fun lastKnownFix(manager: LocationManager): LocationFix.Found? {
        val now = System.currentTimeMillis()
        val best = PROVIDERS
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .filter { now - it.time <= LAST_KNOWN_MAX_AGE_MILLIS }
            .filter { it.hasAccuracy() && it.accuracy <= LAST_KNOWN_MAX_ACCURACY_M }
            .minByOrNull { it.accuracy }
            ?: return null
        return LocationFix.Found(best.toGeoPoint(), best.accuracyOrNull())
    }

    /**
     * One fix, then unsubscribe.
     *
     * `LocationManager.getCurrentLocation` does exactly this and is the right
     * call, but it arrived in API 30 and this app supports 26. Below that, a
     * subscription that removes itself on its first callback is the same thing
     * written out, which is all `requestSingleUpdate` ever was.
     */
    @Suppress("MissingPermission", "DEPRECATION")
    private suspend fun awaitFix(
        context: Context,
        manager: LocationManager,
        provider: String,
    ): Location? = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { continuation ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                manager.getCurrentLocation(
                    provider,
                    signal,
                    ContextCompat.getMainExecutor(context),
                ) { location -> continuation.resumeIfActive(location) }
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        manager.removeUpdates(this)
                        continuation.resumeIfActive(location)
                    }

                    // Abstract on API 26, defaulted from API 30. Both have to
                    // compile, so it is declared and does nothing.
                    @Deprecated("Required by the API 26 LocationListener")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) =
                        Unit

                    override fun onProviderDisabled(provider: String) {
                        manager.removeUpdates(this)
                        continuation.resumeIfActive(null)
                    }

                    override fun onProviderEnabled(provider: String) = Unit
                }
                continuation.invokeOnCancellation { manager.removeUpdates(listener) }
                manager.requestLocationUpdates(provider, 0L, 0f, listener)
            }
        }
    }

    private fun <T> CancellableContinuation<T>.resumeIfActive(value: T) {
        if (isActive) resume(value)
    }

    private companion object {
        val PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )

        /**
         * Providers in the order they are worth asking.
         *
         * GPS first because it is the only one that works with no network, and
         * this app has no `INTERNET` permission. Network is listed anyway
         * because a device with a recent fused fix will hand one over from
         * cache, and a free answer is worth asking for.
         */
        val PROVIDERS = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)

        /**
         * How stale a cached fix may be and still be used, two minutes. Long
         * enough to cover walking in from the street and opening the form,
         * short enough that it cannot be the last suburb you were in.
         */
        const val LAST_KNOWN_MAX_AGE_MILLIS = 2 * 60 * 1000L

        /**
         * How vague a cached fix may be and still be used, in metres. A hundred
         * puts the ring on the right block, which is somewhere you can drag
         * from; a kilometre puts it in the wrong neighbourhood while looking
         * exactly as confident.
         */
        const val LAST_KNOWN_MAX_ACCURACY_M = 100f
    }
}

private fun Location.toGeoPoint() = GeoPoint(latitude, longitude)

private fun Location.accuracyOrNull(): Float? = if (hasAccuracy()) accuracy else null
