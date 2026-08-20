package com.citymemory.data.repository

import android.net.Uri
import androidx.room.withTransaction
import com.citymemory.data.local.database.CityMemoryDatabase
import com.citymemory.data.local.entities.PlaceEntity
import com.citymemory.data.local.entities.PlacePhotoEntity
import com.citymemory.data.local.entities.UserPlaceStateEntity
import com.citymemory.data.local.entities.VisitSuggestionEntity
import com.citymemory.data.local.seed.DatabaseSeeder
import com.citymemory.data.mapper.toDomain
import com.citymemory.data.photo.NoPhotoStore
import com.citymemory.data.photo.PhotoStore
import com.citymemory.domain.model.City
import com.citymemory.domain.model.Place
import com.citymemory.domain.model.PlaceCategory
import com.citymemory.domain.model.PlacePhoto
import com.citymemory.domain.model.SuggestionSource
import com.citymemory.domain.model.SuggestionStatus
import com.citymemory.domain.model.PendingSuggestion
import com.citymemory.domain.repository.PlaceRepository
import com.citymemory.domain.model.GeoPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.util.UUID
import kotlinx.coroutines.flow.map

class PlaceRepositoryImpl(
    private val database: CityMemoryDatabase,
    private val seeder: DatabaseSeeder,
    /**
     * Where photo bytes go. Defaulted to a store that refuses every import, so
     * the many tests that do not care about photos need not supply one and
     * cannot accidentally write files during a unit test.
     */
    private val photoStore: PhotoStore = NoPhotoStore,
    /** Injected so visit timestamps are deterministic under test. */
    private val now: () -> Long = System::currentTimeMillis,
) : PlaceRepository {

    override fun observeCity(cityId: String): Flow<City?> = flow {
        seeder.seedIfNeeded()
        emitAll(database.cityDao().observeCity(cityId).map { it?.toDomain() })
    }

    override fun observePlaces(cityId: String): Flow<List<Place>> = flow {
        seeder.seedIfNeeded()
        emitAll(
            database.placeDao()
                .observePlacesWithState(cityId)
                .map { rows -> rows.map { it.toDomain() } },
        )
    }

    override fun observePlace(placeId: String): Flow<Place?> = flow {
        seeder.seedIfNeeded()
        emitAll(database.placeDao().observePlaceWithState(placeId).map { it?.toDomain() })
    }

    override suspend fun setVisited(placeId: String, isVisited: Boolean) {
        mutateState(placeId) { current ->
            current.copy(
                isVisited = isVisited,
                // Keep the original timestamp on a repeat mark; clear it on undo,
                // so visitedAt always means "when this place was first explored".
                visitedAt = if (isVisited) current.visitedAt ?: now() else null,
            )
        }
        // Marking a place visited by hand answers anything the detector was
        // about to ask about it. Leaving the question pending would mean a
        // notification, minutes later, about somewhere the user has just
        // finished telling us they went.
        if (isVisited) {
            database.visitSuggestionDao().dismissPendingFor(placeId, now())
        }
    }

    override suspend fun setWishlisted(placeId: String, isWishlisted: Boolean) {
        mutateState(placeId) { current ->
            current.copy(
                isWishlisted = isWishlisted,
                wishlistedAt = if (isWishlisted) current.wishlistedAt ?: now() else null,
            )
        }
    }

    override suspend fun setReview(placeId: String, rating: Int?, note: String?) {
        // A blank box and an untouched one mean the same thing, and storing the
        // difference would make `hasReview` true for an empty string.
        val cleaned = note?.trim()?.takeIf { it.isNotEmpty() }
        mutateState(placeId) { current ->
            current.copy(
                rating = rating?.coerceIn(MIN_RATING, MAX_RATING),
                note = cleaned,
            )
        }
    }

    override suspend fun addUserPlace(
        cityId: String,
        name: String,
        category: PlaceCategory,
        latitude: Double,
        longitude: Double,
        address: String?,
        markVisited: Boolean,
    ): String {
        // The city row has to exist before a place can point at it.
        seeder.seedIfNeeded()
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "a place needs a name" }
        val cleanAddress = address?.trim()?.takeIf { it.isNotEmpty() }

        val placeDao = database.placeDao()
        val id = uniqueId(cleanName)
        val entity = PlaceEntity(
            id = id,
            cityId = cityId,
            name = cleanName,
            category = category.id,
            // Written rather than left blank so the detail screen reads the
            // same as it does for a catalogued place. The address is the only
            // thing here the user actually told us, so it is the only thing
            // this says.
            description = cleanAddress?.let { "Added by you. $it." } ?: "Added by you.",
            latitude = latitude,
            longitude = longitude,
            imageUrl = null,
            displayOrder = (placeDao.lowestDisplayOrder(cityId) ?: 0) - 1,
            address = cleanAddress,
            isUserAdded = true,
        )

        database.withTransaction {
            placeDao.upsert(entity)
            if (markVisited) {
                database.userPlaceStateDao().upsert(
                    UserPlaceStateEntity(placeId = id, isVisited = true, visitedAt = now()),
                )
            }
        }
        return id
    }

    override suspend fun deleteUserPlace(placeId: String) {
        seeder.seedIfNeeded()
        // Read the file names before the cascade takes their rows. SQLite will
        // remove the rows and knows nothing whatever about the JPEGs beside
        // them, so this is the only moment anything still knows what to delete.
        val files = database.placePhotoDao().photosFor(placeId).map { it.fileName }
        database.placeDao().deleteUserPlace(placeId)
        files.forEach { photoStore.delete(it) }
    }

    override suspend fun setAddress(placeId: String, address: String?) {
        seeder.seedIfNeeded()
        database.placeDao().updateAddress(placeId, address?.trim()?.takeIf { it.isNotEmpty() })
    }

    override fun observePhotos(placeId: String): Flow<List<PlacePhoto>> =
        database.placePhotoDao().observePhotos(placeId).map { rows ->
            rows.map {
                PlacePhoto(
                    id = it.id,
                    placeId = it.placeId,
                    // Resolved on read, so the stored row is only ever a name
                    // and the app's data directory is free to move.
                    path = photoStore.fileFor(it.fileName).absolutePath,
                    addedAt = it.addedAt,
                )
            }
        }

    override suspend fun addPhoto(placeId: String, sourceUri: String): Boolean {
        seeder.seedIfNeeded()
        // Checked before the copy, so a refused photo costs no disk and no
        // decode. The cap exists because nothing else bounds this: a place with
        // two hundred photos is ~80 MB of app storage and a strip nobody can
        // scroll, and neither failure announces itself.
        if (database.placePhotoDao().countFor(placeId) >= MAX_PHOTOS_PER_PLACE) return false
        // The copy happens before the row is written, so a failed import leaves
        // nothing behind pointing at a file that was never created.
        val fileName = photoStore.import(Uri.parse(sourceUri)) ?: return false
        database.placePhotoDao().insert(
            PlacePhotoEntity(
                id = "$PHOTO_ID_PREFIX${UUID.randomUUID()}",
                placeId = placeId,
                fileName = fileName,
                addedAt = now(),
            ),
        )
        return true
    }

    override fun observePendingSuggestions(): Flow<List<PendingSuggestion>> =
        database.visitSuggestionDao().observePending().map { rows ->
            rows.map { row ->
                PendingSuggestion(
                    id = row.id,
                    placeId = row.placeId,
                    source = SuggestionSource.fromId(row.source),
                    detectedAt = row.detectedAt,
                    location = GeoPoint(row.latitude, row.longitude),
                    photoUri = row.photoUri,
                )
            }
        }

    override suspend fun recordSuggestion(
        placeId: String,
        source: SuggestionSource,
        detectedAt: Long,
        latitude: Double,
        longitude: Double,
        photoUri: String?,
    ): String? {
        seeder.seedIfNeeded()
        if (!database.placeDao().exists(placeId)) return null

        // Already been there. The data model records a place as visited or not,
        // with no repeat visits, so there is nothing left to ask.
        if (database.userPlaceStateDao().getState(placeId)?.isVisited == true) return null

        val latest = database.visitSuggestionDao().latestFor(placeId)
        if (latest != null) {
            val age = now() - latest.detectedAt
            val suppressed = when (SuggestionStatus.fromId(latest.status)) {
                // One open question per place. A second card for the same cafe
                // is not more information, it is the same question twice.
                SuggestionStatus.PENDING -> true
                // "No" has a shelf life. Re-asking tomorrow is nagging;
                // never asking again would mean one mis-tap silently disables
                // the feature for that place forever.
                SuggestionStatus.DISMISSED -> age < RESUGGEST_AFTER_DISMISS_MILLIS
                // Confirmed rows are normally caught by the visited check
                // above; this covers a confirmation the user has since undone.
                SuggestionStatus.CONFIRMED -> age < RESUGGEST_AFTER_CONFIRM_MILLIS
            }
            if (suppressed) return null
        }

        val id = "$SUGGESTION_ID_PREFIX${UUID.randomUUID()}"
        database.visitSuggestionDao().insert(
            VisitSuggestionEntity(
                id = id,
                placeId = placeId,
                source = source.id,
                status = SuggestionStatus.PENDING.id,
                detectedAt = detectedAt,
                latitude = latitude,
                longitude = longitude,
                photoUri = photoUri,
                createdAt = now(),
                resolvedAt = null,
            ),
        )
        return id
    }

    override suspend fun confirmSuggestion(suggestionId: String) {
        val suggestion = database.visitSuggestionDao().get(suggestionId) ?: return
        if (SuggestionStatus.fromId(suggestion.status) != SuggestionStatus.PENDING) return

        // Status first, and the visit second on purpose: marking a place
        // visited dismisses whatever is still pending for it, and this row must
        // already be out of that set or it would resolve itself as dismissed.
        database.visitSuggestionDao()
            .setStatus(suggestionId, SuggestionStatus.CONFIRMED.id, now())

        // **Dated when it happened, not when it was confirmed.** This is the
        // whole of what makes importing a camera roll worth doing. `setVisited`
        // stamps `now()`, which is right for a person standing in the place and
        // catastrophic for a backfill: confirm two years of photographs through
        // it and every one of those visits is dated today, the timeline
        // collapses into a single afternoon, and nothing in the database
        // remembers otherwise well enough to undo it.
        mutateState(suggestion.placeId) { current ->
            current.copy(
                isVisited = true,
                // Still the earliest known visit rather than blindly the
                // suggestion's own date: confirming an older photo for a place
                // already visited should move the date back, never forward.
                visitedAt = minOf(current.visitedAt ?: suggestion.detectedAt, suggestion.detectedAt),
            )
        }
        database.visitSuggestionDao().dismissPendingFor(suggestion.placeId, now())

        // Deliberately last, and deliberately unchecked. The visit is the fact
        // worth keeping; a photo whose grant lapsed while the notification sat
        // in the shade must not take it down with it.
        suggestion.photoUri?.let { addPhoto(suggestion.placeId, it) }
    }

    override suspend fun dismissSuggestion(suggestionId: String) {
        database.visitSuggestionDao()
            .setStatus(suggestionId, SuggestionStatus.DISMISSED.id, now())
    }

    override suspend fun pendingSuggestionCount(): Int =
        database.visitSuggestionDao().pendingCount()

    override suspend fun deletePhoto(photoId: String) {
        val photo = database.placePhotoDao().photo(photoId) ?: return
        // Row first: an orphaned file is swept up later, whereas a row pointing
        // at a file that is gone is a broken thumbnail on the screen now.
        database.placePhotoDao().delete(photoId)
        photoStore.delete(photo.fileName)
    }

    /**
     * A readable id that is not already taken.
     *
     * Slugged from the name rather than a UUID so a row stays recognisable in a
     * database dump, and suffixed on collision because two places called "Home"
     * is an ordinary thing for one person to add.
     */
    private suspend fun uniqueId(name: String): String {
        val base = "$USER_PLACE_PREFIX${slugify(name)}"
        if (!database.placeDao().exists(base)) return base
        var suffix = 2
        while (database.placeDao().exists("$base-$suffix")) suffix++
        return "$base-$suffix"
    }

    /**
     * Read-modify-write inside a transaction. Two toggles racing on the same
     * place therefore serialize instead of clobbering each other's field.
     */
    private suspend fun mutateState(
        placeId: String,
        transform: (UserPlaceStateEntity) -> UserPlaceStateEntity,
    ) {
        // Writes seed too, not just reads: user_place_state has a foreign key on
        // places, so a mutation that beat the first read would fail the
        // constraint rather than silently no-op.
        seeder.seedIfNeeded()
        database.withTransaction {
            val dao = database.userPlaceStateDao()
            val current = dao.getState(placeId) ?: UserPlaceStateEntity(placeId = placeId)
            val updated = transform(current)
            // The row is dropped only once it holds nothing at all. A rating or
            // an opinion counts: un-marking a visit on a place the user wrote
            // about must not silently delete what they wrote.
            val empty = !updated.isVisited &&
                !updated.isWishlisted &&
                updated.rating == null &&
                updated.note.isNullOrBlank()
            if (empty) {
                dao.delete(placeId)
            } else {
                dao.upsert(updated)
            }
        }
    }

    private companion object {
        const val MIN_RATING = 1
        const val MAX_RATING = 5

        /**
         * How many photos one place may hold.
         *
         * Twelve is more than anybody adds to one cafe, and it is the only
         * thing bounding this at all: without it a place could hold two hundred
         * photos, which is tens of megabytes of app storage and a strip nobody
         * can scroll, and neither failure announces itself.
         */
        const val MAX_PHOTOS_PER_PLACE = 12

        /**
         * Namespaces user ids away from the catalog's, whose ids are slugs with
         * no prefix. A future catalog can therefore never collide with a place
         * someone added, however it slugs its names.
         */
        const val USER_PLACE_PREFIX = "user-"

        /** Namespaces photo ids the same way, and for the same reason. */
        const val PHOTO_ID_PREFIX = "photo-"

        /** And suggestion ids, which are generated rather than derived. */
        const val SUGGESTION_ID_PREFIX = "sug-"

        /**
         * How long a "no" holds before the same place may be offered again.
         *
         * A week. Short enough that dismissing the wrong card on a Tuesday does
         * not blind the app to a place you go to every weekend; long enough
         * that walking past the same restaurant every morning cannot produce a
         * daily notification about it.
         */
        const val RESUGGEST_AFTER_DISMISS_MILLIS = 7L * 24 * 60 * 60 * 1000

        /** And after a yes that was later undone. */
        const val RESUGGEST_AFTER_CONFIRM_MILLIS = 12L * 60 * 60 * 1000

        /** Same rule as `slugify` in `tools/build_seed.py`, so ids look alike. */
        fun slugify(name: String): String =
            name.lowercase()
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
                .take(48)
                .ifEmpty { "place" }
    }
}
