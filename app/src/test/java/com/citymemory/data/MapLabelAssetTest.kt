package com.citymemory.data

import com.citymemory.data.map.MapLabelCodec
import com.citymemory.domain.model.LabelTier
import com.citymemory.domain.model.MapLabel
import java.io.ByteArrayInputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests over the *real* shipped label list.
 *
 * The positions are the part worth asserting. A postal area's label has to sit
 * inside its own polygon, and the obvious way to place one — the average of the
 * boundary's vertices — does not: Mumbai's areas wrap around creeks and the
 * coastline, so a centroid of Mahim or Trombay lands in the water.
 * `tools/build_labels.py` computes a pole of inaccessibility instead and fails
 * its own build if any pole comes out outside its polygon. What is left to check
 * here is that what shipped is complete, in Mumbai, and readable.
 */
class MapLabelAssetTest {

    // Gradle runs unit tests from the module directory; the second path keeps
    // this working if it is ever run from the repository root instead.
    private val asset: File = listOf(
        File("src/main/assets/mumbai-labels.tsv"),
        File("app/src/main/assets/mumbai-labels.tsv"),
    ).firstOrNull { it.isFile }
        ?: error("mumbai-labels.tsv is missing — run tools/build_labels.py")

    private val labels: List<MapLabel> by lazy {
        asset.inputStream().use { MapLabelCodec.decode(it) }
    }

    private val areas get() = labels.filter { it.tier == LabelTier.AREA }
    private val places get() = labels.filter { it.tier == LabelTier.PLACE }

    @Test
    fun `every postal area is named`() {
        // boundary.geojson is 89 delivery areas and all of them should have a
        // label; a pole that failed would silently cost the city a name.
        assertEquals(89, areas.size)
    }

    @Test
    fun `the higher tier carries the places the data ranks highest`() {
        assertTrue("expected place labels, got ${places.size}", places.size > 50)
    }

    @Test
    fun `every label is inside the map`() {
        val outside = labels.filterNot {
            it.latitude in 18.86..19.30 && it.longitude in 72.75..73.01
        }
        assertTrue("labels off the map: ${outside.map { it.name }}", outside.isEmpty())
    }

    @Test
    fun `every label has something to draw`() {
        val blank = labels.filter { it.name.isBlank() }
        assertTrue("blank label at ${blank.map { it.latitude }}", blank.isEmpty())
    }

    @Test
    fun `area names are short enough to sit on a map`() {
        // These are drawn over geometry, not in a list, so a name that needs
        // half the screen is one that cannot be placed without covering the
        // city it is naming.
        val long = areas.filter { it.name.length > 28 }
        assertTrue("area names too long to place: ${long.map { it.name }}", long.isEmpty())
    }

    @Test
    fun `area labels carry their pin code and place labels do not`() {
        assertTrue("an area label lost its pin code", areas.all { it.detail.length == 6 })
        assertTrue("a place label gained a pin code", places.all { it.detail.isEmpty() })
    }

    @Test
    fun `no two labels sit on the exact same point`() {
        // Two labels at one coordinate is a build-time mistake — the collision
        // pass would silently drop one of them forever.
        val duplicates = labels.groupBy { it.latitude to it.longitude }.filterValues { it.size > 1 }
        assertTrue("stacked labels: ${duplicates.values.map { g -> g.map { it.name } }}", duplicates.isEmpty())
    }

    // -----------------------------------------------------------------------

    @Test
    fun `labels round-trip through the codec`() {
        val decoded = MapLabelCodec.decode(
            labelFile(
                "0\tColaba\t18.906000\t72.815000\t400005",
                "1\tGateway of India\t18.922000\t72.834700\t",
            ),
        )

        assertEquals(2, decoded.size)
        assertEquals(LabelTier.AREA, decoded[0].tier)
        assertEquals("Colaba", decoded[0].name)
        assertEquals("400005", decoded[0].detail)
        assertEquals(18.9060, decoded[0].latitude, 1e-9)
        assertEquals(LabelTier.PLACE, decoded[1].tier)
        assertEquals("", decoded[1].detail)
    }

    @Test(expected = MapLabelCodec.MalformedLabelsException::class)
    fun `a file that is not a label list is rejected`() {
        MapLabelCodec.decode(ByteArrayInputStream("NOPE\nnot labels\n".toByteArray()))
    }

    @Test(expected = MapLabelCodec.MalformedLabelsException::class)
    fun `an unknown tier is rejected rather than guessed at`() {
        MapLabelCodec.decode(labelFile("7\tSomewhere\t19.0\t72.8\t"))
    }

    @Test(expected = MapLabelCodec.MalformedLabelsException::class)
    fun `a truncated label list is rejected`() {
        MapLabelCodec.decode(
            ByteArrayInputStream(
                (
                    "CMLB\t1\t9\tabc\n" +
                        "tier\tname\tlat\tlon\tdetail\n" +
                        "0\tColaba\t18.9\t72.8\t400005\n"
                    ).toByteArray(),
            ),
        )
    }

    private fun labelFile(vararg rows: String) = ByteArrayInputStream(
        (
            "CMLB\t1\t${rows.size}\tstamp0000\n" +
                "tier\tname\tlat\tlon\tdetail\n" +
                rows.joinToString("\n") + "\n"
            ).toByteArray(),
    )
}
