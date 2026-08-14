package com.citymemory.data.local.entities

import androidx.room.Embedded
import androidx.room.Relation

/**
 * A place joined with its (optional) user state. [state] is null until the user
 * first wishlists or visits the place.
 */
data class PlaceWithState(
    @Embedded val place: PlaceEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "placeId",
    )
    val state: UserPlaceStateEntity?,
)
