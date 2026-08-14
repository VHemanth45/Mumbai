package com.citymemory.ui.map

import androidx.compose.ui.graphics.Color
import com.citymemory.domain.model.ShapeKind

/**
 * How each kind of geometry is painted, in the two states the map has.
 *
 * The map is drawn twice. The **unlit** pass covers the whole city and is
 * deliberately almost invisible — a coastline you can just make out and the
 * suggestion of the big arterials, everything else sunk into the background.
 * The **lit** pass is the same geometry in warm sodium light, and it is masked
 * down to the areas the user has actually explored (see `CityMapView`).
 *
 * So the two palettes are not light and dark variants of one style. They are an
 * unlit city and a lit one, and the difference between them is the product.
 *
 * [minScale] is the camera zoom a kind starts drawing at. At the overview zoom
 * the whole city is on screen at roughly 25 m per pixel, where a residential
 * street is a sub-pixel smear that costs a lot and reads as noise; it appears
 * once you are close enough for it to mean something.
 */
data class KindStyle(
    val fill: Color? = null,
    val stroke: Color? = null,
    val widthDp: Float = 1f,
)

data class MapLayer(
    val kind: ShapeKind,
    val lit: KindStyle,
    val unlit: KindStyle,
    val minScale: Float = 0f,
)

object MapStyle {

    /**
     * How far around a place the city is lit, in metres.
     *
     * This is the number the whole feature turns on. It has to be large enough
     * that what lights up is a neighbourhood you recognise rather than a
     * street corner, and no larger than the radius `tools/extract_osm.py` kept
     * building and footpath geometry for — past that edge the lit area would
     * fade out into a warm patch with nothing drawn in it.
     */
    const val RevealRadiusMeters = 420f

    /** The ground the whole city sits on. */
    val Background = Color(0xFF05060B)
    val BackgroundTop = Color(0xFF090C16)
    val BackgroundBottom = Color(0xFF030408)

    /** Warm wash laid under a lit area, so light reads as light and not as a hole. */
    val LightWash = Color(0xFFFFA94D)
    val LightHalo = Color(0xFFFF9A3C)

    /**
     * Painter's order: ground, then areas, then roads thin-to-thick so a
     * motorway crosses over a service road rather than under it.
     */
    val layers: List<MapLayer> = listOf(
        MapLayer(
            kind = ShapeKind.LAND,
            lit = KindStyle(fill = Color(0xFF1A1409)),
            unlit = KindStyle(fill = Color(0xFF0B0F19)),
        ),
        MapLayer(
            kind = ShapeKind.WATER,
            // Water is the one thing painted *darker* than the ground in both
            // states: the sea around Mumbai should read as absence, lit or not.
            lit = KindStyle(fill = Color(0xFF06141F), stroke = Color(0xFF17384B), widthDp = 0.8f),
            unlit = KindStyle(fill = Color(0xFF03050B)),
        ),
        MapLayer(
            kind = ShapeKind.GREEN,
            lit = KindStyle(fill = Color(0xFF0F2418), stroke = Color(0xFF255138), widthDp = 0.8f),
            unlit = KindStyle(fill = Color(0xFF060A0D)),
        ),
        MapLayer(
            kind = ShapeKind.BUILDING,
            // Footprints stay dark and are read by their lit edges — a block of
            // solid warm rectangles would drown the street network running past.
            lit = KindStyle(fill = Color(0xFF2A2015), stroke = Color(0xFF70552F), widthDp = 0.7f),
            unlit = KindStyle(),
            minScale = 6f,
        ),
        MapLayer(
            kind = ShapeKind.COASTLINE,
            lit = KindStyle(stroke = Color(0xFFFFB765).copy(alpha = 0.55f), widthDp = 1.2f),
            unlit = KindStyle(stroke = Color(0xFF161C2D), widthDp = 1.1f),
        ),
        MapLayer(
            kind = ShapeKind.SERVICE,
            lit = KindStyle(stroke = Color(0xFF7C6749), widthDp = 0.9f),
            unlit = KindStyle(),
            minScale = 10f,
        ),
        MapLayer(
            kind = ShapeKind.RESIDENTIAL,
            lit = KindStyle(stroke = Color(0xFFB08E64), widthDp = 1.1f),
            unlit = KindStyle(stroke = Color(0xFF07090F), widthDp = 0.7f),
            minScale = 5f,
        ),
        MapLayer(
            kind = ShapeKind.RAIL,
            lit = KindStyle(stroke = Color(0xFF8B7F6B), widthDp = 1.0f),
            unlit = KindStyle(stroke = Color(0xFF0D1119), widthDp = 0.9f),
            minScale = 1.5f,
        ),
        MapLayer(
            kind = ShapeKind.TERTIARY,
            lit = KindStyle(stroke = Color(0xFFD2A972), widthDp = 1.3f),
            unlit = KindStyle(stroke = Color(0xFF080A11), widthDp = 0.9f),
            minScale = 2.5f,
        ),
        MapLayer(
            kind = ShapeKind.SECONDARY,
            lit = KindStyle(stroke = Color(0xFFE9BC80), widthDp = 1.6f),
            unlit = KindStyle(stroke = Color(0xFF090C14), widthDp = 1.0f),
            minScale = 1.4f,
        ),
        MapLayer(
            kind = ShapeKind.PRIMARY,
            lit = KindStyle(stroke = Color(0xFFFFCB8C), widthDp = 2.0f),
            unlit = KindStyle(stroke = Color(0xFF0C1019), widthDp = 1.2f),
        ),
        MapLayer(
            kind = ShapeKind.MOTORWAY,
            lit = KindStyle(stroke = Color(0xFFFFD9A3), widthDp = 2.4f),
            unlit = KindStyle(stroke = Color(0xFF0F1422), widthDp = 1.6f),
        ),
    )
}
