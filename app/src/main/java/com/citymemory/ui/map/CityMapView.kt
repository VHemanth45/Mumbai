package com.citymemory.ui.map

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.citymemory.domain.model.CityGeometry
import com.citymemory.domain.model.Place
import com.citymemory.ui.theme.DimSlate
import com.citymemory.ui.theme.GlowAmber
import com.citymemory.ui.theme.GlowCore
import com.citymemory.ui.theme.WishCyan
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The dark city that lights up.
 *
 * Renders real OpenStreetMap geometry — coastline, water, parkland, the road
 * hierarchy, building footprints — for a whole city, and then hides almost all
 * of it. The unlit city is drawn barely above the background: a coastline you
 * can just find, and the ghost of the arterials. Nothing else.
 *
 * Where you have actually been, the same geometry is drawn again in warm
 * sodium light and masked down to a soft-edged disc around the place, so an
 * explored place is not a dot on a map — it is *the streets around it*, legible
 * down to the buildings, with the rest of the city still dark around them.
 *
 * How the reveal is composited, and why it is two layers rather than one:
 *
 * ```
 * saveLayer A                      the lit city
 *   warm wash + lit geometry
 *   saveLayer B (blend = DstIn)    the mask
 *     one soft radial disc per visited place
 *   restore B  -> multiplies A's alpha by the mask
 * restore A    -> composites what survived over the dark city
 * ```
 *
 * The mask needs its own layer because discs overlap. Punching each disc
 * straight into A with `DstIn` would let the transparent rim of a second disc
 * erase the solid centre of the first, so two nearby explored places would
 * carve holes in each other. Accumulating them in B first turns overlap into
 * union, which is what "both of these are lit" should mean.
 *
 * The renderer knows nothing about Mumbai, Room or the dataset: it takes
 * geometry in lat/lng and places, and draws them.
 */
