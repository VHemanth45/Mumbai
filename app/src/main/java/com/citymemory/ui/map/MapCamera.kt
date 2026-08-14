package com.citymemory.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size

/**
 * Where the map is looked at from: a uniform scale about the top-left of the
 * projected city, plus a translation.
 *
 * ```
 * screen = world * scale + offset
 * ```
 *
 * Zoom exists because the feature needs it. A lit area is a few hundred metres
 * across, and the whole city on a phone is roughly 25 metres to the pixel — at
 * the overview an explored neighbourhood is a seventeen-pixel smudge. Being
 * able to go down to about a metre per pixel is what turns "a light on the map"
 * into "the streets I walked".
 *
 * The camera is state, not geometry: [MapPaths] projects once at scale 1 and
 * this rides on top as a canvas transform, so panning and zooming never rebuild
 * a path.
 */
@Stable
class MapCamera(scale: Float = MIN_SCALE, offset: Offset = Offset.Zero) {

    var scale by mutableFloatStateOf(scale)
        private set

    var offset by mutableStateOf(offset)
        private set

    /** Applies one pinch/drag gesture step, keeping [centroid] over the same ground. */
    fun transform(zoom: Float, pan: Offset, centroid: Offset, viewport: Size) {
        val next = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
        // Solve for the offset that leaves the world point under the centroid
        // where it was: centroid = world * next + offset', world = (centroid - offset) / scale.
        val zoomed = centroid - (centroid - offset) * (next / scale)
        scale = next
        offset = constrain(zoomed + pan, next, viewport)
    }

    /** Double-tap: dive to street level around [focus], or back out if already there. */
    fun toggleZoom(focus: Offset, viewport: Size) {
        val target = if (scale < DETAIL_SCALE * 0.9f) DETAIL_SCALE else MIN_SCALE
        val zoomed = focus - (focus - offset) * (target / scale)
        scale = target
        offset = constrain(zoomed, target, viewport)
    }

    /** Frames [worldPoint] in the middle of the viewport at [targetScale]. */
    fun centerOn(worldPoint: Offset, targetScale: Float, viewport: Size) {
        val next = targetScale.coerceIn(MIN_SCALE, MAX_SCALE)
        val centred = Offset(viewport.width / 2f, viewport.height / 2f) - worldPoint * next
        scale = next
        offset = constrain(centred, next, viewport)
    }

    fun worldToScreen(world: Offset): Offset = world * scale + offset

    fun screenToWorld(screen: Offset): Offset = (screen - offset) / scale

    /** The rectangle of world space currently on screen, used to cull tiles. */
    fun visibleWorld(viewport: Size): Rect {
        val topLeft = screenToWorld(Offset.Zero)
        val bottomRight = screenToWorld(Offset(viewport.width, viewport.height))
        return Rect(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y)
    }

    /**
     * Keeps the city covering the viewport, so the map can never be flung off
     * into empty space. At [MIN_SCALE] both bounds collapse to zero, which pins
     * the overview exactly where the projector framed it.
     */
    private fun constrain(candidate: Offset, forScale: Float, viewport: Size): Offset {
        if (viewport.width <= 0f || viewport.height <= 0f) return candidate
        return Offset(
            x = candidate.x.coerceIn(viewport.width - viewport.width * forScale, 0f),
            y = candidate.y.coerceIn(viewport.height - viewport.height * forScale, 0f),
        )
    }

    companion object {
        const val MIN_SCALE = 1f

        /** ~0.8 m/px on a phone — close enough to pick out individual buildings. */
        const val MAX_SCALE = 32f

        /** Where a double-tap lands: a neighbourhood, not a building. */
        const val DETAIL_SCALE = 14f

        val Saver: Saver<MapCamera, List<Float>> = Saver(
            save = { listOf(it.scale, it.offset.x, it.offset.y) },
            restore = { MapCamera(it[0], Offset(it[1], it[2])) },
        )
    }
}

@Composable
internal fun rememberMapCamera(): MapCamera =
    rememberSaveable(saver = MapCamera.Saver) { MapCamera() }
