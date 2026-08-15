package com.citymemory.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import com.citymemory.data.local.seed.MumbaiSeed
import com.citymemory.data.map.MockMumbaiGeometryProvider
import com.citymemory.domain.model.CityGeometry
import com.citymemory.domain.model.GeoPoint
import com.citymemory.domain.model.Place
import com.citymemory.domain.model.PlaceCategory
import com.citymemory.ui.map.CityMapView
import com.citymemory.ui.map.FlyTarget
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class ScratchC1MapProbeTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var geometry: CityGeometry
    private val reported = mutableListOf<GeoPoint>()

    @Before
    fun setUp() {
        geometry = runBlocking { MockMumbaiGeometryProvider().geometryFor(MumbaiSeed.CITY_ID) }
    }

    private fun mapNode() = compose.onNodeWithContentDescription("Map of the city", substring = true)

    private fun place(id: String, lat: Double, lng: Double, visited: Boolean = false) = Place(
        id = id,
        cityId = MumbaiSeed.CITY_ID,
        name = "Place $id",
        category = PlaceCategory.TOURIST,
        description = "",
        latitude = lat,
        longitude = lng,
        imageUrl = null,
        displayOrder = 0,
        isVisited = visited,
        isWishlisted = false,
        visitedAt = null,
    )

    @Test
    fun `probe H - gestures move the reported centre`() {
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                CityMapView(
                    geometry = geometry,
                    places = listOf(place("a", 19.0760, 72.8777, visited = true)),
                    onViewportCenterChange = { reported += it },
                )
            }
        }
        compose.waitForIdle()
        compose.waitUntil(timeoutMillis = 10_000) { reported.isNotEmpty() }
        println("PROBE-H initial=${reported.lastOrNull()} (n=${reported.size})")

        mapNode().performTouchInput { swipeUp() }
        compose.waitForIdle()
        println("PROBE-H after swipe at min scale=${reported.lastOrNull()} (n=${reported.size})")

        mapNode().performTouchInput { doubleClick() }
        compose.waitForIdle()
        println("PROBE-H after double tap=${reported.lastOrNull()} (n=${reported.size})")

        mapNode().performTouchInput {
            swipe(start = Offset(centerX, centerY + 200f), end = Offset(centerX, centerY - 200f))
        }
        compose.waitForIdle()
        println("PROBE-H after pan when zoomed=${reported.lastOrNull()} (n=${reported.size})")
    }

    @Test
    fun `probe I - flyTo and focusedPlace land the camera`() {
        val target = place("a", 19.0760, 72.8777, visited = true)
        var focused by mutableStateOf<Place?>(null)
        var fly by mutableStateOf<FlyTarget?>(null)
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                CityMapView(
                    geometry = geometry,
                    places = listOf(target),
                    focusedPlace = focused,
                    flyTo = fly,
                    onViewportCenterChange = { reported += it },
                )
            }
        }
        compose.waitForIdle()
        compose.waitUntil(timeoutMillis = 10_000) { reported.isNotEmpty() }
        println("PROBE-I initial=${reported.last()}")

        focused = target
        compose.waitForIdle()
        println("PROBE-I after focusedPlace(19.0760,72.8777)=${reported.last()} n=${reported.size}")

        fly = FlyTarget(GeoPoint(19.2000, 72.9500), 1L)
        compose.waitForIdle()
        println("PROBE-I after flyTo(19.2000,72.9500)=${reported.last()} n=${reported.size}")
    }

    @Test
    fun `probe J - picking moves the anchor and zooms`() {
        var picking by mutableStateOf(false)
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                CityMapView(
                    geometry = geometry,
                    places = emptyList(),
                    pickingLocation = picking,
                    onViewportCenterChange = { reported += it },
                )
            }
        }
        compose.waitForIdle()
        compose.waitUntil(timeoutMillis = 10_000) { reported.isNotEmpty() }
        println("PROBE-J overview centre=${reported.last()}")

        picking = true
        compose.waitForIdle()
        println("PROBE-J picking anchor=${reported.last()} n=${reported.size}")
    }

    @Test
    fun `probe K - the map's only semantics is its content description`() {
        var places by mutableStateOf(listOf(place("a", 19.0760, 72.8777, visited = false)))
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                CityMapView(geometry = geometry, places = places)
            }
        }
        compose.waitForIdle()
        println("PROBE-K before=${
            mapNode().fetchSemanticsNode().config.toString().take(300)
        }")
        places = listOf(place("a", 19.0760, 72.8777, visited = true))
        compose.waitForIdle()
        println("PROBE-K after visit=${
            mapNode().fetchSemanticsNode().config.toString().take(300)
        }")
    }
}
