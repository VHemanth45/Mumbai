package com.citymemory.domain

import com.citymemory.domain.model.GeoPoint
import com.citymemory.domain.model.LabelTier
import com.citymemory.domain.model.MapLabel
import com.citymemory.domain.model.Place

/**
 * Which named area of the city a place falls in, and how many of them the user
 * has been to.
 *
 * There is no locality column to count. `address` carries one for some places
 * and a street line for others — 17,240 distinct leading fields across the
 * catalog, "Ground Floor" and "Shop No 1" among them — so parsing it is not a
 * way to count neighbourhoods. The map labels are: `tools/build_labels.py`
 * writes Mumbai's 89 postal localities with a coordinate each, every place has
 * a coordinate, and so the nearest label is a definition that works for the
 * whole catalog and for places the user adds themselves.
 *
 * Nearest-centroid is not a boundary test, and near a border it will put a
 * place in the neighbouring area. That is the right trade for this number: it
 * is *stable* — a place always lands in the same area, so the count never moves
 * on its own — it needs no polygons in the asset, and it costs 89 comparisons
 * per visited place, which is a few thousand for a heavy user.
 */
object Neighbourhoods {

    /**
     * How far from an area's centre a place can be and still count as in it.
     *
     * The catalog reaches past the city into Thane, Navi Mumbai and Mira
     * Bhayander, none of which have labels of their own. Without a limit every
     * one of those places would be counted as whichever northern locality
     * happened to be closest, and a weekend in Thane would light up Borivali.
     * Past this radius a place counts as nowhere, which is the honest answer.
     */
    const val MaxDistanceMeters = 6_000.0

    /** The area-tier labels — the only ones that name a neighbourhood. */
    fun areasIn(labels: List<MapLabel>): List<MapLabel> =
        labels.filter { it.tier == LabelTier.AREA }

    /** The area [point] falls in, or null if it is outside all of them. */
    fun nearest(point: GeoPoint, areas: List<MapLabel>): MapLabel? {
        var best: MapLabel? = null
        var bestDistance = Double.MAX_VALUE
        for (area in areas) {
            val distance = point.distanceTo(area.location)
            if (distance < bestDistance) {
                bestDistance = distance
                best = area
            }
        }
        return if (bestDistance <= MaxDistanceMeters) best else null
    }

    /** How many distinct areas the user has visited a place in. */
    fun exploredCount(places: List<Place>, areas: List<MapLabel>): Int {
        if (areas.isEmpty()) return 0
        val seen = HashSet<String>()
        for (place in places) {
            if (!place.isVisited) continue
            nearest(place.location, areas)?.let { seen.add(it.name) }
        }
        return seen.size
    }
}
