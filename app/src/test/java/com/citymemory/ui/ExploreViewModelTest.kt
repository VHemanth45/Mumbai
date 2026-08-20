package com.citymemory.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.citymemory.data.local.database.CityMemoryDatabase
import com.citymemory.data.local.seed.DatabaseSeeder
import com.citymemory.data.dwell.FakeDwellStateStore
import com.citymemory.data.map.MockMumbaiGeometryProvider
import com.citymemory.data.photo.NoPhotoLocationReader
import com.citymemory.data.photo.PhotoVisitImporter
import com.citymemory.util.NoVisitNotifier
import com.citymemory.domain.model.GeoPoint
import com.citymemory.util.LocationFix
import com.citymemory.SeedPlaces
import com.citymemory.data.repository.PlaceRepositoryImpl
import com.citymemory.domain.model.PlaceCategory
import com.citymemory.domain.repository.PlaceRepository
import com.citymemory.ui.screens.explore.ExploreUiState
import com.citymemory.ui.screens.explore.ExploreViewModel
import com.citymemory.ui.screens.explore.LocatingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the Explore screen's state, which its own UI test cannot reach: the
 * map's permanent breathing animation keeps the Compose test clock from ever
 * going idle, so the screen is verified through its ViewModel instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class) // Dispatchers.setMain / resetMain
class ExploreViewModelTest {

    private lateinit var database: CityMemoryDatabase
    private lateinit var repository: PlaceRepository
    private lateinit var viewModel: ExploreViewModel
    private val locationSource = FakeLocationSource()

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CityMemoryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PlaceRepositoryImpl(database, DatabaseSeeder(database, SeedPlaces.catalog))
        viewModel = ExploreViewModel(
            repository = repository,
            geometryProvider = MockMumbaiGeometryProvider(),
            locationSource = locationSource,
            dwellStateStore = FakeDwellStateStore(isEnabled = false),
            photoVisitImporter = PhotoVisitImporter(repository, NoPhotoLocationReader),
            visitNotifier = NoVisitNotifier,
        )
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    // ---- Adding a place ---------------------------------------------------

    /**
     * The bug this whole path exists around: at the overview the camera cannot
     * pan, so the ring never moves and every place added from there was written
     * at one coordinate. `MapCameraTest` pins the camera half; this pins that
     * the view model will not silently save a place with nowhere to put it.
     */
    @Test
    fun `a place cannot be saved before the map has said where the ring is`() = runBlocking {
        loadedState { it.geometry.shapes.isNotEmpty() }
        viewModel.onAddPlaceRequested("Chai stall")

        assertFalse("nothing has reported a location yet", viewModel.hasPinnedLocation.value)
        viewModel.onAddPlaceConfirmed()

        // Still open, nothing written: the form has not silently thrown the
        // name away, and the Save button is disabled off the same flow.
        assertNotNull(viewModel.addDraft.value)
        assertEquals(SeedPlaces.total, loadedState().places.size)
    }

    @Test
    fun `the ring reports where it is and the place lands there`() = runBlocking {
        loadedState { it.geometry.shapes.isNotEmpty() }
        viewModel.onAddPlaceRequested("Chai stall")
        viewModel.onViewportCenterChanged(GeoPoint(19.0176, 72.8562))

        assertTrue(viewModel.hasPinnedLocation.value)
        viewModel.onAddPlaceConfirmed()

        val added = loadedState { state -> state.places.any { it.isUserAdded } }
            .places.first { it.isUserAdded }
        assertEquals("Chai stall", added.name)
        assertEquals(19.0176, added.latitude, 1e-9)
        assertEquals(72.8562, added.longitude, 1e-9)
    }

    // ---- Use my location --------------------------------------------------

