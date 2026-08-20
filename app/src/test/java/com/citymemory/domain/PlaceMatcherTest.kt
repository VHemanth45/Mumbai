package com.citymemory.domain

import com.citymemory.domain.model.GeoPoint
import com.citymemory.domain.model.Place
import com.citymemory.domain.model.PlaceCategory
import com.citymemory.domain.model.SuggestionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceMatcherTest {

    private var nextId = 0

    private val here = GeoPoint(18.9220, 72.8347)

    private fun north(metres: Double) =
        GeoPoint(here.latitude + metres / GeoPoint.METRES_PER_DEGREE_LATITUDE, here.longitude)

    private fun place(
        name: String,
        at: GeoPoint,
        category: PlaceCategory = PlaceCategory.TOURIST,
    ) = Place(
        id = "p${nextId++}",
        cityId = "mumbai",
        name = name,
        category = category,
        description = "",
        latitude = at.latitude,
        longitude = at.longitude,
        imageUrl = null,
        displayOrder = 0,
        isVisited = false,
        isWishlisted = false,
        visitedAt = null,
    )

    @Test
    fun `nothing in range yields nothing rather than a bad guess`() {
        val far = listOf(place("Far", north(5_000.0)))

        val found = PlaceMatcher.candidatesAt(here, 20f, far, SuggestionSource.DWELL)

        assertTrue(found.isEmpty())
    }

    @Test
    fun `the nearest place wins when categories are equal`() {
        val places = listOf(
            place("Further", north(120.0)),
            place("Nearer", north(30.0)),
        )

        val found = PlaceMatcher.candidatesAt(here, 90f, places, SuggestionSource.DWELL)

        assertEquals("Nearer", found.first().place.name)
        assertEquals(30.0, found.first().distanceMeters, 2.0)
    }

    @Test
    fun `a dwell prefers somewhere you can sit for twenty minutes`() {
        // The park is marginally nearer, but nobody stays 20 minutes at a park
        // bench as often as they stay in the restaurant across the road.
        val places = listOf(
            place("Park", north(60.0), PlaceCategory.PARK),
            place("Restaurant", north(70.0), PlaceCategory.RESTAURANT),
        )

        val found = PlaceMatcher.candidatesAt(here, 60f, places, SuggestionSource.DWELL)

        assertEquals("Restaurant", found.first().place.name)
    }

    @Test
    fun `a photo prefers the landmark over the cafe`() {
        val places = listOf(
            place("Cafe", north(60.0), PlaceCategory.CAFE),
            place("Monument", north(75.0), PlaceCategory.TOURIST),
        )

        val found = PlaceMatcher.candidatesAt(here, 60f, places, SuggestionSource.PHOTO)

        assertEquals("Monument", found.first().place.name)
    }

    @Test
    fun `the prior reorders near neighbours but cannot rescue a far one`() {
        val places = listOf(
            place("Close park", north(40.0), PlaceCategory.PARK),
            place("Distant restaurant", north(200.0), PlaceCategory.RESTAURANT),
        )

        val found = PlaceMatcher.candidatesAt(here, 140f, places, SuggestionSource.DWELL)

        assertEquals("Close park", found.first().place.name)
    }

    @Test
    fun `the reported distance is the true one, not the weighted one`() {
        val places = listOf(place("Restaurant", north(100.0), PlaceCategory.RESTAURANT))

        val found = PlaceMatcher.candidatesAt(here, 90f, places, SuggestionSource.DWELL).first()

        assertEquals(100.0, found.distanceMeters, 2.0)
        assertTrue(found.effectiveDistanceMeters < found.distanceMeters)
    }

    @Test
    fun `a vague fix looks further, but never past the ceiling`() {
        assertEquals(60.0, PlaceMatcher.searchRadiusMeters(5f), 0.001)
        assertEquals(150.0, PlaceMatcher.searchRadiusMeters(100f), 0.001)
        // The ceiling is what the vaguest usable fix yields; vaguer than that
        // is refused outright by `isFixUsable` rather than clamped.
        assertEquals(225.0, PlaceMatcher.searchRadiusMeters(4_000f), 0.001)
        // A fix that says nothing about itself is assumed ordinary.
        assertEquals(90.0, PlaceMatcher.searchRadiusMeters(null), 0.001)
    }

    @Test
    fun `at most the limit comes back, best first`() {
        val places = (1..20).map { place("P$it", north(it * 10.0)) }

        val found = PlaceMatcher.candidatesAt(here, 140f, places, SuggestionSource.PHOTO, limit = 3)

        assertEquals(3, found.size)
        assertEquals(listOf("P1", "P2", "P3"), found.map { it.place.name })
    }

    @Test
    fun `a fix too vague to name a place names none`() {
        // Approximate location on Android 12+ is a circle kilometres across.
        // There is always a nearest place inside it, and naming it would be
        // confidently wrong — which costs more trust than saying nothing.
        val places = listOf(place("Somewhere", north(40.0)))

        val found = PlaceMatcher.candidatesAt(here, 2_000f, places, SuggestionSource.DWELL)

        assertTrue(found.isEmpty())
        assertTrue(PlaceMatcher.isFixUsable(140f))
        assertTrue(PlaceMatcher.isFixUsable(null))
        assertTrue(!PlaceMatcher.isFixUsable(400f))
    }

    @Test
    fun `an empty catalog is not a crash`() {
        assertTrue(
            PlaceMatcher.candidatesAt(here, 20f, emptyList(), SuggestionSource.DWELL).isEmpty(),
        )
    }
}
