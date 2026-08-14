package com.citymemory.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.citymemory.data.local.database.CityMemoryDatabase
import com.citymemory.data.local.seed.DatabaseSeeder
import com.citymemory.data.local.seed.MumbaiSeed
import com.citymemory.data.repository.PlaceRepositoryImpl
import com.citymemory.domain.ExplorationSummarizer
import com.citymemory.domain.model.AchievementId
import com.citymemory.domain.model.ExplorerLevel
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
        return PlaceRepositoryImpl(database, DatabaseSeeder(database))
    }

    @Test
    fun `visited places wishlist and progress all survive a restart`() = runBlocking {
        // --- Session one: explore a little. ---
        val firstRun = relaunchApp()
        firstRun.observePlaces(MumbaiSeed.CITY_ID).first() // triggers the seed

        val visitedIds = listOf(
            "gateway-of-india", "marine-drive", "cst", "haji-ali", "juhu-beach",
            "leopold-cafe", "britannia-and-co", "shivaji-park", "csmvs", "banganga-tank",
        )
        visitedIds.forEach { firstRun.setVisited(it, true) }

        val wishlistedIds = listOf("elephanta-caves", "kanheri-caves", "sewri-jetty")
        wishlistedIds.forEach { firstRun.setWishlisted(it, true) }

        val before = firstRun.observePlaces(MumbaiSeed.CITY_ID).first()
        val progressBefore = ExplorationSummarizer.progressOf(before)
        assertEquals(10, progressBefore.visitedCount)
        assertEquals(3, progressBefore.wishlistCount)
        assertEquals(12, progressBefore.percent)
        assertEquals(ExplorerLevel.EXPLORER, progressBefore.level)

        // --- Session two: same database file, brand new objects. ---
        val secondRun = relaunchApp()
        val after = secondRun.observePlaces(MumbaiSeed.CITY_ID).first()

        assertEquals(80, after.size)
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
        firstRun.setVisited("kala-ghoda", true)
        firstRun.setVisited("kala-ghoda", false)

        val secondRun = relaunchApp()
        val reopened = secondRun.observePlaces(MumbaiSeed.CITY_ID).first()
            .first { it.id == "kala-ghoda" }

        assertFalse(reopened.isVisited)
        assertEquals(null, reopened.visitedAt)
    }

    @Test
    fun `re-seeding on relaunch never clears user state`() = runBlocking {
        val firstRun = relaunchApp()
        firstRun.observePlaces(MumbaiSeed.CITY_ID).first()
        firstRun.setVisited("powai-lake", true)

        // Three more cold starts, each of which runs the seeder again.
        repeat(3) {
            val run = relaunchApp()
            val all = run.observePlaces(MumbaiSeed.CITY_ID).first()
            assertEquals(80, all.size)
            assertTrue(all.first { it.id == "powai-lake" }.isVisited)
        }
    }
}
