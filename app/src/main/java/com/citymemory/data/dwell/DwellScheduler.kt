package com.citymemory.data.dwell

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Turns the dwell sampler on and off.
 *
 * The interval is fifteen minutes because that is WorkManager's floor, and it
 * happens to suit: [com.citymemory.domain.DwellTracker.DwellMillis] is twenty
 * minutes, so two consecutive runs are enough to establish a stay, and the
 * tracker tolerates one skipped run before it gives up on the thread.
 *
 * **What this costs, stated plainly.** A GPS fix roughly every quarter of an
 * hour, all day. The platform will not honour that while the device is dozing —
 * it batches the work to maintenance windows — which is a feature here rather
 * than a limitation: a phone that has been face-down on a desk for two hours
 * has not been anywhere. The battery cost lands when the user is out and about
 * with the screen going on and off, which is exactly when the feature earns it.
 *
 * There is no `setRequiresDeviceIdle`, no network constraint and no charging
 * constraint on purpose. Every one of them would mean sampling precisely when
 * the user is *not* out having the visits this exists to catch.
 *
 * **The honest limitation, which no scheduler on this platform escapes.**
 * App Standby buckets (API 28+) throttle deferrable work by how often the app
 * is opened, and City Memory is opened a couple of times a week by design — a
 * journal is not a feed. An app in the RARE bucket gets roughly one deferrable
 * job a day; RESTRICTED is worse. On top of that, since API 26 the platform
 * caps background location for *any* app at a few computations an hour whatever
 * API it calls. So this will miss dwells, and on a lightly-used install it will
 * miss most of them.
 *
 * That is survivable only because of how the feature is shaped: a missed dwell
 * is silent and costs nothing, since the map is unchanged and no wrong entry is
 * written. It is also why photo import is the primary path — it is user-
 * triggered, so nothing throttles it, and it can recover months of history in
 * one go where this recovers one afternoon at a time.
 *
 * An `AlarmManager.setAndAllowWhileIdle` receiver would get a somewhat better
 * cadence in Doze, at the cost of hand-rolling reboot handling and its own
 * bucket quotas. It is the upgrade to make if real-world misses turn out to
 * matter more than the miss-tolerant design assumes.
 */
object DwellScheduler {

    const val WORK_NAME = "dwell-sampler"
    const val INTERVAL_MINUTES = 15L

    /**
     * Starts sampling, or leaves an existing schedule exactly as it is.
     *
     * `KEEP`, not `UPDATE`: re-enqueuing with `UPDATE` restarts the period, so
     * an app that scheduled on every launch would push the next run fifteen
     * minutes away every time it was opened, and a user who checks the app
     * often would never be sampled at all.
     */
    fun enable(context: Context) {
        val request = PeriodicWorkRequestBuilder<DwellWorker>(
            INTERVAL_MINUTES,
            TimeUnit.MINUTES,
        )
            .setConstraints(
                Constraints.Builder()
                    // Sampling a device that is nearly flat to ask whether it
                    // went to a cafe is not a trade anyone would choose.
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
