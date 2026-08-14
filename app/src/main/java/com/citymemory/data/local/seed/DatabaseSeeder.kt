package com.citymemory.data.local.seed

import androidx.room.withTransaction
import com.citymemory.data.local.database.CityMemoryDatabase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Puts the mock Mumbai catalog into the database on first launch.
 *
 * Seeding is driven from the repository's read path rather than a Room
 * `onCreate` callback, so a collector can never observe an empty database
 * before the seed lands. It is idempotent and guarded by a mutex, so several
 * screens subscribing at once still seed exactly once.
 *
 * Only the catalog is seeded. User state is never touched, so this stays safe
 * to call on every launch forever.
 */
class DatabaseSeeder(
    private val database: CityMemoryDatabase,
) {
    private val mutex = Mutex()

    @Volatile
    private var seeded = false

    suspend fun seedIfNeeded() {
        if (seeded) return
        mutex.withLock {
            if (seeded) return
            if (database.placeDao().count() == 0) {
                database.withTransaction {
                    database.cityDao().upsertAll(listOf(MumbaiSeed.city))
                    database.placeDao().upsertAll(MumbaiSeed.places)
                }
            }
            seeded = true
        }
    }
}
