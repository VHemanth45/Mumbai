package com.citymemory.ui.map

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.citymemory.domain.model.LabelTier
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
     * This is the number the whole feature turns on, and it has come down twice:
     * 420 m, then 340 m, now 100 m. Each step was the same argument. 420 was
     * sized against a catalog of 177 curated places scattered across the city;
     * at 3,191 the places are dense enough that a busy stretch of Bandra has
     * dozens inside one disc, and discs that wide merge into a single lit smear
     * the moment you explore a neighbourhood properly.
     *
     * 100 m is a different kind of number, not just a smaller one, and it is
     * worth being clear about what it buys and costs. What lights up is now the
     * building and the street outside it — a place, not a neighbourhood — so
     * exploring a district reads as a constellation of separate points rather
     * than one glow. That is a sharper picture of where you have actually
     * *been*. The cost is at the overview: the whole city is ~25 m to the pixel,
     * so a reveal there is four pixels across and effectively invisible. The map
     * only tells its story once you are zoomed in.
     *
     * It is also below what the data reliably supports. At this radius 37 of
     * the 3,191 places have no building or footpath inside their lit area and 5
     * have nothing at all — beaches, Sanjay Gandhi National Park, Shivaji Park,
     * where OpenStreetMap has genuinely mapped nothing within 100 m of the
     * point. `CityMapAssetTest` allows for that rather than pretending
     * otherwise.
     *
     * `DETAIL_RADIUS_M` in `tools/extract_osm.py` is deliberately *not* tracked
     * down to match. It stays at 440 m, so the shipped asset carries far more
     * detail than this radius can show — which costs APK size and buys the
     * ability to change this constant back without regenerating anything.
     */
    const val RevealRadiusMeters = 100f

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

    // -----------------------------------------------------------------------
    // Labels
    // -----------------------------------------------------------------------

    /**
     * The names are drawn **above the reveal**, not inside it.
     *
     * Everything else on this map is hidden until you have been there — that is
     * the product. Names are the exception, and deliberately: an unlit city you
     * cannot read is a city you cannot navigate, and the point of putting
     * "Bandra West" on it is to tell you where you are looking *before* you
     * have explored it. So they sit over the composited reveal rather than
     * being masked by it.
     *
     * Both styles carry their own shadow, and it matters more the fainter the
     * text gets. A near-black map has no contrast to spare and text laid
     * straight onto it disappears wherever it crosses a road; the shadow is a
     * cheap halo that keeps a name readable over the coastline, over a lit
     * district, and over nothing at all. It is what lets the ink itself sit at
     * around 60% opacity without becoming unreadable.
     */
    val LabelShadow = Shadow(color = Color(0xE6000000), blurRadius = 10f)

    /**
     * A postal locality. Letterspaced small capitals, the way a printed map
     * sets a district — it reads as a region rather than as a thing at a point,
     * which is exactly the difference between this tier and the other one.
     */
    val AreaLabelStyle = TextStyle(
        // Held back to about 60% opacity. Names are an aid to reading the map,
        // not a layer of the map: at full strength they were the brightest
        // thing on a near-black screen, which put the labelling in front of the
        // city it was labelling. Faded, they sit behind the geometry until you
        // look for them — and the shadow below is what keeps them legible at
        // this weight rather than the ink itself.
        color = Color(0x9B9DAEC4),
        fontSize = 9.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.16.em,
        shadow = LabelShadow,
    )

    /** A single place. Warm, to sit with the lit geometry rather than against it. */
    val PlaceLabelStyle = TextStyle(
        color = Color(0xA6E2C9A4),
        fontSize = 10.sp,
        fontWeight = FontWeight.Normal,
        shadow = LabelShadow,
    )

    fun styleFor(tier: LabelTier): TextStyle = when (tier) {
        LabelTier.AREA -> AreaLabelStyle
        LabelTier.PLACE -> PlaceLabelStyle
    }

    /**
     * The zoom range a tier is drawn over.
     *
     * Area names go at the overview, where a place name would be one of three
     * thousand illegible specks and "Bandra West" is the answer to what you are
     * looking at. They stop once you are inside one, where the name is off the
     * top of the screen anyway. Place names start once a building is a thing
     * you can see. The ranges overlap by a few zoom steps so neither tier
     * blinks out before the other arrives.
     */
    fun isLabelVisible(tier: LabelTier, scale: Float): Boolean = when (tier) {
        LabelTier.AREA -> scale <= AREA_LABEL_MAX_SCALE
        LabelTier.PLACE -> scale >= PLACE_LABEL_MIN_SCALE
    }

    private const val AREA_LABEL_MAX_SCALE = 9f
    private const val PLACE_LABEL_MIN_SCALE = 5f

    /** Empty space kept around a label when deciding whether two collide. */
    const val LabelPaddingPx = 5f

    /**
     * How far a place name sits from the marker it names.
     *
     * Enough to clear the dot `drawPlaces` draws at the same coordinate. Area
     * names ignore this — they name a region, not a point, and have nothing to
     * clear.
     */
    const val LabelMarkerGapPx = 11f
}
