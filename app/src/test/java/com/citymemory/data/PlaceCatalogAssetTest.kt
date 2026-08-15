package com.citymemory.data

import com.citymemory.SeedPlaces
import com.citymemory.data.local.seed.MumbaiSeed
import com.citymemory.data.local.seed.PlaceCatalogCodec
import com.citymemory.domain.model.PlaceCategory
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests over the *real* shipped catalog, not a fixture — the same 3,191 rows
 * that end up in the APK, which is why [PlaceCatalogCodec] takes an
 * `InputStream` rather than an `AssetManager`.
 *
 * A regenerated catalog that lost its addresses, duplicated an id or drifted
 * out of Mumbai fails the build rather than shipping as a broken list.
 */
class PlaceCatalogAssetTest {

    private companion object {
        /**
         * How many same-named places may share an address. Today 23 of the 334
         * repeated-name places do, which is 6.9% of them — it was 13.6% before
         * franchise outlets stopped shipping, since a chain is the purest case
         * of a repeated name.
         */
        const val MAX_AMBIGUOUS_FRACTION = 0.15
    }

    private val places = SeedPlaces.all

    @Test
    fun `the real asset decodes into the whole city`() {
        assertTrue("expected thousands of places, got ${places.size}", places.size > 2_500)
    }

    @Test
    fun `every place has an id, a name and a description`() {
        val broken = places.filter {
            it.id.isBlank() || it.name.isBlank() || it.description.isBlank()
        }
        assertTrue("rows with an empty field: ${broken.map { it.id }}", broken.isEmpty())
    }

    @Test
    fun `ids are unique, because they are primary keys`() {
        val duplicates = places.groupBy { it.id }.filterValues { it.size > 1 }.keys
        assertTrue("duplicate ids: $duplicates", duplicates.isEmpty())
    }

    @Test
    fun `every place carries an address`() {
        // Not every place has a street address in OpenStreetMap, but every one
        // falls in a postal area, so every one can say where it is. With 15
        // places called Hanuman Mandir in here, that line is often the only
        // thing that tells them apart.
        val addressless = places.filter { it.address.isNullOrBlank() }
        assertTrue("places with no address: ${addressless.map { it.name }}", addressless.isEmpty())
    }

    /**
     * The catalog holds every mapped non-franchise place in Mumbai, so 334 of
     * them still share a name with another — 15 Hanuman Mandirs, 9 BMC Parks.
     * This is the assertion that the address is what makes such a list usable.
     *
     * Not all of them: 23 are a same-named place in the *same* postal area, and
     * only a street address separates those. OpenStreetMap has one for a
     * quarter of its POIs and nothing else in the data can invent the rest, so
     * the residual is real and is what `setAddress` exists for — the person who
     * went there can write the address the extract does not have.
     */
    @Test
    fun `the address tells same-named places apart`() {
        val repeated = places.groupBy { it.name }.filterValues { it.size > 1 }
        assertTrue("expected repeated names in a full catalog", repeated.isNotEmpty())

        val repeatedCount = repeated.values.sumOf { it.size }
        val ambiguous = repeated.values.sumOf { group ->
            group.size - group.mapTo(HashSet()) { it.address }.size
        }

        assertTrue(
            "$ambiguous of $repeatedCount repeated-name places are not told apart by " +
                "their address, which is more than the ${MAX_AMBIGUOUS_FRACTION * 100}% allowed",
            ambiguous <= repeatedCount * MAX_AMBIGUOUS_FRACTION,
        )
    }

    @Test
    fun `every place is inside Mumbai`() {
        val outside = places.filterNot {
            it.latitude in 18.86..19.30 && it.longitude in 72.75..73.01
        }
        assertTrue("outside the extract's box: ${outside.map { it.name }}", outside.isEmpty())
    }

    @Test
    fun `every category the app renders is present`() {
        val present = places.mapTo(HashSet()) { it.category }
        val missing = PlaceCategory.entries.filterNot { it.id in present }
        assertTrue("no places in: $missing", missing.isEmpty())
    }

