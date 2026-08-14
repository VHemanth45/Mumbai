package com.citymemory.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.citymemory.ui.map.MapCamera
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The camera is pure arithmetic, and the kind that is easy to get subtly wrong:
 * a pinch that drifts, or a zoom that lets the city slide off the screen, is
 * only obvious on a device. It is all testable without one.
 */
class MapCameraTest {

    private val viewport = Size(1080f, 2200f)

    @Test
    fun `starts framed on the whole city`() {
        val camera = MapCamera()

        assertEquals(MapCamera.MIN_SCALE, camera.scale, 0f)
        assertEquals(Offset.Zero, camera.offset)
    }

    @Test
    fun `pinching keeps the ground under the fingers`() {
        val camera = MapCamera()
        val centroid = Offset(700f, 1500f)
        val groundBefore = camera.screenToWorld(centroid)

        camera.transform(zoom = 2.4f, pan = Offset.Zero, centroid = centroid, viewport = viewport)

        val groundAfter = camera.screenToWorld(centroid)
        assertEquals(groundBefore.x, groundAfter.x, 0.5f)
        assertEquals(groundBefore.y, groundAfter.y, 0.5f)
    }

    @Test
    fun `repeated pinching does not drift`() {
        val camera = MapCamera()
        val centroid = Offset(540f, 1100f)
        val groundBefore = camera.screenToWorld(centroid)

        repeat(30) {
            camera.transform(1.08f, Offset.Zero, centroid, viewport)
        }

        val groundAfter = camera.screenToWorld(centroid)
        assertEquals(groundBefore.x, groundAfter.x, 1f)
        assertEquals(groundBefore.y, groundAfter.y, 1f)
    }

    @Test
    fun `zoom is clamped at both ends`() {
        val camera = MapCamera()

        repeat(60) { camera.transform(1.5f, Offset.Zero, Offset(540f, 1100f), viewport) }
        assertEquals(MapCamera.MAX_SCALE, camera.scale, 0f)

        repeat(120) { camera.transform(0.5f, Offset.Zero, Offset(540f, 1100f), viewport) }
        assertEquals(MapCamera.MIN_SCALE, camera.scale, 0f)
    }

    @Test
    fun `the city cannot be dragged off the screen`() {
        val camera = MapCamera()
        camera.transform(8f, Offset.Zero, Offset(540f, 1100f), viewport)

        repeat(50) { camera.transform(1f, Offset(400f, 400f), Offset(540f, 1100f), viewport) }

        // Top-left of the city may not come inside the viewport...
        assertTrue("offset ${camera.offset}", camera.offset.x <= 0f)
        assertTrue("offset ${camera.offset}", camera.offset.y <= 0f)

        repeat(200) { camera.transform(1f, Offset(-400f, -400f), Offset(540f, 1100f), viewport) }

        // ...and its bottom-right may not either.
        assertTrue(
            "offset ${camera.offset}",
            camera.offset.x + viewport.width * camera.scale >= viewport.width - 0.5f,
        )
        assertTrue(
            "offset ${camera.offset}",
            camera.offset.y + viewport.height * camera.scale >= viewport.height - 0.5f,
        )
    }

    @Test
    fun `zooming back out re-pins the overview exactly`() {
        val camera = MapCamera()
        camera.transform(6f, Offset(-300f, -900f), Offset(200f, 400f), viewport)
        camera.transform(0.02f, Offset.Zero, Offset(900f, 1800f), viewport)

        assertEquals(MapCamera.MIN_SCALE, camera.scale, 0f)
        assertEquals(Offset.Zero, camera.offset)
    }

    @Test
    fun `double tap dives in around the tap and back out again`() {
        val camera = MapCamera()
        val tap = Offset(300f, 1700f)
        val ground = camera.screenToWorld(tap)

        camera.toggleZoom(tap, viewport)
        assertEquals(MapCamera.DETAIL_SCALE, camera.scale, 0f)
        assertEquals(ground.x, camera.screenToWorld(tap).x, 0.5f)

        camera.toggleZoom(tap, viewport)
        assertEquals(MapCamera.MIN_SCALE, camera.scale, 0f)
    }

    @Test
    fun `centring puts a world point in the middle of the viewport`() {
        val camera = MapCamera()
        val target = Offset(540f, 1100f)

        camera.centerOn(target, targetScale = 10f, viewport = viewport)

        val screen = camera.worldToScreen(target)
        assertEquals(viewport.width / 2f, screen.x, 0.5f)
        assertEquals(viewport.height / 2f, screen.y, 0.5f)
    }

    @Test
    fun `world and screen conversions are inverses`() {
        val camera = MapCamera()
        camera.transform(5.5f, Offset(-120f, -260f), Offset(400f, 900f), viewport)

        val screen = Offset(812f, 1466f)
        val round = camera.worldToScreen(camera.screenToWorld(screen))

        assertEquals(screen.x, round.x, 0.01f)
        assertEquals(screen.y, round.y, 0.01f)
    }

    @Test
    fun `the visible world shrinks as you zoom in`() {
        val camera = MapCamera()
        val wide = camera.visibleWorld(viewport)

        camera.transform(10f, Offset.Zero, Offset(540f, 1100f), viewport)
        val close = camera.visibleWorld(viewport)

        assertTrue(close.width < wide.width / 9f)
        assertTrue(close.height < wide.height / 9f)
    }
}
