package com.citymemory.domain.repository

import com.citymemory.domain.model.City
import com.citymemory.domain.model.Place
import com.citymemory.domain.model.PlaceCategory
import com.citymemory.domain.model.PlacePhoto
import kotlinx.coroutines.flow.Flow

/**
 * The single read/write surface the UI layer sees. Implemented over Room today;
 * a remote or multi-city source later would implement this same interface.
 */
interface PlaceRepository {

    fun observeCity(cityId: String): Flow<City?>

    /**
     * All places in the city with the user's state joined on, ordered for display.
     * This is the app's single source of truth — the Explore map, Discover list,
     * Wishlist, Progress and achievements are all derived from this one stream.
     */
    fun observePlaces(cityId: String): Flow<List<Place>>

    fun observePlace(placeId: String): Flow<Place?>

    suspend fun setVisited(placeId: String, isVisited: Boolean)

    suspend fun setWishlisted(placeId: String, isWishlisted: Boolean)

    /**
     * Records what the user thought of a place: a score out of five and their
     * own words, either of which may be null for "not said".
     *
     * One call rather than two setters because a rating and an opinion are
     * written together from one form, and two writes would emit two states —
     * the UI would see the stars land before the text.
     */
    suspend fun setReview(placeId: String, rating: Int?, note: String?)

    /**
     * Adds a place the catalog does not have, and returns its id.
     *
     * The catalog ships every place OpenStreetMap has mapped in Mumbai, which
     * still leaves the ones it has not: somewhere that opened last month,
     * somewhere nobody has got round to mapping, somewhere that is only a place
     * because you went there. Those land in the same table as the rest and are
     * from then on indistinguishable to the map, to search and to progress.
     *
     * [markVisited] because adding a place you have never been to is the
     * unusual case — the reason to type one in is almost always that you just
     * came back from it.
     */
    suspend fun addUserPlace(
        cityId: String,
        name: String,
        category: PlaceCategory,
        latitude: Double,
        longitude: Double,
        address: String? = null,
        markVisited: Boolean = true,
    ): String

    /** Removes a user-added place. Catalogued places are left alone. */
    suspend fun deleteUserPlace(placeId: String)

    /**
     * Sets the address on any place, catalogued or not.
     *
     * Around one place in four has a street address in OpenStreetMap; the rest
     * carry only the locality and pin code the postal boundary gives them. If
     * you have been there, you know the address better than the extract does.
     */
    suspend fun setAddress(placeId: String, address: String?)

    /** The user's own photos of a place, oldest first. */
    fun observePhotos(placeId: String): Flow<List<PlacePhoto>>

    /**
     * Copies the image at [sourceUri] into the app and attaches it to a place.
     *
     * Takes the URI as a string rather than an `android.net.Uri` so this layer
     * stays free of Android types, which is the rule the rest of `domain` keeps
     * to. It is a locator either way.
     *
     * Returns false when the image could not be read — a lapsed permission, a
     * file that is not an image — which the caller should say out loud rather
     * than swallow, because from the user's side they just picked a photo and
     * nothing appeared.
     */
    suspend fun addPhoto(placeId: String, sourceUri: String): Boolean

    suspend fun deletePhoto(photoId: String)
}
