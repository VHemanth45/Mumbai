package com.citymemory.data.local.seed

import android.util.Log
import androidx.room.withTransaction
import com.citymemory.data.local.database.CityMemoryDatabase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Puts the shipped Mumbai catalog into the database, on first launch and again
 * whenever an update ships a different one.
 *
 * Seeding is driven from the repository's read path rather than a Room
 * `onCreate` callback, so a collector can never observe an empty database
 * before the seed lands. It is idempotent and guarded by a mutex, so several
 * screens subscribing at once still seed exactly once.
 *
 * **Re-seeding is the part that has to be careful.** The catalog went from 177
 * places to 3,191, so an installed app has to pick the rest up — but the same
 * table now also holds places the user added themselves, and every row in it
 * may have a visit, a rating and an opinion hanging off it. So:
 *
 *  * the catalog is written with `@Upsert`, never `INSERT OR REPLACE`, which in
 *    SQLite is delete-then-insert and would cascade through the foreign key and
 *    wipe the user's history;
 *  * nothing is ever deleted, so a user-added place survives every re-seed, and
 *    so does a catalogued place that a later extract happens to drop;
 *  * the stamp is written in the same transaction as the places, so an
 *    interrupted re-seed is retried rather than recorded as done.
 */
class DatabaseSeeder(
    private val database: CityMemoryDatabase,
    private val catalog: PlaceCatalog,
    private val cityId: String = MumbaiSeed.CITY_ID,
) {
    private val mutex = Mutex()

    @Volatile
    private var seeded = false

    suspend fun seedIfNeeded() {
        if (seeded) return
        mutex.withLock {
            if (seeded) return
            try {
                seed()
            } catch (e: Exception) {
                // A missing or malformed asset must not take the app down on
                // launch. The city is still there, the map still draws, and the
                // next launch tries again — which is why `seeded` is not set.
                Log.e(TAG, "could not seed the place catalog", e)
                return
            }
            seeded = true
        }
    }

    private suspend fun seed() {
        val existing = database.cityDao().getCity(cityId)
        // Reading the asset is ~490 KB of parsing, so it only happens when the
        // database might actually need it: a fresh install, or an update whose
        // catalog turns out to differ.
        if (existing != null && existing.catalogStamp != null &&
            existing.catalogStamp == currentStampOrNull()
        ) {
            return
        }

        val loaded = catalog.load(cityId)
        if (existing?.catalogStamp == loaded.stamp) return

        database.withTransaction {
            // City first: every place carries a foreign key onto it, so the
            // other order fails the constraint on a fresh database. Both are in
            // one transaction, so an interrupted re-seed still rolls the stamp
            // back and is retried on the next launch.
            database.cityDao().upsertAll(listOf(MumbaiSeed.city.copy(catalogStamp = loaded.stamp)))
            database.placeDao().upsertAll(loaded.places)
        }
    }

    /**
     * The stamp of the catalog on disk, or null if that cannot be answered
     * without reading it.
     *
     * Split out so a catalog that can read its header cheaply — the asset does,
     * it is the first line — can skip decoding 3,191 rows on every launch to
     * discover nothing changed. An implementation that cannot returns null and
     * pays for the full read, which is what the in-memory test catalog does.
     */
    private suspend fun currentStampOrNull(): String? =
        (catalog as? StampedPlaceCatalog)?.stamp(cityId)

    private companion object {
        const val TAG = "DatabaseSeeder"
    }
}

/**
 * A catalog that can report which version it holds without decoding all of it.
 */
interface StampedPlaceCatalog : PlaceCatalog {
    suspend fun stamp(cityId: String): String?
}
