package com.citymemory.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.citymemory.data.local.database.CityMemoryDatabase
import com.citymemory.data.local.seed.DatabaseSeeder
import com.citymemory.SeedPlaces
import com.citymemory.data.repository.PlaceRepositoryImpl
import com.citymemory.domain.model.PlaceCategory
import com.citymemory.domain.repository.PlaceRepository
import com.citymemory.ui.navigation.Screen
import com.citymemory.ui.screens.discover.DiscoverScreen
import com.citymemory.ui.screens.discover.DiscoverViewModel
import com.citymemory.ui.screens.place.PlaceDetailScreen
import com.citymemory.ui.screens.place.PlaceDetailViewModel
import com.citymemory.ui.screens.progress.ProgressScreen
import com.citymemory.ui.screens.progress.ProgressViewModel
import com.citymemory.ui.screens.wishlist.WishlistScreen
import com.citymemory.ui.screens.wishlist.WishlistViewModel
import com.citymemory.ui.theme.CityMemoryTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Screen-level tests for the exploration loop, run on Robolectric so the whole
 * suite stays device-free.
 *
 * ExploreScreen is deliberately not rendered here: its map runs a permanent
 * breathing animation, so the Compose test clock never reaches idle and any
 * assertion would hang. Its state is covered by ExploreViewModelTest and its
 * drawing maths by GeoProjectorTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class ScreenInteractionTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var database: CityMemoryDatabase
    private lateinit var repository: PlaceRepository

    /** The catalog is generated, so tests name places through the fixture. */
    private val subject = SeedPlaces.shortNamed
    private val firstName = subject.name
    private val navigationLauncher = RecordingNavigationLauncher()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CityMemoryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PlaceRepositoryImpl(database, DatabaseSeeder(database, SeedPlaces.catalog))
    }

    @After
    fun tearDown() {
        database.close()
    }

    // -- Harness ------------------------------------------------------------

    private fun setScreen(content: @Composable () -> Unit) {
        compose.setContent {
            CityMemoryTheme {
                CompositionLocalProvider(LocalNavigationLauncher provides navigationLauncher) {
                    content()
                }
            }
        }
    }

    // ViewModels are built here, outside the composable lambda, so these read
    // the same way production code does — the screens take a ViewModel
    // parameter precisely so a test can supply one backed by a test database.

    private fun showDiscover(onPlaceClick: (String) -> Unit = {}) {
        val viewModel = DiscoverViewModel(repository)
        setScreen { DiscoverScreen(onPlaceClick = onPlaceClick, viewModel = viewModel) }
    }

    private fun showDetail(placeId: String) {
        val viewModel = PlaceDetailViewModel(
            repository,
            SavedStateHandle(mapOf(Screen.PlaceDetail.ARG_PLACE_ID to placeId)),
        )
        setScreen { PlaceDetailScreen(onBack = {}, viewModel = viewModel) }
    }

    private fun showWishlist() {
        val viewModel = WishlistViewModel(repository)
        setScreen { WishlistScreen(onPlaceClick = {}, viewModel = viewModel) }
    }

    private fun showProgress() {
        val viewModel = ProgressViewModel(repository)
        setScreen { ProgressScreen(viewModel = viewModel) }
    }

    private fun matches(text: String, substring: Boolean = false): Int =
        compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().size

    /** Waits for data to arrive from Room's background executors and render. */
    private fun awaitText(text: String, substring: Boolean = false) {
        compose.waitUntil(timeoutMillis = 20_000) { matches(text, substring) > 0 }
    }

    // -- Discover -----------------------------------------------------------

    @Test
    fun `discover lists the seeded catalog`() {
        showDiscover()

        awaitText(firstName)
        compose.onNodeWithText(firstName).assertIsDisplayed()
        compose.onNodeWithText("${SeedPlaces.total} places waiting to be found")
            .assertIsDisplayed()
    }

    @Test
    fun `searching narrows the catalog to matching places`() {
        showDiscover()
        awaitText(firstName)

        val other = SeedPlaces.of(PlaceCategory.CULTURE)
        compose.onNode(hasSetTextAction()).performTextInput(other.name)

        awaitText(other.name)
        assertEquals(0, matches(firstName))
    }

    @Test
    fun `search also matches a place description`() {
        showDiscover()
        awaitText(firstName)

        // "hectares" only ever appears in a park's generated description, and
        // never in any place's name.
        compose.onNode(hasSetTextAction()).performTextInput("hectares")

        awaitText(SeedPlaces.name(PlaceCategory.PARK))
        assertEquals(0, matches(firstName))
    }

    @Test
    fun `a search with no matches shows the empty state`() {
        showDiscover()
        awaitText(firstName)

        compose.onNode(hasSetTextAction()).performTextInput("zzzznotaplace")

        awaitText("Nothing matches")
        compose.onNodeWithText("Clear filters").assertIsDisplayed()
    }

    @Test
    fun `tapping a place card reports its id for navigation`() {
        var opened: String? = null
        showDiscover(onPlaceClick = { opened = it })
        awaitText(firstName)

        compose.onNodeWithText(firstName).performClick()
        compose.waitForIdle()

        assertEquals(subject.id, opened)
    }

    @Test
    fun `the wishlist toggle flips its own accessibility label`() {
        showDiscover()
        awaitText(firstName)

        compose.onNodeWithContentDescription("Add $firstName to wishlist").performClick()

        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodesWithContentDescription("Remove $firstName from wishlist")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    // -- Place detail -------------------------------------------------------

    @Test
    fun `marking a place visited flips the primary action to explored`() {
        showDetail(subject.id)

        awaitText("Mark as Visited")
        compose.onNodeWithText(firstName).assertIsDisplayed()

        compose.onNodeWithText("Mark as Visited").performClick()

        compose.waitUntil(timeoutMillis = 20_000) { matches("Mark as Visited") == 0 }
        awaitText("Explored", substring = true)
    }

    @Test
    fun `the navigate button hands off to the launcher with the right coordinates`() {
        val place = subject
        showDetail(place.id)
        awaitText("Navigate")

        compose.onNodeWithText("Navigate").performClick()
        compose.waitForIdle()

        assertEquals(
            listOf(
                RecordingNavigationLauncher.Call(
                    place.latitude, place.longitude, place.name,
                ),
            ),
            navigationLauncher.calls,
        )
    }

    @Test
    fun `wishlisting from detail updates the button label`() {
        showDetail(SeedPlaces.id(PlaceCategory.PARK))

        awaitText("Wishlist")
        compose.onNodeWithText("Wishlist").performClick()

        awaitText("Wishlisted")
    }

    // -- Wishlist -----------------------------------------------------------

    @Test
    fun `wishlist shows its empty state before anything is saved`() {
        showWishlist()

        awaitText("Nothing saved yet")
        compose.onNodeWithText("Nothing saved yet").assertIsDisplayed()
    }

    @Test
    fun `wishlist lists saved places and removing one empties it again`(): Unit = runBlocking {
        repository.setWishlisted(SeedPlaces.id(PlaceCategory.CULTURE), true)

        showWishlist()

        awaitText(SeedPlaces.name(PlaceCategory.CULTURE))
        compose.onNodeWithText("TO EXPLORE").assertIsDisplayed()
        compose.onNodeWithText("1 place saved").assertIsDisplayed()

        compose.onNodeWithContentDescription(
            "Remove ${SeedPlaces.name(PlaceCategory.CULTURE)} from wishlist",
        ).performClick()

        awaitText("Nothing saved yet")
    }

    @Test
    fun `a visited wishlist place moves to the explored section`(): Unit = runBlocking {
        repository.setWishlisted(SeedPlaces.id(PlaceCategory.TOURIST, 1), true)
        repository.setVisited(SeedPlaces.id(PlaceCategory.TOURIST, 1), true)

        showWishlist()

        awaitText("ALREADY EXPLORED")
        assertEquals(0, matches("TO EXPLORE"))
    }

    // -- Progress -----------------------------------------------------------

    @Test
    fun `progress starts at zero and reflects visits`(): Unit = runBlocking {
        showProgress()

        awaitText("0 / ${SeedPlaces.total} Places")
        compose.onNodeWithText("0%").assertIsDisplayed()
        compose.onNodeWithText("Explorer Level 1").assertIsDisplayed()

        // Several visits, not one: the headline percentage is rounded down, and
        // a single place in a catalog this size does not reach a whole percent.
        val visits = 5
        SeedPlaces.ids(PlaceCategory.TOURIST, visits).forEach {
            repository.setVisited(it, true)
        }

        awaitText("$visits / ${SeedPlaces.total} Places")
        compose.onNodeWithText("${visits * 100 / SeedPlaces.total}%").assertIsDisplayed()

        // Tourist Places is the first category row, and the visited places are
        // tourist ones, so that row is the one that moved. It sits below the
        // fold, so scroll to it rather than assuming where the page ends.
        val touristRow = "$visits / ${SeedPlaces.countOf(PlaceCategory.TOURIST)}"
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(touristRow))
        compose.onNodeWithText(touristRow).assertIsDisplayed()
    }

    @Test
    fun `achievements unlock from state alone`(): Unit = runBlocking {
        showProgress()
        awaitText("0 / ${SeedPlaces.total} Places")

        compose.onNode(hasScrollAction())
            .performScrollToNode(hasText("ACHIEVEMENTS", substring = true))
        compose.onNodeWithText("ACHIEVEMENTS  ·  0/5").assertIsDisplayed()

        repository.setVisited(subject.id, true)

        awaitText("ACHIEVEMENTS  ·  1/5")
    }
}