    @Test
    fun `a fix inside Mumbai flies the map there and the ring follows`() = runBlocking {
        loadedState { it.geometry.shapes.isNotEmpty() }
        val here = GeoPoint(19.0596, 72.8295)
        locationSource.fix = LocationFix.Found(here, accuracyMeters = 12f)

        viewModel.onUseMyLocation(ApplicationProvider.getApplicationContext())

        assertEquals(here, viewModel.flyTo.value?.point)
        // The fix asks the camera to move; it does not set the ring itself.
        // Whatever the ring reports on arrival is what gets saved, so the two
        // can never disagree — which is what happened when both were written.
        assertFalse(viewModel.hasPinnedLocation.value)
        viewModel.onViewportCenterChanged(here)
        assertEquals(here, viewModel.pinnedLocation.value)

        val state = viewModel.locating.value
        assertTrue("expected a success message, got $state", state is LocatingState.Located)
        assertTrue((state as LocatingState.Located).message.contains("12 m"))
    }

    /**
     * Press it, pan away, press it again from the same doorway.
     *
     * The fix is the same coordinate both times, and a `StateFlow` conflates
     * equal values — so without a token the second press changed nothing the
     * map was keyed on and the camera stayed where the user had panned it,
     * while the fix was reported as accepted. The ring showed one place and
     * Save wrote another.
     */
    @Test
    fun `pressing use my location twice from the same spot flies twice`() = runBlocking {
        loadedState { it.geometry.shapes.isNotEmpty() }
        val here = GeoPoint(19.0596, 72.8295)
        locationSource.fix = LocationFix.Found(here, null)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        viewModel.onUseMyLocation(context)
        val first = viewModel.flyTo.value!!
        viewModel.onViewportCenterChanged(GeoPoint(19.20, 72.90)) // the user pans away
        viewModel.onUseMyLocation(context)
        val second = viewModel.flyTo.value!!

        assertEquals(here, second.point)
        assertTrue("the second press must be a distinct request", first.token != second.token)
        assertTrue("and must therefore be a change the map can see", first != second)
    }

    @Test
    fun `reopening the form does not greet you with the last place's message`() = runBlocking {
        loadedState { it.geometry.shapes.isNotEmpty() }
        locationSource.fix = LocationFix.Found(GeoPoint(19.0596, 72.8295), 12f)
        viewModel.onAddPlaceRequested("First")
        viewModel.onUseMyLocation(ApplicationProvider.getApplicationContext())
        assertTrue(viewModel.locating.value is LocatingState.Located)

        viewModel.onAddPlaceRequested("Second")

        assertEquals(LocatingState.Idle, viewModel.locating.value)
        assertNull(viewModel.flyTo.value)
    }

    /**
     * A perfectly good fix in the wrong city. Flying the camera to the edge of
     * Mumbai would look like a bug rather than an answer, so it says so and
     * leaves the ring where the user put it.
     */
    @Test
    fun `a fix outside Mumbai is refused rather than clamped onto the map`() = runBlocking {
        loadedState { it.geometry.shapes.isNotEmpty() }
        viewModel.onViewportCenterChanged(GeoPoint(19.0176, 72.8562))
        locationSource.fix = LocationFix.Found(GeoPoint(18.5204, 73.8567), null) // Pune

        viewModel.onUseMyLocation(ApplicationProvider.getApplicationContext())

        assertNull("the map must not fly out of the city", viewModel.flyTo.value)
        assertEquals(
            "the ring must stay where the user put it",
            GeoPoint(19.0176, 72.8562),
            viewModel.pinnedLocation.value,
        )
        assertTrue(viewModel.locating.value is LocatingState.Failed)
    }

    @Test
    fun `a refused permission is reported rather than retried forever`() = runBlocking {
        loadedState { it.geometry.shapes.isNotEmpty() }
        locationSource.granted = false

        viewModel.onUseMyLocation(ApplicationProvider.getApplicationContext())

        val state = viewModel.locating.value
        assertTrue("expected a failure, got $state", state is LocatingState.Failed)
        assertNull(viewModel.flyTo.value)
    }

