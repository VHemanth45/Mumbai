package com.citymemory.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.citymemory.data.local.database.CityMemoryDatabase
import com.citymemory.data.local.seed.DatabaseSeeder
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

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CityMemoryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PlaceRepositoryImpl(database, DatabaseSeeder(database)) { fakeNow }
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

        assertEquals(80, seeded.size)
        assertEquals(MumbaiSeed.places.size, seeded.size)
        assertEquals("Gateway of India", seeded.first().name)
    }

    @Test
    fun `every category in the enum is represented in the seed`() = runBlocking {
        val categories = places().map { it.category }.toSet()

        assertEquals(PlaceCategory.entries.toSet(), categories)
    }

    @Test
    fun `seeding is idempotent across repeated reads`() = runBlocking {
        repeat(3) { assertEquals(80, places().size) }

        assertEquals(80, database.placeDao().count())
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
        repository.setVisited("gateway-of-india", true)

        val marked = place("gateway-of-india")
        assertTrue(marked.isVisited)
        assertEquals(1_000L, marked.visitedAt)
    }

    @Test
    fun `re-marking a visited place keeps the original timestamp`() = runBlocking {
        repository.setVisited("marine-drive", true)
        fakeNow = 9_999L
        repository.setVisited("marine-drive", true)

        assertEquals(1_000L, place("marine-drive").visitedAt)
    }

    @Test
    fun `undoing a visit clears both the flag and the timestamp`() = runBlocking {
        repository.setVisited("juhu-beach", true)
        repository.setVisited("juhu-beach", false)

        val undone = place("juhu-beach")
        assertFalse(undone.isVisited)
        assertNull(undone.visitedAt)
    }

    @Test
    fun `wishlist and visited are independent`() = runBlocking {
        repository.setWishlisted("haji-ali", true)
        assertTrue(place("haji-ali").isWishlisted)
        assertFalse(place("haji-ali").isVisited)

        repository.setVisited("haji-ali", true)
        val both = place("haji-ali")
        assertTrue(both.isWishlisted)
        assertTrue(both.isVisited)

        repository.setWishlisted("haji-ali", false)
        val visitedOnly = place("haji-ali")
        assertFalse(visitedOnly.isWishlisted)
        assertTrue(visitedOnly.isVisited)
    }

    @Test
    fun `state row is dropped once a place is neither visited nor wishlisted`() = runBlocking {
        val dao = database.userPlaceStateDao()

        repository.setWishlisted("kala-ghoda", true)
        assertNotNull(dao.getState("kala-ghoda"))

        repository.setWishlisted("kala-ghoda", false)
        assertNull(dao.getState("kala-ghoda"))
    }

    @Test
    fun `clearing one flag keeps the row while the other is still set`() = runBlocking {
        val dao = database.userPlaceStateDao()

        repository.setVisited("bandra-fort", true)
        repository.setWishlisted("bandra-fort", true)
        repository.setVisited("bandra-fort", false)

        val remaining = dao.getState("bandra-fort")
        assertNotNull(remaining)
        assertTrue(remaining!!.isWishlisted)
        assertFalse(remaining.isVisited)
    }

    @Test
    fun `observePlace tracks a single place`() = runBlocking {
        repository.setVisited("elephanta-caves", true)

        val single = repository.observePlace("elephanta-caves").first()

        assertNotNull(single)
        assertEquals("Elephanta Caves", single!!.name)
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
    fun `observePlaces re-emits when a place is marked visited`() = runBlocking {
        withTimeout(15_000) {
            val stream = repository.observePlaces(MumbaiSeed.CITY_ID)
            assertFalse(stream.first().first { it.id == "siddhivinayak" }.isVisited)

            val awaitingUpdate = async {
                stream.first { batch -> batch.first { it.id == "siddhivinayak" }.isVisited }
            }
            delay(250)
            repository.setVisited("siddhivinayak", true)

            val updated = awaitingUpdate.await()
            assertTrue(updated.first { it.id == "siddhivinayak" }.isVisited)
        }
    }
}
