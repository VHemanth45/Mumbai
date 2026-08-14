package com.citymemory.ui.map

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs
import kotlin.math.pow

/**
 * Every gesture the map understands, in one detector.
 *
 * This replaces a `detectTransformGestures` and a `detectTapGestures` stacked on
 * top of each other, which is the arrangement Compose's samples suggest and
 * which is wrong for a map, for three reasons:
 *
 * **Taps were delayed.** `detectTapGestures` consumes the down and then, because
 * an `onDoubleTap` is set, holds the tap for the double-tap timeout to see
 * whether a second one arrives. Every tap on a place therefore took ~300 ms to
 * do anything. Here the hit test runs on the up, and only a tap that hit
 * *nothing* waits to see if it was the first half of a double-tap — so
 * selecting a place is immediate and double-tap-to-zoom still works.
 *
 * **Pinch started late.** Transform gestures do not begin until the touch slop
 * is crossed, which on a pinch means the first few millimetres of finger travel
 * are swallowed and the map jumps once it catches up. A second finger on screen
 * is already unambiguous, so a two-finger gesture here starts on the very first
 * move event, with no slop at all. One finger still gets slop — it has to, or
 * the map would slide out from under a tap.
 *
 * **Nothing had momentum.** A pan that stops dead the moment the finger lifts
 * reads as stiff no matter how good the frame rate is, so a single-finger drag
 * hands its velocity to [flingPan] on release.
 */
internal suspend fun PointerInputScope.detectMapGestures(
    /** A new touch. Used to kill an in-flight fling before it fights the finger. */
    onGestureStart: () -> Unit,
    onTransform: (centroid: Offset, pan: Offset, zoom: Float) -> Unit,
    /** Release velocity of a drag, in pixels/second. [Velocity.Zero] if it was not a drag. */
    onGestureEnd: (velocity: Velocity) -> Unit,
    /** Returns true if the tap hit something, which suppresses the double-tap wait. */
    onTap: (Offset) -> Boolean,
    onDoubleTap: (Offset) -> Unit,
) {
    val maxFlingVelocity = viewConfiguration.maximumFlingVelocity
    val touchSlop = viewConfiguration.touchSlop

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        onGestureStart()

        val tracker = VelocityTracker()
        // Velocity is tracked against the gesture centroid rather than one
        // finger, so lifting a finger out of a pinch does not register as a
        // sudden flick of whatever finger is left.
        var centroidTrack = down.position
        tracker.addPosition(down.uptimeMillis, centroidTrack)

        var multiTouch = false
        var moving = false
        var slopTravelled = 0f
        var canceled = false

        while (true) {
            val event = awaitPointerEvent()
            if (event.changes.fastAny { it.isConsumed }) {
                canceled = true
                break
            }

            val pressed = event.changes.count { it.pressed }
            if (pressed == 0) break
            if (pressed > 1) multiTouch = true

            val pan = event.calculatePan()
            val zoom = event.calculateZoom()

            if (!moving) {
                // Two fingers down is already a deliberate gesture; one finger
                // has to clear the slop so a tap does not nudge the map.
                moving = if (multiTouch) {
                    true
                } else {
                    slopTravelled += pan.getDistance()
                    slopTravelled > touchSlop
                }
            }

            if (moving) {
                val centroid = event.calculateCentroid(useCurrent = false)
                if (centroid != Offset.Unspecified && (zoom != 1f || pan != Offset.Zero)) {
                    onTransform(centroid, pan, zoom)
                }
                centroidTrack += pan
                tracker.addPosition(event.changes[0].uptimeMillis, centroidTrack)

                // Claiming the moves keeps a parent — a pager, a scrollable
                // sheet — from stealing the gesture halfway through a pinch.
                event.changes.fastForEach { if (it.positionChanged()) it.consume() }
            }
        }

        when {
            canceled -> Unit

            moving -> {
                val raw = tracker.calculateVelocity()
                // A pinch should settle where the fingers left it, not coast.
                val velocity = if (multiTouch) Velocity.Zero else raw.coerceMagnitude(maxFlingVelocity)
                onGestureEnd(velocity)
            }

            // A stationary single touch: a tap.
            !multiTouch -> {
                if (!onTap(down.position)) {
                    val second = awaitSecondDown(down.uptimeMillis)
                    if (second != null) onDoubleTap(second.position)
                }
            }
        }
    }
}

/**
 * Waits out the double-tap window for a second touch, or gives up.
 *
 * Only ever reached by a tap that hit no place, so the wait costs nothing the
 * user can feel.
 */
private suspend fun AwaitPointerEventScope.awaitSecondDown(
    firstDownUptime: Long,
): PointerInputChange? = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
    val minUptime = firstDownUptime + viewConfiguration.doubleTapMinTimeMillis
    var change: PointerInputChange
    do {
        change = awaitFirstDown(requireUnconsumed = false)
    } while (change.uptimeMillis < minUptime)
    change
}

/**
 * Coasts the map to a stop after a drag.
 *
 * A 2-D decay rather than one per axis: exponential decay is the same curve at
 * any speed, so both axes would in fact stay in step, but they would hit the
 * stop threshold at different times and a diagonal fling would hook at the end.
 */
internal suspend fun MapCamera.flingPan(velocity: Velocity, viewport: Size) {
    if (abs(velocity.x) < MIN_FLING_VELOCITY && abs(velocity.y) < MIN_FLING_VELOCITY) return

    var last = Offset.Zero
    AnimationState(
        typeConverter = Offset.VectorConverter,
        initialValue = Offset.Zero,
        initialVelocityVector = AnimationVector2D(velocity.x, velocity.y),
    ).animateDecay(exponentialDecay(frictionMultiplier = FLING_FRICTION)) {
        val delta = value - last
        last = value
        // Stop the moment the city is against the edge, rather than grinding
        // out the rest of the decay with nothing moving.
        if (!panBy(delta, viewport)) cancelAnimation()
    }
}

/**
 * The animated half of a double-tap.
 *
 * Scale is interpolated geometrically, not linearly: zoom is perceived in
 * ratios, so a linear ramp from 1x to 14x spends most of its time at the far
 * end and reads as a lurch followed by a crawl.
 */
internal suspend fun MapCamera.animateZoomTo(target: Float, focus: Offset, viewport: Size) {
    val start = scale
    if (start <= 0f || target <= 0f) return
    val ratio = target / start
    animate(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = tween(durationMillis = ZOOM_DURATION_MILLIS, easing = FastOutSlowInEasing),
    ) { t, _ ->
        zoomTo(start * ratio.pow(t), focus, viewport)
    }
}

private fun Velocity.coerceMagnitude(max: Float): Velocity =
    Velocity(x.coerceIn(-max, max), y.coerceIn(-max, max))

// `fastAny`/`fastForEach` are Compose's allocation-free list helpers; the public
// ones live in an internal package, so the map keeps its own two-line copies
// rather than allocating an iterator per pointer event.
private inline fun <T> List<T>.fastAny(predicate: (T) -> Boolean): Boolean {
    for (i in indices) if (predicate(get(i))) return true
    return false
}

private inline fun <T> List<T>.fastForEach(action: (T) -> Unit) {
    for (i in indices) action(get(i))
}

/** Below this a "fling" is just the noise at the end of a slow drag. */
private const val MIN_FLING_VELOCITY = 80f

/** Higher is stickier. Tuned to stop within about a screen from a hard flick. */
private const val FLING_FRICTION = 1.6f

private const val ZOOM_DURATION_MILLIS = 300
