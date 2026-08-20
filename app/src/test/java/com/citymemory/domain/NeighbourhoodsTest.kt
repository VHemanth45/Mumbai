package com.citymemory.domain

import com.citymemory.domain.model.GeoPoint
import com.citymemory.domain.model.LabelTier
import com.citymemory.domain.model.MapLabel
import com.citymemory.domain.model.Place
import com.citymemory.domain.model.PlaceCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure JVM tests for the bounded number the headline leans on.
 */
class NeighbourhoodsTest {

    private var nextId = 0

    private fun place(lat: Double, lng: Double, visited: Boolean = true) = Place(
        id = "p${nextId++}",
        cityId = "mumbai",
        name = "Place",
        category = PlaceCategory.TOURIST,
        description = "",
        latitude = lat,
        longitude = lng,
        imageUrl = null,
        displayOrder = 0,
        isVisited = visited,
        isWishlisted = false,
        visitedAt = if (visited) 1L else null,
    )

    private fun area(name: String, lat: Double, lng: Double) =
        MapLabel(tier = LabelTier.AREA, name = name, latitude = lat, longitude = lng)

    // Real coordinates, far enough apart that nothing here turns on rounding.
    private val colaba = area("Colaba", 18.9067, 72.8147)
    private val bandraWest = area("Bandra West", 19.0596, 72.8295)
    private val borivali = area("Borivali", 19.2307, 72.8567)
    private val areas = listOf(colaba, bandraWest, borivali)

    @Test
    fun `only area-tier labels name a neighbourhood`() {
        val labels = areas + MapLabel(LabelTier.PLACE, "Gateway of India", 18.9220, 72.8347)

        assertEquals(3, Neighbourhoods.areasIn(labels).size)
    }

    @Test
    fun `a place belongs to the nearest area`() {
        assertEquals(bandraWest, Neighbourhoods.nearest(GeoPoint(19.0600, 72.8300), areas))
        assertEquals(colaba, Neighbourhoods.nearest(GeoPoint(18.9100, 72.8150), areas))
    }

    @Test
    fun `a place beyond the limit belongs to no area at all`() {
        // Thane, which the catalog reaches into and which has no label of its
        // own. Without the limit it would be counted as Borivali and a weekend
        // out of town would light up a neighbourhood the user never entered.
        assertNull(Neighbourhoods.nearest(GeoPoint(19.2183, 72.9781), areas))
    }

    @Test
    fun `counting is by distinct area, not by place`() {
        val places = listOf(
            place(19.0596, 72.8295),
            place(19.0601, 72.8301),
            place(19.0610, 72.8288),
            place(18.9067, 72.8147),
        )

        assertEquals(2, Neighbourhoods.exploredCount(places, areas))
    }

    @Test
    fun `unvisited places count for nothing`() {
        val places = listOf(
            place(19.0596, 72.8295, visited = false),
            place(18.9067, 72.8147, visited = true),
        )

        assertEquals(1, Neighbourhoods.exploredCount(places, areas))
    }

    @Test
    fun `no areas means no count rather than a crash`() {
        assertEquals(0, Neighbourhoods.exploredCount(listOf(place(19.0, 72.8)), emptyList()))
        assertNull(Neighbourhoods.nearest(GeoPoint(19.0, 72.8), emptyList()))
    }

    @Test
    fun `the summary carries the count and the size of the set`() {
        val places = listOf(place(19.0596, 72.8295), place(18.9067, 72.8147))

        val progress = ExplorationSummarizer.progressOf(places, areas)

        assertEquals(2, progress.neighbourhoodsExplored)
        assertEquals(3, progress.neighbourhoodTotal)
    }

    @Test
    fun `city explorer is measured in neighbourhoods, not in an unreachable percentage`() {
        val places = listOf(place(19.0596, 72.8295), place(18.9067, 72.8147))

        val achievement = ExplorationSummarizer.achievementsOf(places, areas)
            .first { it.id == com.citymemory.domain.model.AchievementId.CITY_EXPLORER }

        assertEquals(2, achievement.progress)
        assertEquals(10, achievement.target)
    }
}
