package com.citymemory.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.citymemory.data.local.database.CityMemoryDatabase
import com.citymemory.SeedPlaces
import com.citymemory.data.local.seed.MumbaiSeed
import com.citymemory.data.local.seed.DatabaseSeeder
import com.citymemory.data.repository.PlaceRepositoryImpl
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
 * Every update this app has ever shipped, replayed.
 *
 * Version 2 added ratings and notes. Version 3 added addresses, places the user
 * adds themselves, and the catalog stamp that let the catalog grow from 177
 * places to 3,191. Version 4 added photos. These are the tests that say none of
 * it costs anyone the city they already lit.
 *
 * They build genuine version 1, 2 and 3 files — the exact DDL from
 * `schemas/1.json`, `2.json` and `3.json`, not an approximation — and open them
 * through [CityMemoryDatabase.build], the same call the app makes. That matters
 * more than testing the migration objects directly: the common way to lose
 * everyone's data is to write a correct migration and forget to register it,
 * and only the real builder catches that. A version 1 file therefore also
 * replays 1 -> 2 -> 3 -> 4 in one go, which is what an old install will do.
 *
 * Room validates the post-migration schema against what it expects and throws
 * if they differ, so a wrong `ALTER TABLE` fails here rather than on a phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private var openDatabase: CityMemoryDatabase? = null

    @Before
    fun setUp() {
        context.deleteDatabase(CityMemoryDatabase.DATABASE_NAME)
    }

    @After
    fun tearDown() {
        openDatabase?.close()
        context.deleteDatabase(CityMemoryDatabase.DATABASE_NAME)
    }

    /** Writes a version 1 database file with one explored and one wishlisted place. */
    private fun writeVersion1Database() {
        val file = context.getDatabasePath(CityMemoryDatabase.DATABASE_NAME)
        file.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            db.execSQL(CITIES_V1)
            db.execSQL(PLACES_V1)
            db.execSQL(PLACES_CITY_INDEX_V1)
            db.execSQL(PLACES_CATEGORY_INDEX_V1)
            db.execSQL(USER_PLACE_STATE_V1)

            db.execSQL("INSERT INTO cities VALUES ('mumbai', 'Mumbai', 'India')")
            db.execSQL(
                "INSERT INTO places VALUES " +
                    "('gateway-of-india', 'mumbai', 'Gateway of India', 'tourist', " +
                    "'The basalt arch.', 18.9220, 72.8347, NULL, 0)",
            )
            db.execSQL(
                "INSERT INTO places VALUES " +
                    "('elephanta-caves', 'mumbai', 'Elephanta Caves', 'tourist', " +
                    "'Rock-cut temples.', 18.9633, 72.9315, NULL, 1)",
            )
            db.execSQL(
                "INSERT INTO user_place_state VALUES ('gateway-of-india', 1, 0, 4242, NULL)",
            )
            db.execSQL(
                "INSERT INTO user_place_state VALUES ('elephanta-caves', 0, 1, NULL, 99)",
            )
            db.version = 1
        } finally {
            db.close()
        }
    }

    private fun openMigrated(): CityMemoryDatabase =
        CityMemoryDatabase.build(context).also {
            openDatabase?.close()
            openDatabase = it
        }

    @Test
    fun `an existing version 1 database opens at version 2 with its state intact`() = runBlocking {
        writeVersion1Database()

        val dao = openMigrated().userPlaceStateDao()

        val gateway = dao.getState("gateway-of-india")
        assertNotNull("the visited row survived the migration", gateway)
        assertTrue(gateway!!.isVisited)
        assertEquals(4242L, gateway.visitedAt)

        val elephanta = dao.getState("elephanta-caves")
        assertNotNull("the wishlisted row survived the migration", elephanta)
        assertTrue(elephanta!!.isWishlisted)
        assertEquals(99L, elephanta.wishlistedAt)
    }

    @Test
    fun `the new columns arrive empty rather than defaulted to a rating`() = runBlocking {
        writeVersion1Database()

        val state = openMigrated().userPlaceStateDao().getState("gateway-of-india")!!

        // Zero would be a score. A place explored before ratings existed has
        // not been rated, and has to read as exactly that.
        assertNull(state.rating)
        assertNull(state.note)
    }

    @Test
    fun `a place explored before the update can be rated after it`() = runBlocking {
        writeVersion1Database()

        val database = openMigrated()
        val repository = PlaceRepositoryImpl(database, DatabaseSeeder(database, SeedPlaces.catalog))
        repository.setReview("gateway-of-india", rating = 5, note = "Still the best arrival.")

        val place = repository.observePlace("gateway-of-india").first()!!
        assertTrue(place.isVisited)
        assertEquals(4242L, place.visitedAt)
        assertEquals(5, place.rating)
        assertEquals("Still the best arrival.", place.note)
    }

    /** Writes a version 2 file: ratings and notes exist, addresses do not yet. */
    private fun writeVersion2Database() {
        val file = context.getDatabasePath(CityMemoryDatabase.DATABASE_NAME)
        file.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            db.execSQL(CITIES_V1)
            db.execSQL(PLACES_V1)
            db.execSQL(PLACES_CITY_INDEX_V1)
            db.execSQL(PLACES_CATEGORY_INDEX_V1)
            db.execSQL(USER_PLACE_STATE_V2)

            db.execSQL("INSERT INTO cities VALUES ('mumbai', 'Mumbai', 'India')")
            db.execSQL(
                "INSERT INTO places VALUES " +
                    "('gateway-of-india', 'mumbai', 'Gateway of India', 'tourist', " +
                    "'The basalt arch.', 18.9220, 72.8347, NULL, 0)",
            )
            // A place the catalog no longer carries under that id. The update
            // must leave it alone rather than tidy it away — someone has been
            // there, and the catalog changing is not a reason to forget that.
            db.execSQL(
                "INSERT INTO places VALUES " +
                    "('a-place-since-removed', 'mumbai', 'The old chai stall', 'cafe', " +
                    "'Gone from the extract.', 19.0100, 72.8400, NULL, 1)",
            )
            db.execSQL(
                "INSERT INTO user_place_state VALUES " +
                    "('gateway-of-india', 1, 0, 4242, NULL, 5, 'Still the best arrival.')",
            )
            db.execSQL(
                "INSERT INTO user_place_state VALUES " +
                    "('a-place-since-removed', 1, 0, 777, NULL, NULL, NULL)",
            )
            db.version = 2
        } finally {
            db.close()
        }
    }

    /**
     * The update that took the catalog from 177 places to 3,191 and let people
     * add their own. It is the biggest re-seed the app will ever do, and this
     * is the test that says it does not cost anyone what they had written.
     */
    @Test
    fun `a version 2 database keeps its visit and its review across the catalog update`() =
        runBlocking {
            writeVersion2Database()

            val database = openMigrated()
            val repository = PlaceRepositoryImpl(
                database,
                DatabaseSeeder(database, SeedPlaces.catalog),
            )

            val place = repository.observePlace("gateway-of-india").first()!!
            assertTrue(place.isVisited)
            assertEquals(4242L, place.visitedAt)
            assertEquals(5, place.rating)
            assertEquals("Still the best arrival.", place.note)
        }

    @Test
    fun `the catalog update lands the new places without disturbing the old row`() = runBlocking {
        writeVersion2Database()

        val database = openMigrated()
        val repository = PlaceRepositoryImpl(database, DatabaseSeeder(database, SeedPlaces.catalog))
        val places = repository.observePlaces(MumbaiSeed.CITY_ID).first()

        // The catalog arrived, and the row it does not carry came through it.
        assertTrue("the catalog arrived: ${places.size}", places.size == SeedPlaces.total + 1)
        val kept = places.firstOrNull { it.id == "a-place-since-removed" }
        assertNotNull("re-seeding deleted a row it did not write", kept)
        assertTrue(kept!!.isVisited)
        assertEquals(777L, kept.visitedAt)
    }

    @Test
    fun `address arrives null on an upgraded row rather than invented`() = runBlocking {
        writeVersion2Database()

        val database = openMigrated()
        val repository = PlaceRepositoryImpl(database, DatabaseSeeder(database, SeedPlaces.catalog))

        // The row the catalog does not carry: nothing knows its address, and
        // nothing makes one up. `setAddress` is how it gets one.
        val untouched = repository.observePlace("a-place-since-removed").first()!!
        assertNull(untouched.address)
        assertFalse("a catalog row is not a user-added one", untouched.isUserAdded)

        // The row the catalog does carry is upserted, so it picks the address up.
        val reseeded = repository.observePlace("gateway-of-india").first()!!
        assertNotNull("the update should fill in an address it now has", reseeded.address)
    }

    /** Writes a version 3 file: the big catalog and user-added places, no photos. */
    private fun writeVersion3Database() {
        val file = context.getDatabasePath(CityMemoryDatabase.DATABASE_NAME)
        file.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            db.execSQL(CITIES_V3)
            db.execSQL(PLACES_V3)
            db.execSQL(PLACES_CITY_INDEX_V1)
            db.execSQL(PLACES_CATEGORY_INDEX_V1)
            db.execSQL(USER_PLACE_STATE_V2)

            db.execSQL("INSERT INTO cities VALUES ('mumbai', 'Mumbai', 'India', NULL)")
            db.execSQL(
                "INSERT INTO places VALUES " +
                    "('user-the-roof', 'mumbai', 'The roof', 'hidden_gem', 'Added by you.', " +
                    "19.0200, 72.8300, NULL, -1, 'Dadar, Mumbai 400014', 1)",
            )
            db.execSQL(
                "INSERT INTO user_place_state VALUES " +
                    "('user-the-roof', 1, 0, 555, NULL, 5, 'The best evening.')",
            )
            db.version = 3
        } finally {
            db.close()
        }
    }

    /**
     * Version 4 added photos. Nobody had any yet, so what this asserts is that
     * the table arrives and everything already there is untouched — including a
     * place the user typed in themselves, which no catalog can replace.
     */
    @Test
    fun `a version 3 database keeps its user-added place across the photos update`() =
        runBlocking {
            writeVersion3Database()

            val database = openMigrated()
            val repository = PlaceRepositoryImpl(
                database,
                DatabaseSeeder(database, SeedPlaces.catalog),
            )

            val place = repository.observePlace("user-the-roof").first()!!
            assertTrue("a place the user added must survive an update", place.isUserAdded)
            assertTrue(place.isVisited)
            assertEquals(555L, place.visitedAt)
            assertEquals(5, place.rating)
            assertEquals("The best evening.", place.note)
            assertEquals("Dadar, Mumbai 400014", place.address)
        }

    @Test
    fun `the photos table arrives empty and usable`() = runBlocking {
        writeVersion3Database()

        val database = openMigrated()
        val repository = PlaceRepositoryImpl(
            database,
            DatabaseSeeder(database, SeedPlaces.catalog),
            FakePhotoStore(),
        )

        assertTrue(repository.observePhotos("user-the-roof").first().isEmpty())
        assertTrue(repository.addPhoto("user-the-roof", "content://media/1"))
        assertEquals(1, repository.observePhotos("user-the-roof").first().size)
    }

    private companion object {
        const val CITIES_V3 =
            "CREATE TABLE IF NOT EXISTS `cities` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`country` TEXT NOT NULL, `catalogStamp` TEXT, PRIMARY KEY(`id`))"

        const val PLACES_V3 =
            "CREATE TABLE IF NOT EXISTS `places` (`id` TEXT NOT NULL, `cityId` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, `category` TEXT NOT NULL, `description` TEXT NOT NULL, " +
                "`latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `imageUrl` TEXT, " +
                "`displayOrder` INTEGER NOT NULL, `address` TEXT, " +
                "`isUserAdded` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`cityId`) REFERENCES `cities`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"

        const val USER_PLACE_STATE_V2 =
            "CREATE TABLE IF NOT EXISTS `user_place_state` (`placeId` TEXT NOT NULL, " +
                "`isVisited` INTEGER NOT NULL, `isWishlisted` INTEGER NOT NULL, " +
                "`visitedAt` INTEGER, `wishlistedAt` INTEGER, `rating` INTEGER, `note` TEXT, " +
                "PRIMARY KEY(`placeId`), FOREIGN KEY(`placeId`) REFERENCES `places`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"

        // Copied verbatim from schemas/1.json. Written out rather than derived
        // so this test keeps describing version 1 after the entities move on.
        const val CITIES_V1 =
            "CREATE TABLE IF NOT EXISTS `cities` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`country` TEXT NOT NULL, PRIMARY KEY(`id`))"

        const val PLACES_V1 =
            "CREATE TABLE IF NOT EXISTS `places` (`id` TEXT NOT NULL, `cityId` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, `category` TEXT NOT NULL, `description` TEXT NOT NULL, " +
                "`latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `imageUrl` TEXT, " +
                "`displayOrder` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`cityId`) REFERENCES `cities`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"

        const val PLACES_CITY_INDEX_V1 =
            "CREATE INDEX IF NOT EXISTS `index_places_cityId` ON `places` (`cityId`)"

        const val PLACES_CATEGORY_INDEX_V1 =
            "CREATE INDEX IF NOT EXISTS `index_places_category` ON `places` (`category`)"

        const val USER_PLACE_STATE_V1 =
            "CREATE TABLE IF NOT EXISTS `user_place_state` (`placeId` TEXT NOT NULL, " +
                "`isVisited` INTEGER NOT NULL, `isWishlisted` INTEGER NOT NULL, " +
                "`visitedAt` INTEGER, `wishlistedAt` INTEGER, PRIMARY KEY(`placeId`), " +
                "FOREIGN KEY(`placeId`) REFERENCES `places`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
    }
}
