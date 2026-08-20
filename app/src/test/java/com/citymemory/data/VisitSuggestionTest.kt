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
import com.citymemory.domain.model.SuggestionSource
import com.citymemory.domain.model.VisitSuggestions
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
 * The rules that decide whether the app is allowed to ask, and what a "yes"
 * actually writes.
 *
 * This is where the product rule of both automatic-logging features is
 * enforced: a sensor may propose, only a person may dispose, and a confirmed
 * visit is dated when it happened.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VisitSuggestionTest {

    private lateinit var database: CityMemoryDatabase
    private lateinit var repository: PlaceRepository

    private var fakeNow = 10_000_000L

    private val aCafe = SeedPlaces.id(PlaceCategory.CAFE)
    private val aRestaurant = SeedPlaces.id(PlaceCategory.RESTAURANT)

    private val day = 24 * 60 * 60 * 1000L

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
    fun tearDown() = database.close()

    private suspend fun suggest(
        placeId: String,
        source: SuggestionSource = SuggestionSource.DWELL,
        detectedAt: Long = fakeNow,
        photoUri: String? = null,
    ) = repository.recordSuggestion(
        placeId = placeId,
        source = source,
        detectedAt = detectedAt,
        latitude = 19.0,
        longitude = 72.8,
        photoUri = photoUri,
    )

    /** The joined view the screen sees: stored rows against the place list. */
    private suspend fun pending() = VisitSuggestions.join(
        places = repository.observePlaces(MumbaiSeed.CITY_ID).first(),
        pending = repository.observePendingSuggestions().first(),
    )

    @Test
    fun `a suggestion does not mark anything visited`() = runBlocking {
        assertNotNull(suggest(aCafe))

        val place = repository.observePlace(aCafe).first()!!
        assertFalse("a guess must never light the map", place.isVisited)
        assertEquals(1, pending().size)
    }

    @Test
    fun `confirming logs the visit and clears the question`() = runBlocking {
        suggest(aCafe)

        repository.confirmSuggestion(pending().single().id)

        assertTrue(repository.observePlace(aCafe).first()!!.isVisited)
        assertTrue(pending().isEmpty())
        assertEquals(0, repository.pendingSuggestionCount())
    }

    @Test
    fun `a confirmed visit is dated when it happened, not when it was confirmed`() = runBlocking {
        // The whole reason importing a camera roll is worth doing. Confirming a
        // photo from two years ago must not stamp it today.
        val twoYearsAgo = fakeNow - 730 * day
        suggest(aRestaurant, source = SuggestionSource.PHOTO, detectedAt = twoYearsAgo)

        repository.confirmSuggestion(pending().single().id)

        assertEquals(twoYearsAgo, repository.observePlace(aRestaurant).first()!!.visitedAt)
    }

    @Test
    fun `an older photo moves the visit date back, never forward`() = runBlocking {
        val lastYear = fakeNow - 365 * day
        val theYearBefore = fakeNow - 700 * day

        suggest(aCafe, source = SuggestionSource.PHOTO, detectedAt = lastYear)
        repository.confirmSuggestion(pending().single().id)

        // Un-visit so a second suggestion is allowed, then confirm an older one.
        repository.setVisited(aCafe, false)
        fakeNow += 13 * 60 * 60 * 1000L
        suggest(aCafe, source = SuggestionSource.PHOTO, detectedAt = theYearBefore)
        repository.confirmSuggestion(pending().single().id)

        assertEquals(theYearBefore, repository.observePlace(aCafe).first()!!.visitedAt)
    }

    @Test
    fun `one open question per place`() = runBlocking {
        assertNotNull(suggest(aCafe))
        assertNull("the same cafe twice is the same question twice", suggest(aCafe))
        assertEquals(1, pending().size)
    }

    @Test
    fun `somewhere already visited is not asked about`() = runBlocking {
        repository.setVisited(aCafe, true)

        assertNull(suggest(aCafe))
    }

    @Test
    fun `a dismissal holds for a week and then lapses`() = runBlocking {
        suggest(aCafe)
        repository.dismissSuggestion(pending().single().id)
        assertTrue(pending().isEmpty())

        fakeNow += 3 * day
        assertNull("three days later is nagging", suggest(aCafe))

        fakeNow += 5 * day
        assertNotNull("eight days later it may be asked again", suggest(aCafe))
    }

    @Test
    fun `marking a place visited by hand withdraws the pending question`() = runBlocking {
        suggest(aCafe)
        assertEquals(1, pending().size)

        repository.setVisited(aCafe, true)

        assertTrue(pending().isEmpty())
        assertEquals(0, repository.pendingSuggestionCount())
    }

    @Test
    fun `a suggestion for a place that does not exist is refused`() = runBlocking {
        assertNull(suggest("no-such-place"))
    }

    @Test
    fun `dismissing does not touch the place`() = runBlocking {
        suggest(aCafe)

        repository.dismissSuggestion(pending().single().id)

        assertFalse(repository.observePlace(aCafe).first()!!.isVisited)
    }

    @Test
    fun `the card carries how far the evidence was from the place`() = runBlocking {
        suggest(aCafe)

        val suggestion = pending().single()
        assertEquals(SuggestionSource.DWELL, suggestion.source)
        assertTrue(suggestion.distanceMeters >= 0.0)
        assertEquals(aCafe, suggestion.place.id)
    }
}
