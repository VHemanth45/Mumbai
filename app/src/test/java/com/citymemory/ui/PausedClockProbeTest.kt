package com.citymemory.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.citymemory.SeedPlaces
import com.citymemory.data.local.database.CityMemoryDatabase
import com.citymemory.data.local.seed.DatabaseSeeder
import com.citymemory.data.map.MockMumbaiGeometryProvider
import com.citymemory.data.repository.PlaceRepositoryImpl
import com.citymemory.domain.repository.PlaceRepository
import com.citymemory.ui.screens.explore.ExploreScreen
import com.citymemory.ui.screens.explore.ExploreViewModel
import com.citymemory.ui.theme.CityMemoryTheme
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Throwaway probe: is the real ExploreScreen assertable when the test clock is
 * paused (`mainClock.autoAdvance = false`) — the documented workaround for
 * infinite animations — with no production change at all?
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class PausedClockProbeTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var database: CityMemoryDatabase
    private lateinit var repository: PlaceRepository
    private lateinit var viewModel: ExploreViewModel

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CityMemoryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PlaceRepositoryImpl(database, DatabaseSeeder(database, SeedPlaces.catalog))
        viewModel = ExploreViewModel(repository, MockMumbaiGeometryProvider(), FakeLocationSource())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun setScreen(content: @Composable () -> Unit) {
        compose.setContent {
            CityMemoryTheme {
                CompositionLocalProvider(
                    LocalNavigationLauncher provides RecordingNavigationLauncher(),
                ) {
                    content()
                }
            }
        }
    }

    @Test
    fun `explore screen is assertable with the clock paused`() {
        compose.mainClock.autoAdvance = false

        setScreen { ExploreScreen(onPlaceClick = {}, viewModel = viewModel) }

        // Pump the clock by hand until the header text appears. No waitForIdle
        // here: fetchSemanticsNodes is what forces the sync.
        var found = 0
        var frames = 0
        for (i in 0 until 600) {
            compose.mainClock.advanceTimeBy(50L)
            Thread.sleep(2)
            frames = i
            found = compose.onAllNodesWithText("Explored").fetchSemanticsNodes().size
            if (found > 0) break
        }

        val all = compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("any") { true })
            .fetchSemanticsNodes()
        println("PROBE: frames=$frames explored=$found totalNodes=${all.size}")
        all.take(25).forEach { node ->
            println("PROBE node: ${node.config}")
        }
        assertTrue("expected the Explore header to render", found > 0)
    }
}
