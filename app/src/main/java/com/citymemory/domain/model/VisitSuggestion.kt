package com.citymemory.domain.model

/**
 * Something the app thinks you did, waiting for you to say whether it is true.
 *
 * The whole point of both automatic-logging features is that they never write a
 * visit themselves. A visit is a memory, and a memory the user did not agree to
 * is worse than no memory: it corrupts the one record the product promises to
 * keep. So detection produces one of these, the user confirms or dismisses it,
 * and only a confirmation touches `user_place_state`.
 *
 * That also means a wrong guess is cheap. The detector can be generous — offer
 * the cafe *and* the restaurant upstairs — because the cost of being wrong is a
 * dismissed card rather than a false entry in the journal.
 */
/**
 * A pending suggestion as it is *stored* — a place id, not a place.
 *
 * Kept separate from [VisitSuggestion] so that reading the pending list costs
 * one small query rather than a second copy of the 31,657-row place stream.
 * The screen already holds that stream; joining onto it in memory is a hash
 * lookup, whereas re-observing it in the repository would have Room run the
 * whole catalog query twice on every visit, rating and wishlist change.
 */
data class PendingSuggestion(
    val id: String,
    val placeId: String,
    val source: SuggestionSource,
    val detectedAt: Long,
    val location: GeoPoint,
    val photoUri: String?,
)

data class VisitSuggestion(
    val id: String,
    val place: Place,
    val source: SuggestionSource,
    /** When the app thinks the visit happened, not when it noticed. */
    val detectedAt: Long,
    /** Where the evidence put them — the dwell anchor, or the photo's EXIF fix. */
    val location: GeoPoint,
    /**
     * The photo that produced this, for [SuggestionSource.PHOTO].
     *
     * Held as a string rather than an `android.net.Uri` so this stays in the
     * Android-free domain, the same call [PlaceRepository.addPhoto] makes. It
     * is attached to the place on confirmation, so confirming a photo
     * suggestion both logs the visit and files the picture in one action.
     */
    val photoUri: String?,
    /**
     * How far the place was from the evidence. Shown to the user, because
     * "40 m away" and "180 m away" deserve different amounts of trust.
     */
    val distanceMeters: Double,
)

/**
 * Joins stored suggestions onto the place list the screen already has.
 *
 * Pure, so the rules about what is *not* shown — a place visited by some other
 * route since the row was written, a row whose place has gone — are testable
 * without a database.
 */
object VisitSuggestions {

    fun join(places: List<Place>, pending: List<PendingSuggestion>): List<VisitSuggestion> {
        if (pending.isEmpty()) return emptyList()
        val byId = places.associateBy { it.id }
        return pending.mapNotNull { row ->
            val place = byId[row.placeId] ?: return@mapNotNull null
            // Visited some other way since the row was written, so there is no
            // question left. The row is tidied when the user next touches the
            // place; it simply is not shown in the meantime.
            if (place.isVisited) return@mapNotNull null
            VisitSuggestion(
                id = row.id,
                place = place,
                source = row.source,
                detectedAt = row.detectedAt,
                location = row.location,
                photoUri = row.photoUri,
                distanceMeters = row.location.distanceTo(place.location),
            )
        }
    }
}

/** What noticed the visit. */
enum class SuggestionSource(val id: String) {
    /** The user stayed inside a small radius for long enough to have been somewhere. */
    DWELL("dwell"),

    /** A photo carried a coordinate and a time. */
    PHOTO("photo"),
    ;

    companion object {
        fun fromId(id: String): SuggestionSource = entries.firstOrNull { it.id == id } ?: DWELL
    }
}

/**
 * Where a suggestion has got to.
 *
 * Dismissals are kept rather than deleted, which is the only reason the same
 * cafe does not get offered again on the next sample: "no" has to be a fact the
 * detector can read, or the feature becomes a machine for re-asking.
 */
enum class SuggestionStatus(val id: String) {
    PENDING("pending"),
    CONFIRMED("confirmed"),
    DISMISSED("dismissed"),
    ;

    companion object {
        fun fromId(id: String): SuggestionStatus = entries.firstOrNull { it.id == id } ?: PENDING
    }
}
