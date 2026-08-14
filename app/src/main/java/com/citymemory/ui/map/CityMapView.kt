package com.citymemory.ui.map

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
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
 * union, which is what "both of these are lit" should mean. With a single disc
 * there is nothing to overlap, and that layer is skipped — see [drawLitAreas].
 *
 * **Everything that moves is read in the draw pass, not in composition.** Zoom,
 * pan, the breathing and the bloom are all snapshot state read inside
 * `drawBehind`, so a pinch invalidates drawing and nothing else. Reading any of
 * them in the composable body instead — which is the easy mistake, and what
 * this used to do with the breathing — recomposes the whole map on every frame
 * of every gesture, rebuilding the modifier chain sixty times a second while
 * the user is trying to zoom.
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
    // that safe.
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

    val cache = remember { MapRenderCache() }

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

    // ---- Prewarming -------------------------------------------------------

    // Paths are built lazily, and a tile of building footprints takes far
    // longer than a frame. Built on demand inside the draw pass, the frame that
    // first needs one is dropped — which is exactly the frame in the middle of
    // the pinch that zoomed in far enough to need it. So they are built here
    // instead, off the main thread and slightly ahead of the camera, and the
    // draw pass skips anything not ready yet while a gesture is running.

    LaunchedEffect(prepared) {
        val map = prepared ?: return@LaunchedEffect
        // The overview, in full, once: it is what every gesture starts from.
        withContext(Dispatchers.Default) {
            for (layer in MapStyle.layers) {
                if (layer.minScale < MapPaths.COARSE_MAX_SCALE) {
                    map.paths.prewarm(layer.kind, WHOLE_WORLD, coarse = true)
                }
            }
        }
    }

    LaunchedEffect(prepared) {
        val map = prepared ?: return@LaunchedEffect
        snapshotFlow { camera.scale to camera.offset }
            .conflate()
            .collect {
                val canvas = viewport.toSize()
                if (canvas.minDimension > 0f) {
                    val scale = camera.scale
                    val visible = camera.visibleWorld(canvas)
                    // Reach past the edges, so panning finds tiles already built.
                    val margin = max(visible.width, visible.height) * PREWARM_MARGIN
                    val ahead = visible.inflate(margin)
                    // The same level of detail the draw pass settled on, so this
                    // can never spend its time building the layer it is not using.
                    val coarse = cache.detail.coarse
                    withContext(Dispatchers.Default) {
                        for (layer in MapStyle.layers) {
                            if (scale >= layer.minScale) {
                                map.paths.prewarm(layer.kind, ahead, coarse)
                            }
                        }
                    }
                }
                delay(PREWARM_INTERVAL_MILLIS)
            }
    }

    // ---- Gestures ---------------------------------------------------------

    val scope = rememberCoroutineScope()
    var settleJob by remember { mutableStateOf<Job?>(null) }
    // Read only in the draw pass, so toggling it costs a redraw and not a
    // recomposition.
    var gesturing by remember { mutableStateOf(false) }

    // Read inside the draw pass and the gesture callbacks rather than captured
    // by them, so that marking a single place visited repaints without
    // restarting the pointer handler.
    val placesState = rememberUpdatedState(places)
    val selectedState = rememberUpdatedState(selectedPlaceId)
    val preparedState = rememberUpdatedState(prepared)
    val currentOnPlaceSelected by rememberUpdatedState(onPlaceSelected)
    val tapSlopPx = with(density) { TAP_SLOP.toPx() }

    val description = remember(places) {
        val visitedCount = places.count { it.isVisited }
        "Map of the city. $visitedCount of ${places.size} places explored. " +
            "Pinch to zoom into an explored area, double tap to jump in."
    }

    Spacer(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = description }
            .onSizeChanged { viewport = it }
            // `Unit`: this must never restart. Keyed on anything that changes
            // during use, a gesture in progress would be cancelled mid-pinch.
            .pointerInput(Unit) {
                detectMapGestures(
                    onGestureStart = {
                        settleJob?.cancel()
                        gesturing = true
                    },
                    onTransform = { centroid, pan, zoom ->
                        camera.transform(zoom, pan, centroid, size.toSize())
                    },
                    onGestureEnd = { velocity ->
                        gesturing = false
                        if (velocity != Velocity.Zero) {
                            settleJob = scope.launch {
                                camera.flingPan(velocity, viewport.toSize())
                            }
                        }
                    },
                    onTap = { tap ->
                        val hit = preparedState.value?.projector?.let { projector ->
                            placesState.value.nearestWithin(tap, camera, projector, tapSlopPx)
                        }
                        hit?.let(currentOnPlaceSelected)
                        hit != null
                    },
                    onDoubleTap = { focus ->
                        settleJob = scope.launch {
                            camera.animateZoomTo(
                                target = camera.targetForToggle(),
                                focus = focus,
                                viewport = viewport.toSize(),
                            )
                        }
                    },
                )
            }
            .drawBehind {
                val map = prepared
                if (map == null) {
                    drawNight()
                    return@drawBehind
                }
                // Every one of these reads happens here, in the draw phase.
                val scale = camera.scale
                cache.detail.update(scale)
                drawCity(
                    paths = map.paths,
                    projector = map.projector,
                    camera = camera,
                    cache = cache,
                    places = placesState.value,
                    revealRadius = map.revealRadius,
                    // Read only while zoomed out. Not just taste: an animation
                    // that is never read is one this map never redraws for, so
                    // at street level it stops repainting entirely instead of
                    // re-rasterising every building sixty times a second to
                    // pulse them by three percent. It is also what you want to
                    // look at — a map you are reading should hold still.
                    breathing = if (scale < BREATHING_MAX_SCALE) breath.value else 1f,
                    selectedPlaceId = selectedState.value,
                    bloomingPlaceId = bloomingPlaceId,
                    bloomProgress = bloom.value,
                    // Mid-gesture, draw what is ready and let the prewarmer
                    // catch up. Frame rate is worth more than a tile of
                    // buildings arriving one frame late.
                    onlyReady = gesturing,
                )
            },
    )
}

