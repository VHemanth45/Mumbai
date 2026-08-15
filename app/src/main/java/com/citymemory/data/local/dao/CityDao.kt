package com.citymemory.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.citymemory.data.local.entities.CityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CityDao {

    @Query("SELECT * FROM cities WHERE id = :cityId")
    fun observeCity(cityId: String): Flow<CityEntity?>

    /** A one-shot read, used by the seeder to check which catalog is loaded. */
    @Query("SELECT * FROM cities WHERE id = :cityId")
    suspend fun getCity(cityId: String): CityEntity?

    @Upsert
    suspend fun upsertAll(cities: List<CityEntity>)
}
