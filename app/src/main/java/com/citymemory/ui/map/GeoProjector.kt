package com.citymemory.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.citymemory.domain.model.GeoBounds
import com.citymemory.domain.model.GeoPoint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min

/**
 * Maps latitude/longitude onto canvas pixels.
 *
 * A local equirectangular projection: longitude is scaled by cos(latitude) so a
 * degree of longitude and a degree of latitude cover comparable ground, which is
 * what stops the city looking horizontally stretched. Over a city-sized extent
 * the error against a true Mercator is invisible, and it stays cheap enough to
 * run inside a draw pass.
 *
 * Deliberately free of Compose state and of the mock data, so the same
 * projection serves real geometry later — and so it is unit-testable.
 */
class GeoProjector(
    private val bounds: GeoBounds,
    size: Size,
    padding: Float = 0f,
) {
    /**
     * How much a degree of longitude is squashed at this latitude.
     *
     * Exposed, along with [originX], [originY], [minLongitude] and
     * [maxLatitude], so [MapPaths] can inline this projection into its one hot
     * loop over ~867,000 points rather than calling through [project] for each.
     */
    val longitudeScale: Double =
        cos((bounds.minLatitude + bounds.maxLatitude) / 2.0 * PI / 180.0).coerceAtLeast(0.01)

    val minLongitude: Double get() = bounds.minLongitude
    val maxLatitude: Double get() = bounds.maxLatitude

    private val worldWidth: Double = bounds.longitudeSpan * longitudeScale
    private val worldHeight: Double = bounds.latitudeSpan

    private val usableWidth = (size.width - padding * 2).coerceAtLeast(1f)
    private val usableHeight = (size.height - padding * 2).coerceAtLeast(1f)

    /** Pixels per projected degree. Uniform on both axes to preserve shape. */
    val scale: Float = if (worldWidth <= 0.0 || worldHeight <= 0.0) {
        1f
    } else {
        min(usableWidth / worldWidth, usableHeight / worldHeight).toFloat()
    }

    val originX: Float = padding + (usableWidth - (worldWidth * scale).toFloat()) / 2f
    val originY: Float = padding + (usableHeight - (worldHeight * scale).toFloat()) / 2f

    /**
     * Pixels per metre on the ground, at camera scale 1.
     *
     * [scale] is pixels per degree of latitude, and a degree of latitude is
     * ~111.32 km everywhere. This lets the renderer express distances that
     * should mean something on the ground — how far around a place is lit —
     * in metres rather than in pixels that would change with screen size.
     */
    val pixelsPerMeter: Float = scale / METERS_PER_DEGREE_LATITUDE

    fun metersToPixels(meters: Float): Float = meters * pixelsPerMeter

    fun project(point: GeoPoint): Offset = Offset(
        x = originX + ((point.longitude - bounds.minLongitude) * longitudeScale * scale).toFloat(),
        // Latitude increases northward, y increases downward.
        y = originY + ((bounds.maxLatitude - point.latitude) * scale).toFloat(),
    )

    fun project(latitude: Double, longitude: Double): Offset = Offset(
        x = originX + ((longitude - bounds.minLongitude) * longitudeScale * scale).toFloat(),
        y = originY + ((bounds.maxLatitude - latitude) * scale).toFloat(),
    )

    /**
     * The exact inverse of [project]: world pixels back to a coordinate.
     *
     * This is what lets a place be added at a spot on the map rather than by
     * typing numbers. The caller turns a screen point into world space through
     * the camera first — [MapCamera.screenToWorld] — because zoom and pan are a
     * transform on top of this projection, not part of it.
     */
    fun unproject(world: Offset): GeoPoint = GeoPoint(
        latitude = bounds.maxLatitude - (world.y - originY) / scale,
        longitude = bounds.minLongitude + (world.x - originX) / (longitudeScale * scale),
    )

    private companion object {
        const val METERS_PER_DEGREE_LATITUDE = 111_320f
    }
}
