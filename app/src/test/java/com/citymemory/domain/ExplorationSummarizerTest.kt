package com.citymemory.domain

import com.citymemory.domain.model.AchievementId
import com.citymemory.domain.model.ExplorerLevel
import com.citymemory.domain.model.Place
import com.citymemory.domain.model.PlaceCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for the numbers the whole product is built around.
 */
class ExplorationSummarizerTest {

    private var nextId = 0

    private fun place(
        category: PlaceCategory = PlaceCategory.TOURIST,
        visited: Boolean = false,
        wishlisted: Boolean = false,
    ) = Place(
        id = "p${nextId++}",
        cityId = "mumbai",
        name = "Place",
        category = category,
        description = "",
        latitude = 19.0,
        longitude = 72.8,
        imageUrl = null,
        displayOrder = 0,
        isVisited = visited,
        isWishlisted = wishlisted,
        visitedAt = if (visited) 1L else null,
    )

    @Test
    fun `empty dataset yields empty progress rather than dividing by zero`() {
        val progress = ExplorationSummarizer.progressOf(emptyList())

        assertEquals(0, progress.totalCount)
        assertEquals(0, progress.percent)
        assertEquals(0f, progress.fraction, 0f)
    }

    @Test
    fun `percent counts visited over total`() {
        val places = List(4) { place(visited = true) } + List(16) { place() }

        val progress = ExplorationSummarizer.progressOf(places)

        assertEquals(4, progress.visitedCount)
        assertEquals(20, progress.totalCount)
        assertEquals(20, progress.percent)
    }

    @Test
    fun `percent rounds down so 100 percent means genuinely complete`() {
        val places = List(79) { place(visited = true) } + place(visited = false)

        val progress = ExplorationSummarizer.progressOf(places)

        assertEquals(98, progress.percent)
    }

    @Test
    fun `category progress only includes categories present in the dataset`() {
        val places = listOf(
            place(PlaceCategory.CAFE, visited = true),
            place(PlaceCategory.CAFE),
            place(PlaceCategory.PARK),
        )

        val progress = ExplorationSummarizer.progressOf(places)

        assertEquals(listOf(PlaceCategory.CAFE, PlaceCategory.PARK), progress.categories.map { it.category })
        val cafes = progress.categories.first { it.category == PlaceCategory.CAFE }
        assertEquals(1, cafes.visited)
        assertEquals(2, cafes.total)
    }

    @Test
    fun `wishlist count is independent of visited count`() {
        val places = listOf(
            place(visited = true, wishlisted = true),
            place(wishlisted = true),
            place(visited = true),
        )

        val progress = ExplorationSummarizer.progressOf(places)

        assertEquals(2, progress.visitedCount)
        assertEquals(2, progress.wishlistCount)
    }

    @Test
    fun `explorer level climbs with visit thresholds`() {
        assertEquals(ExplorerLevel.WANDERER, ExplorerLevel.forVisitedCount(0))
        assertEquals(ExplorerLevel.WANDERER, ExplorerLevel.forVisitedCount(2))
        assertEquals(ExplorerLevel.STROLLER, ExplorerLevel.forVisitedCount(3))
        assertEquals(ExplorerLevel.EXPLORER, ExplorerLevel.forVisitedCount(8))
        assertEquals(ExplorerLevel.LUMINARY, ExplorerLevel.forVisitedCount(80))
        assertEquals(ExplorerLevel.LUMINARY, ExplorerLevel.forVisitedCount(1000))
    }

    @Test
    fun `visits to next level is null only at max level`() {
        val nearlyThere = ExplorationSummarizer.progressOf(
            List(7) { place(visited = true) } + List(73) { place() },
        )
        assertEquals(1, nearlyThere.visitsToNextLevel)

        val maxed = ExplorationSummarizer.progressOf(List(80) { place(visited = true) })
        assertEquals(null, maxed.visitsToNextLevel)
    }

    @Test
    fun `first exploration unlocks on a single visit and never exceeds its target`() {
        val none = ExplorationSummarizer.achievementsOf(List(5) { place() })
        assertFalse(none.first { it.id == AchievementId.FIRST_EXPLORATION }.isUnlocked)

        val many = ExplorationSummarizer.achievementsOf(List(5) { place(visited = true) })
        val first = many.first { it.id == AchievementId.FIRST_EXPLORATION }
        assertTrue(first.isUnlocked)
        assertEquals(1, first.progress)
        assertEquals(1f, first.fraction, 0f)
    }

    @Test
    fun `foodie counts cafes and restaurants together`() {
        val places = List(6) { place(PlaceCategory.CAFE, visited = true) } +
            List(4) { place(PlaceCategory.RESTAURANT, visited = true) } +
            List(5) { place(PlaceCategory.PARK, visited = true) }

        val foodie = ExplorationSummarizer.achievementsOf(places)
            .first { it.id == AchievementId.FOODIE }

        assertEquals(10, foodie.progress)
        assertTrue(foodie.isUnlocked)
    }

    @Test
    fun `tourist achievement ignores non-tourist visits`() {
        val places = List(10) { place(PlaceCategory.CAFE, visited = true) } +
            List(4) { place(PlaceCategory.TOURIST, visited = true) }

        val tourist = ExplorationSummarizer.achievementsOf(places)
            .first { it.id == AchievementId.TOURIST }

        assertEquals(4, tourist.progress)
        assertFalse(tourist.isUnlocked)
    }

    @Test
    fun `city explorer unlocks at half the dataset`() {
        fun cityExplorerAt(visited: Int) = ExplorationSummarizer
            .achievementsOf(List(visited) { place(visited = true) } + List(80 - visited) { place() })
            .first { it.id == AchievementId.CITY_EXPLORER }

        assertFalse(cityExplorerAt(39).isUnlocked)
        assertTrue(cityExplorerAt(40).isUnlocked)
    }

    @Test
    fun `achievements are derived, so undoing a visit re-locks them`() {
        val visitedFirst = ExplorationSummarizer.achievementsOf(List(10) { place(visited = true) })
        assertTrue(visitedFirst.first { it.id == AchievementId.EXPLORER }.isUnlocked)

        val thenUndone = ExplorationSummarizer.achievementsOf(
            List(9) { place(visited = true) } + place(),
        )
        assertFalse(thenUndone.first { it.id == AchievementId.EXPLORER }.isUnlocked)
    }
}
