package com.citymemory.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * The user's relationship to a place — and nothing else. No name, no category,
 * no coordinates: place facts live exactly once, in [PlaceEntity].
 *
 * Rows are sparse. A place the user has never touched simply has no row here,
 * which keeps "untouched" and "explicitly un-marked" the same thing.
 *
 * [rating] and [note] are the user's own verdict on a place, and are kept
 * independent of [isVisited]: un-marking a visit is an undo of the visit, not a
 * reason to throw away what they wrote about it.
 */
@Entity(
    tableName = "user_place_state",
    foreignKeys = [
        ForeignKey(
            entity = PlaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["placeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class UserPlaceStateEntity(
    @PrimaryKey val placeId: String,
    val isVisited: Boolean = false,
    val isWishlisted: Boolean = false,
    val visitedAt: Long? = null,
    val wishlistedAt: Long? = null,
    /** 1..5, or null for "not rated". Never 0 — that would be a rating. */
    val rating: Int? = null,
    /** The user's own words. Null and blank both mean "nothing written". */
    val note: String? = null,
)
