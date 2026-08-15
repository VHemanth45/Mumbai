package com.citymemory.ui.map

import com.citymemory.domain.model.GeoPoint

/**
 * Somewhere the map has been asked to fly to, and which request asked.
 *
 * The token is not decoration, and it is the whole reason this is a type rather
 * than a bare [GeoPoint]. The map flies on a *change* of value and a
 * `StateFlow` conflates equal ones, so pressing "use my location" twice from
 * the same doorway produces the same coordinate and the second press moves
 * nothing. That was worse than a dead button: the camera stayed where the user
 * had panned it while the fix was treated as accepted, so the ring showed one
 * place and Save wrote another.
 *
 * It lives here rather than beside the view model that produces it because
 * `CityMapView` is documented as knowing nothing about Mumbai, Room or the
 * dataset — and a screen's view model is exactly the kind of thing it must not
 * know about.
 */
data class FlyTarget(val point: GeoPoint, val token: Long)
