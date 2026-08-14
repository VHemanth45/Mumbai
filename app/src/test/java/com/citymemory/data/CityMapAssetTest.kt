package com.citymemory.data

import com.citymemory.data.local.seed.MumbaiSeed
import com.citymemory.data.map.CityMapCodec
import com.citymemory.domain.model.CityGeometry
import com.citymemory.domain.model.ShapeKind
import com.citymemory.ui.map.MapStyle
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.math.cos
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests over the *real* shipped map asset, not a fixture.
 *
 * `CityMapCodec` takes an `InputStream` rather than an `AssetManager` precisely
 * so this can happen in an ordinary JVM test: the thing that gets asserted here
 * is the same 1.8 MB of OpenStreetMap geometry that ends up in the APK. A
 * regenerated asset that lost its buildings, drifted off Mumbai or broke the
 * delta encoding fails the build rather than shipping as a dark screen.
 */
class CityMapAssetTest {

    // Gradle runs unit tests from the module directory; the second path keeps
    // this working if it is ever run from the repository root instead.
    private val asset: File = listOf(
        File("src/main/assets/mumbai.map"),
        File("app/src/main/assets/mumbai.map"),
    ).firstOrNull { it.isFile }
        ?: error("mumbai.map is missing — run tools/build_map_asset.py")

    private val geometry: CityGeometry by lazy {
        asset.inputStream().use { CityMapCodec.decode(MumbaiSeed.CITY_ID, it) }
    }

    @Test
    fun `real asset decodes into a substantial amount of geometry`() {
        assertTrue(
            "expected tens of thousands of shapes, got ${geometry.shapes.size}",
            geometry.shapes.size > 20_000,
        )
        val points = geometry.shapes.sumOf { it.size }
        assertTrue("expected >100k points, got $points", points > 100_000)
    }

    @Test
    fun `real asset is framed on Mumbai`() {
        val b = geometry.bounds
        assertTrue("south edge $b", b.minLatitude in 18.5..19.0)
        assertTrue("north edge $b", b.maxLatitude in 19.1..19.6)
        assertTrue("west edge $b", b.minLongitude in 72.5..72.9)
        assertTrue("east edge $b", b.maxLongitude in 72.9..73.3)
    }

    @Test
    fun `every seeded place falls inside the map`() {
        val b = geometry.bounds
        val outside = MumbaiSeed.places.filterNot {
            it.latitude in b.minLatitude..b.maxLatitude &&
                it.longitude in b.minLongitude..b.maxLongitude
        }
        assertTrue(
            "these places would render outside the map: ${outside.map { p -> p.name }}",
            outside.isEmpty(),
        )
    }

    @Test
    fun `the asset carries every kind the renderer styles`() {
        val present = geometry.shapes.mapTo(HashSet()) { it.kind }
        val expected = ShapeKind.entries.filter { it != ShapeKind.LAND }
        val missing = expected.filterNot { it in present }
        assertTrue("asset has no geometry of kind: $missing", missing.isEmpty())
    }

    @Test
    fun `areas are closeable and lines are drawable`() {
        val badArea = geometry.shapes.firstOrNull { it.kind.isArea && it.size < 3 }
        val badLine = geometry.shapes.firstOrNull { !it.kind.isArea && it.size < 2 }
        assertEquals("an area with fewer than 3 points cannot be filled", null, badArea)
        assertEquals("a line with fewer than 2 points cannot be stroked", null, badLine)
    }

    /**
     * The point of the whole feature: marking a place visited has to light up
     * something worth looking at. A place with no geometry within the reveal
     * radius would light up an empty warm circle.
     */
    @Test
    fun `every seeded place has geometry inside its lit area`() {
        val barren = MumbaiSeed.places
            .map { it.name to countShapesNear(it.latitude, it.longitude) }
            .filter { (_, count) -> count == 0 }

        assertTrue("nothing would light up around: ${barren.map { it.first }}", barren.isEmpty())
    }

    /**
     * Roads alone read as a diagram. Building footprints and footpaths are what
     * make a lit area look like a place someone walked around in.
     */
    @Test
    fun `every seeded place has street-level detail inside its lit area`() {
        val flat = MumbaiSeed.places
            .map { place ->
                place.name to countShapesNear(place.latitude, place.longitude) { it.isDetail }
            }
            .filter { (_, count) -> count == 0 }

        assertTrue("no buildings or paths around: ${flat.map { it.first }}", flat.isEmpty())
    }