    @Test
    fun `no fix in time says so instead of hanging`() = runBlocking {
        loadedState { it.geometry.shapes.isNotEmpty() }
        locationSource.fix = LocationFix.TimedOut

        viewModel.onUseMyLocation(ApplicationProvider.getApplicationContext())

        assertTrue(viewModel.locating.value is LocatingState.Failed)
    }

    @Test
    fun `closing the form forgets the fix, so reopening it does not fly again`() = runBlocking {
        loadedState { it.geometry.shapes.isNotEmpty() }
        locationSource.fix = LocationFix.Found(GeoPoint(19.0596, 72.8295), null)
        viewModel.onAddPlaceRequested("Somewhere")
        viewModel.onUseMyLocation(ApplicationProvider.getApplicationContext())
        assertNotNull(viewModel.flyTo.value)

        viewModel.onAddPlaceCancelled()

        assertNull(viewModel.flyTo.value)
        assertEquals(LocatingState.Idle, viewModel.locating.value)
    }

    /** The state flow starts with a loading placeholder; wait for real data. */
    private suspend fun loadedState(
        predicate: (ExploreUiState) -> Boolean = { true },
    ): ExploreUiState = withTimeout(20_000) {
        viewModel.uiState.first { !it.isLoading && predicate(it) }
    }

    @Test
    fun `initial state loads the city, its places and its geometry`() = runBlocking {
        val state = loadedState { it.geometry.shapes.isNotEmpty() }

        assertEquals("Mumbai", state.cityName)
        assertEquals(SeedPlaces.total, state.places.size)
        assertTrue(state.geometry.shapes.isNotEmpty())
        assertEquals(0, state.progress.visitedCount)
        assertEquals(0, state.progress.percent)
    }

    @Test
    fun `progress is derived from the same list the map draws`() = runBlocking {
        val (firstId, secondId) = SeedPlaces.ids(2)
        repository.setVisited(firstId, true)
        repository.setVisited(secondId, true)

        val state = loadedState { it.progress.visitedCount == 2 }

        assertEquals(2, state.places.count { it.isVisited })
        assertEquals(2, state.progress.visitedCount)
        assertEquals(SeedPlaces.total, state.progress.totalCount)
    }

    @Test
    fun `toggling visited from the map lights the place and undoes cleanly`() = runBlocking {
        val initial = loadedState()
        val target = SeedPlaces.all.first().id
        val place = initial.places.first { it.id == target }
        assertFalse(place.isVisited)

        viewModel.onToggleVisited(place)
        val lit = loadedState { it.progress.visitedCount == 1 }
        assertTrue(lit.places.first { it.id == target }.isVisited)

        viewModel.onToggleVisited(lit.places.first { it.id == target })
        val dark = loadedState { it.progress.visitedCount == 0 }
        assertFalse(dark.places.first { it.id == target }.isVisited)
    }

    @Test
    fun `toggling wishlist from the map is reflected in progress`() = runBlocking {
        val initial = loadedState()
        val target = SeedPlaces.id(PlaceCategory.CULTURE)
        val place = initial.places.first { it.id == target }

        viewModel.onToggleWishlist(place)

        val state = loadedState { it.progress.wishlistCount == 1 }
        assertTrue(state.places.first { it.id == target }.isWishlisted)
    }

    @Test
    fun `selecting a place on the map exposes it, and dismissing clears it`() = runBlocking {
        val initial = loadedState()
        val target = SeedPlaces.of(PlaceCategory.PARK)
        val place = initial.places.first { it.id == target.id }

        viewModel.onPlaceSelected(place)
        val selected = loadedState { it.selectedPlace != null }
        assertNotNull(selected.selectedPlace)
        assertEquals(target.name, selected.selectedPlace!!.name)

        viewModel.onSelectionDismissed()
        val cleared = loadedState { it.selectedPlace == null }
        assertNull(cleared.selectedPlace)
    }

