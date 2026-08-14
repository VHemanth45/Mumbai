package com.citymemory.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.citymemory.ui.map.detectMapGestures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The map's gesture detector, which is the part of the zoom the user actually
 * feels.
 *
 * These are the properties that were wrong when the map stacked Compose's
 * `detectTransformGestures` and `detectTapGestures` on top of each other, and
 * that no unit test would have caught because both of those are themselves
 * well tested — the bug was in the combination.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class MapGestureTest {

    @get:Rule
    val compose = createComposeRule()

    private var totalZoom = 1f
    private var totalPan = Offset.Zero
    private var transforms = 0
    private var tapped: Offset? = null
    private var doubleTapped: Offset? = null
    private var endVelocity: Velocity? = null

    /** What a tap should report back: true means it landed on a place. */
    private var tapHits = false

    @Composable
    private fun Harness() {
        Box(
            Modifier
                .size(400.dp)
                .testTag(TAG)
                .pointerInput(Unit) {
                    detectMapGestures(
                        onGestureStart = {},
                        onTransform = { _, pan, zoom ->
                            totalZoom *= zoom
                            totalPan += pan
                            transforms++
                        },
                        onGestureEnd = { endVelocity = it },
                        onTap = {
                            tapped = it
                            tapHits
                        },
                        onDoubleTap = { doubleTapped = it },
                    )
                },
        )
    }

    private fun start() = compose.setContent { Harness() }

    private fun surface() = compose.onNodeWithTag(TAG)

    // -- Pinch --------------------------------------------------------------

    @Test
    fun `a pinch reports the full zoom, with none of it eaten by touch slop`() {
        start()

        // Two fingers 100px apart, spread to 200px: exactly a doubling.
        surface().performTouchInput {
            down(0, Offset(150f, 200f))
            down(1, Offset(250f, 200f))
            moveTo(0, Offset(125f, 200f))
            moveTo(1, Offset(275f, 200f))
            moveTo(0, Offset(100f, 200f))
            moveTo(1, Offset(300f, 200f))
            up(0)
            up(1)
        }
        compose.waitForIdle()

        // If the detector applied touch slop to the pinch — which is what
        // `detectTransformGestures` does — the first millimetres would be
        // swallowed and this would come out short of 2.
        assertEquals(2f, totalZoom, 0.01f)
    }

    @Test
    fun `a pinch starts on its very first move, with no dead zone`() {
        start()

        surface().performTouchInput {
            down(0, Offset(150f, 200f))
            down(1, Offset(250f, 200f))
            // One small move, well inside the touch slop a drag would need.
            moveTo(0, Offset(147f, 200f))
        }
        compose.waitForIdle()

        assertTrue("no transform was reported for the first pinch move", transforms > 0)
    }

    @Test
    fun `a pinch settles where the fingers left it rather than coasting`() {
        start()

        surface().performTouchInput {
            down(0, Offset(150f, 200f))
            down(1, Offset(250f, 200f))
            moveTo(0, Offset(100f, 200f))
            moveTo(1, Offset(300f, 200f))
            up(0)
            up(1)
        }
        compose.waitForIdle()

        assertEquals(Velocity.Zero, endVelocity)
    }

    // -- Pan ----------------------------------------------------------------

    @Test
    fun `one finger has to clear the touch slop before the map moves`() {
        start()

        surface().performTouchInput {
            down(Offset(200f, 200f))
            moveTo(Offset(202f, 200f))
            up()
        }
        compose.waitForIdle()

        assertEquals("a tap must not nudge the map", 0, transforms)
    }

    @Test
    fun `a long enough drag pans and hands back a velocity to fling with`() {
        start()

        surface().performTouchInput {
            down(Offset(200f, 200f))
            moveTo(Offset(230f, 200f))
            moveTo(Offset(280f, 200f))
            moveTo(Offset(330f, 200f))
            up()
        }
        compose.waitForIdle()

        assertTrue("the drag never panned", totalPan.x > 0f)
        assertNull("a drag is not a tap", tapped)
        assertTrue("a drag should end with a velocity", endVelocity != null)
    }

    // -- Taps ---------------------------------------------------------------

    @Test
    fun `a tap on a place is reported and never waits to become a double tap`() {
        tapHits = true
        start()

        surface().performTouchInput {
            down(Offset(200f, 200f))
            up()
        }
        compose.waitForIdle()

        assertEquals(Offset(200f, 200f), tapped)
        // The old stack held every tap for the double-tap timeout before
        // reporting it. A tap that hit a place is unambiguous, so it must not.
        assertNull("a tap that hit a place must not turn into a zoom", doubleTapped)
    }

    @Test
    fun `two taps on empty map zoom in`() {
        tapHits = false
        start()

        // Both taps in one injection, so the gap between them is controlled
        // rather than however long the test framework took to go idle.
        surface().performTouchInput {
            down(Offset(200f, 200f))
            up()
            advanceEventTime(60)
            down(Offset(200f, 200f))
            up()
        }
        compose.waitForIdle()

        assertEquals(Offset(200f, 200f), doubleTapped)
    }

    @Test
    fun `a lone tap on empty map is still reported`() {
        tapHits = false
        start()

        surface().performTouchInput {
            down(Offset(120f, 90f))
            up()
        }
        compose.waitForIdle()

        assertEquals(Offset(120f, 90f), tapped)
        assertNull(doubleTapped)
    }

    private companion object {
        const val TAG = "map-surface"
    }
}
