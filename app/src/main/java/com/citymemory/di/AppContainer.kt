package com.citymemory.di

import android.content.Context
import com.citymemory.data.local.database.CityMemoryDatabase
import com.citymemory.data.local.seed.DatabaseSeeder
import com.citymemory.data.map.OsmCityGeometryProvider
import com.citymemory.data.repository.PlaceRepositoryImpl
import com.citymemory.domain.repository.CityGeometryProvider
import com.citymemory.domain.repository.PlaceRepository
import com.citymemory.util.AndroidNavigationLauncher
import com.citymemory.util.NavigationLauncher

/**
 * Manual dependency container.
 *
 * An MVP with three dependencies does not need Hilt. This is the whole graph,
 * readable in one screen, constructed once per process and held by the
 * Application. Everything is exposed as an interface, so swapping the mock map
 * for real geometry (or Room for something else) is a one-line change here.
 */
class AppContainer(context: Context) {

    private val applicationContext: Context = context.applicationContext

    private val database: CityMemoryDatabase by lazy {
        CityMemoryDatabase.build(applicationContext)
    }

    private val seeder: DatabaseSeeder by lazy {
        DatabaseSeeder(database)
    }

    val placeRepository: PlaceRepository by lazy {
        PlaceRepositoryImpl(database, seeder)
    }

    /**
     * Real OpenStreetMap geometry from `assets/mumbai.map`, falling back to the
     * hand-authored outline if that asset is missing or unreadable.
     */
    val cityGeometryProvider: CityGeometryProvider by lazy {
        OsmCityGeometryProvider(applicationContext)
    }

    val navigationLauncher: NavigationLauncher by lazy {
        AndroidNavigationLauncher()
    }
}