@Composable
fun CityMapView(
    geometry: CityGeometry,
    places: List<Place>,
    modifier: Modifier = Modifier,
    selectedPlaceId: String? = null,
    onPlaceSelected: (Place) -> Unit = {},
) {
    val camera = rememberMapCamera()

    // Projecting the whole city is ~387,000 points of arithmetic — near a
    // second of it on a mid-range device, and it needs the canvas size, so it
    // cannot happen before layout. Doing it inside the draw pass would freeze
    // the first frame, so it happens off the main thread and the map fades in
    // when it is ready. `MapPaths` touches no Skia objects, which is what makes
    // that safe; the Path objects themselves are still built on the UI thread,
    // lazily, one tile at a time.
    val density = LocalDensity.current
    var viewport by remember { mutableStateOf(IntSize.Zero) }

    val prepared by produceState<PreparedMap?>(null, geometry, viewport, density) {
        val canvas = viewport.toSize()
        if (geometry.shapes.isEmpty() || canvas.minDimension <= 0f) {
            value = null
            return@produceState
        }
        val padding = with(density) { MAP_PADDING.toPx() }
        value = withContext(Dispatchers.Default) {
            val projector = GeoProjector(geometry.bounds, canvas, padding)
            PreparedMap(
                projector = projector,
                paths = MapPaths(geometry, projector),
                revealRadius = projector.metersToPixels(MapStyle.RevealRadiusMeters),
            )
        }
    }

    // A slow shared breath so a lit area feels alive rather than stencilled.
    val breath = rememberInfiniteTransition(label = "breathing").animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath",
    )

    // Read only while zoomed out. This is not just taste — an animation that is
    // never read is an animation this composable never recomposes for, so at
    // street level the map stops redrawing entirely instead of re-rasterising
    // every building sixty times a second to pulse them by three percent. It is
    // also what you want to look at: a map you are reading should hold still.
    val breathing = if (camera.scale < BREATHING_MAX_SCALE) breath.value else 1f

    // One-shot: light spreading outward when a place is newly marked visited.
    val visitedIds = remember(places) { places.filter { it.isVisited }.map { it.id }.toSet() }
    var knownVisitedIds by remember { mutableStateOf(visitedIds) }
    var bloomingPlaceId by remember { mutableStateOf<String?>(null) }
    val bloom = remember { Animatable(0f) }

    LaunchedEffect(visitedIds) {
        val newlyVisited = (visitedIds - knownVisitedIds).singleOrNull()
        knownVisitedIds = visitedIds
        if (newlyVisited != null) {
            bloomingPlaceId = newlyVisited
            bloom.snapTo(0f)
            bloom.animateTo(1f, tween(durationMillis = 1400, easing = FastOutSlowInEasing))
            bloomingPlaceId = null
        }
    }

    // Read inside the draw pass rather than captured by it, so that marking a
    // single place visited repaints without disturbing anything upstream.
    val placesState = rememberUpdatedState(places)
    val selectedState = rememberUpdatedState(selectedPlaceId)
    val bloomingState = rememberUpdatedState(bloomingPlaceId)
    val bloomState = rememberUpdatedState(bloom.value)
    val breathingState = rememberUpdatedState(breathing)
    val currentOnPlaceSelected by rememberUpdatedState(onPlaceSelected)

    val visitedCount = places.count { it.isVisited }
    val description =
        "Map of the city. $visitedCount of ${places.size} places explored. " +
            "Pinch to zoom into an explored area, double tap to jump in."

    Spacer(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = description }
            .onSizeChanged { viewport = it }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    camera.transform(zoom, pan, centroid, size.toSize())
                }
            }
            .pointerInput(prepared) {
                val projector = prepared?.projector ?: return@pointerInput
                val slop = TAP_SLOP.toPx()
                detectTapGestures(
                    onDoubleTap = { camera.toggleZoom(it, size.toSize()) },
                    onTap = { tap ->
                        placesState.value
                            .minByOrNull {
                                (camera.worldToScreen(projector.project(it.location)) - tap)
                                    .getDistanceSquared()
                            }
                            ?.takeIf {
                                (camera.worldToScreen(projector.project(it.location)) - tap)
                                    .getDistance() <= slop
                            }
                            ?.let(currentOnPlaceSelected)
                    },
                )
            }
            .drawBehind {
                val map = prepared
                if (map == null) {
                    drawNight()
                    return@drawBehind
                }
                drawCity(
                    paths = map.paths,
                    projector = map.projector,
                    camera = camera,
                    places = placesState.value,
                    revealRadius = map.revealRadius,
                    breathing = breathingState.value,
                    selectedPlaceId = selectedState.value,
                    bloomingPlaceId = bloomingState.value,
                    bloomProgress = bloomState.value,
                )
            },
    )
}

// ---------------------------------------------------------------------------
// Drawing
// ---------------------------------------------------------------------------

/** Everything the draw pass needs that depends on the canvas size. */
private class PreparedMap(
    val projector: GeoProjector,
    val paths: MapPaths,
    /** [MapStyle.RevealRadiusMeters] in world (scale-1) pixels. */
    val revealRadius: Float,
)

/** A place's lit area, in world (scale-1) coordinates. */
private class Reveal(val center: Offset, val radius: Float)

private fun DrawScope.drawCity(
    paths: MapPaths,
    projector: GeoProjector,
    camera: MapCamera,
    places: List<Place>,
    revealRadius: Float,
    breathing: Float,
    selectedPlaceId: String?,
    bloomingPlaceId: String?,
    bloomProgress: Float,
) {
    val scale = camera.scale
    val offset = camera.offset
    val viewport = size
    val visible = camera.visibleWorld(viewport)

    drawNight()

    withTransform({
        translate(offset.x, offset.y)
        scale(scale, scale, pivot = Offset.Zero)
    }) {
        drawLayers(paths, visible, lit = false, scale = scale)
    }

    val reveals = places.buildReveals(
        projector = projector,
        radius = revealRadius,
        breathing = breathing,
        bloomingPlaceId = bloomingPlaceId,
        bloomProgress = bloomProgress,
        visible = visible,
    )

    if (reveals.isNotEmpty()) {
        drawLitAreas(paths, camera, reveals, viewport)
    }

    drawPlaces(
        places = places,
        projector = projector,
        camera = camera,
        breathing = breathing,
        selectedPlaceId = selectedPlaceId,
        bloomingPlaceId = bloomingPlaceId,
        bloomProgress = bloomProgress,
    )
}

