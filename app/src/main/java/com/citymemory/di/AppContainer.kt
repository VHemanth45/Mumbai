package com.citymemory.di

import android.content.Context
import com.citymemory.data.local.database.CityMemoryDatabase
import com.citymemory.data.local.seed.AssetPlaceCatalog
import com.citymemory.data.dwell.DwellStateStore
import com.citymemory.data.dwell.PreferencesDwellStateStore
import com.citymemory.data.local.seed.DatabaseSeeder
import com.citymemory.data.map.OsmCityGeometryProvider
import com.citymemory.data.photo.AndroidPhotoLocationReader
import com.citymemory.data.photo.FilePhotoStore
import com.citymemory.data.photo.PhotoLocationReader
import com.citymemory.data.photo.PhotoStore
import com.citymemory.data.photo.PhotoVisitImporter
import com.citymemory.data.repository.PlaceRepositoryImpl
import com.citymemory.domain.repository.CityGeometryProvider
import com.citymemory.domain.repository.PlaceRepository
import com.citymemory.util.AndroidLocationSource
import com.citymemory.util.AndroidNavigationLauncher
import com.citymemory.util.AndroidVisitNotifier
import com.citymemory.util.LocationSource
import com.citymemory.util.NavigationLauncher
import com.citymemory.util.VisitNotifier

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
        DatabaseSeeder(database, AssetPlaceCatalog(applicationContext.assets))
    }

    /** Copies photos into app storage so they outlive the picker's grant. */
    private val photoStore: PhotoStore by lazy {
        FilePhotoStore(applicationContext)
    }

    val placeRepository: PlaceRepository by lazy {
        PlaceRepositoryImpl(database, seeder, photoStore)
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

    /**
     * The platform GPS, for "use my location" when adding a place. An interface
     * so the add flow can be tested with a fake fix and no device.
     */
    val locationSource: LocationSource by lazy {
        AndroidLocationSource()
    }

    // -- Automatic logging --------------------------------------------------

    /**
     * Where the dwell detector keeps its state, and whether it is switched on.
     *
     * Preferences-backed, so `DwellWorker` can read it without opening the
     * database — see [PreferencesDwellStateStore].
     */
    val dwellStateStore: DwellStateStore by lazy {
        PreferencesDwellStateStore(applicationContext)
    }

    /** Posts the "were you at ...?" question. */
    val visitNotifier: VisitNotifier by lazy {
        AndroidVisitNotifier()
    }

    /** Pulls the coordinate out of a photograph, redaction and all. */
    val photoLocationReader: PhotoLocationReader by lazy {
        AndroidPhotoLocationReader(applicationContext)
    }

    /** Photographs in, suggested visits out. */
    val photoVisitImporter: PhotoVisitImporter by lazy {
        PhotoVisitImporter(placeRepository, photoLocationReader)
    }
}
