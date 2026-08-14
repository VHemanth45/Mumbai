package com.citymemory.data.repository

import androidx.room.withTransaction
import com.citymemory.data.local.database.CityMemoryDatabase
import com.citymemory.data.local.entities.UserPlaceStateEntity
import com.citymemory.data.local.seed.DatabaseSeeder
import com.citymemory.data.mapper.toDomain
import com.citymemory.domain.model.City
import com.citymemory.domain.model.Place
import com.citymemory.domain.repository.PlaceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class PlaceRepositoryImpl(
    private val database: CityMemoryDatabase,
    private val seeder: DatabaseSeeder,
    /** Injected so visit timestamps are deterministic under test. */
    private val now: () -> Long = System::currentTimeMillis,
) : PlaceRepository {

    override fun observeCity(cityId: String): Flow<City?> = flow {
        seeder.seedIfNeeded()
        emitAll(database.cityDao().observeCity(cityId).map { it?.toDomain() })
    }

    override fun observePlaces(cityId: String): Flow<List<Place>> = flow {
        seeder.seedIfNeeded()
        emitAll(
            database.placeDao()
                .observePlacesWithState(cityId)
                .map { rows -> rows.map { it.toDomain() } },
        )
    }

    override fun observePlace(placeId: String): Flow<Place?> = flow {
        seeder.seedIfNeeded()
        emitAll(database.placeDao().observePlaceWithState(placeId).map { it?.toDomain() })
    }

    override suspend fun setVisited(placeId: String, isVisited: Boolean) {
        mutateState(placeId) { current ->
            current.copy(
                isVisited = isVisited,
                // Keep the original timestamp on a repeat mark; clear it on undo,
                // so visitedAt always means "when this place was first explored".
                visitedAt = if (isVisited) current.visitedAt ?: now() else null,
            )
        }
    }

    override suspend fun setWishlisted(placeId: String, isWishlisted: Boolean) {
        mutateState(placeId) { current ->
            current.copy(
                isWishlisted = isWishlisted,
                wishlistedAt = if (isWishlisted) current.wishlistedAt ?: now() else null,
            )
        }
    }

    /**
     * Read-modify-write inside a transaction. Two toggles racing on the same
     * place therefore serialize instead of clobbering each other's field.
     */
    private suspend fun mutateState(
        placeId: String,
        transform: (UserPlaceStateEntity) -> UserPlaceStateEntity,
    ) {
        // Writes seed too, not just reads: user_place_state has a foreign key on
        // places, so a mutation that beat the first read would fail the
        // constraint rather than silently no-op.
        seeder.seedIfNeeded()
        database.withTransaction {
            val dao = database.userPlaceStateDao()
            val current = dao.getState(placeId) ?: UserPlaceStateEntity(placeId = placeId)
            val updated = transform(current)
            if (!updated.isVisited && !updated.isWishlisted) {
                dao.delete(placeId)
            } else {
                dao.upsert(updated)
            }
        }
    }
}
