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
    val isVisited: Boolean,
    val isWishlisted: Boolean,
    val visitedAt: Long?,
) {
    val location: GeoPoint get() = GeoPoint(latitude, longitude)
}
