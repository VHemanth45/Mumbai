package com.citymemory.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.citymemory.data.local.entities.UserPlaceStateEntity

@Dao
interface UserPlaceStateDao {

    @Query("SELECT * FROM user_place_state WHERE placeId = :placeId")
    suspend fun getState(placeId: String): UserPlaceStateEntity?

    @Upsert
    suspend fun upsert(state: UserPlaceStateEntity)

    /**
     * Drops the row entirely once a place is neither visited nor wishlisted, so
     * "never touched" and "un-marked again" stay indistinguishable.
     */
    @Query("DELETE FROM user_place_state WHERE placeId = :placeId")
    suspend fun delete(placeId: String)
}
