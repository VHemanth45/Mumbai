package com.citymemory

import com.citymemory.data.local.entities.PlaceEntity
import com.citymemory.data.local.seed.MumbaiSeed
import com.citymemory.data.local.seed.PlaceCatalog
import com.citymemory.data.local.seed.PlaceCatalogCodec
import com.citymemory.domain.model.PlaceCategory
import java.io.File

/**
 * Handles onto the seeded catalog for tests.
 *
 * The catalog is generated from the OpenStreetMap extract, so which places are
 * in it — and what their ids are — changes whenever the dataset is rebuilt or
 * the rules are tuned. Tests that hardcoded `"gateway-of-india"` were really
 * asserting that one regeneration of the data, and every one of them broke the
 * first time it was regenerated for reasons that had nothing to do with the
 * behaviour under test.
 *
 * So tests ask for "a cafe" or "the third tourist place" instead, and the
 * fixture resolves it against whatever is currently seeded. What is being
 * tested — visiting, rating, wishlisting, progress — was never about which
 * place it happened to.
 *
 * Read straight off the shipped asset rather than through an `AssetManager`,
 * which is why [PlaceCatalogCodec] takes an `InputStream`: what these tests run
 * against is the same 3,566 rows that end up in the APK.
 */
object SeedPlaces {

    // Gradle runs unit tests from the module directory; the second path keeps
    // this working if it is ever run from the repository root instead.
    private val assetFile: File = listOf(
        File("src/main/assets/${MumbaiSeed.CATALOG_ASSET}"),
        File("app/src/main/assets/${MumbaiSeed.CATALOG_ASSET}"),
    ).firstOrNull { it.isFile }
        ?: error("${MumbaiSeed.CATALOG_ASSET} is missing — run tools/build_seed.py")

    /** The shipped catalog, for a test that wants to read it a second way. */
    fun assetStream(): java.io.InputStream = assetFile.inputStream()

    private val decoded: PlaceCatalogCodec.Catalog =
        assetStream().use { PlaceCatalogCodec.decode(MumbaiSeed.CITY_ID, it) }

    val all: List<PlaceEntity> = decoded.places

    val stamp: String = decoded.stamp

    val total: Int = all.size

    /** The real catalog, for a [com.citymemory.data.local.seed.DatabaseSeeder]. */
    val catalog: PlaceCatalog = PlaceCatalog { decoded }

    fun of(category: PlaceCategory, index: Int = 0): PlaceEntity {
        val group = all.filter { it.category == category.id }
        require(group.size > index) {
            "seed has only ${group.size} ${category.id} places, wanted index $index"
        }
        return group[index]
    }

    fun id(category: PlaceCategory, index: Int = 0): String = of(category, index).id

    fun name(category: PlaceCategory, index: Int = 0): String = of(category, index).name

    /** [count] distinct ids spread across the catalog, for bulk state tests. */
    fun ids(count: Int): List<String> {
        require(all.size >= count) { "seed has only ${all.size} places, wanted $count" }
        return all.take(count).map { it.id }
    }

    /** [count] ids from one category, for tests that assert a category rollup. */
    fun ids(category: PlaceCategory, count: Int): List<String> =
        (0 until count).map { id(category, it) }

    /**
     * A place near the top of the catalog whose name is short enough to render
     * in full.
     *
     * Compose's `onNodeWithText` matches the string that actually reached the
     * screen, and a place card ellipsizes. The generated catalog opens with
     * "Chhatrapati Shivaji Maharaj Terminus", so UI assertions that name a
     * place have to pick one that fits rather than whichever sorts first.
     *
     * Pinned to a tourist place so the category rollup it lands in stays the
     * same one across regenerations.
     */
    val shortNamed: PlaceEntity =
        all.first { it.category == PlaceCategory.TOURIST.id && it.name.length <= SHORT_NAME_CHARS }

    /** How many places the catalog holds in a category, for rollup assertions. */
    fun countOf(category: PlaceCategory): Int = all.count { it.category == category.id }

    val tourist: PlaceEntity get() = of(PlaceCategory.TOURIST)
    val cafe: PlaceEntity get() = of(PlaceCategory.CAFE)
    val restaurant: PlaceEntity get() = of(PlaceCategory.RESTAURANT)
    val park: PlaceEntity get() = of(PlaceCategory.PARK)
    val culture: PlaceEntity get() = of(PlaceCategory.CULTURE)
    val hiddenGem: PlaceEntity get() = of(PlaceCategory.HIDDEN_GEM)

    /** Comfortably inside what a place card shows before it ellipsizes. */
    private const val SHORT_NAME_CHARS = 20
}
