package com.citymemory.data.map

import android.content.Context
import android.util.Log
import com.citymemory.data.local.seed.MumbaiSeed
import com.citymemory.domain.model.CityGeometry
import com.citymemory.domain.model.MapLabel
import com.citymemory.domain.repository.CityGeometryProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The real city, from OpenStreetMap.
 *
 * Serves `assets/mumbai.map`, built from a Geofabrik western-India extract by
 * `tools/extract_osm.py` + `tools/build_map_asset.py`. Decoding ~867,000 points
 * takes long enough to matter, so it happens on [Dispatchers.Default] and the
 * result is held for the process lifetime — the geometry never changes, and
 * re-reading it on every configuration change would be pure waste.
 *
 * If the asset is missing or malformed the hand-authored silhouette is served
 * instead. A map that is merely stylised is a far better failure than a blank
 * screen, and it keeps the app's one screen working in any build where the
 * asset has not been generated.
 */
class OsmCityGeometryProvider(
    context: Context,
    private val fallback: CityGeometryProvider = MockMumbaiGeometryProvider(),
) : CityGeometryProvider {

    private val assets = context.applicationContext.assets

    @Volatile
    private var cached: CityGeometry? = null

    override suspend fun geometryFor(cityId: String): CityGeometry {
        if (cityId != MumbaiSeed.CITY_ID) return CityGeometry.Empty

        cached?.let { return it }

        return withContext(Dispatchers.Default) {
            // Double-checked: two screens can ask at once on a cold start.
            cached ?: load(cityId).also { cached = it }
        }
    }

    private suspend fun load(cityId: String): CityGeometry = try {
        assets.open(ASSET_NAME).use { CityMapCodec.decode(cityId, it) }.copy(labels = labels())
    } catch (e: Exception) {
        Log.w(TAG, "could not read $ASSET_NAME, falling back to the outline", e)
        fallback.geometryFor(cityId)
    }

    /**
     * The names, which are optional in a way the geometry is not.
     *
     * A map with no labels is the map this app shipped for its whole life so
     * far; a map with no streets is a black screen. So a broken label asset
     * costs the names and nothing else, rather than dropping the city to the
     * hand-authored outline.
     */
    private fun labels(): List<MapLabel> = try {
        assets.open(LABEL_ASSET_NAME).use { MapLabelCodec.decode(it) }
    } catch (e: Exception) {
        Log.w(TAG, "could not read $LABEL_ASSET_NAME, drawing the map unlabelled", e)
        emptyList()
    }

    private companion object {
        const val ASSET_NAME = "mumbai.map"
        const val LABEL_ASSET_NAME = "mumbai-labels.tsv"
        const val TAG = "OsmCityGeometry"
    }
}
