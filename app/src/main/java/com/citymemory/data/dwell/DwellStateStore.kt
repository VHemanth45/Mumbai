package com.citymemory.data.dwell

import android.content.Context
import com.citymemory.domain.DwellState
import com.citymemory.domain.model.GeoPoint

/**
 * Where the dwell state lives between two runs of the sampler.
 *
 * It has to live *somewhere* outside the process: the sampler wakes every
 * fifteen minutes, and the process is usually gone in between, so an in-memory
 * state machine would forget the user had arrived anywhere before it could
 * notice they had stayed.
 *
 * Preferences rather than a Room table, which is the deliberate choice here.
 * This is six primitives with a single writer, read and written at most four
 * times an hour, and — the part that decides it — it must be readable in a
 * background worker *without opening the app's database*. Opening Room would
 * pull the whole catalog machinery into a process that woke up to take one GPS
 * fix, on a schedule, all day.
 */
interface DwellStateStore {
    fun read(): DwellState?
    fun write(state: DwellState)
    fun clear()

    /** Whether the user has switched the detector on. Off until they do. */
    var isEnabled: Boolean
}

class PreferencesDwellStateStore(context: Context) : DwellStateStore {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    override fun read(): DwellState? {
        // `arrivedAt` doubles as the presence flag: no stay has ever started at
        // the epoch, and a half-written record is better treated as no record.
        val arrivedAt = prefs.getLong(KEY_ARRIVED_AT, 0L)
        if (arrivedAt == 0L) return null
        return DwellState(
            anchor = GeoPoint(
                latitude = prefs.getFloat(KEY_LATITUDE, 0f).toDouble(),
                longitude = prefs.getFloat(KEY_LONGITUDE, 0f).toDouble(),
            ),
            arrivedAt = arrivedAt,
            lastSeenAt = prefs.getLong(KEY_LAST_SEEN_AT, arrivedAt),
            samples = prefs.getInt(KEY_SAMPLES, 1),
            bestAccuracyMeters = prefs.getFloat(KEY_ACCURACY, -1f).takeIf { it >= 0f },
            reported = prefs.getBoolean(KEY_REPORTED, false),
        )
    }

    override fun write(state: DwellState) {
        prefs.edit()
            // Float, not Double: SharedPreferences has no double, and the
            // alternative — packing the bits into a Long — buys precision this
            // has no use for. A float carries about seven significant digits,
            // which at Mumbai's latitude is a metre or so, against a dwell
            // radius of eighty.
            .putFloat(KEY_LATITUDE, state.anchor.latitude.toFloat())
            .putFloat(KEY_LONGITUDE, state.anchor.longitude.toFloat())
            .putLong(KEY_ARRIVED_AT, state.arrivedAt)
            .putLong(KEY_LAST_SEEN_AT, state.lastSeenAt)
            .putInt(KEY_SAMPLES, state.samples)
            .putFloat(KEY_ACCURACY, state.bestAccuracyMeters ?: -1f)
            .putBoolean(KEY_REPORTED, state.reported)
            .apply()
    }

    override fun clear() {
        prefs.edit()
            .remove(KEY_LATITUDE)
            .remove(KEY_LONGITUDE)
            .remove(KEY_ARRIVED_AT)
            .remove(KEY_LAST_SEEN_AT)
            .remove(KEY_SAMPLES)
            .remove(KEY_ACCURACY)
            .remove(KEY_REPORTED)
            .apply()
    }

    private companion object {
        const val FILE_NAME = "dwell"
        const val KEY_ENABLED = "enabled"
        const val KEY_LATITUDE = "lat"
        const val KEY_LONGITUDE = "lng"
        const val KEY_ARRIVED_AT = "arrivedAt"
        const val KEY_LAST_SEEN_AT = "lastSeenAt"
        const val KEY_SAMPLES = "samples"
        const val KEY_ACCURACY = "accuracy"
        const val KEY_REPORTED = "reported"
    }
}

/** In-memory, for tests. */
class FakeDwellStateStore(override var isEnabled: Boolean = true) : DwellStateStore {
    private var state: DwellState? = null
    override fun read(): DwellState? = state
    override fun write(state: DwellState) { this.state = state }
    override fun clear() { state = null }
}
