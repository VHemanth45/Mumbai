package com.citymemory.domain

import com.citymemory.domain.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for the state machine behind "you stayed somewhere".
 *
 * Twenty minutes of real time is three integers here, which is the entire
 * reason the tracker holds no state of its own.
 */
class DwellTrackerTest {

    private val bandra = GeoPoint(19.0596, 72.8295)
    private val minute = 60_000L
    private val t0 = 1_700_000_000_000L

    /** A point [metres] north of [from]. */
    private fun north(from: GeoPoint, metres: Double) =
        GeoPoint(from.latitude + metres / GeoPoint.METRES_PER_DEGREE_LATITUDE, from.longitude)

    @Test
    fun `the first fix is an arrival, never a dwell`() {
        val update = DwellTracker.sample(null, bandra, 12f, t0)

        assertTrue(update is DwellUpdate.Arrived)
        assertEquals(1, update.state.samples)
        assertEquals(false, update.state.reported)
    }

    @Test
    fun `staying past twenty minutes reports exactly once`() {
        var state = DwellTracker.sample(null, bandra, 10f, t0).state

        val fifteen = DwellTracker.sample(state, bandra, 10f, t0 + 15 * minute)
        assertTrue("15 minutes is not a visit", fifteen is DwellUpdate.Staying)
        state = fifteen.state

        val twenty = DwellTracker.sample(state, bandra, 10f, t0 + 25 * minute)
        assertTrue(twenty is DwellUpdate.Dwelled)
        assertEquals(25 * minute, (twenty as DwellUpdate.Dwelled).stayedMillis)
        state = twenty.state

        // Still sitting there at the next sampling run. One stay is one
        // question — and note the gap has to be a plausible one, because a gap
        // longer than the continuity window is a *different* stay by design.
        val later = DwellTracker.sample(state, bandra, 10f, t0 + 40 * minute)
        assertTrue("a stay must not re-ask", later is DwellUpdate.Staying)
    }

    @Test
    fun `drifting inside the radius is still one stay`() {
        var state = DwellTracker.sample(null, bandra, 10f, t0).state
        // A phone on a table reports fixes scattered over tens of metres.
        state = DwellTracker.sample(state, north(bandra, 60.0), 10f, t0 + 10 * minute).state

        // 40 m south of the original fix is 70 m from the centroid, which has
        // moved 30 m north — comfortably inside the radius rather than on it.
        val update = DwellTracker.sample(state, north(bandra, -40.0), 10f, t0 + 25 * minute)

        assertTrue(update is DwellUpdate.Dwelled)
    }

    @Test
    fun `walking out of the radius starts again`() {
        val state = DwellTracker.sample(null, bandra, 10f, t0).state

        val update = DwellTracker.sample(state, north(bandra, 400.0), 10f, t0 + 25 * minute)

        assertTrue(update is DwellUpdate.Arrived)
        assertEquals(1, update.state.samples)
    }

    @Test
    fun `a broken thread starts a new stay, which the repository then dedupes`() {
        // Continuity is the tracker's only claim, so when it breaks it says so
        // rather than guessing. That does mean sitting in one cafe through a
        // long gap can produce a second stay for the same place — which is why
        // "should this be asked?" is answered in `recordSuggestion`, against
        // what the user has already been told, and not here.
        var state = DwellTracker.sample(null, bandra, 10f, t0).state
        state = DwellTracker.sample(state, bandra, 10f, t0 + 25 * minute).state

        val resumed = DwellTracker.sample(state, bandra, 10f, t0 + 200 * minute)
        assertTrue(resumed is DwellUpdate.Arrived)
        assertEquals(false, resumed.state.reported)
    }

    @Test
    fun `a long gap in the samples is not evidence of staying`() {
        // The phone was in Doze, or location was off, or it was underground.
        // Returning to the same spot four hours later must not be read as
        // having sat there for four hours.
        val state = DwellTracker.sample(null, bandra, 10f, t0).state

        val update = DwellTracker.sample(state, bandra, 10f, t0 + 240 * minute)

        assertTrue(update is DwellUpdate.Arrived)
    }

    @Test
    fun `a gap of one missed sampling run still counts as one stay`() {
        val state = DwellTracker.sample(null, bandra, 10f, t0).state

        val update = DwellTracker.sample(state, bandra, 10f, t0 + 30 * minute)

        assertTrue(update is DwellUpdate.Dwelled)
    }

    @Test
    fun `a clock set backwards starts again rather than producing negative time`() {
        val state = DwellTracker.sample(null, bandra, 10f, t0 + 60 * minute).state

        val update = DwellTracker.sample(state, bandra, 10f, t0)

        assertTrue(update is DwellUpdate.Arrived)
    }

    @Test
    fun `the anchor is the centroid of the stay, not the first fix`() {
        var state = DwellTracker.sample(null, bandra, 10f, t0).state
        state = DwellTracker.sample(state, north(bandra, 60.0), 10f, t0 + 10 * minute).state

        // Two fixes, 0 m and 60 m north: the centroid sits between them.
        val offsetMetres = (state.anchor.latitude - bandra.latitude) *
            GeoPoint.METRES_PER_DEGREE_LATITUDE
        assertEquals(30.0, offsetMetres, 1.0)
    }

    @Test
    fun `accuracy is kept at its best, not its latest`() {
        var state = DwellTracker.sample(null, bandra, 8f, t0).state
        state = DwellTracker.sample(state, bandra, 55f, t0 + 10 * minute).state

        assertEquals(8f, state.bestAccuracyMeters!!, 0.01f)
    }
}
