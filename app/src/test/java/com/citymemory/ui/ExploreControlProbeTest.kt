package com.citymemory.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Throwaway control: render the real ExploreScreen exactly the way
 * ScreenInteractionTest renders every other screen, and see whether it hangs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class ExploreControlProbeTest {

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
    fun `explore screen renders under the default clock`() {
        println("PROBE: setContent")
        setScreen { ExploreScreen(onPlaceClick = {}, viewModel = viewModel) }

        println("PROBE: waitUntil for header")
        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodesWithText("Explored").fetchSemanticsNodes().isNotEmpty()
        }
        println("PROBE: header rendered")

        println("PROBE: calling waitForIdle")
        compose.waitForIdle()
        println("PROBE: waitForIdle returned")

        val n = compose.onAllNodesWithText("Explored").fetchSemanticsNodes().size
        println("PROBE: explored nodes = $n")
    }

    /** ExploreScreen -> AddPlaceSheet -> LocationPermission, all four files, one test. */
    @Test
    fun `the add place flow is reachable through the real screen`() {
        setScreen { ExploreScreen(onPlaceClick = {}, viewModel = viewModel) }

        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodesWithText("Explored").fetchSemanticsNodes().isNotEmpty()
        }
        println("PROBE: screen up")

        // Search for something the catalog cannot have.
        compose.onNode(androidx.compose.ui.test.hasSetTextAction())
            .performTextInput("zzzznotaplace")
        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodesWithText("Add “zzzznotaplace”").fetchSemanticsNodes().isNotEmpty()
        }
        println("PROBE: add row shown")

        compose.onAllNodesWithText("Add “zzzznotaplace”")[0].performClick()

        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodesWithText("ADD A PLACE").fetchSemanticsNodes().isNotEmpty()
        }
        println("PROBE: sheet open, clock=${compose.mainClock.currentTime}")

        compose.waitForIdle()
        println("PROBE: waitForIdle after the sheet opened returned")

        // AddPlaceSheet's own rows, incl. the LocationPermission-driven button.
        compose.onNodeWithText("Use my location").assertIsDisplayed()
        compose.onNodeWithContentDescription("The new place goes here").assertIsDisplayed()

        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodesWithText("Drag the map to move the ring")
                .fetchSemanticsNodes().isNotEmpty()
        }
        println("PROBE: pinned row shown")

        compose.onNodeWithText("Cancel").performClick()
        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodesWithText("ADD A PLACE").fetchSemanticsNodes().isEmpty()
        }
        compose.waitForIdle()
        println("PROBE: sheet closed and idle")
    }
}