private fun DrawScope.drawNight() {
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                MapStyle.BackgroundTop,
                MapStyle.Background,
                MapStyle.BackgroundBottom,
            ),
        ),
    )
}

/**
 * Which places are lit, how wide, and only those that could touch the screen.
 */
private fun List<Place>.buildReveals(
    projector: GeoProjector,
    radius: Float,
    breathing: Float,
    bloomingPlaceId: String?,
    bloomProgress: Float,
    visible: Rect,
): List<Reveal> {
    var reveals: MutableList<Reveal>? = null
    for (place in this) {
        if (!place.isVisited) continue
        val center = projector.project(place.location)
        // A place lighting up for the first time spreads rather than snaps on.
        val growth = if (place.id == bloomingPlaceId) {
            BLOOM_START_FRACTION + (1f - BLOOM_START_FRACTION) * bloomProgress
        } else {
            1f
        }
        val r = radius * growth * breathing
        if (center.x + r < visible.left || center.x - r > visible.right) continue
        if (center.y + r < visible.top || center.y - r > visible.bottom) continue
        (reveals ?: ArrayList<Reveal>().also { reveals = it }) += Reveal(center, r)
    }
    return reveals ?: emptyList()
}

/**
 * The lit city, masked to the explored areas. See the class docs for why the
 * mask gets its own layer.
 */
