package com.citymemory.domain

import com.citymemory.domain.model.GeoPoint

/**
 * Turning a camera roll into visits.
 *
 * A phone's photo library is the record of where someone has been that they
 * already keep, kept honestly, going back years — and it is the only way this
 * app can offer a new user a map that is already lit on the first evening.
 * What it is not is a list of visits: eleven photographs of the same lunch are
 * one visit, and the sunset over Marine Drive an hour later is another.
 *
 * So the photos are clustered on time *and* space, and the clusters are what
 * the user is offered.
 *
 * The algorithm is a single pass over photos sorted by time, extending the
 * current cluster while each next photo is both soon enough after the last one
 * and close enough to where the cluster has been. It is deliberately not
 * k-means or DBSCAN: those need a k or an epsilon chosen against a corpus that
 * does not exist here, and they would happily merge two different evenings at
 * the same restaurant into one blob. Sequential clustering keeps the thing that
 * matters — that a visit is a *contiguous* stretch of time in one place.
 */
object PhotoVisitGrouper {

    /**
     * How far apart two photos can be and still be the same visit.
     *
     * Wider than the dwell radius on purpose. People walk around inside a
     * visit: the length of Chowpatty beach, or from the entrance of a museum to
     * the far gallery, is a couple of hundred metres and obviously one outing.
     */
    const val RadiusMeters = 200.0

    /**
     * The longest quiet stretch inside one visit.
     *
     * Ninety minutes covers a meal where the only photographs are of the
     * starters and the bill. Much longer and a morning and an afternoon at the
     * same address merge into a single entry.
     */
    const val GapMillis = 90 * 60 * 1000L

    /**
     * Groups [photos] into candidate visits, earliest first.
     *
     * Input need not be sorted; it is sorted here, because the caller is a
     * `MediaStore` cursor whose ordering is the store's business rather than a
     * promise.
     */
    fun group(photos: List<PhotoRecord>): List<PhotoVisit> {
        if (photos.isEmpty()) return emptyList()

        val ordered = photos.sortedBy { it.takenAt }
        val visits = ArrayList<PhotoVisit>()
        var current = ArrayList<PhotoRecord>()
        var centroid = ordered.first().location

        fun flush() {
            if (current.isEmpty()) return
            visits += PhotoVisit(
                photos = current.toList(),
                center = centroid,
                startedAt = current.first().takenAt,
                endedAt = current.last().takenAt,
            )
        }

        for (photo in ordered) {
            val continues = current.isNotEmpty() &&
                photo.takenAt - current.last().takenAt <= GapMillis &&
                photo.location.distanceTo(centroid) <= RadiusMeters

            if (!continues) {
                flush()
                current = ArrayList()
                centroid = photo.location
            }

            // Rolling centroid, so a cluster is measured from where it has been
            // rather than from whichever photo happened to open it.
            val n = current.size
            centroid = GeoPoint(
                latitude = (centroid.latitude * n + photo.location.latitude) / (n + 1),
                longitude = (centroid.longitude * n + photo.location.longitude) / (n + 1),
            )
            current += photo
        }
        flush()

        return visits
    }
}

/** One geotagged photograph, as the grouper needs it. */
data class PhotoRecord(
    /** A `content://` locator, kept as a string to stay out of Android types. */
    val uri: String,
    /** When it was taken, from EXIF where there is any and the file date otherwise. */
    val takenAt: Long,
    val location: GeoPoint,
)

/**
 * A stretch of time in one place, evidenced by photographs.
 *
 * [center] is what gets matched against the catalog, and [coverPhoto] is what
 * the user is shown — the first one, because the picture you took on arriving
 * is the one most likely to be of the place rather than of the food.
 */
data class PhotoVisit(
    val photos: List<PhotoRecord>,
    val center: GeoPoint,
    val startedAt: Long,
    val endedAt: Long,
) {
    val coverPhoto: PhotoRecord get() = photos.first()
    val durationMillis: Long get() = endedAt - startedAt
}
