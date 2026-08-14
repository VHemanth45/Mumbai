package com.citymemory.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.citymemory.data.local.dao.CityDao
import com.citymemory.data.local.dao.PlaceDao
import com.citymemory.data.local.dao.UserPlaceStateDao
import com.citymemory.data.local.entities.CityEntity
import com.citymemory.data.local.entities.PlaceEntity
import com.citymemory.data.local.entities.UserPlaceStateEntity

@Database(
    entities = [
        CityEntity::class,
        PlaceEntity::class,
        UserPlaceStateEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class CityMemoryDatabase : RoomDatabase() {

    abstract fun cityDao(): CityDao
    abstract fun placeDao(): PlaceDao
    abstract fun userPlaceStateDao(): UserPlaceStateDao

    companion object {
        const val DATABASE_NAME = "city_memory.db"

        // Room turns on `PRAGMA foreign_keys` itself for schemas that declare
        // them, so the places -> user_place_state cascade needs no extra setup.
        fun build(context: Context): CityMemoryDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                CityMemoryDatabase::class.java,
                DATABASE_NAME,
            ).build()
    }
}