    @Test
    fun `codec round-trips delta and zigzag encoded coordinates`() {
        // Deliberately includes a westward/southward run so the zigzag encoding
        // of negative deltas is exercised, not just the happy northeast case.
        val encoded = encode(
            bounds = doubleArrayOf(18.86, 72.75, 19.30, 73.01),
            shapes = listOf(
                ShapeKind.MOTORWAY to listOf(
                    19.000000 to 72.800000,
                    19.000500 to 72.800900,
                    18.999100 to 72.799200,
                ),
                ShapeKind.BUILDING to listOf(
                    19.076000 to 72.876000,
                    19.076100 to 72.876000,
                    19.076100 to 72.876120,
                    19.076000 to 72.876120,
                ),
            ),
        )

        val decoded = CityMapCodec.decode("test", ByteArrayInputStream(encoded))

        assertEquals(2, decoded.shapes.size)
        assertEquals(ShapeKind.MOTORWAY, decoded.shapes[0].kind)
        assertEquals(ShapeKind.BUILDING, decoded.shapes[1].kind)
        assertEquals(3, decoded.shapes[0].size)
        assertEquals(4, decoded.shapes[1].size)
        assertEquals(18.999100, decoded.shapes[0].latitudes[2], 1e-9)
        assertEquals(72.799200, decoded.shapes[0].longitudes[2], 1e-9)
        assertEquals(72.876120, decoded.shapes[1].longitudes[2], 1e-9)
        assertEquals(19.30, decoded.bounds.maxLatitude, 1e-9)
    }

    @Test(expected = CityMapCodec.MalformedMapException::class)
    fun `a file that is not a city map is rejected`() {
        CityMapCodec.decode("test", ByteArrayInputStream("NOPE not a map at all".toByteArray()))
    }

    @Test(expected = CityMapCodec.MalformedMapException::class)
    fun `truncated data is rejected rather than silently half-decoded`() {
        val full = encode(
            bounds = doubleArrayOf(18.86, 72.75, 19.30, 73.01),
            shapes = listOf(
                ShapeKind.PRIMARY to listOf(19.0 to 72.8, 19.1 to 72.9, 19.2 to 72.95),
            ),
        )
        CityMapCodec.decode("test", ByteArrayInputStream(full.copyOf(full.size - 3)))
    }

    // -----------------------------------------------------------------------

    /**
     * Shapes with at least one vertex inside the reveal radius of a point.
     * Equirectangular, like `GeoProjector` — over a few hundred metres the
     * error against a proper geodesic is far below what is being asserted.
     */
    private fun countShapesNear(
        latitude: Double,
        longitude: Double,
        predicate: (ShapeKind) -> Boolean = { true },
    ): Int {
        val radius = MapStyle.RevealRadiusMeters.toDouble()
        val metresPerDegLat = 111_320.0
        val metresPerDegLng = 111_320.0 * cos(Math.toRadians(latitude))
        var count = 0

        for (shape in geometry.shapes) {
            if (!predicate(shape.kind)) continue
            for (i in 0 until shape.size) {
                val dy = (shape.latitudes[i] - latitude) * metresPerDegLat
                val dx = (shape.longitudes[i] - longitude) * metresPerDegLng
                if (sqrt(dx * dx + dy * dy) <= radius) {
                    count++
                    break
                }
            }
        }
        return count
    }

    /** Mirrors `tools/build_map_asset.py`, so the round-trip test is honest. */
    private fun encode(
        bounds: DoubleArray,
        shapes: List<Pair<ShapeKind, List<Pair<Double, Double>>>>,
    ): ByteArray {
        val out = ArrayList<Byte>()

        fun varint(value: Int) {
            var v = value
            while (true) {
                val chunk = v and 0x7F
                v = v ushr 7
                if (v != 0) out.add((chunk or 0x80).toByte()) else { out.add(chunk.toByte()); return }
            }
        }

        fun zigzag(value: Long) {
            var v = (value shl 1) xor (value shr 63)
            while (true) {
                val chunk = (v and 0x7F).toInt()
                v = v ushr 7
                if (v != 0L) out.add((chunk or 0x80).toByte()) else { out.add(chunk.toByte()); return }
            }
        }

        "CMAP".forEach { out.add(it.code.toByte()) }
        out.add(1)
        for (value in bounds) {
            val e6 = Math.round(value * 1e6).toInt()
            out.add((e6 and 0xFF).toByte())
            out.add(((e6 shr 8) and 0xFF).toByte())
            out.add(((e6 shr 16) and 0xFF).toByte())
            out.add(((e6 shr 24) and 0xFF).toByte())
        }
        varint(shapes.size)

        for ((kind, points) in shapes) {
            out.add(kind.id.toByte())
            varint(points.size)
            var prevLat = 0L
            var prevLng = 0L
            points.forEachIndexed { index, (lat, lng) ->
                val la = Math.round(lat * 1e6)
                val ln = Math.round(lng * 1e6)
                if (index == 0) {
                    zigzag(la)
                    zigzag(ln)
                } else {
                    zigzag(la - prevLat)
                    zigzag(ln - prevLng)
                }
                prevLat = la
                prevLng = ln
            }
        }
        return out.toByteArray()
    }
}
