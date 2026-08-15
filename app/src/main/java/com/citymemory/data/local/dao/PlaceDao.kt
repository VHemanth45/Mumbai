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

    @Query("SELECT EXISTS(SELECT 1 FROM places WHERE id = :placeId)")
    suspend fun exists(placeId: String): Boolean

    /**
     * Where a user-added place goes in the list: one before whatever is first.
     *
     * Null on an unseeded city, which the caller reads as zero. Places the user
     * added themselves sort ahead of the catalog deliberately — the catalog is
     * 3,191 rows deep and the one you typed in yourself should not be somewhere
     * inside it.
     */
    @Query("SELECT MIN(displayOrder) FROM places WHERE cityId = :cityId")
    suspend fun lowestDisplayOrder(cityId: String): Int?

    @Upsert
    suspend fun upsert(place: PlaceEntity)

    /**
     * Only ever called for a place the user added. Their state row goes with it
     * through the foreign key's cascade.
     */
    @Query("DELETE FROM places WHERE id = :placeId AND isUserAdded = 1")
    suspend fun deleteUserPlace(placeId: String)

    @Query("UPDATE places SET address = :address WHERE id = :placeId")
    suspend fun updateAddress(placeId: String, address: String?)

    /**
     * Upsert, not INSERT OR REPLACE: REPLACE is delete-then-insert in SQLite,
     * which would cascade through the user_place_state foreign key and silently
     * wipe the user's visited history on any future re-seed.
     */
    @Upsert
    suspend fun upsertAll(places: List<PlaceEntity>)
}
