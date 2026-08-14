package com.citymemory.domain.repository

import com.citymemory.domain.model.City
import com.citymemory.domain.model.Place
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
}
