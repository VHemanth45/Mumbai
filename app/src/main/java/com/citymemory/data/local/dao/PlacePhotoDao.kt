package com.citymemory.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.citymemory.data.local.entities.PlacePhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlacePhotoDao {

    /** Oldest first, so a place's photos read as the order they were added. */
    @Query("SELECT * FROM place_photos WHERE placeId = :placeId ORDER BY addedAt ASC")
    fun observePhotos(placeId: String): Flow<List<PlacePhotoEntity>>

    @Query("SELECT * FROM place_photos WHERE placeId = :placeId ORDER BY addedAt ASC")
    suspend fun photosFor(placeId: String): List<PlacePhotoEntity>

    @Query("SELECT * FROM place_photos WHERE id = :photoId")
    suspend fun photo(photoId: String): PlacePhotoEntity?

    @Query("SELECT COUNT(*) FROM place_photos WHERE placeId = :placeId")
    suspend fun countFor(placeId: String): Int

    @Insert
    suspend fun insert(photo: PlacePhotoEntity)

    @Query("DELETE FROM place_photos WHERE id = :photoId")
    suspend fun delete(photoId: String)
}
