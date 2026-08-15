package com.citymemory.ui.map

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import com.citymemory.domain.model.CityGeometry
import com.citymemory.domain.model.ShapeKind
import kotlin.math.max

/**
 * Projected geometry, tiled and turned into [Path]s on demand.
 *
 * Three problems this solves, none of which exist while the map is a
 * sixty-point outline and all of which appear at once with real OpenStreetMap
 * data:
 *
 * **Traversal.** Mumbai is ~867,000 points. A single `Path` per kind means Skia
 * walks every residential street in the city to draw the four you can see at
 * full zoom. Shapes are bucketed into a coarse grid, and the renderer only
 * touches the tiles that intersect the viewport.
 *
 * **Detail you cannot see.** At the overview the city is ~25 metres to the
 * pixel, so a coastline surveyed to the metre puts twenty vertices on the same
 * pixel. Each tile can also produce a decimated path (see [build]) which the
 * renderer uses below [COARSE_MAX_SCALE].
 *
 * **Build cost.** Building every path up front stalls on zoom levels the user
 * may never reach. Tiles build lazily on first draw, so the overview pays only
 * for coastline, water and arterials, and the buildings around one place cost
 * one tile's worth of work at the moment you zoom into it.
 *
 * Constructing this is pure arithmetic over flat arrays — no Skia objects — so
 * it is safe to do off the main thread, which is exactly what `CityMapView`
 * does. Coordinates are projected once at camera scale 1; zoom and pan are a
 * canvas transform on top, so nothing here is rebuilt while the user moves
 * around. Only a resize invalidates it.
 */
