package com.citymemory.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.citymemory.data.map.MockMumbaiGeometryProvider
import com.citymemory.domain.model.GeoBounds
import com.citymemory.domain.model.GeoPoint
import com.citymemory.domain.model.ShapeKind
import com.citymemory.ui.map.GeoProjector
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The projection is the one piece of the map that can be wrong in a way the eye
 * will not catch — an off-by-one in the y flip puts the city upside down and it
 * still looks like a city.
 */
class GeoProjectorTest {

    private val bounds = GeoBounds(
        minLatitude = 18.0,
        minLongitude = 72.0,
        maxLatitude = 20.0,
        maxLongitude = 74.0,
    )

    @Test
    fun `north is up`() {
        val projector = GeoProjector(bounds, Size(500f, 500f))

        val north = projector.project(GeoPoint(19.9, 73.0))
        val south = projector.project(GeoPoint(18.1, 73.0))

        assertTrue("north should have a smaller y than south", north.y < south.y)
    }

    @Test
    fun `east is right`() {
        val projector = GeoProjector(bounds, Size(500f, 500f))

        val west = projector.project(GeoPoint(19.0, 72.1))
        val east = projector.project(GeoPoint(19.0, 73.9))

        assertTrue("east should have a larger x than west", east.x > west.x)
    }

    @Test
    fun `content is centred within the canvas`() {
        val projector = GeoProjector(bounds, Size(1000f, 400f))

        val centre = projector.project(GeoPoint(19.0, 73.0))

        assertEquals(500f, centre.x, 0.5f)
        assertEquals(200f, centre.y, 0.5f)
    }

    @Test
    fun `projection stays inside the canvas including padding`() {
        val size = Size(400f, 700f)
        val padding = 20f
        val projector = GeoProjector(bounds, size, padding)

        val corners = listOf(
            GeoPoint(bounds.minLatitude, bounds.minLongitude),
            GeoPoint(bounds.minLatitude, bounds.maxLongitude),
            GeoPoint(bounds.maxLatitude, bounds.minLongitude),
            GeoPoint(bounds.maxLatitude, bounds.maxLongitude),
        )

        corners.forEach { corner ->
            val offset = projector.project(corner)
            assertTrue("x=${offset.x} out of range", offset.x >= padding - 0.5f && offset.x <= size.width - padding + 0.5f)
            assertTrue("y=${offset.y} out of range", offset.y >= padding - 0.5f && offset.y <= size.height - padding + 0.5f)
        }
    }

    @Test
    fun `aspect ratio is preserved so the city is not stretched`() {
        // A square geographic extent must stay square on a wide canvas.
        val projector = GeoProjector(bounds, Size(1000f, 400f))

        val topLeft = projector.project(GeoPoint(20.0, 72.0))
        val topRight = projector.project(GeoPoint(20.0, 74.0))
        val bottomLeft = projector.project(GeoPoint(18.0, 72.0))

        val width = topRight.x - topLeft.x
        val height = bottomLeft.y - topLeft.y

        // Longitude is compressed by cos(latitude), so width is the shorter side.
        val expectedRatio = kotlin.math.cos(Math.toRadians(19.0)).toFloat()
        assertEquals(expectedRatio, width / height, 0.01f)
    }

    /**
     * Adding a place puts a coordinate back out of a screen position, so the
     * inverse has to be right to well under the width of a street — a pin that
     * lands on the far pavement is a pin on the wrong place.
     *
     * The tolerance is a hundredth of a second of arc, about 11 cm here, and it
     * is bounded by [androidx.compose.ui.geometry.Offset] holding floats rather
     * than by the arithmetic: a round trip through a `Float` pixel cannot carry
     * more precision than the pixel has. That is far below anything the map can
     * express — at full zoom a pixel is about 0.8 m of ground.
     */
    @Test
    fun `unproject inverts project to well under a metre`() {
        val projector = GeoProjector(bounds, Size(1080f, 1920f), padding = 24f)

        for (point in PROBE_POINTS) {
            val roundTripped = projector.unproject(projector.project(point))

            assertEquals(point.latitude, roundTripped.latitude, 1e-6)
            assertEquals(point.longitude, roundTripped.longitude, 1e-6)
        }
    }

    @Test
    fun `unproject reads the middle of the canvas as the middle of the bounds`() {
        val size = Size(1000f, 1000f)
        val projector = GeoProjector(bounds, size)

        val middle = projector.unproject(Offset(size.width / 2f, size.height / 2f))

        assertEquals(19.0, middle.latitude, 1e-6)
        assertEquals(73.0, middle.longitude, 1e-6)
    }

    @Test
    fun `degenerate size does not blow up`() {
        val projector = GeoProjector(bounds, Size(0f, 0f))

        val offset = projector.project(GeoPoint(19.0, 73.0))

        assertTrue(offset.x.isFinite())
        assertTrue(offset.y.isFinite())
    }

    @Test
    fun `every seeded mumbai place lands inside the mock geometry bounds`() = runBlocking {
        val geometry = MockMumbaiGeometryProvider().geometryFor("mumbai")
        val places = com.citymemory.SeedPlaces.all

        val outOfBounds = places.filterNot { place ->
            place.latitude in geometry.bounds.minLatitude..geometry.bounds.maxLatitude &&
                place.longitude in geometry.bounds.minLongitude..geometry.bounds.maxLongitude
        }

        assertTrue(
            "these places would render outside the map: ${outOfBounds.map { it.name }}",
            outOfBounds.isEmpty(),
        )
    }

    @Test
    fun `fallback outline provides land, green space and roads`() = runBlocking {
        val geometry = MockMumbaiGeometryProvider().geometryFor("mumbai")

        val kinds = geometry.shapes.map { it.kind }.toSet()

        assertEquals(setOf(ShapeKind.LAND, ShapeKind.GREEN, ShapeKind.PRIMARY), kinds)
        assertTrue(geometry.shapes.all { it.size >= 2 })
    }

    @Test
    fun `fallback outline carries no detail geometry to light up`() = runBlocking {
        // Buildings and footpaths only exist in the real OSM asset. The outline
        // has to degrade to warm ground and arterials, not draw nothing at all.
        val geometry = MockMumbaiGeometryProvider().geometryFor("mumbai")

        assertTrue(geometry.shapes.none { it.kind.isDetail })
    }

    @Test
    fun `unknown city yields empty geometry rather than mumbai`() = runBlocking {
        val geometry = MockMumbaiGeometryProvider().geometryFor("paris")

        assertTrue(geometry.shapes.isEmpty())
    }

    private companion object {
        /** Corners, edges and the middle — where an off-by-one origin shows up. */
        val PROBE_POINTS = listOf(
            GeoPoint(18.0, 72.0),
            GeoPoint(20.0, 74.0),
            GeoPoint(19.0, 73.0),
            GeoPoint(18.9220, 72.8347),
            GeoPoint(19.2622, 72.9729),
        )
    }
}