/** The place nearest [tap] on screen, if any is within [slop] of it. */
private fun List<Place>.nearestWithin(
    tap: Offset,
    camera: MapCamera,
    projector: GeoProjector,
    slop: Float,
): Place? {
    var best: Place? = null
    var bestDistance = slop * slop
    for (place in this) {
        val delta = camera.worldToScreen(projector.project(place.location)) - tap
        val distance = delta.getDistanceSquared()
        if (distance <= bestDistance) {
            bestDistance = distance
            best = place
        }
    }
    return best
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
private class Reveal(var center: Offset, var radius: Float)

/**
 * The per-view scratch a frame needs and must not allocate.
 *
 * At sixty frames a second everything here would otherwise be garbage: two
 * `Paint`s, a list, and — the expensive one — a radial gradient per lit place
 * per frame, each of which builds a Skia shader.
 */
private class MapRenderCache {
    val brushes = RevealBrushes()
    val layerPaint = Paint()
    val maskPaint = Paint().apply { blendMode = BlendMode.DstIn }
    val detail = DetailLevel()
    val reveals = ArrayList<Reveal>()
    private var pooled = ArrayList<Reveal>()

    /** Hands back a [Reveal], reusing one from a previous frame if there is one. */
    fun reveal(center: Offset, radius: Float): Reveal {
        val index = reveals.size
        val existing = pooled.getOrNull(index)
        return if (existing != null) {
            existing.center = center
            existing.radius = radius
            existing
        } else {
            Reveal(center, radius).also { pooled.add(it) }
        }
    }
}

/**
 * Which level of detail to draw, with a deadband around the switch.
 *
 * Without one, a pinch that hovers near [MapPaths.COARSE_MAX_SCALE] alternates
 * between the decimated paths and the full ones frame by frame, and since the
 * two cost very different amounts to rasterise the result is visible judder at
 * exactly the zoom where people stop to look.
 */
private class DetailLevel {
    var coarse = true
        private set

    fun update(scale: Float) {
        val threshold = if (coarse) {
            MapPaths.COARSE_MAX_SCALE * DETAIL_HYSTERESIS
        } else {
            MapPaths.COARSE_MAX_SCALE / DETAIL_HYSTERESIS
        }
        coarse = scale < threshold
    }
}

/**
 * The radial gradients a lit area is made of, kept alive between frames.
 *
 * `Brush.radialGradient` bakes its centre and radius in and builds a shader the
 * first time it is drawn, so building one per reveal per frame meant three new
 * shaders for every lit place on screen, every frame. Every reveal in a frame
 * shares a radius — the breathing and the zoom are common to all of them — so a
 * single entry per gradient serves the whole frame, and survives into the next
 * one whenever the camera and the breath are still.
 *
 * They are centred on the origin and positioned by translating the canvas,
 * which is what lets one brush serve reveals in different places.
 */
private class RevealBrushes {
    private var washRadius = Float.NaN
    private var wash: Brush? = null

    private var maskRadius = Float.NaN
    private var mask: Brush? = null

    private var hazeRadius = Float.NaN
    private var hazeAlpha = Float.NaN
    private var haze: Brush? = null

    /** Warm ground under the streets, so a lit area reads as illuminated. */
    fun wash(radius: Float): Brush {
        val cached = wash
        if (cached != null && washRadius == radius) return cached
        return Brush.radialGradient(
            colors = listOf(
                MapStyle.LightWash.copy(alpha = 0.15f),
                MapStyle.LightWash.copy(alpha = 0.06f),
                MapStyle.LightWash.copy(alpha = 0.02f),
            ),
            center = Offset.Zero,
            radius = radius,
        ).also {
            wash = it
            washRadius = radius
        }
    }

    /**
     * Solid to well past halfway, then feathered — a lit district with a soft
     * edge, not a vignette that is only bright dead centre.
     */
    fun mask(radius: Float): Brush {
        val cached = mask
        if (cached != null && maskRadius == radius) return cached
        return Brush.radialGradient(
            colorStops = arrayOf(
                0.0f to Color.White,
                MASK_SOLID_FRACTION to Color.White,
                1.0f to Color.Transparent,
            ),
            center = Offset.Zero,
            radius = radius,
        ).also {
            mask = it
            maskRadius = radius
        }
    }

    /** Atmospheric spill, reaching past the lit area itself. */
    fun haze(radius: Float, alpha: Float): Brush {
        val cached = haze
        if (cached != null && hazeRadius == radius && hazeAlpha == alpha) return cached
        return Brush.radialGradient(
            colors = listOf(
                MapStyle.LightHalo.copy(alpha = alpha),
                MapStyle.LightHalo.copy(alpha = alpha * 0.25f),
                Color.Transparent,
            ),
            center = Offset.Zero,
            radius = radius,
        ).also {
            haze = it
            hazeRadius = radius
            hazeAlpha = alpha
        }
    }
}

private fun DrawScope.drawCity(
    paths: MapPaths,
    projector: GeoProjector,
    camera: MapCamera,
    cache: MapRenderCache,
    places: List<Place>,
    revealRadius: Float,
    breathing: Float,
    selectedPlaceId: String?,
    bloomingPlaceId: String?,
    bloomProgress: Float,
    onlyReady: Boolean,
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
        drawLayers(paths, visible, lit = false, scale = scale, cache = cache, onlyReady = onlyReady)
    }

    val reveals = places.buildReveals(
        into = cache,
        projector = projector,
        radius = revealRadius,
        breathing = breathing,
        bloomingPlaceId = bloomingPlaceId,
        bloomProgress = bloomProgress,
        visible = visible,
    )

    if (reveals.isNotEmpty()) {
        drawLitAreas(paths, camera, cache, reveals, viewport, onlyReady)
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
    into: MapRenderCache,
    projector: GeoProjector,
    radius: Float,
    breathing: Float,
    bloomingPlaceId: String?,
    bloomProgress: Float,
    visible: Rect,
): List<Reveal> {
    val reveals = into.reveals
    reveals.clear()
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
        reveals.add(into.reveal(center, r))
    }
    return reveals
}