private fun DrawScope.drawLitAreas(
    paths: MapPaths,
    camera: MapCamera,
    reveals: List<Reveal>,
    viewport: Size,
) {
    val scale = camera.scale

    // Haze belongs to the distant view: at the overview it is what tells you
    // an area is lit at all, and by street level it would only fog the streets.
    val haze = lerp(0.16f, 0.02f, normalize(scale, 1f, 10f))
    if (haze > 0.01f) {
        for (reveal in reveals) {
            val center = camera.worldToScreen(reveal.center)
            val radius = reveal.radius * scale * HALO_SPREAD
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        MapStyle.LightHalo.copy(alpha = haze),
                        MapStyle.LightHalo.copy(alpha = haze * 0.25f),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
    }

    // Only tiles that could fall inside a lit area need drawing at all.
    val worldExtent = reveals.extent()
    val litExtent = worldExtent.intersectOrNull(camera.visibleWorld(viewport)) ?: return

    // The layer is sized to the lit areas, not to the screen. `saveLayer`
    // bounds also clip, so this both shrinks two full-screen offscreen buffers
    // and saves Skia from rasterising streets the mask is about to discard.
    // Zoomed into one place it is typically a fraction of the screen.
    val topLeft = camera.worldToScreen(Offset(worldExtent.left, worldExtent.top))
    val bottomRight = camera.worldToScreen(Offset(worldExtent.right, worldExtent.bottom))
    val layerBounds = Rect(topLeft, bottomRight)
        .intersectOrNull(Rect(Offset.Zero, viewport))
        ?: return

    val canvas = drawContext.canvas

    canvas.saveLayer(layerBounds, Paint())

    // Warm ground under the streets, so a lit area reads as illuminated rather
    // than as a hole cut in the dark.
    for (reveal in reveals) {
        val center = camera.worldToScreen(reveal.center)
        val radius = reveal.radius * scale
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    MapStyle.LightWash.copy(alpha = 0.15f),
                    MapStyle.LightWash.copy(alpha = 0.06f),
                    MapStyle.LightWash.copy(alpha = 0.02f),
                ),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
    }

    withTransform({
        translate(camera.offset.x, camera.offset.y)
        scale(scale, scale, pivot = Offset.Zero)
    }) {
        drawLayers(paths, litExtent, lit = true, scale = scale)
    }

    canvas.saveLayer(layerBounds, Paint().apply { blendMode = BlendMode.DstIn })
    for (reveal in reveals) {
        val center = camera.worldToScreen(reveal.center)
        val radius = reveal.radius * scale
        drawCircle(
            brush = Brush.radialGradient(
                // Solid to well past halfway, then feathered — a lit district
                // with a soft edge, not a vignette that is only bright dead
                // centre.
                colorStops = arrayOf(
                    0.0f to Color.White,
                    MASK_SOLID_FRACTION to Color.White,
                    1.0f to Color.Transparent,
                ),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
    }
    canvas.restore()

    canvas.restore()
}

private fun DrawScope.drawLayers(
    paths: MapPaths,
    visible: Rect,
    lit: Boolean,
    scale: Float,
) {
    for (layer in MapStyle.layers) {
        if (scale < layer.minScale) continue
        val style = if (lit) layer.lit else layer.unlit
        val fill = style.fill
        val stroke = style.stroke
        if (fill == null && stroke == null) continue

        // Widths are screen-space, but we are drawing inside the camera
        // transform, so they have to be divided back out.
        val strokeStyle = if (stroke == null) {
            null
        } else {
            Stroke(
                width = max(style.widthDp.dp.toPx() / scale, MIN_STROKE_PX / scale),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            )
        }

        val coarse = scale < MapPaths.COARSE_MAX_SCALE
        paths.forEachTile(layer.kind, visible, coarse) { path ->
            if (fill != null) drawPath(path, color = fill)
            if (stroke != null && strokeStyle != null) {
                drawPath(path, color = stroke, style = strokeStyle)
            }
        }
    }
}

private fun DrawScope.drawPlaces(
    places: List<Place>,
    projector: GeoProjector,
    camera: MapCamera,
    breathing: Float,
    selectedPlaceId: String?,
    bloomingPlaceId: String?,
    bloomProgress: Float,
) {
    // As you zoom in, the marker glow hands over to the lit streets themselves.
    // At the overview it is the only thing carrying "I have been here".
    val glow = 1f - normalize(camera.scale, 1f, 9f)
    val screenOf = { place: Place -> camera.worldToScreen(projector.project(place.location)) }

    // Three passes, so a lit place is never occluded by a dim one.
    for (place in places) {
        if (place.isVisited || place.isWishlisted) continue
        drawUnvisited(screenOf(place))
    }
    for (place in places) {
        if (place.isVisited || !place.isWishlisted) continue
        drawWishlisted(screenOf(place), breathing)
    }
    for (place in places) {
        if (!place.isVisited) continue
        drawVisited(screenOf(place), breathing, glow)
    }

    selectedPlaceId
        ?.let { id -> places.firstOrNull { it.id == id } }
        ?.let { drawSelectionRing(screenOf(it)) }

    if (bloomingPlaceId != null && bloomProgress < 1f) {
        places.firstOrNull { it.id == bloomingPlaceId }
            ?.let { drawBloom(screenOf(it), bloomProgress) }
    }
}

private fun DrawScope.drawUnvisited(center: Offset) {
    drawCircle(color = DimSlate.copy(alpha = 0.42f), radius = 1.9.dp.toPx(), center = center)
}

private fun DrawScope.drawWishlisted(center: Offset, breathing: Float) {
    val ringRadius = 4.6.dp.toPx() * breathing
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(WishCyan.copy(alpha = 0.22f), Color.Transparent),
            center = center,
            radius = 11.dp.toPx(),
        ),
        radius = 11.dp.toPx(),
        center = center,
    )
    drawCircle(
        color = WishCyan.copy(alpha = 0.85f),
        radius = ringRadius,
        center = center,
        style = Stroke(width = 1.5.dp.toPx()),
    )
    drawCircle(color = WishCyan.copy(alpha = 0.55f), radius = 1.2.dp.toPx(), center = center)
}

