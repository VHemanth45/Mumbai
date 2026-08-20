package com.citymemory.data.photo

import android.util.Log
import com.citymemory.domain.PhotoVisit
import com.citymemory.domain.PhotoVisitGrouper
import com.citymemory.domain.PlaceMatcher
import com.citymemory.domain.model.SuggestionSource
import com.citymemory.domain.repository.PlaceRepository
import kotlinx.coroutines.flow.first

/**
 * Turns a handful of photographs into questions about where you were.
 *
 * This is the whole of photo-first logging, and it is deliberately thin,
 * because everything it does is somebody else's tested logic:
 * [PhotoLocationReader] gets a coordinate out of each file,
 * [PhotoVisitGrouper] decides which photographs were the same outing,
 * [PlaceMatcher] decides what was there, and `recordSuggestion` decides which
 * of those are worth asking about.
 *
 * It is also the backfill. Selecting a year of photographs and selecting the
 * three from lunch run through exactly this path — the grouper does not care
 * whether the gap between two photographs is nine minutes or nine months — so
 * there is no separate "import my history" code to keep working.
 */
class PhotoVisitImporter(
    private val repository: PlaceRepository,
    private val locationReader: PhotoLocationReader,
) {

    /**
     * Reads [uris], groups them, and records what it can.
     *
     * Returns a summary rather than throwing, because every one of these
     * outcomes is normal and the user deserves to be told which one happened.
     * "Nothing was added" means something quite different when the photos had
     * no location than when they were all of places already visited.
     */
    suspend fun import(cityId: String, uris: List<String>): PhotoImportResult {
        if (uris.isEmpty()) return PhotoImportResult()

        val located = uris.mapNotNull { uri ->
            runCatching { locationReader.read(uri) }
                .onFailure { Log.w(TAG, "could not read $uri", it) }
                .getOrNull()
        }
        val withoutLocation = uris.size - located.size
        if (located.isEmpty()) {
            return PhotoImportResult(photosSeen = uris.size, photosWithoutLocation = withoutLocation)
        }

        val visits = PhotoVisitGrouper.group(located)
        // Read once for the whole batch rather than per visit: a year of photos
        // can be dozens of groups, and the catalog is 31,657 rows.
        val places = repository.observePlaces(cityId).first()

        var suggested = 0
        var unmatched = 0
        var alreadyKnown = 0

        for (visit in visits) {
            val best = PlaceMatcher.candidatesAt(
                point = visit.center,
                // Photo EXIF has no accuracy field, so the matcher's own
                // assumption is the honest input here rather than a number
                // invented at this layer.
                accuracyMeters = null,
                places = places,
                source = SuggestionSource.PHOTO,
            ).firstOrNull()

            if (best == null) {
                unmatched++
                continue
            }

            val id = repository.recordSuggestion(
                placeId = best.place.id,
                source = SuggestionSource.PHOTO,
                // When the visit happened, which for a backfill is months ago —
                // not now. It is what the card shows and what makes a year of
                // history read as history.
                detectedAt = visit.startedAt,
                latitude = visit.center.latitude,
                longitude = visit.center.longitude,
                photoUri = visit.coverPhoto.uri,
            )
            if (id != null) suggested++ else alreadyKnown++
        }

        return PhotoImportResult(
            photosSeen = uris.size,
            photosWithoutLocation = withoutLocation,
            visitsFound = visits.size,
            suggested = suggested,
            unmatched = unmatched,
            alreadyKnown = alreadyKnown,
        )
    }

    private companion object {
        const val TAG = "PhotoVisitImporter"
    }
}

/**
 * What came of an import, in enough detail to explain it.
 *
 * Every field is a different reason a photograph did not become a question,
 * because "we found nothing" is the answer the user is most likely to get and
 * the least useful thing to say to them.
 */
data class PhotoImportResult(
    val photosSeen: Int = 0,
    /** Screenshots, saved images, anything sent through a messaging app. */
    val photosWithoutLocation: Int = 0,
    val visitsFound: Int = 0,
    /** New questions the user will be asked. */
    val suggested: Int = 0,
    /** Grouped fine, but nothing in the catalog is near enough to name. */
    val unmatched: Int = 0,
    /** Already visited, already asked, or asked and dismissed recently. */
    val alreadyKnown: Int = 0,
) {
    val hasAnythingToShow: Boolean get() = suggested > 0
}
