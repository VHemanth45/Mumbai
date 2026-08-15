package com.citymemory.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.printToString
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.citymemory.SeedPlaces
import com.citymemory.data.local.database.CityMemoryDatabase
import com.citymemory.data.local.seed.DatabaseSeeder
import com.citymemory.data.local.seed.MumbaiSeed
import com.citymemory.data.map.MockMumbaiGeometryProvider
import com.citymemory.data.repository.PlaceRepositoryImpl
import com.citymemory.domain.model.PlaceCategory
import com.citymemory.domain.repository.PlaceRepository
import com.citymemory.ui.screens.explore.ExploreScreen
import com.citymemory.ui.screens.explore.ExploreViewModel
import com.citymemory.ui.theme.CityMemoryTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class ScratchC1ProbeTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var database: CityMemoryDatabase
    private lateinit var repository: PlaceRepository
    private lateinit var viewModel: ExploreViewModel

    private val subject = SeedPlaces.shortNamed

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

    private fun show() {
        compose.setContent {
            CityMemoryTheme { ExploreScreen(onPlaceClick = {}, viewModel = viewModel) }
        }
    }

    private fun count(text: String, substring: Boolean = false): Int =
        compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().size

    private fun countDesc(desc: String, substring: Boolean = false): Int =
        compose.onAllNodesWithContentDescription(desc, substring = substring)
            .fetchSemanticsNodes().size

    private fun await(text: String, substring: Boolean = false) {
        compose.waitUntil(timeoutMillis = 30_000) { count(text, substring) > 0 }
    }

    private fun openCardFor(name: String) {
        // Type a prefix, so the search field's own text never equals the name
        // and the only exact match is the result row.
        compose.onNode(hasSetTextAction()).performTextReplacement(name.take(name.length - 2))
        await(name)
        println("PROBE search '$name': matches=${count(name)}, addRow=${count("Add ", true)}")
        compose.onAllNodesWithText(name).onFirst().performClick()
        val opened = runCatching {
            compose.waitUntil(timeoutMillis = 10_000) {
                count("Mark as visited") > 0 || count("I have been here") > 0
            }
        }
        if (opened.isFailure) {
            println("PROBE card did NOT open for '$name'. Tree:\n" +
                compose.onRoot().printToString(maxDepth = 40))
            throw opened.exceptionOrNull()!!
        }
    }

    // ---- E: the visit card rules -------------------------------------------

    @Test
    fun `probe E - visit card dirty rule`() {
        show()
        await("0 / ${SeedPlaces.total} Places")
        openCardFor(subject.name)

        println("PROBE-E card open. Save=${count("Save")}, Saved=${count("Saved")}")
        compose.onNodeWithText("Save").assertIsNotEnabled()

        compose.onNodeWithText("Mark as visited").performClick()
        compose.waitForIdle()
        println("PROBE-E after chip: 'I have been here'=${count("I have been here")}")
        compose.onNodeWithText("Save").assertIsEnabled()

        println("PROBE-E star nodes=${countDesc("Rate 4 out of 5")}")
        compose.onNodeWithContentDescription("Rate 4 out of 5").performClick()
        compose.waitForIdle()
        println("PROBE-E after star: 4/5 shown=${count("4/5")}, clear=${countDesc("Clear rating")}")

        compose.onNodeWithText("Save").performClick()
        await("Saved")
        println("PROBE-E after save: Saved=${count("Saved")}, Save=${count("Save")}")
        compose.onNodeWithText("Saved").assertIsNotEnabled()

        val stored = runBlocking { repository.observePlaces(MumbaiSeed.CITY_ID).first() }
            .first { it.id == subject.id }
        println("PROBE-E stored: visited=${stored.isVisited} rating=${stored.rating}")
    }

    // ---- F: remember(place.id) form reset -----------------------------------

    @Test
    fun `probe F - switching places resets the form`() {
        show()
        await("0 / ${SeedPlaces.total} Places")
        openCardFor(subject.name)

        compose.onAllNodesWithText("What did you think of it?").onFirst()
            .performTextInput("half-typed")
        compose.waitForIdle()
        println("PROBE-F note typed, present=${count("half-typed")}")

        compose.onNodeWithContentDescription("Dismiss").performClick()
        compose.waitForIdle()

        val other = SeedPlaces.of(PlaceCategory.PARK)
        openCardFor(other.name)
        println("PROBE-F switched to '${other.name}': stale note=${count("half-typed")}")
    }

    // ---- G: add-a-place, and whether the map reports a coordinate -----------

    @Test
    fun `probe G - add place form and pinned location`() {
        show()
        await("0 / ${SeedPlaces.total} Places")

        compose.onNode(hasSetTextAction()).performTextReplacement("zzzznotaplace")
        await("No place called", substring = true)
        println("PROBE-G add button present=${count("Add ", substring = true)}")

        compose.onAllNodesWithText("Add ", substring = true).onFirst().performClick()
        await("ADD A PLACE")
        compose.waitForIdle()

        println("PROBE-G waiting-for-map=${count("Waiting for the map")}")
        println("PROBE-G crosshair=${countDesc("The new place goes here")}")
        println("PROBE-G map desc=${
            compose.onAllNodesWithContentDescription("Map of the city", substring = true)
                .fetchSemanticsNodes().size
        }")
        println("PROBE-G tree:\n" + compose.onRoot().printToString(maxDepth = 40))
    }
}
