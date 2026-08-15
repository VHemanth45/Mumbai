package com.citymemory.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.citymemory.SeedPlaces
import com.citymemory.data.local.database.CityMemoryDatabase
import com.citymemory.data.local.seed.DatabaseSeeder
import com.citymemory.data.local.seed.MumbaiSeed
import com.citymemory.data.repository.PlaceRepositoryImpl
import com.citymemory.domain.model.PlaceCategory
import com.citymemory.domain.repository.PlaceRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Photos the user takes at a place.
 *
 * The catalog ships no imagery — `imageUrl` is null throughout — so these are
 * the only real pictures in the app, and losing one is losing something that
 * cannot be regenerated from OpenStreetMap. Most of what is asserted here is
 * about them not going missing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlacePhotoTest {

    private lateinit var database: CityMemoryDatabase
    private lateinit var repository: PlaceRepository
    private val photos = FakePhotoStore()

    private var fakeNow = 9_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CityMemoryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PlaceRepositoryImpl(
            database,
            DatabaseSeeder(database, SeedPlaces.catalog),
            photos,
        ) { fakeNow }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private val aPlace get() = SeedPlaces.all.first().id

    private suspend fun photosOf(placeId: String) = repository.observePhotos(placeId).first()

    @Test
    fun `a picked photo is copied in and attached to the place`() = runBlocking {
        val added = repository.addPhoto(aPlace, "content://media/42")

        assertTrue(added)
        assertEquals(listOf("photo-1.jpg"), photos.imported)
        val stored = photosOf(aPlace)
        assertEquals(1, stored.size)
        assertEquals(fakeNow, stored.first().addedAt)
    }

    @Test
    fun `the path comes from the store, not from the row`() = runBlocking {
        repository.addPhoto(aPlace, "content://media/42")

        // The row holds a bare file name so the app's data directory can move —
        // which it does, on a restore to a new device — without every photo
        // becoming a broken link.
        assertEquals("/photos/photo-1.jpg", photosOf(aPlace).first().path)
    }

    @Test
    fun `an unreadable image writes no row at all`() = runBlocking {
        photos.readable = false

        val added = repository.addPhoto(aPlace, "content://media/gone")

        // A row pointing at a file that was never written is worse than a
        // refusal the screen can report, so the copy happens first.
        assertFalse(added)
        assertTrue(photosOf(aPlace).isEmpty())
    }

    @Test
    fun `photos come back oldest first`() = runBlocking {
        repository.addPhoto(aPlace, "content://media/1")
        fakeNow += 1_000
        repository.addPhoto(aPlace, "content://media/2")
        fakeNow += 1_000
        repository.addPhoto(aPlace, "content://media/3")

        assertEquals(
            listOf("photo-1.jpg", "photo-2.jpg", "photo-3.jpg"),
            photosOf(aPlace).map { it.path.substringAfterLast('/') },
        )
    }

    @Test
    fun `deleting a photo takes the file with it`() = runBlocking {
        repository.addPhoto(aPlace, "content://media/1")
        val photo = photosOf(aPlace).first()

        repository.deletePhoto(photo.id)

        assertTrue(photosOf(aPlace).isEmpty())
        assertEquals(listOf("photo-1.jpg"), photos.deleted)
    }

    @Test
    fun `deleting a photo that is already gone is not an error`() = runBlocking {
        repository.deletePhoto("photo-does-not-exist")

        assertTrue(photos.deleted.isEmpty())
    }

    @Test
    fun `photos belong to their own place`() = runBlocking {
        val other = SeedPlaces.all[1].id
        repository.addPhoto(aPlace, "content://media/1")
        repository.addPhoto(other, "content://media/2")

        assertEquals(1, photosOf(aPlace).size)
        assertEquals(1, photosOf(other).size)
    }

    @Test
    fun `removing a place the user added takes its photos with it`() = runBlocking {
        val id = repository.addUserPlace(
            cityId = MumbaiSeed.CITY_ID,
            name = "The roof",
            category = PlaceCategory.HIDDEN_GEM,
            latitude = 19.02,
            longitude = 72.83,
        )
        repository.addPhoto(id, "content://media/1")
        assertEquals(1, photosOf(id).size)

        repository.deleteUserPlace(id)

        // The foreign-key cascade does this. Without it the rows would outlive
        // the place and point at nothing.
        assertTrue(photosOf(id).isEmpty())
        // And the files go too. SQLite cascades rows and knows nothing about
        // the JPEGs, so the repository has to read the names before the cascade
        // runs — this is the assertion that says it still does.
        assertEquals(listOf("photo-1.jpg"), photos.deleted)
    }

    @Test
    fun `removing a place with several photos leaves none of their files behind`() = runBlocking {
        val id = repository.addUserPlace(
            cityId = MumbaiSeed.CITY_ID,
            name = "The roof",
            category = PlaceCategory.HIDDEN_GEM,
            latitude = 19.02,
            longitude = 72.83,
        )
        repeat(3) { repository.addPhoto(id, "content://media/$it") }

        repository.deleteUserPlace(id)

        assertEquals(photos.imported.toSet(), photos.deleted.toSet())
    }

    @Test
    fun `a place stops accepting photos once it is full`() = runBlocking {
        // Nothing else bounds this. Without a cap a place could hold hundreds,
        // which is tens of megabytes and a strip nobody can scroll.
        repeat(12) { assertTrue("photo $it should fit", repository.addPhoto(aPlace, "content://$it")) }

        assertFalse("the thirteenth must be refused", repository.addPhoto(aPlace, "content://13"))
        assertEquals(12, photosOf(aPlace).size)
        // Refused before the copy, so it costs no disk and no decode.
        assertEquals(12, photos.imported.size)
    }

    @Test
    fun `a full place still accepts photos again once one is removed`() = runBlocking {
        repeat(12) { repository.addPhoto(aPlace, "content://$it") }
        repository.deletePhoto(photosOf(aPlace).first().id)

        assertTrue(repository.addPhoto(aPlace, "content://fresh"))
        assertEquals(12, photosOf(aPlace).size)
    }

    @Test
    fun `a visit and a review survive photos being added and removed`() = runBlocking {
        repository.setVisited(aPlace, true)
        repository.setReview(aPlace, rating = 5, note = "Worth the walk.")
        repository.addPhoto(aPlace, "content://media/1")
        repository.deletePhoto(photosOf(aPlace).first().id)

        val place = repository.observePlace(aPlace).first()!!
        assertTrue(place.isVisited)
        assertEquals(5, place.rating)
        assertEquals("Worth the walk.", place.note)
    }
}
