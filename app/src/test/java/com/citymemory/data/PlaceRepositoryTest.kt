package com.citymemory.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.citymemory.data.local.database.CityMemoryDatabase
import com.citymemory.data.local.seed.DatabaseSeeder
import com.citymemory.SeedPlaces
import com.citymemory.data.local.seed.MumbaiSeed
import com.citymemory.data.repository.PlaceRepositoryImpl
import com.citymemory.domain.model.Place
import com.citymemory.domain.model.PlaceCategory
import com.citymemory.domain.repository.PlaceRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaceRepositoryTest {

    private lateinit var database: CityMemoryDatabase
    private lateinit var repository: PlaceRepository

    private var fakeNow = 1_000L

    // Resolved from the generated seed rather than typed, so regenerating the
    // dataset cannot break a test about visiting and rating.
    private val anyPlace = SeedPlaces.all.first().id
    private val aCafe = SeedPlaces.id(PlaceCategory.CAFE)
    private val aRestaurant = SeedPlaces.id(PlaceCategory.RESTAURANT)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CityMemoryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PlaceRepositoryImpl(database, DatabaseSeeder(database, SeedPlaces.catalog)) { fakeNow }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun places(): List<Place> = repository.observePlaces(MumbaiSeed.CITY_ID).first()

    private suspend fun place(id: String): Place = places().first { it.id == id }

    @Test
    fun `first read seeds the full mumbai catalog`() = runBlocking {
        val seeded = places()

        assertEquals(SeedPlaces.total, seeded.size)
        assertEquals(SeedPlaces.all.first().name, seeded.first().name)
    }

    @Test
    fun `every category in the enum is represented in the seed`() = runBlocking {
        val categories = places().map { it.category }.toSet()

        assertEquals(PlaceCategory.entries.toSet(), categories)
    }

    @Test
    fun `seeding is idempotent across repeated reads`() = runBlocking {
        repeat(3) { assertEquals(SeedPlaces.total, places().size) }

        assertEquals(SeedPlaces.total, database.placeDao().count())
    }

    @Test
    fun `places start neither visited nor wishlisted`() = runBlocking {
        val all = places()

        assertTrue(all.none { it.isVisited })
        assertTrue(all.none { it.isWishlisted })
        assertTrue(all.all { it.visitedAt == null })
    }

    @Test
    fun `marking visited persists the flag and the timestamp`() = runBlocking {
        repository.setVisited(anyPlace, true)

        val marked = place(anyPlace)
        assertTrue(marked.isVisited)
        assertEquals(1_000L, marked.visitedAt)
    }

    @Test
    fun `re-marking a visited place keeps the original timestamp`() = runBlocking {
        repository.setVisited(anyPlace, true)
        fakeNow = 9_999L
        repository.setVisited(anyPlace, true)

        assertEquals(1_000L, place(anyPlace).visitedAt)
    }

    @Test
    fun `undoing a visit clears both the flag and the timestamp`() = runBlocking {
        repository.setVisited(anyPlace, true)
        repository.setVisited(anyPlace, false)

        val undone = place(anyPlace)
        assertFalse(undone.isVisited)
        assertNull(undone.visitedAt)
    }

    @Test
    fun `wishlist and visited are independent`() = runBlocking {
        repository.setWishlisted(anyPlace, true)
        assertTrue(place(anyPlace).isWishlisted)
        assertFalse(place(anyPlace).isVisited)

        repository.setVisited(anyPlace, true)
        val both = place(anyPlace)
        assertTrue(both.isWishlisted)
        assertTrue(both.isVisited)

        repository.setWishlisted(anyPlace, false)
        val visitedOnly = place(anyPlace)
        assertFalse(visitedOnly.isWishlisted)
        assertTrue(visitedOnly.isVisited)
    }

    @Test
    fun `state row is dropped once a place is neither visited nor wishlisted`() = runBlocking {
        val dao = database.userPlaceStateDao()

        repository.setWishlisted(anyPlace, true)
        assertNotNull(dao.getState(anyPlace))

        repository.setWishlisted(anyPlace, false)
        assertNull(dao.getState(anyPlace))
    }

    @Test
    fun `clearing one flag keeps the row while the other is still set`() = runBlocking {
        val dao = database.userPlaceStateDao()

        repository.setVisited(anyPlace, true)
        repository.setWishlisted(anyPlace, true)
        repository.setVisited(anyPlace, false)

        val remaining = dao.getState(anyPlace)
        assertNotNull(remaining)
        assertTrue(remaining!!.isWishlisted)
        assertFalse(remaining.isVisited)
    }

    @Test
    fun `observePlace tracks a single place`() = runBlocking {
        repository.setVisited(anyPlace, true)

        val single = repository.observePlace(anyPlace).first()

        assertNotNull(single)
        assertEquals(SeedPlaces.all.first().name, single!!.name)
        assertTrue(single.isVisited)
    }

    @Test
    fun `observeCity returns the seeded city`() = runBlocking {
        // Reading places first is what triggers the seed.
        places()

        val city = repository.observeCity(MumbaiSeed.CITY_ID).first()

        assertNotNull(city)
        assertEquals("Mumbai", city!!.name)
        assertEquals("India", city.country)
    }

    @Test
    fun `a rating and an opinion are stored against the place`() = runBlocking {
        repository.setVisited(aCafe, true)
        repository.setReview(aCafe, rating = 4, note = "Loud, and worth it.")

        val rated = place(aCafe)
        assertEquals(4, rated.rating)
        assertEquals("Loud, and worth it.", rated.note)
        assertTrue(rated.hasReview)
    }

    @Test
    fun `a place can be rated without having been marked visited`() = runBlocking {
        repository.setReview(aRestaurant, rating = 5, note = null)

        val rated = place(aRestaurant)
        assertFalse(rated.isVisited)
        assertEquals(5, rated.rating)
    }

    @Test
    fun `un-marking a visit keeps what the user wrote about the place`() = runBlocking {
        repository.setVisited(aRestaurant, true)
        repository.setReview(aRestaurant, rating = 5, note = "Eat it standing up.")

        repository.setVisited(aRestaurant, false)

        // The undo is of the visit, not of the opinion — losing the text here
        // would be losing the only thing in this app the user actually wrote.
        val undone = place(aRestaurant)
        assertFalse(undone.isVisited)
        assertNull(undone.visitedAt)
        assertEquals(5, undone.rating)
        assertEquals("Eat it standing up.", undone.note)
    }

    @Test
    fun `clearing the review of an unvisited place drops the row entirely`() = runBlocking {
        val dao = database.userPlaceStateDao()
        repository.setReview(anyPlace, rating = 3, note = "Odd and quiet.")
        assertNotNull(dao.getState(anyPlace))

        repository.setReview(anyPlace, rating = null, note = "")

        assertNull(dao.getState(anyPlace))
    }

    @Test
    fun `a blank opinion is stored as nothing written`() = runBlocking {
        repository.setVisited(anyPlace, true)
        repository.setReview(anyPlace, rating = null, note = "   ")

        val place = place(anyPlace)
        assertNull(place.note)
        assertFalse(place.hasReview)
    }

    @Test
    fun `an opinion is trimmed rather than stored with its whitespace`() = runBlocking {
        repository.setReview(anyPlace, rating = 4, note = "  Best from the bridge. \n")

        assertEquals("Best from the bridge.", place(anyPlace).note)
    }

    @Test
    fun `a rating outside one to five is pulled back into range`() = runBlocking {
        repository.setReview(anyPlace, rating = 9, note = null)
        assertEquals(5, place(anyPlace).rating)

        repository.setReview(anyPlace, rating = 0, note = null)
        assertEquals(1, place(anyPlace).rating)
    }

    @Test
    fun `observePlaces re-emits when a place is marked visited`() = runBlocking {
        withTimeout(15_000) {
            val stream = repository.observePlaces(MumbaiSeed.CITY_ID)
            assertFalse(stream.first().first { it.id == anyPlace }.isVisited)

            val awaitingUpdate = async {
                stream.first { batch -> batch.first { it.id == anyPlace }.isVisited }
            }
            delay(250)
            repository.setVisited(anyPlace, true)

            val updated = awaitingUpdate.await()
            assertTrue(updated.first { it.id == anyPlace }.isVisited)
        }
    }
}
