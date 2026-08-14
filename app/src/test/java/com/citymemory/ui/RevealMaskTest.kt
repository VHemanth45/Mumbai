package com.citymemory.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The two ways the map masks a lit area down to a soft disc must agree.
 *
 * `CityMapView` accumulates the reveal discs in their own `saveLayer` so that
 * overlapping ones union rather than punch holes in each other, but a single
 * disc has nothing to overlap and can be composited straight into the layer
 * below, saving an offscreen buffer in exactly the case the map spends most of
 * its time in — zoomed into one place.
 *
 * The subtlety, and the reason this is worth a pixel test: `DstIn` only affects
 * the pixels a draw actually covers. Restoring a layer applies it across the
 * whole layer, but drawing a *circle* with it applies it only inside the
 * circle, leaving the corners of the layer untouched and fully bright. The fast
 * path therefore fills the layer with the gradient rather than stroking a disc
 * with it, and this test is what says those are the same picture.
 *
 * Rendered straight into a bitmap rather than through a Compose rule: there is
 * no composition involved in the thing under test, only a draw scope.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RevealMaskTest {

    private val side = 120
    private val center = Offset(side / 2f, side / 2f)
    private val radius = side / 2f

    private fun maskBrush() = Brush.radialGradient(
        colorStops = arrayOf(
            0.0f to Color.White,
            0.58f to Color.White,
            1.0f to Color.Transparent,
        ),
        center = Offset.Zero,
        radius = radius,
    )

    /**
     * Draws something opaque across the whole layer — standing in for the lit
     * city — and then masks it the way [mask] says.
     */
    private fun render(mask: DrawScope.(Rect) -> Unit): PixelMap {
        val image = ImageBitmap(side, side)
        val canvas = Canvas(image)
        val bounds = Rect(0f, 0f, side.toFloat(), side.toFloat())
        CanvasDrawScope().draw(
            density = Density(1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = canvas,
            size = Size(side.toFloat(), side.toFloat()),
        ) {
            val target = drawContext.canvas
            target.saveLayer(bounds, Paint())
            drawRect(color = Color.Red)
            mask(bounds)
            target.restore()
        }
        return image.toPixelMap()
    }

    /** The path used when two or more places are lit. */
    private val layeredMask: DrawScope.(Rect) -> Unit = { bounds ->
        val target = drawContext.canvas
        target.saveLayer(bounds, Paint().apply { blendMode = BlendMode.DstIn })
        translate(center.x, center.y) {
            drawCircle(brush = maskBrush(), radius = radius, center = Offset.Zero)
        }
        target.restore()
    }

    /** The fast path used when exactly one place is lit. */
    private val directMask: DrawScope.(Rect) -> Unit = { bounds ->
        translate(center.x, center.y) {
            drawRect(
                brush = maskBrush(),
                topLeft = Offset(bounds.left - center.x, bounds.top - center.y),
                size = bounds.size,
                blendMode = BlendMode.DstIn,
            )
        }
    }

    /** What the fast path would look like if it drew a disc instead of a rect. */
    private val discMask: DrawScope.(Rect) -> Unit = {
        translate(center.x, center.y) {
            drawCircle(
                brush = maskBrush(),
                radius = radius,
                center = Offset.Zero,
                blendMode = BlendMode.DstIn,
            )
        }
    }

    @Test
    fun `the single-disc fast path masks the same pixels as the layer it replaces`() {
        val layered = render(layeredMask)
        val direct = render(directMask)

        var worst = 0f
        for (y in 0 until side) {
            for (x in 0 until side) {
                val a = layered[x, y]
                val b = direct[x, y]
                worst = maxOf(worst, kotlin.math.abs(a.alpha - b.alpha))
                worst = maxOf(worst, kotlin.math.abs(a.red - b.red))
            }
        }
        // One unit of 8-bit rounding apart: the layered path quantises to the
        // offscreen buffer and back, the direct one does not. Anything the eye
        // could find would be orders of magnitude larger than this.
        assertTrue("masks differ by ${worst * 255}/255 at worst", worst * 255f <= 1.01f)
    }

    @Test
    fun `masking with a disc would leave the corners lit, which is the bug`() {
        // Not a description of current behaviour — a guard on the reasoning. If
        // this ever stops holding, the fast path could go back to a circle.
        val disc = render(discMask)

        assertEquals(
            "a circle drawn with DstIn cannot reach the corners",
            1f,
            disc[1, 1].alpha,
            0.01f,
        )
    }

    @Test
    fun `the fast path leaves the corners dark and the centre lit`() {
        val direct = render(directMask)

        assertEquals("the corner should be fully masked out", 0f, direct[1, 1].alpha, 0.01f)
        assertEquals("the centre should be fully lit", 1f, direct[side / 2, side / 2].alpha, 0.01f)
    }
}