    @Test
    fun `searching finds places by name, best guess first`() = runBlocking {
        loadedState()

        // A place's own full name has to bring it back first, whatever the
        // dataset currently holds.
        val wanted = SeedPlaces.of(PlaceCategory.TOURIST, 1)
        viewModel.onSearchQueryChange(wanted.name)
        val state = loadedState { it.searchResults.isNotEmpty() }

        assertEquals(wanted.name, state.searchResults.first().name)
    }

    @Test
    fun `search is case insensitive and matches part of a name`() = runBlocking {
        loadedState()

        val wanted = SeedPlaces.of(PlaceCategory.CULTURE)
        // Shouted, and only the first word of the name.
        viewModel.onSearchQueryChange(wanted.name.substringBefore(" ").uppercase())
        val state = loadedState { it.searchResults.isNotEmpty() }

        assertTrue(state.searchResults.any { it.name == wanted.name })
    }

    @Test
    fun `searching by category name finds the places in it`() = runBlocking {
        loadedState()

        viewModel.onSearchQueryChange("Hidden Gem")
        val state = loadedState { it.searchResults.isNotEmpty() }

        assertTrue(state.searchResults.isNotEmpty())
        assertTrue(state.searchResults.all { it.category == PlaceCategory.HIDDEN_GEM })
    }

    @Test
    fun `a long result list is capped rather than burying the map`() = runBlocking {
        loadedState()

        // Matches most of the catalog, so the cap is what decides the size.
        viewModel.onSearchQueryChange("a")
        val state = loadedState { it.searchResults.isNotEmpty() }

        assertEquals(ExploreViewModel.MAX_SEARCH_RESULTS, state.searchResults.size)
    }

    @Test
    fun `an empty box searches for nothing rather than for everything`() = runBlocking {
        loadedState()

        viewModel.onSearchQueryChange("   ")

        assertTrue(loadedState().searchResults.isEmpty())
    }

    @Test
    fun `picking a result selects it and clears the search out of the way`() = runBlocking {
        loadedState()
        val wanted = SeedPlaces.of(PlaceCategory.PARK)
        viewModel.onSearchQueryChange(wanted.name)
        val results = loadedState { it.searchResults.isNotEmpty() }.searchResults

        viewModel.onPlaceSelected(results.first())

        val state = loadedState { it.selectedPlace != null }
        assertEquals(wanted.name, state.selectedPlace!!.name)
        assertEquals("", state.searchQuery)
        assertTrue(state.searchResults.isEmpty())
    }

    @Test
    fun `saving the card records the visit, the rating and the opinion together`() = runBlocking {
        val initial = loadedState()
        val target = SeedPlaces.all.first().id
        val place = initial.places.first { it.id == target }

        viewModel.onSaveVisit(place, visited = true, rating = 4, note = "  Go at dusk.  ")

        val state = loadedState { it.places.first { p -> p.id == target }.rating != null }
        val saved = state.places.first { it.id == target }
        assertTrue(saved.isVisited)
        assertNotNull(saved.visitedAt)
        assertEquals(4, saved.rating)
        assertEquals("Go at dusk.", saved.note)
        assertEquals(1, state.progress.visitedCount)
    }

    @Test
    fun `saving a rating alone does not silently mark the place explored`() = runBlocking {
        val initial = loadedState()
        val target = SeedPlaces.id(PlaceCategory.RESTAURANT)
        val place = initial.places.first { it.id == target }

        viewModel.onSaveVisit(place, visited = false, rating = 3, note = "")

        val state = loadedState { it.places.first { p -> p.id == target }.rating != null }
        val saved = state.places.first { it.id == target }
        assertFalse(saved.isVisited)
        assertEquals(3, saved.rating)
        assertEquals(0, state.progress.visitedCount)
    }

    @Test
    fun `the selected place tracks its own state changes`() = runBlocking {
        val initial = loadedState()
        val target = SeedPlaces.all.first().id
        val place = initial.places.first { it.id == target }
        viewModel.onPlaceSelected(place)

        viewModel.onToggleVisited(place)

        val state = loadedState { it.selectedPlace?.isVisited == true }
        assertTrue(state.selectedPlace!!.isVisited)
    }
}
