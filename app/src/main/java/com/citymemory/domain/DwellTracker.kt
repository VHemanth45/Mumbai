package com.citymemory.domain

import com.citymemory.domain.model.GeoPoint

/**
 * Deciding, from a trickle of location fixes, that someone *stayed somewhere*.
 *
 * Pure Kotlin and completely stateless: every call takes the previous state and
 * returns the next one. That is not tidiness for its own sake — the sampler
 * that drives this runs as a background job every quarter of an hour, and
 * between two runs the process is usually dead. There is nowhere to keep a
 * state machine in memory, so the state has to be a value that can be written
 * down and read back, and the logic has to be a function of it. It also means
 * a twenty-minute dwell can be tested in a microsecond by handing it three
 * timestamps.
 *
 * **What it must not do is claim continuity it cannot see.** A phone in Doze,
 * with location off, or in a pocket underground produces a gap in the samples,
 * and a gap is not evidence of staying — it is absence of evidence. Treating it
 * as a dwell would offer the user a place they walked past hours ago. So a gap
 * longer than [ContinuityGapMillis] abandons the anchor and starts again, and
 * the honest failure mode of this feature is missing a real visit rather than
 * inventing one.
 */
object DwellTracker {

    /**
     * How far you can move and still be "here", in metres.
     *
     * A phone lying on a table reports fixes scattered over tens of metres —
     * more indoors, where this feature spends its life. Below about fifty this
     * measures GPS noise instead of the user; much above a hundred and a
     * building in south Mumbai holds several different places, so a walk down
     * the block reads as sitting still.
     */
    const val RadiusMeters = 80.0

    /** How long counts as having been somewhere rather than passing through. */
    const val DwellMillis = 20 * 60 * 1000L

    /**
     * The longest gap between fixes that still counts as one stay.
     *
     * The sampler aims for a fix every fifteen minutes and will often miss one,
     * so this has to tolerate a skipped run; at twice the sampling interval it
     * does. Two missed runs in a row means the phone was asleep or dark for
     * three quarters of an hour, and there is no honest way to say what
     * happened in the middle of that.
     */
    const val ContinuityGapMillis = 40 * 60 * 1000L

    /**
     * Folds one fix into the running state.
     *
     * [accuracyMeters] is kept at its best-seen value rather than its latest:
     * the coordinate this eventually hands to [PlaceMatcher] is a centroid of
     * every fix in the stay, so it deserves to be judged by the best evidence
     * that went into it, not by whichever fix happened to arrive last.
     */
    fun sample(
        previous: DwellState?,
        point: GeoPoint,
        accuracyMeters: Float?,
        at: Long,
    ): DwellUpdate {
        val fresh = DwellState(
            anchor = point,
            arrivedAt = at,
            lastSeenAt = at,
            samples = 1,
            bestAccuracyMeters = accuracyMeters,
            reported = false,
        )
        if (previous == null) return DwellUpdate.Arrived(fresh)

        // Time cannot run backwards, but a device clock can be set backwards.
        // Rather than reason about negative durations everywhere below, that is
        // treated as a broken thread and started over.
        if (at < previous.lastSeenAt) return DwellUpdate.Arrived(fresh)
        if (at - previous.lastSeenAt > ContinuityGapMillis) return DwellUpdate.Arrived(fresh)
        if (point.distanceTo(previous.anchor) > RadiusMeters) return DwellUpdate.Arrived(fresh)

        val next = previous.withSample(point, accuracyMeters, at)
        val stayed = at - next.arrivedAt
        return if (!previous.reported && stayed >= DwellMillis) {
            DwellUpdate.Dwelled(next.copy(reported = true), stayedMillis = stayed)
        } else {
            DwellUpdate.Staying(next)
        }
    }
}

/**
 * Where the user has been sitting, and for how long.
 *
 * Every field is a primitive so this can be written to preferences and read
 * back without a serialiser — see `DwellStateStore`.
 */
data class DwellState(
    /**
     * The running centroid of every fix in this stay, not the first one.
     *
     * A single fix taken through a roof can be sixty metres out. Averaging the
     * two or three that make up a stay pulls the point back towards the middle
     * of wherever the user actually is, and it is this point — not any
     * individual fix — that gets matched against the catalog.
     */
    val anchor: GeoPoint,
    val arrivedAt: Long,
    val lastSeenAt: Long,
    val samples: Int,
    val bestAccuracyMeters: Float?,
    /** Whether this stay has already been offered to the user. */
    val reported: Boolean,
) {
    internal fun withSample(point: GeoPoint, accuracyMeters: Float?, at: Long): DwellState {
        val n = samples
        val moved = GeoPoint(
            latitude = (anchor.latitude * n + point.latitude) / (n + 1),
            longitude = (anchor.longitude * n + point.longitude) / (n + 1),
        )
        val best = listOfNotNull(bestAccuracyMeters, accuracyMeters).minOrNull()
        return copy(
            anchor = moved,
            lastSeenAt = at,
            samples = n + 1,
            bestAccuracyMeters = best,
        )
    }
}

/** What one fix did to the state. */
sealed interface DwellUpdate {

    val state: DwellState

    /** Somewhere new — either the first fix, or they moved, or the thread broke. */
    data class Arrived(override val state: DwellState) : DwellUpdate

    /** Still here. Either not long enough yet, or already offered. */
    data class Staying(override val state: DwellState) : DwellUpdate

    /** Long enough, for the first time. This is the one that asks the user. */
    data class Dwelled(
        override val state: DwellState,
        val stayedMillis: Long,
    ) : DwellUpdate
}
