package com.citymemory.domain

import com.citymemory.domain.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoVisitGrouperTest {

    private val colaba = GeoPoint(18.9220, 72.8347)
    private val bandra = GeoPoint(19.0596, 72.8295)
    private val minute = 60_000L
    private val hour = 60 * minute
    private val t0 = 1_700_000_000_000L

    private var nextId = 0

    private fun photo(at: GeoPoint, takenAt: Long, offsetMetres: Double = 0.0) = PhotoRecord(
        uri = "content://media/external/images/media/${nextId++}",
        takenAt = takenAt,
        location = GeoPoint(
            latitude = at.latitude + offsetMetres / GeoPoint.METRES_PER_DEGREE_LATITUDE,
            longitude = at.longitude,
        ),
    )

    @Test
    fun `nothing in, nothing out`() {
        assertTrue(PhotoVisitGrouper.group(emptyList()).isEmpty())
    }

    @Test
    fun `eleven photos of one lunch are one visit`() {
        val photos = (0..10).map { photo(colaba, t0 + it * 3 * minute, offsetMetres = it * 5.0) }

        val visits = PhotoVisitGrouper.group(photos)

        assertEquals(1, visits.size)
        assertEquals(11, visits.first().photos.size)
        assertEquals(30 * minute, visits.first().durationMillis)
    }

    @Test
    fun `the same place on two different evenings is two visits`() {
        val photos = listOf(
            photo(colaba, t0),
            photo(colaba, t0 + 10 * minute),
            photo(colaba, t0 + 30 * hour),
        )

        val visits = PhotoVisitGrouper.group(photos)

        assertEquals(2, visits.size)
        assertEquals(2, visits[0].photos.size)
        assertEquals(1, visits[1].photos.size)
    }

    @Test
    fun `two places in the same hour are two visits`() {
        val photos = listOf(
            photo(colaba, t0),
            photo(bandra, t0 + 40 * minute),
        )

        val visits = PhotoVisitGrouper.group(photos)

        assertEquals(2, visits.size)
    }

    @Test
    fun `a quiet stretch inside one meal does not split it`() {
        // Starters, then nothing until the bill an hour and a quarter later.
        val photos = listOf(
            photo(colaba, t0),
            photo(colaba, t0 + 75 * minute, offsetMetres = 20.0),
        )

        assertEquals(1, PhotoVisitGrouper.group(photos).size)
    }

    @Test
    fun `unsorted input is grouped as if it were sorted`() {
        val photos = listOf(
            photo(colaba, t0 + 30 * hour),
            photo(colaba, t0 + 10 * minute),
            photo(colaba, t0),
        )

        val visits = PhotoVisitGrouper.group(photos)

        assertEquals(2, visits.size)
        assertTrue("visits come back in time order", visits[0].startedAt < visits[1].startedAt)
        assertEquals(2, visits[0].photos.size)
    }

    @Test
    fun `walking the length of a beach is still one visit`() {
        // 180 m of drift over half an hour: inside the radius on purpose,
        // because people move around within a single outing.
        val photos = listOf(
            photo(colaba, t0),
            photo(colaba, t0 + 15 * minute, offsetMetres = 90.0),
            photo(colaba, t0 + 30 * minute, offsetMetres = 180.0),
        )

        assertEquals(1, PhotoVisitGrouper.group(photos).size)
    }

    @Test
    fun `the cover is the first photo, and the centre is where the cluster sat`() {
        val first = photo(colaba, t0)
        val visits = PhotoVisitGrouper.group(
            listOf(photo(colaba, t0 + 10 * minute, offsetMetres = 100.0), first),
        )

        val visit = visits.single()
        assertEquals(first.uri, visit.coverPhoto.uri)
        val offset = (visit.center.latitude - colaba.latitude) *
            GeoPoint.METRES_PER_DEGREE_LATITUDE
        assertEquals(50.0, offset, 2.0)
    }
}