private fun DrawScope.drawVisited(center: Offset, breathing: Float, glow: Float) {
    if (glow > 0.01f) {
        val haloRadius = 15.dp.toPx() * breathing
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    GlowAmber.copy(alpha = 0.34f * glow),
                    GlowAmber.copy(alpha = 0.07f * glow),
                    Color.Transparent,
                ),
                center = center,
                radius = haloRadius,
            ),
            radius = haloRadius,
            center = center,
        )
    }
    // The core never fades all the way out: it stays the tap target, and the
    // anchor that says which point of the lit area is the place itself.
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(GlowCore.copy(alpha = 0.55f), Color.Transparent),
            center = center,
            radius = 5.dp.toPx(),
        ),
        radius = 5.dp.toPx(),
        center = center,
    )
    drawCircle(color = GlowCore.copy(alpha = 0.92f), radius = 2.2.dp.toPx(), center = center)
}

private fun DrawScope.drawSelectionRing(center: Offset) {
    drawCircle(
        color = Color.White.copy(alpha = 0.55f),
        radius = 11.dp.toPx(),
        center = center,
        style = Stroke(width = 1.2.dp.toPx()),
    )
}

/** An expanding ring, fading as it grows — the "this just lit up" moment. */
private fun DrawScope.drawBloom(center: Offset, progress: Float) {
    val radius = (7.dp.toPx()) + (34.dp.toPx() - 7.dp.toPx()) * progress
    drawCircle(
        color = GlowCore.copy(alpha = 0.5f * (1f - progress)),
        radius = radius,
        center = center,
        style = Stroke(width = 2.dp.toPx() * (1f - progress) + 0.5f),
    )
}

// ---------------------------------------------------------------------------

/** The bounding box of every lit area, in world coordinates. */
private fun List<Reveal>.extent(): Rect {
    var left = Float.MAX_VALUE
    var top = Float.MAX_VALUE
    var right = -Float.MAX_VALUE
    var bottom = -Float.MAX_VALUE
    for (r in this) {
        left = min(left, r.center.x - r.radius)
        top = min(top, r.center.y - r.radius)
        right = max(right, r.center.x + r.radius)
        bottom = max(bottom, r.center.y + r.radius)
    }
    return Rect(left, top, right, bottom)
}

private fun Rect.intersectOrNull(other: Rect): Rect? {
    val left = max(left, other.left)
    val top = max(top, other.top)
    val right = min(right, other.right)
    val bottom = min(bottom, other.bottom)
    return if (left < right && top < bottom) Rect(left, top, right, bottom) else null
}

/** Where [value] sits in [from]..[to], clamped to 0..1. */
private fun normalize(value: Float, from: Float, to: Float): Float =
    ((value - from) / (to - from)).coerceIn(0f, 1f)

private fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction

/**
 * A lit area starts at a fraction of its size and grows. Not from zero: a
 * light switching on is instant, and only the *reach* of it spreads.
 */
private const val BLOOM_START_FRACTION = 0.35f

/** Fraction of the reveal radius that is fully lit before the edge feathers. */
private const val MASK_SOLID_FRACTION = 0.58f

/** How far the atmospheric haze reaches past the lit area itself. */
private const val HALO_SPREAD = 1.7f

/** Past this zoom the map stops breathing, and stops redrawing with it. */
private const val BREATHING_MAX_SCALE = 4f

/** Below about half a pixel a stroke stops being drawn at all. */
private const val MIN_STROKE_PX = 0.6f

private val MAP_PADDING: Dp = 20.dp

/** Generous, because the lights are small and fingers are not. */
private val TAP_SLOP: Dp = 22.dp
