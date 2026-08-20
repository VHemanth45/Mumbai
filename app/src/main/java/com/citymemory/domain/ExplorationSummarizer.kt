package com.citymemory.domain

import com.citymemory.domain.model.Achievement
import com.citymemory.domain.model.AchievementId
import com.citymemory.domain.model.CategoryProgress
import com.citymemory.domain.model.ExplorationProgress
import com.citymemory.domain.model.MapLabel
import com.citymemory.domain.model.Place
import com.citymemory.domain.model.PlaceCategory

/**
 * Turns the raw place list into the derived numbers the app displays.
 *
 * Pure Kotlin with no Android or Room dependency, so all of it is unit-testable
 * on the JVM — which matters, because these are the numbers the whole product
 * is built around.
 */
object ExplorationSummarizer {

    /**
     * [areas] are the city's named areas, from `CityGeometry.labels`. Passing
     * none is allowed and simply leaves the neighbourhood count at zero, which
     * is what a caller that has not loaded the map geometry yet should show.
     */
    fun progressOf(
        places: List<Place>,
        areas: List<MapLabel> = emptyList(),
    ): ExplorationProgress {
        if (places.isEmpty()) return ExplorationProgress.Empty

        val byCategory = places.groupBy { it.category }
        val categories = PlaceCategory.entries.mapNotNull { category ->
            val inCategory = byCategory[category] ?: return@mapNotNull null
            CategoryProgress(
                category = category,
                visited = inCategory.count { it.isVisited },
                total = inCategory.size,
            )
        }

        return ExplorationProgress(
            visitedCount = places.count { it.isVisited },
            totalCount = places.size,
            wishlistCount = places.count { it.isWishlisted },
            categories = categories,
            neighbourhoodsExplored = Neighbourhoods.exploredCount(places, areas),
            neighbourhoodTotal = areas.size,
        )
    }

    fun achievementsOf(
        places: List<Place>,
        areas: List<MapLabel> = emptyList(),
    ): List<Achievement> {
        val visited = places.filter { it.isVisited }
        val visitedTourist = visited.count { it.category == PlaceCategory.TOURIST }
        val visitedFood = visited.count { it.category.isFood }
        val neighbourhoods = Neighbourhoods.exploredCount(places, areas)

        return listOf(
            Achievement(
                id = AchievementId.FIRST_EXPLORATION,
                title = "First Exploration",
                description = "Visit your first place",
                progress = visited.size.coerceAtMost(1),
                target = 1,
            ),
            Achievement(
                id = AchievementId.EXPLORER,
                title = "Explorer",
                description = "Visit 10 places",
                progress = visited.size,
                target = 10,
            ),
            Achievement(
                id = AchievementId.TOURIST,
                title = "Tourist",
                description = "Visit 5 tourist places",
                progress = visitedTourist,
                target = 5,
            ),
            Achievement(
                id = AchievementId.FOODIE,
                title = "Foodie",
                description = "Visit 10 cafes and restaurants",
                progress = visitedFood,
                target = 10,
            ),
            // Was "reach 50% exploration", which against a catalog of 31,657
            // places meant visiting 15,829 of them — an achievement no one
            // could ever unlock, sitting permanently at 0 on the progress
            // screen. Neighbourhoods are the same idea at a reachable scale.
            Achievement(
                id = AchievementId.CITY_EXPLORER,
                title = "City Explorer",
                description = "Explore 10 neighbourhoods",
                progress = neighbourhoods,
                target = 10,
            ),
        )
    }
}
