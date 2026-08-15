package com.citymemory.ui

import androidx.activity.ComponentActivity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.citymemory.data.local.seed.MumbaiSeed
import com.citymemory.data.map.MockMumbaiGeometryProvider
import com.citymemory.domain.model.CityGeometry

import com.citymemory.domain.model.Place
import com.citymemory.domain.model.PlaceCategory
import com.citymemory.ui.map.CityMapView
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Throwaway probe: does CityMapView actually stop the Compose test clock idling? */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class MapIdleProbeTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var geometry: CityGeometry

    @Before
    fun setUp() {
        geometry = runBlocking {
            MockMumbaiGeometryProvider().geometryFor(MumbaiSeed.CITY_ID)
        }
    }

    @Composable
    private fun breath(): Float =
        rememberInfiniteTransition(label = "breathing").animateFloat(
            initialValue = 0.97f,
            targetValue = 1.03f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 4200, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "breath",
        ).value

    // ---- A: bare infinite transition, value read in composition -------------

    @Test
    fun probeA_bareInfiniteTransition_readInComposition() {
        val start = System.currentTimeMillis()
        compose.setContent {
            val b = breath()
            BasicText("breath=$b", modifier = Modifier.testTag("t"))
        }
        compose.waitForIdle()
        compose.onNodeWithTag("t").assertIsDisplayed()
        println(
            "PROBE_A OK elapsed=${System.currentTimeMillis() - start}ms " +
                "clock=${compose.mainClock.currentTime}",
        )
    }

    // ---- B: infinite transition read conditionally in drawBehind ------------

    @Test
    fun probeB_infiniteTransition_readInDrawBehind() {
        val start = System.currentTimeMillis()
        var draws = 0
        compose.setContent {
            val b = breath()
            val scale by remember { mutableStateOf(1f) }
            Box(
                Modifier
                    .size(300.dp)
                    .drawBehind {
                        val v = if (scale < 4f) b else 1f
                        draws++
                        drawRect(Color.Red, alpha = (0.5f * v).coerceIn(0f, 1f))
                    },
            )
            BasicText("hello", modifier = Modifier.testTag("t"))
        }
        compose.waitForIdle()
        compose.onNodeWithTag("t").assertIsDisplayed()
        println(
            "PROBE_B OK elapsed=${System.currentTimeMillis() - start}ms " +
                "draws=$draws clock=${compose.mainClock.currentTime}",
        )
    }

    // ---- C: the real CityMapView, no places --------------------------------

    @Test
    fun probeC_cityMapView_noPlaces() {
        val start = System.currentTimeMillis()
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                CityMapView(geometry = geometry, places = emptyList())
            }
        }
        compose.waitForIdle()
        println(
            "PROBE_C1 setContent+waitForIdle OK elapsed=${System.currentTimeMillis() - start}ms " +
                "clock=${compose.mainClock.currentTime}",
        )
        val nodes = compose
            .onAllNodesWithContentDescription("Map of the city", substring = true)
            .fetchSemanticsNodes()
        println("PROBE_C2 mapNodes=${nodes.size}")
        compose.waitForIdle()
        println(
            "PROBE_C3 second waitForIdle OK elapsed=${System.currentTimeMillis() - start}ms " +
                "clock=${compose.mainClock.currentTime}",
        )
    }

    // ---- D: the real CityMapView, with visited places ----------------------

    @Test
    fun probeD_cityMapView_withVisitedPlaces() {
        val start = System.currentTimeMillis()
        val places = listOf(
            place("a", 19.0760, 72.8777, visited = true),
            place("b", 19.0176, 72.8562, visited = true),
            place("c", 19.1136, 72.8697, visited = false),
        )
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                CityMapView(geometry = geometry, places = places)
            }
        }
        compose.waitForIdle()
        println(
            "PROBE_D1 OK elapsed=${System.currentTimeMillis() - start}ms " +
                "clock=${compose.mainClock.currentTime}",
        )
        compose
            .onAllNodesWithContentDescription("Map of the city", substring = true)
            .fetchSemanticsNodes()
            .let { println("PROBE_D2 nodes=${it.size} desc=${it.firstOrNull()?.config}") }
    }

    // ---- E: CityMapView with focusedPlace (fly animation) + pickingLocation --

    @Test
    fun probeE_cityMapView_flyAndPicking() {
        val start = System.currentTimeMillis()
        val target = place("a", 19.0760, 72.8777, visited = true)
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                CityMapView(
                    geometry = geometry,
                    places = listOf(target),
                    focusedPlace = target,
                    pickingLocation = true,
                    onViewportCenterChange = { },
                )
            }
        }
        compose.waitForIdle()
        println(
            "PROBE_E OK elapsed=${System.currentTimeMillis() - start}ms " +
                "clock=${compose.mainClock.currentTime}",
        )
    }

    // ---- F: does the Dispatchers.Default projection finish under waitForIdle? -

    @Test
    fun probeF_projectionCompletesUnderWaitForIdle() {
        val start = System.currentTimeMillis()
        val reports = java.util.concurrent.atomic.AtomicInteger(0)
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                CityMapView(
                    geometry = geometry,
                    places = emptyList(),
                    // Only ever invoked once `prepared != null`.
                    onViewportCenterChange = { reports.incrementAndGet() },
                )
            }
        }
        compose.waitForIdle()
        println(
            "PROBE_F1 afterFirstWaitForIdle reports=${reports.get()} " +
                "elapsed=${System.currentTimeMillis() - start}ms clock=${compose.mainClock.currentTime}",
        )
        repeat(5) { compose.waitForIdle() }
        println("PROBE_F2 afterFiveMoreWaitForIdle reports=${reports.get()}")
        Thread.sleep(500)
        compose.waitForIdle()
        println(
            "PROBE_F3 afterSleep500 reports=${reports.get()} clock=${compose.mainClock.currentTime}",
        )
    }

    // ---- G: autoAdvance = false, breathing re-enabled? ----------------------

    @Test
    fun probeG_autoAdvanceFalse_infiniteTransitionLive() {
        compose.mainClock.autoAdvance = false
        val seen = LinkedHashSet<Float>()
        compose.setContent {
            val b = breath()
            seen.add(b)
            BasicText("b=$b", modifier = Modifier.testTag("t"))
        }
        repeat(10) { compose.mainClock.advanceTimeBy(200) }
        println("PROBE_G distinctBreathValues=${seen.size} values=$seen clock=${compose.mainClock.currentTime}")
        compose.mainClock.autoAdvance = true
        val t0 = System.currentTimeMillis()
        compose.waitForIdle()
        println("PROBE_G2 waitForIdleAfterReenable elapsed=${System.currentTimeMillis() - t0}ms")
    }

    private fun place(id: String, lat: Double, lng: Double, visited: Boolean) = Place(
        id = id,
        cityId = MumbaiSeed.CITY_ID,
        name = "Place $id",
        category = PlaceCategory.entries.first(),
        description = "",
        latitude = lat,
        longitude = lng,
        imageUrl = null,
        displayOrder = 0,
        isVisited = visited,
        isWishlisted = false,
        visitedAt = null,
    )
}
