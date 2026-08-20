package com.citymemory.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A visit the app noticed but has not been told it is right about.
 *
 * Its own table rather than columns on [UserPlaceStateEntity], and the reason
 * is the whole design of the automatic-logging features: a suggestion is not a
 * weaker kind of visit, it is a different thing. `user_place_state` means "what
 * the user says about this place", and nothing a sensor guessed belongs in it.
 * Keeping them apart is what guarantees that a bad guess can never appear on
 * the map, in the count, or in an achievement.
 *
 * Resolved rows are kept rather than deleted, which is what stops the detector
 * asking the same question twice — see [status]. They are small, and there will
 * be a few a day at most.
 */
@Entity(
    tableName = "visit_suggestions",
    foreignKeys = [
        ForeignKey(
            entity = PlaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["placeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("placeId"), Index("status")],
)
data class VisitSuggestionEntity(
    @PrimaryKey val id: String,
    val placeId: String,
    /** [com.citymemory.domain.model.SuggestionSource.id] — what noticed it. */
    val source: String,
    /** [com.citymemory.domain.model.SuggestionStatus.id]. */
    val status: String,
    /** When the visit is believed to have happened. */
    val detectedAt: Long,
    /** The evidence's own coordinate: the dwell centroid, or the photo's EXIF fix. */
    val latitude: Double,
    val longitude: Double,
    /**
     * The photo that produced this, for a photo-sourced suggestion.
     *
     * Deliberately the *original* `content://` URI and not a copy in app
     * storage. Copying every candidate would write hundreds of megabytes for
     * suggestions the user is mostly going to dismiss; the bytes are imported
     * through `PhotoStore` only on confirmation. The cost is that the URI can
     * go stale — the user deletes the photo, or the grant lapses — which is why
     * confirmation treats a failed import as non-fatal and still logs the visit.
     */
    val photoUri: String?,
    val createdAt: Long,
    /** When the user answered, or null while [status] is pending. */
    val resolvedAt: Long?,
)
