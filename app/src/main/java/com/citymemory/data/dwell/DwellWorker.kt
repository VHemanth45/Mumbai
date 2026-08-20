package com.citymemory.data.dwell

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.citymemory.CityMemoryApplication
import com.citymemory.data.local.seed.MumbaiSeed
import com.citymemory.domain.DwellTracker
import com.citymemory.domain.DwellUpdate
import com.citymemory.domain.PlaceMatcher
import com.citymemory.domain.model.SuggestionSource
import com.citymemory.util.LocationFix
import kotlinx.coroutines.flow.first

/**
 * Takes one location fix, folds it into the dwell state, and — when that state
 * says the user has been somewhere — asks them about it.
 *
 * Everything that decides anything is elsewhere and is pure: [DwellTracker]
 * decides what the fix means, [PlaceMatcher] decides what is there, and
 * `recordSuggestion` decides whether the question is worth asking. This class
 * is the plumbing between them and the platform, which is why it has no logic
 * worth testing and they have all of it.
 *
 * **It returns success even when it does nothing.** A worker that fails gets
 * retried with backoff, and there is nothing here worth retrying: no fix now
 * means no fix now, and the next run is a quarter of an hour away regardless.
 * Retrying would only wake the radio again sooner, which is the one cost this
 * feature has to keep down.
 */
class DwellWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? CityMemoryApplication ?: return Result.success()
        val container = app.container
        val store = container.dwellStateStore

        // The user can switch this off between two runs, and the cancellation
        // races with a job the system has already started.
        if (!store.isEnabled) return Result.success()

        // The sampling path, not the interactive one: it prefers a fix the
        // platform already had and only pays for a new one when there is
        // nothing usable. See `LocationSource.recentLocation`.
        val fix = container.locationSource.recentLocation(
            context = applicationContext,
            maxAgeMillis = MAX_FIX_AGE_MILLIS,
        )
        if (fix !is LocationFix.Found) {
            // A permission revoked while the app was not looking is the one
            // case worth acting on: keeping the schedule alive would wake the
            // device every quarter hour to be told "no" forever.
            if (fix is LocationFix.PermissionDenied) {
                store.isEnabled = false
                store.clear()
                DwellScheduler.cancel(applicationContext)
            }
            return Result.success()
        }

        val update = DwellTracker.sample(
            previous = store.read(),
            point = fix.point,
            accuracyMeters = fix.accuracyMeters,
            at = System.currentTimeMillis(),
        )
        store.write(update.state)
        if (update !is DwellUpdate.Dwelled) return Result.success()

        return runCatching { offerVisit(container, update) }
            .onFailure { Log.w(TAG, "could not offer a visit for a dwell", it) }
            .let { Result.success() }
    }

    private suspend fun offerVisit(
        container: com.citymemory.di.AppContainer,
        dwelled: DwellUpdate.Dwelled,
    ) {
        val repository = container.placeRepository
        val places = repository.observePlaces(MumbaiSeed.CITY_ID).first()

        val candidates = PlaceMatcher.candidatesAt(
            point = dwelled.state.anchor,
            accuracyMeters = dwelled.state.bestAccuracyMeters,
            places = places,
            source = SuggestionSource.DWELL,
        )
        // Nothing mapped within the error circle. Somewhere real, but not
        // somewhere this catalog knows — and inventing a place from a GPS fix
        // is exactly what the add-a-place flow is for, by hand, later.
        val best = candidates.firstOrNull() ?: return

        val suggestionId = repository.recordSuggestion(
            placeId = best.place.id,
            source = SuggestionSource.DWELL,
            detectedAt = dwelled.state.arrivedAt,
            latitude = dwelled.state.anchor.latitude,
            longitude = dwelled.state.anchor.longitude,
        ) ?: return // Already asked, already answered, or already been there.

        check(suggestionId.isNotEmpty())
        container.visitNotifier.askAboutVisit(
            context = applicationContext,
            placeName = best.place.name,
            pendingCount = repository.pendingSuggestionCount(),
        )
    }

    companion object {
        const val TAG = "DwellWorker"

        /**
         * How stale a cached fix may be and still count as this sample.
         *
         * One sampling interval. Anything older belongs to the previous run and
         * would have the tracker fold the same fix in twice — which looks
         * exactly like sitting perfectly still, and would manufacture a dwell
         * out of a phone that simply stopped reporting.
         */
        val MAX_FIX_AGE_MILLIS = DwellScheduler.INTERVAL_MINUTES * 60 * 1000
    }
}
