package com.citymemory.data.local.seed

import android.content.res.AssetManager
import com.citymemory.data.local.entities.PlaceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Where the shipped catalog comes from.
 *
 * An interface for the same reason [com.citymemory.domain.repository.CityGeometryProvider]
 * is one: it keeps [DatabaseSeeder] free of `AssetManager`, so seeding can be
 * tested against a catalog held in memory without a device or a Robolectric
 * asset path.
 */
fun interface PlaceCatalog {
    suspend fun load(cityId: String): PlaceCatalogCodec.Catalog
}

/**
 * The real catalog, from `assets/mumbai-places.tsv`.
 *
 * Read on [Dispatchers.IO] and not cached: it is used once, on the first launch
 * after an install or an update, and holding 3,191 entities for the life of the
 * process to save a read that will not happen again is the wrong trade.
 */
class AssetPlaceCatalog(
    private val assets: AssetManager,
    private val assetName: String = MumbaiSeed.CATALOG_ASSET,
) : StampedPlaceCatalog {

    override suspend fun load(cityId: String): PlaceCatalogCodec.Catalog =
        withContext(Dispatchers.IO) {
            assets.open(assetName).use { PlaceCatalogCodec.decode(cityId, it) }
        }

    /** One line off the front of the asset, which is all the seeder needs. */
    override suspend fun stamp(cityId: String): String? = withContext(Dispatchers.IO) {
        assets.open(assetName).use { PlaceCatalogCodec.readStamp(it) }
    }
}
