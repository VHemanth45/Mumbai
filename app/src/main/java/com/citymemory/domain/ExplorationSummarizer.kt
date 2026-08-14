package com.citymemory.domain

import com.citymemory.domain.model.Achievement
import com.citymemory.domain.model.AchievementId
import com.citymemory.domain.model.CategoryProgress
import com.citymemory.domain.model.ExplorationProgress
import com.citymemory.domain.model.Place
import com.citymemory.domain.model.PlaceCategory
import kotlin.math.roundToInt

/**
 * Turns the raw place list into the derived numbers the app displays.
 *
 * Pure Kotlin with no Android or Room dependency, so all of it is unit-testable
 * on the JVM — which matters, because these are the numbers the whole product
 * is built around.
 */
object ExplorationSummarizer {

    fun progressOf(places: List<Place>): ExplorationProgress {
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
        )
    }

    fun achievementsOf(places: List<Place>): List<Achievement> {
        val visited = places.filter { it.isVisited }
        val visitedTourist = visited.count { it.category == PlaceCategory.TOURIST }
        val visitedFood = visited.count { it.category.isFood }

        // Expressed in whole percent so the bar fills smoothly rather than snapping.
        val explorationPercent = if (places.isEmpty()) {
            0
        } else {
            (visited.size.toFloat() / places.size * 100).roundToInt()
        }

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
            Achievement(
                id = AchievementId.CITY_EXPLORER,
                title = "City Explorer",
                description = "Reach 50% exploration",
                progress = explorationPercent,
                target = 50,
            ),
        )
    }
}