internal class MapPaths(
    geometry: CityGeometry,
    projector: GeoProjector,
) {

    /** x, y interleaved, every shape concatenated in geometry order. */
    private val xy: FloatArray

    /** Index into [xy] (in points, not floats) of each shape's first point. */
    private val start: IntArray
    private val count: IntArray
    private val isArea: BooleanArray

    /** Longest side of each shape's projected bounding box, for coarse culling. */
    private val span: FloatArray

    private val tilesByKind: Array<Array<Tile>?>

    init {
        val shapes = geometry.shapes
        val n = shapes.size
        start = IntArray(n)
        count = IntArray(n)
        isArea = BooleanArray(n)
        span = FloatArray(n)

        var total = 0
        for (i in 0 until n) {
            start[i] = total
            val size = shapes[i].size
            count[i] = size
            isArea[i] = shapes[i].kind.isArea
            total += size
        }
        xy = FloatArray(total * 2)

        // Per-shape bounds, needed only long enough to bucket into tiles.
        val left = FloatArray(n)
        val top = FloatArray(n)
        val right = FloatArray(n)
        val bottom = FloatArray(n)
        val kindOrdinal = IntArray(n)

        // The projection is unrolled rather than calling through `project()`
        // per point. This loop runs 867,000 times exactly once, which is the
        // worst case for a JIT — it stays largely interpreted, so the constant
        // factor of each iteration is what the user actually waits for.
        val originX = projector.originX
        val originY = projector.originY
        val scale = projector.scale.toDouble()
        val lngScale = projector.longitudeScale * scale
        val minLng = projector.minLongitude
        val maxLat = projector.maxLatitude

        for (i in 0 until n) {
            val shape = shapes[i]
            kindOrdinal[i] = shape.kind.ordinal
            val latitudes = shape.latitudes
            val longitudes = shape.longitudes
            val size = latitudes.size

            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE
            var w = start[i] * 2

            for (p in 0 until size) {
                val x = (originX + (longitudes[p] - minLng) * lngScale).toFloat()
                val y = (originY + (maxLat - latitudes[p]) * scale).toFloat()
                xy[w++] = x
                xy[w++] = y
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }

            left[i] = minX
            top[i] = minY
            right[i] = maxX
            bottom[i] = maxY
            span[i] = max(maxX - minX, maxY - minY)
        }

        tilesByKind = buildTiles(n, kindOrdinal, left, top, right, bottom)
    }

    /**
     * Buckets shapes into a fixed grid, per kind.
     *
     * Membership is decided by bounding-box centre, and each tile's own bounds
     * then grow to cover whatever its members actually span. A long road
     * inflates its tile rather than being split across several, which keeps
     * culling conservative — a tile is never skipped while part of it is on
     * screen.
     */
    private fun buildTiles(
        n: Int,
        kindOrdinal: IntArray,
        left: FloatArray,
        top: FloatArray,
        right: FloatArray,
        bottom: FloatArray,
    ): Array<Array<Tile>?> {
        val kindCount = ShapeKind.entries.size
        val result = arrayOfNulls<Array<Tile>>(kindCount)
        if (n == 0) return result

        var worldLeft = Float.MAX_VALUE
        var worldTop = Float.MAX_VALUE
        var worldRight = -Float.MAX_VALUE
        var worldBottom = -Float.MAX_VALUE
        for (i in 0 until n) {
            if (left[i] < worldLeft) worldLeft = left[i]
            if (top[i] < worldTop) worldTop = top[i]
            if (right[i] > worldRight) worldRight = right[i]
            if (bottom[i] > worldBottom) worldBottom = bottom[i]
        }
        val cellW = ((worldRight - worldLeft) / TILE_COLS).coerceAtLeast(1f)
        val cellH = ((worldBottom - worldTop) / TILE_ROWS).coerceAtLeast(1f)

        val cells = TILE_COLS * TILE_ROWS
        // Flat arrays keyed by kind * cells + cell. A HashMap here would box
        // every one of the 149,000 shape indices it stores.
        val members = arrayOfNulls<IntList>(kindCount * cells)
        val bounds = FloatArray(kindCount * cells * 4)

        for (i in 0 until n) {
            val col = (((left[i] + right[i]) / 2f - worldLeft) / cellW).toInt()
                .coerceIn(0, TILE_COLS - 1)
            val row = (((top[i] + bottom[i]) / 2f - worldTop) / cellH).toInt()
                .coerceIn(0, TILE_ROWS - 1)
            val slot = kindOrdinal[i] * cells + row * TILE_COLS + col

            var list = members[slot]
            if (list == null) {
                list = IntList()
                members[slot] = list
                val b = slot * 4
                bounds[b] = Float.MAX_VALUE
                bounds[b + 1] = Float.MAX_VALUE
                bounds[b + 2] = -Float.MAX_VALUE
                bounds[b + 3] = -Float.MAX_VALUE
            }
            list.add(i)

            val b = slot * 4
            if (left[i] < bounds[b]) bounds[b] = left[i]
            if (top[i] < bounds[b + 1]) bounds[b + 1] = top[i]
            if (right[i] > bounds[b + 2]) bounds[b + 2] = right[i]
            if (bottom[i] > bounds[b + 3]) bounds[b + 3] = bottom[i]
        }

        for (kind in 0 until kindCount) {
            val tiles = ArrayList<Tile>()
            for (cell in 0 until cells) {
                val slot = kind * cells + cell
                val list = members[slot] ?: continue
                val b = slot * 4
                tiles += Tile(
                    bounds = Rect(bounds[b], bounds[b + 1], bounds[b + 2], bounds[b + 3]),
                    shapes = list.toIntArray(),
                )
            }
            if (tiles.isNotEmpty()) result[kind] = tiles.toTypedArray()
        }
        return result
    }

    /**
     * Runs [action] for every tile of [kind] that overlaps [visible], at the
     * level of detail appropriate to [coarse].
     *
     * With [onlyReady] set, tiles whose path has not been built yet are skipped
     * instead of built. That is what the draw pass does while a gesture is in
     * flight: building a tile of building footprints takes far longer than a
     * frame, so doing it inside the pinch that revealed it drops the frame the
     * user is actively looking at. [prewarm] builds them alongside, and they
     * appear a frame or two later with the gesture still smooth.
     */
    inline fun forEachTile(
        kind: ShapeKind,
        visible: Rect,
        coarse: Boolean,
        onlyReady: Boolean,
        action: (Path) -> Unit,
    ) {
        val tiles = tilesFor(kind) ?: return
        for (i in tiles.indices) {
            val tile = tiles[i]
            if (!tile.bounds.overlaps(visible)) continue
            val path = if (onlyReady) tile.built(coarse) else pathOf(tile, coarse)
            if (path != null) action(path)
        }
    }

    /**
     * Builds every path [forEachTile] would need for this view, without drawing.
     *
     * Safe off the main thread: [build] touches only flat arrays and a `Path`
     * that nothing else can see until it is finished and published, so the draw
     * pass either finds a complete path or finds none at all.
     */
    fun prewarm(kind: ShapeKind, visible: Rect, coarse: Boolean) {
        val tiles = tilesFor(kind) ?: return
        for (i in tiles.indices) {
            val tile = tiles[i]
            if (tile.bounds.overlaps(visible)) pathOf(tile, coarse)
        }
    }

    fun tilesFor(kind: ShapeKind): Array<Tile>? = tilesByKind[kind.ordinal]

    fun pathOf(tile: Tile, coarse: Boolean): Path = if (coarse) {
        tile.coarse ?: build(tile, COARSE_MIN_STEP).also { tile.coarse = it }
    } else {
        tile.fine ?: build(tile, 0f).also {
            tile.fine = it
            retainFine(tile)
        }
    }

    /**
     * Caps how many full-detail paths are kept alive, evicting oldest-first.
     *
     * Fine paths are the expensive ones — one tile of Mumbai's building
     * footprints is tens of thousands of points — and panning across the city at
     * full zoom would otherwise retain one for every tile crossed, on a heap
     * that on a low-end device is under 128 MB in total. An evicted tile that is
     * still on screen simply rebuilds; at this cap that is many screens away.
     */
    private fun retainFine(tile: Tile) {
        synchronized(fineBuilt) {
            fineBuilt.addLast(tile)
            while (fineBuilt.size > MAX_FINE_TILES) fineBuilt.removeFirst().fine = null
        }
    }

    private val fineBuilt = ArrayDeque<Tile>()

    /**
     * Builds one tile's geometry into a single [Path].
     *
     * [minStep] decimates: vertices closer than that to the last one kept are
     * dropped, and shapes smaller than that overall are skipped entirely.
     * Zero keeps everything, which is what the deep zoom uses.
     */
    private fun build(tile: Tile, minStep: Float): Path {
        val path = Path()
        val minStep2 = minStep * minStep

        for (index in tile.shapes) {
            if (minStep > 0f && span[index] < minStep) continue

            val first = start[index] * 2
            val points = count[index]
            var lastX = xy[first]
            var lastY = xy[first + 1]
            path.moveTo(lastX, lastY)
            var emitted = 1

            for (p in 1 until points) {
                val x = xy[first + p * 2]
                val y = xy[first + p * 2 + 1]
                if (minStep2 > 0f && p < points - 1) {
                    val dx = x - lastX
                    val dy = y - lastY
                    if (dx * dx + dy * dy < minStep2) continue
                }
                path.lineTo(x, y)
                lastX = x
                lastY = y
                emitted++
            }

            // A polygon decimated down to a sliver would fill as a stray line.
            if (isArea[index] && emitted >= 3) path.close()
        }
        return path
    }

    class Tile(val bounds: Rect, val shapes: IntArray) {
        // Volatile because `prewarm` publishes these from a background thread
        // and the draw pass reads them on the main one.
        @Volatile
        internal var fine: Path? = null

        @Volatile
        internal var coarse: Path? = null

        /** The path at this level of detail, or null if it has not been built. */
        fun built(coarse: Boolean): Path? = if (coarse) this.coarse else fine
    }

    /** A growable int list, to keep 149,000 shape indices out of `Integer` boxes. */
    private class IntList {
        private var data = IntArray(16)
        private var size = 0

        fun add(value: Int) {
            if (size == data.size) data = data.copyOf(size * 2)
            data[size++] = value
        }

        fun toIntArray(): IntArray = data.copyOf(size)
    }

    companion object {
        // 16 x 16 over a city the size of Mumbai is ~3 km a side: small enough
        // that a deep zoom touches one or two tiles, large enough that the
        // overview still only iterates a few hundred of them.
        //
        // Was 12 x 12, sized when the asset held 94,000 shapes. It now holds
        // 149,000, of which 73,000 are building footprints — nearly three times
        // as many as before, because detail geometry is kept near every place
        // in the catalog and the catalog went from 177 places to 3,191. Tiles
        // are built one at a time, on demand, so what matters is the cost of
        // the single most expensive tile; 256 cells puts that back roughly
        // where it was when it was measured.
        private const val TILE_COLS = 16
        private const val TILE_ROWS = 16

        /**
         * Decimation distance for the coarse level, in scale-1 pixels. Below
         * [COARSE_MAX_SCALE] the worst error this can introduce is about two
         * pixels, on a layer that is deliberately almost invisible anyway.
         */
        const val COARSE_MIN_STEP = 0.9f

        /** Zoom at which the renderer switches from coarse paths to full detail. */
        const val COARSE_MAX_SCALE = 3f

        /**
         * How many full-detail tile paths stay resident. A screen holds a
         * handful, so this is many screens of panning before anything on-screen
         * could be evicted, while still bounding the heap.
         */
        private const val MAX_FINE_TILES = 128
    }
}
