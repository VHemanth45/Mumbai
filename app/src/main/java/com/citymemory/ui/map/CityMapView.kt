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
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
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
import com.citymemory.domain.model.GeoPoint
import com.citymemory.domain.model.LabelTier
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
 * **Names sit above the composite, not inside it.** Everything else here is
 * hidden until you have been there; the labels are the one exception, because
 * an unlit city you cannot read is a city you cannot navigate. They are
 * measured once in composition and drawn in screen space, so they stay upright
 * and the same size at every zoom — see [drawLabels] and `MapStyle`.
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
    /**
     * The place to fly to, if any. Flown to once per place — re-emitting the
     * same one (which happens on every visit or rating change) does not restart
     * the animation and yank the map out from under the user.
     */
    focusedPlace: Place? = null,
    /**
     * Reports the coordinate under the picking anchor whenever the camera
     * settles somewhere new. This is how a place gets added at a spot on the
     * map instead of by typing numbers: the caller draws a ring at
     * [PICK_ANCHOR_FRACTION] down the view, and whatever this last reported is
     * underneath it.
     */
    onViewportCenterChange: ((GeoPoint) -> Unit)? = null,
    /**
     * True while the user is choosing where a new place goes.
     *
     * It changes exactly twice per place added — once when the form opens and
     * once when it closes — which is the same cost as [focusedPlace] changing,
     * and is why it is allowed to be a parameter at all. Nothing here may ever
     * become a parameter that changes per frame; see the note above about
     * reading movement in the draw pass rather than in composition.
     *
     * It buys two things, and the map is the only place that can provide
     * either. **Zoom**, because at [MapCamera.MIN_SCALE] the whole city exactly
     * fills the viewport and `constrain` therefore pins the offset to zero —
     * the map cannot pan at all, so the anchor never moves and every place
     * added from the overview lands on the identical coordinate. **Anchor**,
     * because the form that asks for the name covers the bottom of the screen,
     * and a ring in the dead centre would be behind it.
     */
    pickingLocation: Boolean = false,
    /**
     * A coordinate to put under the picking ring, and a token saying which
     * request it belongs to.
     *
     * This is how "use my location" moves the map. The token exists because
     * pressing the button twice from the same doorway produces the same
     * coordinate, and a bare coordinate would compare equal and fly nowhere the
     * second time — see `ExploreViewModel.FlyTarget`. It must never be fed from
     * what [onViewportCenterChange] reports, which would be a loop.
     */
    flyTo: FlyTarget? = null,
) {
    val camera = rememberMapCamera()

    // Projecting the whole city is ~867,000 points of arithmetic — over a
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

    // Names are measured once and never again — and off the main thread, on the
    // same principle as the projection above.
    //
    // Text layout is the expensive half of drawing a string. Doing all 219 in
    // the composable body put them on the main thread on the very frame the map
    // was trying to fade in, which is the one thing this file's whole
    // arrangement exists to avoid; doing them in the draw pass would re-layout
    // every visible name sixty times a second. Measured here, every subsequent
    // frame is a blit at an offset.
    //
    // `cacheSize = 0` twice over: this holds its own finished layouts, so the
    // measurer's own LRU would only thrash — and with no cache the instance
    // carries no mutable state, which is what makes using it off the main
    // thread safe rather than lucky.
    //
    // It also gets its own `produceState` rather than joining the one above.
    // `rememberTextMeasurer` is keyed on the font resolver, density and layout
    // direction, so folding it into the map's keys would mean a locale flip
    // re-projected ~867,000 points in order to re-measure 219 short strings.
    val textMeasurer = rememberTextMeasurer(cacheSize = 0)
    val labels by produceState(
        emptyList<ProjectedLabel>(),
        prepared,
        geometry.labels,
        textMeasurer,
    ) {
        val map = prepared
        if (map == null) {
            value = emptyList()
            return@produceState
        }
        value = withContext(Dispatchers.Default) {
            geometry.labels.map { label ->
                ProjectedLabel(
                    world = map.projector.project(label.latitude, label.longitude),
                    tier = label.tier,
                    text = textMeasurer.measure(
                        // Small capitals for an area, the way a printed map sets
                        // a district. The generator title-cases; this is
                        // presentation.
                        text = if (label.tier == LabelTier.AREA) {
                            label.name.uppercase()
                        } else {
                            label.name
                        },
                        style = MapStyle.styleFor(label.tier),
                        maxLines = 1,
                    ),
                )
            }
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

    // Read through `rememberUpdatedState` so a caller passing a fresh lambda
    // does not restart the collection, and conflated so a pan reports where the
    // map got to rather than every frame on the way.
    //
    // Keyed on `pickingLocation` as well, so that opening the form re-collects
    // and reports immediately: `snapshotFlow` emits its current value to a new
    // collector, which matters when the anchor moves but the camera does not.
    val reportCenter by rememberUpdatedState(onViewportCenterChange)
    LaunchedEffect(prepared, pickingLocation) {
        val map = prepared ?: return@LaunchedEffect
        val anchorY = if (pickingLocation) PICK_ANCHOR_FRACTION else 0.5f
        snapshotFlow { Triple(camera.scale, camera.offset, viewport) }
            .conflate()
            .collect { (_, _, size) ->
                if (size.width <= 0 || size.height <= 0) return@collect
                val anchor = Offset(size.width / 2f, size.height * anchorY)
                reportCenter?.invoke(map.projector.unproject(camera.screenToWorld(anchor)))
            }
    }

    // Zoom in far enough that the ring can actually be aimed.
    //
    // Two reasons, and the first is a correctness one. At MIN_SCALE the city
    // exactly fills the viewport, so `MapCamera.constrain` clamps the offset to
    // zero in both axes: the map does not pan, the anchor stays on one fixed
    // coordinate, and every place added from the overview is written at the
    // same lat/lng. The second is precision — the overview is ~25 m to the
    // pixel, so even a map that *could* pan would place a pin to the nearest
    // building block.
    //
    // A camera the user has already zoomed past this is left where it is: they
    // have chosen a view, and yanking it would lose the place they had found.
    LaunchedEffect(pickingLocation, viewport) {
        if (!pickingLocation) return@LaunchedEffect
        val canvas = viewport.toSize()
        if (canvas.minDimension <= 0f || camera.scale >= PICK_SCALE) return@LaunchedEffect
        camera.animateZoomTo(
            target = PICK_SCALE,
            focus = Offset(canvas.width / 2f, canvas.height * PICK_ANCHOR_FRACTION),
            viewport = canvas,
        )
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
    val labelsState = rememberUpdatedState(labels)

    // Keyed on the id, not the place: the searched place lights up and gains a
    // rating while its card is open, and each of those is a new `Place` value.
    LaunchedEffect(focusedPlace?.id, prepared) {
        val map = prepared ?: return@LaunchedEffect
        val place = focusedPlace ?: return@LaunchedEffect
        settleJob?.cancel()
        settleJob = scope.launch {
            camera.animateCenterOn(
                worldPoint = map.projector.project(place.location),
                targetScale = MapCamera.DETAIL_SCALE,
                viewport = viewport.toSize(),
            )
        }
    }

    // Lands the point under the ring rather than in the middle of the screen,
    // so what the map reports straight afterwards is the fix the user asked
    // for and not something a third of a screen away from it.
    LaunchedEffect(flyTo, prepared) {
        val map = prepared ?: return@LaunchedEffect
        val target = flyTo ?: return@LaunchedEffect
        settleJob?.cancel()
        settleJob = scope.launch {
            camera.animateCenterOn(
                worldPoint = map.projector.project(target.point),
                targetScale = maxOf(camera.scale, PICK_SCALE),
                viewport = viewport.toSize(),
                anchorY = PICK_ANCHOR_FRACTION,
            )
        }
    }

    val description = remember(places) {
        val visitedCount = places.count { it.isVisited }
        "Map of the city. $visitedCount of ${places.size} places explored. " +
            "Pinch to zoom into an explored area, double tap to jump in. " +
            "Use the search box above to open a place."
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
                    // The map is for looking at, not for picking from: a single
                    // tap selects nothing, so it always falls through to the
                    // double-tap check and the only thing a touch can do here
                    // is move the camera. Places are chosen by searching.
                    onTap = { false },
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
                    labels = labelsState.value,
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
 * A name, projected once and laid out once.
 *
 * [world] is in scale-1 coordinates like everything else the renderer holds, so
 * the camera transform maps it to the screen the same way it maps a street.
 * [text] is a finished [TextLayoutResult]: measuring is the expensive half of
 * drawing a string, and holding the result is what keeps the draw pass to a
 * blit at an offset.
 */
@Immutable
private class ProjectedLabel(
    val world: Offset,
    val tier: LabelTier,
    val text: TextLayoutResult,
)

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

    /** Alpha is set per frame — it is how far the heat field fades in by zoom. */
    val heatPaint = Paint()
    val detail = DetailLevel()
    val reveals = ArrayList<Reveal>()
    private var pooled = ArrayList<Reveal>()

    /**
     * The screen rectangles labels have already claimed this frame.
     *
     * Cleared and refilled rather than reallocated, like everything else here:
     * a fresh list per frame is sixty allocations a second for something whose
     * size barely changes.
     */
    val labelRects = ArrayList<Rect>()

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

    private var heatRadius = Float.NaN
    private var heat: Brush? = null

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

    /**
     * One place's contribution to the heat field: a soft kernel, falling off
     * to nothing at its edge.
     *
     * White rather than amber, and that matters. These are summed on top of
     * each other, and summing a colour drives it towards white — eight visits
     * around Bandra would turn the hue out from under the design. So the layer
     * accumulates *density* in white, and the colour is painted through it
     * once at the end. The map stays one hue however heavily it is explored;
     * only the intensity moves.
     */
    fun heat(radius: Float): Brush {
        val cached = heat
        if (cached != null && heatRadius == radius) return cached
        return Brush.radialGradient(
            colorStops = arrayOf(
                0.0f to Color.White,
                0.35f to Color.White.copy(alpha = 0.55f),
                0.70f to Color.White.copy(alpha = 0.16f),
                1.0f to Color.Transparent,
            ),
            center = Offset.Zero,
            radius = radius,
        ).also {
            heat = it
            heatRadius = radius
        }
    }
}

private fun DrawScope.drawCity(
    paths: MapPaths,
    projector: GeoProjector,
    camera: MapCamera,
    cache: MapRenderCache,
    labels: List<ProjectedLabel>,
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

    // The heat field has a floor in screen units, so at the overview it is a
    // blob you can see rather than the four pixels 100 m works out to. Computed
    // here because the cull below has to know about it: a place just off screen
    // still spills heat onto it, and culling on the geographic radius alone
    // would pop those blobs in and out at the edge as the map pans.
    val heatRadius = max(revealRadius * scale * HALO_SPREAD, HEAT_MIN_RADIUS.toPx())

    val reveals = places.buildReveals(
        into = cache,
        projector = projector,
        radius = revealRadius,
        breathing = breathing,
        bloomingPlaceId = bloomingPlaceId,
        bloomProgress = bloomProgress,
        visible = visible,
        cullMargin = heatRadius / scale,
    )

    if (reveals.isNotEmpty()) {
        drawLitAreas(paths, camera, cache, reveals, viewport, onlyReady, heatRadius)
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

    // Last, and outside every transform: text is drawn in screen space so it
    // stays upright and the same size however far the map is zoomed, and it
    // sits above the composited reveal so a name is legible whether or not you
    // have been there. See the note on `MapStyle.LabelShadow` for why that is
    // the right call for names and the wrong one for everything else.
    drawLabels(labels, camera, cache)
}

/**
 * Draws as many names as will fit without overlapping.
 *
 * Greedy rejection in the order the labels arrive, which is the order
 * `tools/build_labels.py` wrote them: postal areas first, then places. So when
 * both tiers are on screen the areas claim their space first, which is the
 * right precedence — the district you are in matters more than which of six
 * temples you can see.
 *
 * The cost is quadratic in the number of labels *kept*, not in the number that
 * exist: everything off screen or out of its zoom range is rejected before the
 * overlap test. In practice that is twenty-odd rectangles against twenty-odd,
 * once a frame, which does not register next to tessellating a tile of
 * buildings.
 */
private fun DrawScope.drawLabels(
    labels: List<ProjectedLabel>,
    camera: MapCamera,
    cache: MapRenderCache,
) {
    if (labels.isEmpty()) return
    val scale = camera.scale
    val placed = cache.labelRects
    placed.clear()

    for (label in labels) {
        if (!MapStyle.isLabelVisible(label.tier, scale)) continue

        val center = camera.worldToScreen(label.world)
        val width = label.text.size.width.toFloat()
        val height = label.text.size.height.toFloat()

        // A place name is offered three positions beside its marker before it
        // gives up; an area name has only the one. See `LabelPlacement`.
        for (index in 0 until LabelPlacement.placementCount(label.tier)) {
            val topLeft = LabelPlacement.placementTopLeft(
                tier = label.tier,
                index = index,
                center = center,
                width = width,
                height = height,
                gap = MapStyle.LabelMarkerGapPx,
            )
            val claim = LabelPlacement.claimAt(topLeft, width, height, MapStyle.LabelPaddingPx)
            if (LabelPlacement.claim(placed, claim, size)) {
                drawText(label.text, topLeft = topLeft)
                break
            }
        }
    }
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
    /**
     * How far past its own radius a place can still affect the screen, in world
     * units. The heat field reaches further than the lit disc does, so the two
     * cannot share a cull.
     */
    cullMargin: Float,
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
        val reach = max(r, cullMargin)
        if (center.x + reach < visible.left || center.x - reach > visible.right) continue
        if (center.y + reach < visible.top || center.y - reach > visible.bottom) continue
        reveals.add(into.reveal(center, r))
    }
    return reveals
}

/**
 * The heat field: where *you* have been, as density rather than as dots.
 *
 * This is the layer that carries the map at the overview, and it replaced an
 * atmospheric haze that could not. The haze was sized off the 100 m reveal, and
 * at whole-city zoom the city is about 25 m to the pixel — so it drew a halo
 * four pixels across, and twenty-two hard-won visits rendered as a scatter of
 * specks on a black rectangle.
 *
 * Two things fix that, and they are the whole idea:
 *
 *  * **A floor in screen units.** [HEAT_MIN_RADIUS] is a size on the glass, not
 *    a distance on the ground, so a place is always a legible blob however far
 *    out you are. Zoomed in, the geographic radius overtakes the floor and the
 *    field goes back to describing real ground.
 *  * **Summing, not unioning.** Overlapping kernels add. One visit is an ember;
 *    eight visits around one neighbourhood compound into something that reads
 *    from across the city. That is the difference between a map of where places
 *    exist and a map of where you keep going back to.
 *
 * Which also settles the zoom-tiering question without any tiers: at the
 * overview neighbouring visits merge into one Bandra-sized glow, and zooming in
 * separates them back into individual places, because that is simply what
 * overlapping kernels do as the floor stops binding.
 *
 * The colour is applied once at the end rather than per kernel — see
 * [RevealBrushes.heat] for why that is what keeps the map one hue.
 */
private fun DrawScope.drawHeat(
    camera: MapCamera,
    cache: MapRenderCache,
    reveals: List<Reveal>,
    viewport: Size,
    radius: Float,
) {
    // Strongest at the overview, where it is the only thing saying "you have
    // been here", and almost gone by street level, where the lit streets say it
    // better and a wash of orange would only fog them.
    val strength = lerp(HEAT_STRENGTH_FAR, HEAT_STRENGTH_NEAR, normalize(camera.scale, 1f, HEAT_FADE_SCALE))
    if (strength < 0.01f) return

    var left = Float.MAX_VALUE
    var top = Float.MAX_VALUE
    var right = -Float.MAX_VALUE
    var bottom = -Float.MAX_VALUE
    for (reveal in reveals) {
        val center = camera.worldToScreen(reveal.center)
        if (center.x - radius < left) left = center.x - radius
        if (center.y - radius < top) top = center.y - radius
        if (center.x + radius > right) right = center.x + radius
        if (center.y + radius > bottom) bottom = center.y + radius
    }
    val bounds = Rect(left, top, right, bottom)
        .intersectOrNull(Rect(Offset.Zero, viewport))
        ?: return

    val canvas = drawContext.canvas
    val brush = cache.brushes.heat(radius)

    canvas.saveLayer(bounds, cache.heatPaint.apply { alpha = strength })

    for (reveal in reveals) {
        val center = camera.worldToScreen(reveal.center)
        translate(center.x, center.y) {
            drawCircle(
                brush = brush,
                radius = radius,
                center = Offset.Zero,
                alpha = HEAT_PEAK_ALPHA,
                blendMode = BlendMode.Plus,
            )
        }
    }

    // The layer now holds accumulated density in its alpha channel and nothing
    // else. `SrcIn` paints the one colour through it, so intensity is the only
    // thing that ever varies.
    drawRect(
        color = MapStyle.LightHalo,
        topLeft = bounds.topLeft,
        size = bounds.size,
        blendMode = BlendMode.SrcIn,
    )

    canvas.restore()
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
    heatRadius: Float,
) {
    val scale = camera.scale
    val brushes = cache.brushes

    drawHeat(camera, cache, reveals, viewport, heatRadius)

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

/**
 * A place you have not been to yet: the faintest mark the map makes.
 *
 * There are 3,191 of these and only a handful of lit ones, so the dots are by
 * far the most numerous thing drawn — at the overview they cover the city in a
 * speckle that competed with the coastline and the arterials for attention. The
 * dot's job is only to say *something is here*, and it does that at a fraction
 * of the weight: held well under the lit markers so a visited place still reads
 * as the brightest thing on screen, and small enough that a dense stretch of
 * Bandra reads as texture rather than as static.
 */
private fun DrawScope.drawUnvisited(center: Offset) {
    drawCircle(color = DimSlate.copy(alpha = 0.22f), radius = 1.5.dp.toPx(), center = center)
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
    // The core never fades all the way out: it is the anchor that says which
    // point of the lit area is the place itself.
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

/** How far the heat field reaches past the lit area itself. */
private const val HALO_SPREAD = 1.7f

/**
 * The smallest a place's heat kernel is allowed to be on screen.
 *
 * The number that makes the overview work. Below roughly this size the field
 * stops reading as a field and goes back to being dots.
 */
private val HEAT_MIN_RADIUS: Dp = 38.dp

/**
 * One place's peak contribution to the field.
 *
 * Deliberately low: a single visit should be a quiet ember, and it should take
 * a handful in one neighbourhood to burn. Roughly five overlapping kernels
 * saturate, which is about where "I know this area" starts being true.
 */
private const val HEAT_PEAK_ALPHA = 0.22f

/** How present the field is at the whole-city view, and once you are in a street. */
private const val HEAT_STRENGTH_FAR = 0.95f
private const val HEAT_STRENGTH_NEAR = 0.12f

/** The zoom by which the field has handed over to the lit streets. */
private const val HEAT_FADE_SCALE = 12f

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
/**
 * How far down the viewport the picking ring sits, as a fraction of its height.
 *
 * Not the middle, because the form asking for the name covers the bottom of the
 * screen and a ring in the dead centre would sit behind it — you would be
 * aiming something you cannot see. A third of the way down clears the header
 * above and the sheet below on a phone.
 *
 * Public because `ExploreScreen` has to align the ring to exactly the point
 * this view reports; the two reading one constant is what keeps them honest.
 */
const val PICK_ANCHOR_FRACTION = 0.35f

/**
 * The zoom a location is picked at, ~1.8 m to the pixel: close enough that the
 * ring sits on a building rather than on a block.
 */
private const val PICK_SCALE = 14f

private val WHOLE_WORLD = Rect(-1e6f, -1e6f, 1e6f, 1e6f)

private val MAP_PADDING: Dp = 20.dp
