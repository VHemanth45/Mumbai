package com.citymemory.domain.model

/**
 * The six lenses a city is explored through.
 *
 * [id] is the value persisted in the database. It is deliberately decoupled from
 * the enum constant name so the enum can be renamed without a schema migration,
 * and so a future real dataset can map its own taxonomy onto these ids.
 */
enum class PlaceCategory(
    val id: String,
    val displayName: String,
) {
    TOURIST("tourist", "Tourist Places"),
    CAFE("cafe", "Cafes"),
    RESTAURANT("restaurant", "Restaurants"),
    PARK("park", "Parks"),
    CULTURE("culture", "Culture"),
    HIDDEN_GEM("hidden_gem", "Hidden Gems"),
    ;

    /** Cafes and restaurants together — used by the Foodie achievement. */
    val isFood: Boolean get() = this == CAFE || this == RESTAURANT

    companion object {
        /**
         * Unknown ids degrade to [TOURIST] rather than crashing: a future dataset
         * should never be able to hard-fail the app with one unexpected category.
         */
        fun fromId(id: String): PlaceCategory = entries.firstOrNull { it.id == id } ?: TOURIST
    }
}