/**
 * The lit city, masked to the explored areas. See the class docs for why the
 * mask normally gets its own layer, and why one reveal does not need it.
 */
private fun DrawScope.drawLitAreas(
    paths: MapPaths,
    camera: MapCamera,
    cache: MapRenderCache,
    reveals: List<Reveal>,
    viewport: Size,
    onlyReady: Boolean,
) {
    val scale = camera.scale
    val brushes = cache.brushes

    // Haze belongs to the distant view: at the overview it is what tells you
    // an area is lit at all, and by street level it would only fog the streets.
    val haze = lerp(0.16f, 0.02f, normalize(scale, 1f, 10f))
    if (haze > 0.01f) {
        for (reveal in reveals) {
            val center = camera.worldToScreen(reveal.center)
            val radius = reveal.radius * scale * HALO_SPREAD
            val brush = brushes.haze(radius, haze)
            translate(center.x, center.y) {
                drawCircle(brush = brush, radius = radius, center = Offset.Zero)
            }
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

    canvas.saveLayer(layerBounds, cache.layerPaint)

    // Warm ground under the streets, so a lit area reads as illuminated rather
    // than as a hole cut in the dark.
    for (reveal in reveals) {
        val center = camera.worldToScreen(reveal.center)
        val radius = reveal.radius * scale
        val brush = brushes.wash(radius)
        translate(center.x, center.y) {
            drawCircle(brush = brush, radius = radius, center = Offset.Zero)
        }
    }

    withTransform({
        translate(camera.offset.x, camera.offset.y)
        scale(scale, scale, pivot = Offset.Zero)
    }) {
        drawLayers(paths, litExtent, lit = true, scale = scale, cache = cache, onlyReady = onlyReady)
    }

    // One disc cannot overlap itself, so it can be punched straight into the
    // layer above and the second offscreen buffer skipped entirely. That is the
    // zoomed-into-one-place case, which is where the frame budget is tightest.
    //
    // It has to be drawn as a *rect* filled with the gradient, not as a circle.
    // `DstIn` only touches the pixels the draw itself covers, so a circle would
    // leave the corners of the layer — a fifth of it — at full brightness,
    // which zoomed in is a square patch of lit streets around the disc. The
    // gradient's last stop is transparent and it clamps beyond its radius, so
    // filling the whole layer with it erases everything outside the disc.
    if (reveals.size == 1) {
        val reveal = reveals[0]
        val center = camera.worldToScreen(reveal.center)
        val radius = reveal.radius * scale
        val brush = brushes.mask(radius)
        translate(center.x, center.y) {
            drawRect(
                brush = brush,
                topLeft = Offset(layerBounds.left - center.x, layerBounds.top - center.y),
                size = layerBounds.size,
                blendMode = BlendMode.DstIn,
            )
        }
    } else {
        canvas.saveLayer(layerBounds, cache.maskPaint)
        for (reveal in reveals) {
            val center = camera.worldToScreen(reveal.center)
            val radius = reveal.radius * scale
            val brush = brushes.mask(radius)
            translate(center.x, center.y) {
                drawCircle(brush = brush, radius = radius, center = Offset.Zero)
            }
        }
        canvas.restore()
    }

    canvas.restore()
}

private fun DrawScope.drawLayers(
    paths: MapPaths,
    visible: Rect,
    lit: Boolean,
    scale: Float,
    cache: MapRenderCache,
    onlyReady: Boolean,
) {
    val coarse = cache.detail.coarse
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

        paths.forEachTile(layer.kind, visible, coarse, onlyReady) { path ->
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

/** How far either side of the level-of-detail switch the current level holds. */
private const val DETAIL_HYSTERESIS = 1.15f

/** Extra fraction of the viewport to build paths for, ahead of a pan. */
private const val PREWARM_MARGIN = 0.35f

/** Floor on how often the background path builder re-reads the camera. */
private const val PREWARM_INTERVAL_MILLIS = 80L

/** Large enough to contain any projected city, for whole-map prewarming. */
private val WHOLE_WORLD = Rect(-1e6f, -1e6f, 1e6f, 1e6f)

private val MAP_PADDING: Dp = 20.dp

/** Generous, because the lights are small and fingers are not. */
private val TAP_SLOP: Dp = 22.dp
