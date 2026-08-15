package com.citymemory.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.citymemory.domain.model.LabelTier
import com.citymemory.ui.map.LabelPlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Overlapping labels are unreadable labels, and at the overview all 89 postal
 * areas want the same thousand pixels. This is the arithmetic that decides who
 * gets them — the kind that fails as text colliding once every few pans, which
 * is exactly the kind of bug looking at a screen does not reliably catch.
 */
class LabelPlacementTest {

    private val viewport = Size(1080f, 2200f)

    private fun placed() = ArrayList<Rect>()

    /** An area label: centred on its anchor, which is its only placement. */
    private fun LabelPlacement.claimFor(
        center: Offset,
        width: Float,
        height: Float,
        padding: Float,
    ): Rect = claimAt(
        placementTopLeft(LabelTier.AREA, 0, center, width, height, gap = 11f),
        width,
        height,
        padding,
    )

    @Test
    fun `the first label always gets its space`() {
        val space = placed()

        val claimed = LabelPlacement.claim(
            space,
            LabelPlacement.claimFor(Offset(540f, 1100f), 200f, 30f, 5f),
            viewport,
        )

        assertTrue(claimed)
        assertEquals(1, space.size)
    }

    @Test
    fun `a label on top of one already drawn is refused`() {
        val space = placed()
        LabelPlacement.claim(space, LabelPlacement.claimFor(Offset(540f, 1100f), 200f, 30f, 5f), viewport)

        val second = LabelPlacement.claim(
            space,
            LabelPlacement.claimFor(Offset(560f, 1105f), 200f, 30f, 5f),
            viewport,
        )

        assertFalse(second)
        assertEquals("a refused label must not reserve space", 1, space.size)
    }

    @Test
    fun `a label clear of the first is drawn`() {
        val space = placed()
        LabelPlacement.claim(space, LabelPlacement.claimFor(Offset(300f, 400f), 180f, 30f, 5f), viewport)

        val second = LabelPlacement.claim(
            space,
            LabelPlacement.claimFor(Offset(800f, 1400f), 180f, 30f, 5f),
            viewport,
        )

        assertTrue(second)
        assertEquals(2, space.size)
    }

    @Test
    fun `padding is what keeps two names from touching`() {
        // 200 wide each, centres 210 apart: 10 px of clear gap between the
        // glyphs, which reads as one run-on name. The padding rejects it.
        val space = placed()
        LabelPlacement.claim(space, LabelPlacement.claimFor(Offset(400f, 1100f), 200f, 30f, 8f), viewport)

        val tooClose = LabelPlacement.claim(
            space,
            LabelPlacement.claimFor(Offset(610f, 1100f), 200f, 30f, 8f),
            viewport,
        )

        assertFalse(tooClose)
    }

    @Test
    fun `an off-screen label neither draws nor reserves space`() {
        val space = placed()

        val far = LabelPlacement.claim(
            space,
            LabelPlacement.claimFor(Offset(-900f, 1100f), 200f, 30f, 5f),
            viewport,
        )

        assertFalse(far)
        // The important half: a name nobody can see must not deny its space to
        // one they can, so it is refused *before* being recorded.
        assertEquals(0, space.size)
    }

    @Test
    fun `a label half off the edge is still drawn`() {
        val space = placed()

        val edge = LabelPlacement.claim(
            space,
            LabelPlacement.claimFor(Offset(20f, 1100f), 200f, 30f, 5f),
            viewport,
        )

        // Dropping it would make names pop in and out as the map pans, which
        // is worse than a clipped one.
        assertTrue(edge)
    }

    @Test
    fun `earlier labels win, which is what makes areas beat places`() {
        val space = placed()
        val contested = Offset(540f, 1100f)

        val area = LabelPlacement.claim(space, LabelPlacement.claimFor(contested, 200f, 30f, 5f), viewport)
        val place = LabelPlacement.claim(space, LabelPlacement.claimFor(contested, 120f, 28f, 5f), viewport)

        assertTrue("the area name is offered first and must keep the space", area)
        assertFalse("the place name must give way", place)
    }

    @Test
    fun `a crowded overview keeps a readable subset rather than everything`() {
        val space = placed()
        // 40 names all wanting a 300 px band down the middle of the screen.
        val claimed = (0 until 40).count { i ->
            LabelPlacement.claim(
                space,
                LabelPlacement.claimFor(Offset(540f, 1000f + i * 8f), 220f, 32f, 5f),
                viewport,
            )
        }

        assertTrue("expected some names to survive, got $claimed", claimed in 1..12)
        assertEquals(claimed, space.size)
    }

    // ---- Point labels sit beside their marker, not on it --------------------

    /**
     * Every place label is drawn at the same projected coordinate as the marker
     * `drawPlaces` puts there, so centring one puts the name on top of its own
     * dot. All 130 of them used to do exactly that.
     */
    @Test
    fun `a place label never covers the marker it names`() {
        val marker = Offset(540f, 1100f)

        for (index in 0 until LabelPlacement.placementCount(LabelTier.PLACE)) {
            val topLeft = LabelPlacement.placementTopLeft(
                LabelTier.PLACE, index, marker, width = 160f, height = 30f, gap = 11f,
            )
            val rect = LabelPlacement.claimAt(topLeft, 160f, 30f, padding = 0f)
            assertFalse(
                "placement $index sits on the marker",
                rect.contains(marker),
            )
        }
    }

    @Test
    fun `an area label is centred, because it names a region and not a point`() {
        val pole = Offset(540f, 1100f)

        assertEquals(1, LabelPlacement.placementCount(LabelTier.AREA))
        val topLeft = LabelPlacement.placementTopLeft(
            LabelTier.AREA, 0, pole, width = 160f, height = 30f, gap = 11f,
        )
        assertEquals(540f - 80f, topLeft.x, 0.01f)
        assertEquals(1100f - 15f, topLeft.y, 0.01f)
    }

    @Test
    fun `a place label takes the left box when the right one is occupied`() {
        val marker = Offset(540f, 1100f)
        val space = placed()
        // Something already owns the space to the right of the marker.
        val right = LabelPlacement.placementTopLeft(
            LabelTier.PLACE, 0, marker, 160f, 30f, gap = 11f,
        )
        LabelPlacement.claim(space, LabelPlacement.claimAt(right, 160f, 30f, 5f), viewport)

        val left = LabelPlacement.placementTopLeft(
            LabelTier.PLACE, 1, marker, 160f, 30f, gap = 11f,
        )
        val taken = LabelPlacement.claim(space, LabelPlacement.claimAt(left, 160f, 30f, 5f), viewport)

        assertTrue("the left box should still be free", taken)
        assertTrue("and it should be to the left of the marker", left.x + 160f < marker.x)
    }
}
