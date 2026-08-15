package com.citymemory.data

import com.citymemory.SeedPlaces
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
 * is the same 3.6 MB of OpenStreetMap geometry that ends up in the APK. A
 * regenerated asset that lost its buildings, drifted off Mumbai or broke the
 * delta encoding fails the build rather than shipping as a dark screen.
 */
class CityMapAssetTest {

    private companion object {
        /**
         * How many places may have no building or footpath in their lit area.
         *
         * Two percent, and the ceiling moved because the light did: at a 340 m
         * reveal this was one place in 3,191, and at 100 m it is 37. That is not
         * a regression, it is the radius asking a harder question — 100 m around
         * Juhu Beach or the middle of Shivaji Park is sand and grass, and
         * OpenStreetMap is right that there is nothing there.
         *
         * It is still worth asserting, because the failure it exists to catch is
         * not subtle: a broken `DETAIL_RADIUS_M` in `tools/extract_osm.py` puts
         * *thousands* here, not dozens.
         */
        const val MAX_FLAT_FRACTION = 0.02

        /**
         * How many places may light up with nothing in them at all — no road,
         * no coastline, no park edge, nothing.
         *
         * Five places in 3,191 today, all of them beaches or open ground where
         * the mapped geometry is the outline and its nearest edge is more than
         * 100 m from the point the catalog carries. Half a percent leaves room
         * for the data to shift without leaving room for the pipeline to break.
         */
        const val MAX_BARREN_FRACTION = 0.005
    }

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

    /** Built once for the whole class; see [PointGrid] for why it has to be. */
    private val grid: PointGrid by lazy {
        PointGrid(geometry, MapStyle.RevealRadiusMeters.toDouble())
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
        val outside = SeedPlaces.all.filterNot {
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
    fun `essentially every seeded place has geometry inside its lit area`() {
        val radius = MapStyle.RevealRadiusMeters.toDouble()
        val barren = SeedPlaces.all.filterNot {
            grid.hasPointWithin(it.latitude, it.longitude, radius, detailOnly = false)
        }

        assertTrue(
            "${barren.size} of ${SeedPlaces.total} places would light up an empty warm " +
                "circle at ${radius.toInt()} m: ${barren.take(20).map { it.name }}",
            barren.size <= SeedPlaces.total * MAX_BARREN_FRACTION,
        )
    }

    /**
     * Roads alone read as a diagram. Building footprints and footpaths are what
     * make a lit area look like a place someone walked around in.
     *
     * This used to demand every place, and could, because the catalog was 177
     * curated places in the middle of the city and the light reached 420 m.
     * Neither is true now. The catalog is everything OpenStreetMap has mapped
     * inside Mumbai, and the reveal is 100 m — close enough that a place on a
     * beach or in the middle of a park has nothing but sand and grass inside it.
     *
     * So the assertion is a ceiling rather than a zero. See
     * [MAX_FLAT_FRACTION]: it is loose enough for the data to be what it is and
     * tight enough that the failure it exists to catch — a broken
     * `DETAIL_RADIUS_M` in `tools/extract_osm.py`, which would put thousands of
     * places here — still fails the build.
     */
    @Test
    fun `almost every seeded place has street-level detail inside its lit area`() {
        val radius = MapStyle.RevealRadiusMeters.toDouble()
        val flat = SeedPlaces.all.filterNot {
            grid.hasPointWithin(it.latitude, it.longitude, radius, detailOnly = true)
        }

        val allowed = SeedPlaces.total * MAX_FLAT_FRACTION
        assertTrue(
            "${flat.size} of ${SeedPlaces.total} places have no buildings or paths " +
                "within ${radius.toInt()} m: ${flat.take(20).map { it.name }}",
            flat.size <= allowed,
        )
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
     * Every vertex in the asset, bucketed into cells one reveal radius across.
     *
     * The naive form of this — walk all 152,000 shapes per place — was fine
     * when the catalog was 177 places and is not remotely fine at 3,566: it is
     * half a billion shape scans, and the test stopped finishing. Bucketing
     * once turns each lookup into the nine cells that can hold a point within
     * the radius, and the whole suite back into seconds.
     *
     * Equirectangular, like `GeoProjector` — over a few hundred metres the
     * error against a proper geodesic is far below what is being asserted.
     */
    private class PointGrid(geometry: CityGeometry, cellMetres: Double) {

        private val cellLat = cellMetres / METRES_PER_DEG_LAT
        private val cellLng = cellMetres / metresPerDegLng(CITY_CENTRE_LAT)

        private val lats: DoubleArray
        private val lngs: DoubleArray
        private val detail: BooleanArray

        /** Cell key -> the indices in [lats] / [lngs] that fall in it. */
        private val cells: Map<Long, IntArray>

        init {
            val total = geometry.shapes.sumOf { it.size }
            lats = DoubleArray(total)
            lngs = DoubleArray(total)
            detail = BooleanArray(total)

            var n = 0
            val keys = LongArray(total)
            for (shape in geometry.shapes) {
                val isDetail = shape.kind.isDetail
                for (i in 0 until shape.size) {
                    lats[n] = shape.latitudes[i]
                    lngs[n] = shape.longitudes[i]
                    detail[n] = isDetail
                    keys[n] = key(shape.latitudes[i], shape.longitudes[i])
                    n++
                }
            }

            val grouped = HashMap<Long, MutableList<Int>>()
            for (i in 0 until total) grouped.getOrPut(keys[i]) { ArrayList() }.add(i)
            cells = grouped.mapValues { (_, list) -> list.toIntArray() }
        }

        // Mumbai is north and east of the origin, so truncation and floor agree
        // and the cheaper one is safe here.
        private fun key(latitude: Double, longitude: Double): Long =
            cellKey((latitude / cellLat).toLong(), (longitude / cellLng).toLong())

        /** True when any vertex — or any detail vertex — is within [radius]. */
        fun hasPointWithin(
            latitude: Double,
            longitude: Double,
            radius: Double,
            detailOnly: Boolean,
        ): Boolean {
            val mPerLng = metresPerDegLng(latitude)
            val row = (latitude / cellLat).toLong()
            val col = (longitude / cellLng).toLong()
            for (dr in -1..1) {
                for (dc in -1..1) {
                    val bucket = cells[cellKey(row + dr, col + dc)] ?: continue
                    for (i in bucket) {
                        if (detailOnly && !detail[i]) continue
                        val dy = (lats[i] - latitude) * METRES_PER_DEG_LAT
                        val dx = (lngs[i] - longitude) * mPerLng
                        if (sqrt(dx * dx + dy * dy) <= radius) return true
                    }
                }
            }
            return false
        }

        private companion object {
            const val METRES_PER_DEG_LAT = 111_320.0
            const val CITY_CENTRE_LAT = 19.07

            /** Keeps a negative cell index positive before packing. */
            const val OFFSET = 1_000_000L
            const val SPAN = 4_000_000L

            fun cellKey(row: Long, col: Long): Long = (row + OFFSET) * SPAN + (col + OFFSET)

            fun metresPerDegLng(latitude: Double): Double =
                METRES_PER_DEG_LAT * cos(Math.toRadians(latitude))
        }
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
