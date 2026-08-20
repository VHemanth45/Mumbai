package com.citymemory.ui.components

import com.citymemory.domain.model.ExplorationProgress

/**
 * The copy under the headline count, shared by Explore and Progress so the two
 * cannot drift apart.
 *
 * The headline itself is the number of places explored, with no denominator.
 * The catalog total belongs on Discover, where "31,657 places waiting to be
 * found" is an invitation; beside a count of what you have done it is a
 * verdict, and an unreachable one — the share of the city visited rounds to 0%
 * until the 317th place.
 *
 * So the bounded number here is neighbourhoods, which is finite, reachable and
 * the unit people use to describe where they have been.
 */
fun explorationSubtitle(progress: ExplorationProgress): String {
    if (progress.visitedCount == 0) return "Go somewhere and watch it light up"

    val parts = buildList {
        if (progress.neighbourhoodTotal > 0) {
            add(
                "across ${progress.neighbourhoodsExplored} of " +
                    "${progress.neighbourhoodTotal} neighbourhoods",
            )
        }
        if (progress.wishlistCount > 0) add("${progress.wishlistCount} wishlisted")
    }
    return parts.joinToString("   ·   ")
}

/** "12 Places Explored", singular where it should be. */
fun explorationHeadlineLabel(progress: ExplorationProgress): String =
    if (progress.visitedCount == 1) "Place Explored" else "Places Explored"

/**
 * What the headline bar is saying, for a screen reader.
 *
 * The bar shows progress through the current explorer level rather than the
 * share of the city visited, so this has to name the level — an unlabelled bar
 * that is 40% full is otherwise 40% of nothing in particular.
 */
fun explorationBarDescription(progress: ExplorationProgress): String {
    val level = progress.level
    val remaining = progress.visitsToNextLevel
        ?: return "Level ${level.level}, ${level.title}. Highest level reached."
    val places = if (remaining == 1) "place" else "places"
    return "Level ${level.level}, ${level.title}. $remaining more $places to level up."
}

/**
 * The headline as one sentence, for a screen reader.
 *
 * The count and its label are two `Text`s so they can be sized differently,
 * which without this reads out as "22" and then, separately, "Places Explored".
 * Merged into one node they say the thing they look like they say.
 */
fun explorationHeadlineDescription(progress: ExplorationProgress): String =
    "${progress.visitedCount} ${explorationHeadlineLabel(progress)}"
