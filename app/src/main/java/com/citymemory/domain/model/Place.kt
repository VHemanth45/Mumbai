package com.citymemory.domain.model

/**
 * A place as the UI needs it: the catalog facts plus this user's state.
 *
 * Note the storage layer keeps these normalized — `places` holds the facts and
 * `user_place_state` holds [isVisited] / [isWishlisted] / [visitedAt], joined on
 * read. This type is a read-model projection, never written back wholesale.
 */
data class Place(
    val id: String,
    val cityId: String,
    val name: String,
    val category: PlaceCategory,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val imageUrl: String?,
    val displayOrder: Int,
    /** Where it is, in words — see [com.citymemory.data.local.entities.PlaceEntity.address]. */
    val address: String? = null,
    /** True for a place the user added because the catalog did not have it. */
    val isUserAdded: Boolean = false,
    val isVisited: Boolean,
    val isWishlisted: Boolean,
    val visitedAt: Long?,
    /** The user's own score, 1..5, or null if they have not rated it. */
    val rating: Int? = null,
    /** The user's own words about the place, or null if they wrote none. */
    val note: String? = null,
) {
    val location: GeoPoint get() = GeoPoint(latitude, longitude)

    /** True once the user has said anything about this place themselves. */
    val hasReview: Boolean get() = rating != null || !note.isNullOrBlank()
}
