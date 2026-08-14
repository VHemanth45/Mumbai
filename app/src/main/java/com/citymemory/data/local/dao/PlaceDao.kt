package com.citymemory.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.citymemory.data.local.entities.PlaceEntity
import com.citymemory.data.local.entities.PlaceWithState
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceDao {

    /**
     * The app's primary stream. Room re-emits this whenever `places` OR
     * `user_place_state` changes, which is what makes marking a place visited
     * light up the map, bump the percentage and unlock achievements at once.
     */
    @Transaction
    @Query("SELECT * FROM places WHERE cityId = :cityId ORDER BY displayOrder ASC")
    fun observePlacesWithState(cityId: String): Flow<List<PlaceWithState>>

    @Transaction
    @Query("SELECT * FROM places WHERE id = :placeId")
    fun observePlaceWithState(placeId: String): Flow<PlaceWithState?>

    @Query("SELECT COUNT(*) FROM places")
    suspend fun count(): Int

    /**
     * Upsert, not INSERT OR REPLACE: REPLACE is delete-then-insert in SQLite,
     * which would cascade through the user_place_state foreign key and silently
     * wipe the user's visited history on any future re-seed.
     */
    @Upsert
    suspend fun upsertAll(places: List<PlaceEntity>)
}
