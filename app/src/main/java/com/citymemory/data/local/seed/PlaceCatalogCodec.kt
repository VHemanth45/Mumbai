package com.citymemory.data.local.seed

import com.citymemory.data.local.entities.PlaceEntity
import com.citymemory.domain.model.PlaceCategory
import java.io.IOException
import java.io.InputStream

/**
 * Reads the packed place catalog produced by `tools/build_seed.py`.
 *
 * The catalog used to be generated Kotlin — a `listOf(place(...), ...)` per
 * category in `MumbaiSeed.kt` — and at 177 places that was the simplest thing
 * that could work. It does not survive the catalog shipping every mapped place
 * in Mumbai: those property initialisers all compile into one `<clinit>`, and a
 * JVM method caps out at 64 KB of bytecode, which the catalog crosses at around
 * 2,300 entries. The failure is a build-time `MethodTooLargeException`.
 *
 * So the catalog went where the map already was: an asset with a codec. Same
 * shape as [com.citymemory.data.map.CityMapCodec], and for the same reason it
 * takes an [InputStream] rather than an `AssetManager` — the real asset can
 * then be decoded and asserted against in an ordinary JVM test.
 *
 * Tab-separated on purpose rather than JSON: 3,191 rows of six short fields,
 * parsed once on first launch, and `split('\t')` needs no parser, no reflection
 * and no allocation beyond the strings themselves.
 *
 * ```
 * CMPL <tab> version <tab> count <tab> stamp
 * id <tab> category <tab> name <tab> lat <tab> lon <tab> address <tab> description
 * <one row per place, same columns>
 * ```
 *
 * The writer collapses whitespace in every field, so there is no escape
 * character and none is needed: a tab or a newline cannot occur inside a value.
 *
 * `stamp` is a digest of the body. It is what lets an installed app notice that
 * an update shipped a different catalog — see [DatabaseSeeder].
 */
object PlaceCatalogCodec {

    private const val MAGIC = "CMPL"
    private const val VERSION = 1
    private const val COLUMNS = 7

    class MalformedCatalogException(message: String) : IOException(message)

    /** A catalog, and the stamp identifying which one it is. */
    data class Catalog(val stamp: String, val places: List<PlaceEntity>)

    /**
     * Just the stamp, from the header line, without decoding the rows.
     *
     * The point of the whole stamp: an app that has already seeded reads one
     * line to find out it has nothing to do, instead of 3,191.
     */
    fun readStamp(input: InputStream): String? =
        input.bufferedReader().readLine()
            ?.split('\t')
            ?.takeIf { it.getOrNull(0) == MAGIC && it.getOrNull(1)?.toIntOrNull() == VERSION }
            ?.getOrNull(3)
            ?.takeIf { it.isNotBlank() }

    /**
     * Decodes every row into a [PlaceEntity] belonging to [cityId].
     *
     * `displayOrder` is the row's position in the file, so the order places are
     * listed in is decided by the pipeline that ranked them rather than being
     * re-derived here.
     */
    fun decode(cityId: String, input: InputStream): Catalog {
        val lines = input.bufferedReader().readLines()
        if (lines.size < 2) throw MalformedCatalogException("catalog is empty")

        val header = lines[0].split('\t')
        if (header.getOrNull(0) != MAGIC) {
            throw MalformedCatalogException("not a place catalog: '${header.getOrNull(0)}'")
        }
        val version = header.getOrNull(1)?.toIntOrNull()
        if (version != VERSION) throw MalformedCatalogException("unsupported version $version")
        val declared = header.getOrNull(2)?.toIntOrNull()
            ?: throw MalformedCatalogException("header carries no row count")
        val stamp = header.getOrNull(3)?.takeIf { it.isNotBlank() }
            ?: throw MalformedCatalogException("header carries no stamp")

        // Row 1 is the column names, which are there so the file reads on its
        // own. They are not parsed — the order is fixed by this decoder.
        val places = ArrayList<PlaceEntity>(declared)
        for (index in 2 until lines.size) {
            val line = lines[index]
            if (line.isBlank()) continue
            val f = line.split('\t')
            if (f.size != COLUMNS) {
                throw MalformedCatalogException(
                    "row ${index + 1} has ${f.size} fields, expected $COLUMNS",
                )
            }
            val latitude = f[3].toDoubleOrNull()
            val longitude = f[4].toDoubleOrNull()
            if (latitude == null || longitude == null) {
                throw MalformedCatalogException("row ${index + 1} has no usable coordinate")
            }
            places += PlaceEntity(
                id = f[0],
                cityId = cityId,
                // The file writes the enum constant; the entity stores the id.
                category = categoryOf(f[1]).id,
                name = f[2],
                latitude = latitude,
                longitude = longitude,
                address = f[5].takeIf { it.isNotEmpty() },
                description = f[6],
                imageUrl = null,
                displayOrder = places.size,
                isUserAdded = false,
            )
        }

        // A truncated download or a half-written asset is the failure this
        // catches: every row above parsed, there were just fewer of them.
        if (places.size != declared) {
            throw MalformedCatalogException("header says $declared places, found ${places.size}")
        }
        return Catalog(stamp, places)
    }

    /**
     * Unknown category names degrade to [PlaceCategory.TOURIST] rather than
     * failing the whole catalog, matching [PlaceCategory.fromId]: one
     * unexpected value in a 3,191-row asset should not leave the app with no
     * places at all.
     */
    private fun categoryOf(name: String): PlaceCategory =
        PlaceCategory.entries.firstOrNull { it.name == name } ?: PlaceCategory.TOURIST
}
