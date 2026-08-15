package com.citymemory.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.citymemory.data.local.database.CityMemoryDatabase
import com.citymemory.data.local.seed.DatabaseSeeder
import com.citymemory.SeedPlaces
import com.citymemory.data.local.seed.MumbaiSeed
import com.citymemory.data.repository.PlaceRepositoryImpl
import com.citymemory.domain.ExplorationSummarizer
import com.citymemory.domain.model.AchievementId
import com.citymemory.domain.model.ExplorerLevel
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
 * The requirement the product actually lives or dies on: close the app, reopen
 * it, and the city is still lit.
 *
 * Unlike the other tests this uses a real on-disk database and genuinely closes
 * it between phases, so it exercises the same file the shipped app writes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PersistenceAcrossRestartTest {

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

    /** Simulates a cold process start against the same database file. */
    private fun relaunchApp(): PlaceRepository {
        openDatabase?.close()
        val database = CityMemoryDatabase.build(context)
        openDatabase = database
        return PlaceRepositoryImpl(database, DatabaseSeeder(database, SeedPlaces.catalog))
    }

    @Test
    fun `visited places wishlist and progress all survive a restart`() = runBlocking {
        // --- Session one: explore a little. ---
        val firstRun = relaunchApp()
        firstRun.observePlaces(MumbaiSeed.CITY_ID).first() // triggers the seed

        // Ten tourist places, so the Tourist achievement is earned and the
        // Foodie one is not — the ids come from the generated seed rather than
        // being typed, so regenerating the dataset cannot break this.
        val visitedIds = SeedPlaces.ids(PlaceCategory.TOURIST, 10)
        visitedIds.forEach { firstRun.setVisited(it, true) }

        val wishlistedIds = SeedPlaces.ids(PlaceCategory.CULTURE, 3)
        wishlistedIds.forEach { firstRun.setWishlisted(it, true) }

        val before = firstRun.observePlaces(MumbaiSeed.CITY_ID).first()
        val progressBefore = ExplorationSummarizer.progressOf(before)
        assertEquals(10, progressBefore.visitedCount)
        assertEquals(3, progressBefore.wishlistCount)
        // Levels are counted in places visited, not in percent, so this one
        // does not move when the catalog is regenerated at a different size.
        assertEquals(ExplorerLevel.EXPLORER, progressBefore.level)

        // --- Session two: same database file, brand new objects. ---
        val secondRun = relaunchApp()
        val after = secondRun.observePlaces(MumbaiSeed.CITY_ID).first()

        assertEquals(SeedPlaces.total, after.size)
        assertEquals(visitedIds.toSet(), after.filter { it.isVisited }.map { it.id }.toSet())
        assertEquals(wishlistedIds.toSet(), after.filter { it.isWishlisted }.map { it.id }.toSet())
        assertTrue(after.filter { it.isVisited }.all { it.visitedAt != null })

        val progressAfter = ExplorationSummarizer.progressOf(after)
        assertEquals(progressBefore, progressAfter)

        // Achievements are derived, so they come back with the state that implies them.
        val achievements = ExplorationSummarizer.achievementsOf(after)
        assertTrue(achievements.first { it.id == AchievementId.FIRST_EXPLORATION }.isUnlocked)
        assertTrue(achievements.first { it.id == AchievementId.EXPLORER }.isUnlocked)
        assertTrue(achievements.first { it.id == AchievementId.TOURIST }.isUnlocked)
        assertFalse(achievements.first { it.id == AchievementId.FOODIE }.isUnlocked)
        assertFalse(achievements.first { it.id == AchievementId.CITY_EXPLORER }.isUnlocked)
    }

    @Test
    fun `undoing a visit also survives a restart`() = runBlocking {
        val firstRun = relaunchApp()
        firstRun.observePlaces(MumbaiSeed.CITY_ID).first()
        val undone = SeedPlaces.all.first().id
        firstRun.setVisited(undone, true)
        firstRun.setVisited(undone, false)

        val secondRun = relaunchApp()
        val reopened = secondRun.observePlaces(MumbaiSeed.CITY_ID).first()
            .first { it.id == undone }

        assertFalse(reopened.isVisited)
        assertEquals(null, reopened.visitedAt)
    }

    @Test
    fun `re-seeding on relaunch never clears user state`() = runBlocking {
        val firstRun = relaunchApp()
        firstRun.observePlaces(MumbaiSeed.CITY_ID).first()
        val kept = SeedPlaces.all.first().id
        firstRun.setVisited(kept, true)

        // Three more cold starts, each of which runs the seeder again.
        repeat(3) {
            val run = relaunchApp()
            val all = run.observePlaces(MumbaiSeed.CITY_ID).first()
            assertEquals(SeedPlaces.total, all.size)
            assertTrue(all.first { it.id == kept }.isVisited)
        }
    }
}
