package com.citymemory.data.map

import com.citymemory.data.local.seed.MumbaiSeed
import com.citymemory.domain.model.CityGeometry
import com.citymemory.domain.model.CityShape
import com.citymemory.domain.model.GeoBounds
import com.citymemory.domain.model.GeoPoint
import com.citymemory.domain.model.ShapeKind
import com.citymemory.domain.repository.CityGeometryProvider

/**
 * A hand-authored, deliberately stylized Mumbai — now the *fallback*.
 *
 * The app serves real OpenStreetMap geometry via [OsmCityGeometryProvider];
 * this outline is what it falls back to when `assets/mumbai.map` is missing or
 * unreadable, and what the pure-JVM tests use so they need no asset at all.
 *
 * It is not a map and is not trying to be one — it is a recognizable silhouette
 * to hang lights on. The coastline is simplified to about sixty points, which is
 * enough to read as Mumbai (the Back Bay curve, the Mahim bay notch, the northern
 * suburbs widening out) and cheap enough to redraw every frame. It carries no
 * [ShapeKind.isDetail] geometry, so on this outline a lit area shows warm ground
 * and the arterials, but no streets or buildings.
 */
class MockMumbaiGeometryProvider : CityGeometryProvider {

    override suspend fun geometryFor(cityId: String): CityGeometry {
        if (cityId != MumbaiSeed.CITY_ID) return CityGeometry.Empty
        return mumbai
    }

    private companion object {

        private fun points(vararg coords: Double): List<GeoPoint> {
            require(coords.size % 2 == 0) { "coordinates must be lat/lng pairs" }
            return List(coords.size / 2) { i ->
                GeoPoint(latitude = coords[i * 2], longitude = coords[i * 2 + 1])
            }
        }

        /** The mainland silhouette: south tip up the west coast, back down the east. */
        private val mainland = points(
            // West coast, Colaba northward
            18.895, 72.815,
            18.906, 72.811,
            18.918, 72.817,
            18.925, 72.820,
            18.932, 72.818,
            18.941, 72.813,
            18.950, 72.806,
            18.956, 72.794,
            18.963, 72.792,
            18.972, 72.799,
            18.982, 72.804,
            18.992, 72.807,
            19.002, 72.811,
            19.010, 72.809,
            19.018, 72.818,
            19.028, 72.827,
            19.036, 72.822,
            19.045, 72.817,
            19.056, 72.819,
            19.068, 72.821,
            19.082, 72.823,
            19.098, 72.823,
            19.114, 72.819,
            19.135, 72.811,
            19.150, 72.794,
            19.166, 72.789,
            19.181, 72.791,
            19.200, 72.794,
            19.221, 72.799,
            19.241, 72.804,
            19.261, 72.814,
            19.280, 72.829,
            19.294, 72.849,
            // North edge
            19.297, 72.878,
            19.294, 72.901,
            // East coast, southward
            19.285, 72.929,
            19.266, 72.944,
            19.241, 72.949,
            19.216, 72.944,
            19.191, 72.934,
            19.166, 72.929,
            19.141, 72.924,
            19.116, 72.919,
            19.091, 72.909,
            19.071, 72.894,
            19.051, 72.879,
            19.031, 72.869,
            19.011, 72.864,
            18.996, 72.859,
            18.981, 72.854,
            18.966, 72.849,
            18.951, 72.844,
            18.941, 72.839,
            18.931, 72.837,
            18.921, 72.834,
            18.911, 72.827,
            18.901, 72.819,
        )

        /** Elephanta, out in the harbour — the one place that is not on the mainland. */
        private val elephantaIsland = points(
            18.955, 72.924,
            18.968, 72.921,
            18.977, 72.930,
            18.974, 72.941,
            18.962, 72.943,
            18.954, 72.935,
        )

        private val sanjayGandhiPark = points(
            19.186, 72.884,
            19.213, 72.876,
            19.243, 72.882,
            19.259, 72.903,
            19.250, 72.929,
            19.219, 72.939,
            19.190, 72.926,
            19.176, 72.903,
        )

        private val aareyForest = points(
            19.144, 72.868,
            19.168, 72.863,
            19.181, 72.879,
            19.170, 72.896,
            19.148, 72.893,
            19.138, 72.879,
        )

        /** The half-dozen arterials that make the silhouette readable. */
        private val roads: List<List<GeoPoint>> = listOf(
            // Marine Drive
            points(
                18.923, 72.822,
                18.932, 72.819,
                18.942, 72.815,
                18.951, 72.808,
            ),
            // Bandra-Worli Sea Link
            points(
                19.006, 72.813,
                19.021, 72.816,
                19.038, 72.820,
            ),
            // Western Express Highway
            points(
                19.056, 72.840,
                19.086, 72.848,
                19.118, 72.853,
                19.152, 72.857,
                19.192, 72.862,
                19.232, 72.868,
                19.268, 72.870,
            ),
            // Eastern Express Highway
            points(
                18.988, 72.848,
                19.020, 72.859,
                19.052, 72.873,
                19.088, 72.895,
                19.124, 72.910,
                19.160, 72.918,
            ),
            // Link Road
            points(
                19.046, 72.827,
                19.078, 72.829,
                19.110, 72.832,
                19.140, 72.836,
            ),
            // The southern spine
            points(
                18.921, 72.833,
                18.945, 72.830,
                18.968, 72.827,
                18.990, 72.829,
                19.012, 72.836,
                19.030, 72.845,
            ),
        )

        val mumbai = CityGeometry(
            cityId = MumbaiSeed.CITY_ID,
            // The same rectangle `tools/extract_osm.py` crops the real asset to,
            // rather than bounds derived from this outline.
            //
            // It used to be derived, so that editing the outline could not leave
            // the camera framing stale. That was right while the catalog was 177
            // places in the middle of the city and wrong now it is every place
            // OpenStreetMap has mapped: a hand-drawn silhouette does not reach
            // as far as the catalog does, and the places past its edge would
            // have been projected outside the canvas. Framing the extract's box
            // also means falling back to this outline does not move the city.
            bounds = GeoBounds(
                minLatitude = 18.860,
                minLongitude = 72.750,
                maxLatitude = 19.300,
                maxLongitude = 73.010,
            ),
            shapes = buildList {
                add(CityShape.of(ShapeKind.LAND, mainland))
                add(CityShape.of(ShapeKind.LAND, elephantaIsland))
                add(CityShape.of(ShapeKind.GREEN, sanjayGandhiPark))
                add(CityShape.of(ShapeKind.GREEN, aareyForest))
                roads.forEach { add(CityShape.of(ShapeKind.PRIMARY, it)) }
            },
        )
    }
}
