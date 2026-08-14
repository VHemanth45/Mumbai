package com.citymemory.domain.model

/**
 * Explorer levels. Thresholds are counts of visited places.
 *
 * Deliberately shallow — the MVP wants a sense of progression, not a metagame.
 */
enum class ExplorerLevel(
    val level: Int,
    val title: String,
    val threshold: Int,
) {
    WANDERER(1, "Wanderer", 0),
    STROLLER(2, "Stroller", 3),
    EXPLORER(3, "Explorer", 8),
    NAVIGATOR(4, "Navigator", 15),
    PATHFINDER(5, "Pathfinder", 25),
    CARTOGRAPHER(6, "Cartographer", 40),
    CITY_KEEPER(7, "City Keeper", 60),
    LUMINARY(8, "Luminary", 80),
    ;

    companion object {
        fun forVisitedCount(visited: Int): ExplorerLevel =
            entries.last { visited >= it.threshold }
    }
}

data class CategoryProgress(
    val category: PlaceCategory,
    val visited: Int,
    val total: Int,
) {
    val fraction: Float get() = if (total == 0) 0f else visited.toFloat() / total
}

/**
 * The headline numbers. Derived from the place list on every emission rather
 * than stored, so it can never drift out of sync with the source of truth.
 */
data class ExplorationProgress(
    val visitedCount: Int,
    val totalCount: Int,
    val wishlistCount: Int,
    val categories: List<CategoryProgress>,
) {
    val fraction: Float get() = if (totalCount == 0) 0f else visitedCount.toFloat() / totalCount

    /** Rounded down, so "100%" only ever appears when the city is genuinely complete. */
    val percent: Int get() = (fraction * 100).toInt()

    val level: ExplorerLevel get() = ExplorerLevel.forVisitedCount(visitedCount)

    /** Visits still needed for the next level, or null at max level. */
    val visitsToNextLevel: Int?
        get() {
            val next = ExplorerLevel.entries.firstOrNull { it.threshold > visitedCount }
            return next?.let { it.threshold - visitedCount }
        }

    companion object {
        val Empty = ExplorationProgress(0, 0, 0, emptyList())
    }
}
