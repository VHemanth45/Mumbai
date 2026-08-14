package com.citymemory.domain.model

/**
 * Geometry primitives for the city visualization.
 *
 * Everything here is expressed in real latitude/longitude, never in screen
 * coordinates. That is the whole point: the mock Mumbai outline and a future
 * GeoJSON-derived outline are the same type, so the renderer never has to know
 * which one it is drawing.
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
)

data class GeoBounds(
    val minLatitude: Double,
    val minLongitude: Double,
    val maxLatitude: Double,
    val maxLongitude: Double,
) {
    val latitudeSpan: Double get() = maxLatitude - minLatitude
    val longitudeSpan: Double get() = maxLongitude - minLongitude

    companion object {
        /** Bounds enclosing [points], padded by [paddingFraction] of each span. */
        fun around(points: List<GeoPoint>, paddingFraction: Double = 0.04): GeoBounds {
            if (points.isEmpty()) return GeoBounds(0.0, 0.0, 1.0, 1.0)
            val minLat = points.minOf { it.latitude }
            val maxLat = points.maxOf { it.latitude }
            val minLng = points.minOf { it.longitude }
            val maxLng = points.maxOf { it.longitude }
            val padLat = (maxLat - minLat).coerceAtLeast(1e-4) * paddingFraction
            val padLng = (maxLng - minLng).coerceAtLeast(1e-4) * paddingFraction
            return GeoBounds(
                minLatitude = minLat - padLat,
                minLongitude = minLng - padLng,
                maxLatitude = maxLat + padLat,
                maxLongitude = maxLng + padLng,
            )
        }
    }
}

/**
 * What a shape represents, which decides how the renderer styles it.
 *
 * The ids are wire values: they are written by `tools/build_map_asset.py` into
 * the packed asset and read back by `CityMapCodec`, so they must not be
 * renumbered without rebuilding the asset.
 */
enum class ShapeKind(val id: Int) {
    /** Sea edge. Stroked — this is the line that makes the city recognisable. */
    COASTLINE(0),

    /** Sea, creeks, docks, reservoirs. Filled darker than the ground. */
    WATER(1),

    /** Parks, forest, cemeteries, pitches. Filled. */
    GREEN(2),

    RAIL(3),
    MOTORWAY(4),
    PRIMARY(5),
    SECONDARY(6),
    TERTIARY(7),
    RESIDENTIAL(8),

    /** Service roads and footpaths. A detail kind — see [isDetail]. */
    SERVICE(9),

    /** Building footprints. A detail kind — see [isDetail]. */
    BUILDING(10),

    /**
     * A filled landmass silhouette. Nothing in the real OSM asset produces
     * this; it exists for the hand-authored fallback outline, which has no
     * coastline-derived water to subtract from.
     */
    LAND(11),
    ;

    /** Closed and filled, rather than stroked as an open polyline. */
    val isArea: Boolean
        get() = this == WATER || this == GREEN || this == BUILDING || this == LAND

    /**
     * True for kinds the asset only carries *near* a place, because they are
     * only ever drawn inside a lit area. Drawing them in the unlit base layer
     * would both cost more than it is worth and give away where the places are.
     */
    val isDetail: Boolean
        get() = this == SERVICE || this == BUILDING

    companion object {
        private val byId = entries.associateBy { it.id }

        fun fromId(id: Int): ShapeKind? = byId[id]
    }
}

/**
 * One closed polygon (see [ShapeKind.isArea]) or open polyline.
 *
 * Points are held as two parallel arrays rather than a `List<GeoPoint>`: the
 * real Mumbai asset is ~387,000 points, and one boxed object per point would
 * cost tens of megabytes of heap and a full GC pause to build.
 */
class CityShape(
    val kind: ShapeKind,
    val latitudes: DoubleArray,
    val longitudes: DoubleArray,
) {
    init {
        require(latitudes.size == longitudes.size) { "lat/lng arrays must match" }
    }

    val size: Int get() = latitudes.size

    fun pointAt(index: Int): GeoPoint = GeoPoint(latitudes[index], longitudes[index])

    companion object {
        fun of(kind: ShapeKind, points: List<GeoPoint>): CityShape = CityShape(
            kind = kind,
            latitudes = DoubleArray(points.size) { points[it].latitude },
            longitudes = DoubleArray(points.size) { points[it].longitude },
        )
    }
}

/**
 * Everything the map renderer needs to draw a city, independent of its source.
 */
data class CityGeometry(
    val cityId: String,
    val bounds: GeoBounds,
    val shapes: List<CityShape>,
) {
    companion object {
        val Empty = CityGeometry("", GeoBounds(0.0, 0.0, 1.0, 1.0), emptyList())
    }
}
