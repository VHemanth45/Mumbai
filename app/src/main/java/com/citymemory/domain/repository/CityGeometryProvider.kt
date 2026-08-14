package com.citymemory.domain.repository

import com.citymemory.domain.model.CityGeometry

/**
 * Supplies the shape of a city to the map renderer.
 *
 * The MVP implementation returns a hand-authored stylized outline. Swapping in
 * real data later means writing one more implementation of this interface —
 * e.g. a GeoJsonCityGeometryProvider reading from assets — and changing which
 * one AppContainer constructs. No UI code changes.
 */
interface CityGeometryProvider {
    suspend fun geometryFor(cityId: String): CityGeometry
}
