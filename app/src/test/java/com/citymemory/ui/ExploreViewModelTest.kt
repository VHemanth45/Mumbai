package com.citymemory.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.citymemory.data.local.database.CityMemoryDatabase
import com.citymemory.data.local.seed.DatabaseSeeder
import com.citymemory.data.map.MockMumbaiGeometryProvider
import com.citymemory.data.repository.PlaceRepositoryImpl
import com.citymemory.domain.repository.PlaceRepository
import com.citymemory.ui.screens.explore.ExploreUiState
import com.citymemory.ui.screens.explore.ExploreViewModel
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

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CityMemoryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PlaceRepositoryImpl(database, DatabaseSeeder(database))
        viewModel = ExploreViewModel(repository, MockMumbaiGeometryProvider())
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
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
        assertEquals(80, state.places.size)
        assertTrue(state.geometry.shapes.isNotEmpty())
        assertEquals(0, state.progress.visitedCount)
        assertEquals(0, state.progress.percent)
    }

    @Test
    fun `progress is derived from the same list the map draws`() = runBlocking {
        repository.setVisited("gateway-of-india", true)
        repository.setVisited("marine-drive", true)

        val state = loadedState { it.progress.visitedCount == 2 }

        assertEquals(2, state.places.count { it.isVisited })
        assertEquals(2, state.progress.visitedCount)
        assertEquals(80, state.progress.totalCount)
        assertEquals(2, state.progress.percent)
    }

    @Test
    fun `toggling visited from the map lights the place and undoes cleanly`() = runBlocking {
        val initial = loadedState()
        val gateway = initial.places.first { it.id == "gateway-of-india" }
        assertFalse(gateway.isVisited)

        viewModel.onToggleVisited(gateway)
        val lit = loadedState { it.progress.visitedCount == 1 }
        assertTrue(lit.places.first { it.id == "gateway-of-india" }.isVisited)

        viewModel.onToggleVisited(lit.places.first { it.id == "gateway-of-india" })
        val dark = loadedState { it.progress.visitedCount == 0 }
        assertFalse(dark.places.first { it.id == "gateway-of-india" }.isVisited)
    }

    @Test
    fun `toggling wishlist from the map is reflected in progress`() = runBlocking {
        val initial = loadedState()
        val elephanta = initial.places.first { it.id == "elephanta-caves" }

        viewModel.onToggleWishlist(elephanta)

        val state = loadedState { it.progress.wishlistCount == 1 }
        assertTrue(state.places.first { it.id == "elephanta-caves" }.isWishlisted)
    }

    @Test
    fun `selecting a place on the map exposes it, and dismissing clears it`() = runBlocking {
        val initial = loadedState()
        val juhu = initial.places.first { it.id == "juhu-beach" }

        viewModel.onPlaceSelected(juhu)
        val selected = loadedState { it.selectedPlace != null }
        assertNotNull(selected.selectedPlace)
        assertEquals("Juhu Beach", selected.selectedPlace!!.name)

        viewModel.onSelectionDismissed()
        val cleared = loadedState { it.selectedPlace == null }
        assertNull(cleared.selectedPlace)
    }

    @Test
    fun `the selected place tracks its own state changes`() = runBlocking {
        val initial = loadedState()
        val cst = initial.places.first { it.id == "cst" }
        viewModel.onPlaceSelected(cst)

        viewModel.onToggleVisited(cst)

        val state = loadedState { it.selectedPlace?.isVisited == true }
        assertTrue(state.selectedPlace!!.isVisited)
    }
}
