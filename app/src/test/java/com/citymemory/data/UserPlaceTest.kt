package com.citymemory.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.citymemory.SeedPlaces
import com.citymemory.data.local.database.CityMemoryDatabase
import com.citymemory.data.local.seed.DatabaseSeeder
import com.citymemory.data.local.seed.MumbaiSeed
import com.citymemory.data.local.seed.PlaceCatalog
import com.citymemory.data.local.seed.PlaceCatalogCodec
import com.citymemory.data.repository.PlaceRepositoryImpl
import com.citymemory.domain.model.Place
import com.citymemory.domain.model.PlaceCategory
import com.citymemory.domain.repository.PlaceRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

/**
 * Places the user adds because the catalog does not have them.
 *
 * The catalog ships every place OpenStreetMap has mapped inside Mumbai, and it
 * is still going to miss the stall that opened last month. What is asserted
 * here is that such a place is a first-class one — it counts, it lights the map,
 * it can be rated — and that no future catalog can quietly take it away.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserPlaceTest {

    private lateinit var database: CityMemoryDatabase
    private lateinit var repository: PlaceRepository

    private var fakeNow = 5_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CityMemoryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PlaceRepositoryImpl(
            database,
            DatabaseSeeder(database, SeedPlaces.catalog),
        ) { fakeNow }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun places(): List<Place> =
        repository.observePlaces(MumbaiSeed.CITY_ID).first()

    private suspend fun addKanheriChai(): String = repository.addUserPlace(
        cityId = MumbaiSeed.CITY_ID,
        name = "Chai stall by the gate",
        category = PlaceCategory.CAFE,
        latitude = 19.2100,
        longitude = 72.9060,
        address = "Borivali East, Mumbai 400066",
    )

    @Test
    fun `an added place joins the catalog and is explored from the start`() = runBlocking {
        val id = addKanheriChai()

        val place = places().first { it.id == id }
        assertEquals("Chai stall by the gate", place.name)
        assertEquals(PlaceCategory.CAFE, place.category)
        assertEquals("Borivali East, Mumbai 400066", place.address)
        assertTrue(place.isUserAdded)
        // The reason to type a place in is almost always that you just came
        // back from it, so it arrives lit rather than waiting to be marked.
        assertTrue(place.isVisited)
        assertEquals(fakeNow, place.visitedAt)
    }

    @Test
    fun `an added place counts towards the total`() = runBlocking {
        val before = places().size

        addKanheriChai()

        assertEquals(before + 1, places().size)
    }

    @Test
    fun `an added place sorts ahead of the catalog rather than into it`() = runBlocking {
        val id = addKanheriChai()

        // 3,566 rows deep is nowhere. The one you typed in yourself is the one
        // you will look for first.
        assertEquals(id, places().first().id)
    }

    @Test
    fun `two places with the same name both survive under distinct ids`() = runBlocking {
        val first = repository.addUserPlace(
            MumbaiSeed.CITY_ID, "Home", PlaceCategory.HIDDEN_GEM, 19.01, 72.84,
        )
        val second = repository.addUserPlace(
            MumbaiSeed.CITY_ID, "Home", PlaceCategory.HIDDEN_GEM, 19.09, 72.88,
        )

        assertTrue("ids collided: $first", first != second)
        assertEquals(2, places().count { it.name == "Home" })
    }

    @Test
    fun `an added place can be rated and wishlisted like any other`() = runBlocking {
        val id = addKanheriChai()

        repository.setReview(id, rating = 5, note = "Cutting, before the climb.")
        repository.setWishlisted(id, true)

        val place = places().first { it.id == id }
        assertEquals(5, place.rating)
        assertEquals("Cutting, before the climb.", place.note)
        assertTrue(place.isWishlisted)
    }

    @Test
    fun `removing an added place takes its review with it`() = runBlocking {
        val id = addKanheriChai()
        repository.setReview(id, rating = 4, note = "Worth the walk.")

        repository.deleteUserPlace(id)

        assertNull(places().firstOrNull { it.id == id })
        assertNull(database.userPlaceStateDao().getState(id))
    }

    @Test
    fun `a catalogued place cannot be deleted by the same call`() = runBlocking {
        val catalogued = SeedPlaces.all.first().id

        repository.deleteUserPlace(catalogued)

        // The catalog is regenerated from OpenStreetMap, so a deletion would
        // come back on the next update. Better that it never appears to work.
        assertNotNull(places().firstOrNull { it.id == catalogued })
    }

    @Test
    fun `an address can be written onto a catalogued place`() = runBlocking {
        val id = SeedPlaces.all.first().id

        repository.setAddress(id, "  Apollo Bandar, Colaba, Mumbai 400001  ")

        // Trimmed on the way in, because a trailing space is not an address.
        assertEquals("Apollo Bandar, Colaba, Mumbai 400001", places().first { it.id == id }.address)
    }

    @Test
    fun `clearing an address stores nothing rather than an empty string`() = runBlocking {
        val id = addKanheriChai()

        repository.setAddress(id, "   ")

        assertNull(places().first { it.id == id }.address)
    }

    @Test
    fun `a later catalog never removes a place the user added`() = runBlocking {
        val id = addKanheriChai()
        repository.setReview(id, rating = 5, note = "Still there.")

        // A second seeder over the same database, standing in for the next
        // app update: a different stamp, and a catalog that has never heard of
        // the user's place.
        val nextCatalog = PlaceCatalog {
            PlaceCatalogCodec.Catalog("a-later-catalog", SeedPlaces.all.take(50))
        }
        val afterUpdate = PlaceRepositoryImpl(
            database,
            DatabaseSeeder(database, nextCatalog),
        ) { fakeNow }

        val place = afterUpdate.observePlaces(MumbaiSeed.CITY_ID).first().first { it.id == id }
        assertTrue(place.isUserAdded)
        assertEquals(5, place.rating)
        assertEquals("Still there.", place.note)
    }

    @Test
    fun `re-seeding an unchanged catalog does not touch anything`() = runBlocking {
        val id = addKanheriChai()
        val before = places().size

        val again = PlaceRepositoryImpl(
            database,
            DatabaseSeeder(database, SeedPlaces.catalog),
        ) { fakeNow }
        again.observePlaces(MumbaiSeed.CITY_ID).first()

        assertEquals(before, places().size)
        assertTrue(places().first { it.id == id }.isVisited)
    }

    @Test
    fun `a place added without an address is still a place`() = runBlocking {
        val id = repository.addUserPlace(
            cityId = MumbaiSeed.CITY_ID,
            name = "The roof",
            category = PlaceCategory.HIDDEN_GEM,
            latitude = 19.02,
            longitude = 72.83,
            address = null,
            markVisited = false,
        )

        val place = places().first { it.id == id }
        assertNull(place.address)
        assertFalse(place.isVisited)
        assertTrue(place.description.isNotBlank())
    }
}