    @Test
    fun `display order follows the file`() {
        assertEquals(places.indices.toList(), places.map { it.displayOrder })
    }

    @Test
    fun `the stamp identifies this catalog`() {
        assertTrue("stamp looks empty: '${SeedPlaces.stamp}'", SeedPlaces.stamp.length >= 8)
    }

    // -----------------------------------------------------------------------

    @Test
    fun `a catalog round-trips through the codec`() {
        val decoded = PlaceCatalogCodec.decode(
            MumbaiSeed.CITY_ID,
            catalog(
                "abc123",
                "a\tCAFE\tA Cafe\t19.010000\t72.840000\t1 Main Road, Mumbai 400001\tCafe.",
                "b\tPARK\tA Park\t19.020000\t72.850000\t\tPark.",
            ),
        )

        assertEquals("abc123", decoded.stamp)
        assertEquals(2, decoded.places.size)
        assertEquals("A Cafe", decoded.places[0].name)
        assertEquals(PlaceCategory.CAFE.id, decoded.places[0].category)
        assertEquals(19.01, decoded.places[0].latitude, 1e-9)
        assertEquals("1 Main Road, Mumbai 400001", decoded.places[0].address)
        // An empty address column is absent, not an empty string.
        assertNull(decoded.places[1].address)
        assertEquals(MumbaiSeed.CITY_ID, decoded.places[1].cityId)
    }

    @Test
    fun `the stamp can be read without decoding the rows`() {
        val stamp = PlaceCatalogCodec.readStamp(
            catalog("deadbeef", "a\tCAFE\tA Cafe\t19.0\t72.8\t\tCafe."),
        )
        assertEquals("deadbeef", stamp)
    }

    @Test
    fun `the real asset reports the same stamp both ways`() {
        val fromHeader = SeedPlaces.assetStream().use { PlaceCatalogCodec.readStamp(it) }
        assertNotNull(fromHeader)
        assertEquals(SeedPlaces.stamp, fromHeader)
    }

    @Test(expected = PlaceCatalogCodec.MalformedCatalogException::class)
    fun `a file that is not a catalog is rejected`() {
        PlaceCatalogCodec.decode(
            "test",
            ByteArrayInputStream("NOPE\nnot a catalog\n".toByteArray()),
        )
    }

    @Test(expected = PlaceCatalogCodec.MalformedCatalogException::class)
    fun `a row with the wrong number of columns is rejected`() {
        PlaceCatalogCodec.decode("test", catalog("abc", "a\tCAFE\tShort row"))
    }

    @Test(expected = PlaceCatalogCodec.MalformedCatalogException::class)
    fun `a row whose coordinate will not parse is rejected`() {
        PlaceCatalogCodec.decode("test", catalog("abc", "a\tCAFE\tA Cafe\tnorth\t72.8\t\tCafe."))
    }

    /**
     * A truncated asset is the failure this catches: every row present parsed,
     * there were simply fewer of them than the header promised.
     */
    @Test(expected = PlaceCatalogCodec.MalformedCatalogException::class)
    fun `a catalog shorter than its header claims is rejected`() {
        PlaceCatalogCodec.decode(
            "test",
            ByteArrayInputStream(
                (
                    "CMPL\t1\t9\tabc\n" +
                        "id\tcategory\tname\tlat\tlon\taddress\tdescription\n" +
                        "a\tCAFE\tA Cafe\t19.0\t72.8\t\tCafe.\n"
                    ).toByteArray(),
            ),
        )
    }

    private fun catalog(stamp: String, vararg rows: String) = ByteArrayInputStream(
        (
            "CMPL\t1\t${rows.size}\t$stamp\n" +
                "id\tcategory\tname\tlat\tlon\taddress\tdescription\n" +
                rows.joinToString("\n") + "\n"
            ).toByteArray(),
    )
}
