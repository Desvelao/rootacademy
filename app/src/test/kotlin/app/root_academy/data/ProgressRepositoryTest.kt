package app.root_academy.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.root_academy.core.content.Catalog
import app.root_academy.core.progress.ProgressExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProgressRepositoryTest {
    private lateinit var db: AppDatabase
    private var today = 100L
    private lateinit var repo: ProgressRepository

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = ProgressRepository(db) { today }
    }

    @After fun tearDown() = db.close()

    private fun state() = runBlocking { repo.state.first() }

    @Test fun completionPaysOnlyOnce() = runBlocking {
        assertTrue(repo.complete("m01", "mission", 100, mapOf("linux" to 20), listOf("shell-basics")))
        assertFalse(repo.complete("m01", "mission", 100, mapOf("linux" to 20), listOf("shell-basics")))
        val s = state()
        assertEquals(100, s.points)
        assertEquals(20, s.skillXp["linux"])
        assertEquals(setOf("shell-basics"), s.codex)
        assertEquals(1, s.streak)
    }

    @Test fun hintsCostPointsOnce() = runBlocking {
        repo.complete("m01", "mission", 100, emptyMap(), emptyList())
        repo.takeHint("m01", 0)
        repo.takeHint("m01", 0)
        repo.takeHint("m01", 1)
        assertEquals(15, state().spent)
        assertEquals(85, state().points)
    }

    @Test fun badgesCountOnlyMissionsAndPuzzles() = runBlocking {
        repo.complete("cq01", "cipher", 80, emptyMap(), emptyList())
        repo.complete("soc-daily-1", "soc", 60, emptyMap(), emptyList())
        assertTrue(state().badges.isEmpty())
        repo.complete("cq-looking-id", "puzzle", 50, emptyMap(), emptyList())
        assertEquals(listOf("first-flag"), state().badges)
    }

    @Test fun exportThenImportRoundTrips() = runBlocking {
        repo.complete("m01", "mission", 100, mapOf("linux" to 20), listOf("shell-basics"))
        repo.takeHint("m01", 0)
        val exported = repo.export()
        // Import into a fresh state; with an empty catalog only soc-daily ids can be resolved.
        repo.import(exported.copy(completed = exported.completed + "soc-daily-5"), Catalog(emptyList(), emptyList(), emptyList()))
        val s = state()
        assertEquals(setOf("soc-daily-5"), s.completed)
        assertEquals("soc", s.kinds["soc-daily-5"])
        assertEquals(DAILY_SOC_POINTS, s.earned)
        assertEquals(5, s.spent)
        assertEquals(20, s.skillXp["linux"])
        assertEquals(exported.hintSpent, mapOf("m01" to 5))
    }

    @Test fun exportDropsTheImportedMarkerRow() = runBlocking {
        repo.import(ProgressExport(spent = 30), Catalog(emptyList(), emptyList(), emptyList()))
        val e = repo.export()
        assertEquals(30, e.spent)
        assertTrue(e.hintsUsed.isEmpty() && e.hintSpent.isEmpty())
    }

    @Test fun importDropsUnknownIdsAndClampsHintValues() = runBlocking {
        val e = ProgressExport(
            hintsUsed = mapOf("soc-daily-1" to 99, "ghost" to 2),
            hintSpent = mapOf("soc-daily-1" to 9999, "ghost" to 50),
            codex = listOf("no-such-lesson"),
            spent = 75,
        )
        repo.import(e, Catalog(emptyList(), emptyList(), emptyList()))
        val s = state()
        assertEquals(mapOf("soc-daily-1" to 4), s.hintCounts - "_imported")
        assertEquals(75, s.hintSpent["soc-daily-1"])
        assertFalse("ghost" in s.hintCounts)
        assertTrue(s.codex.isEmpty())
        assertEquals(75, s.spent)
    }

    @Test fun streakIsReevaluatedWhenTheDayRollsOver() = runBlocking {
        repo.complete("m01", "mission", 100, emptyMap(), emptyList())
        val latest = MutableStateFlow(-1)
        val job = launch(Dispatchers.Default) { repo.state.collect { latest.value = it.streak } }
        withTimeout(5_000) { latest.first { it == 1 } }
        today += 2 // two days without activity: the streak is broken, but no table changed
        repo.refreshDay()
        withTimeout(5_000) { latest.first { it == 0 } }
        job.cancel()
    }

    @Test fun resetErasesEverything() = runBlocking {
        repo.complete("m01", "mission", 100, mapOf("linux" to 20), listOf("shell-basics"))
        repo.takeHint("m01", 0)
        repo.import(repo.export().copy(spent = 50), Catalog(emptyList(), emptyList(), emptyList()))
        repo.reset()
        val s = state()
        assertTrue(s.completed.isEmpty() && s.codex.isEmpty() && s.skillXp.isEmpty() && s.hintCounts.isEmpty())
        assertEquals(0, s.spent)
        assertEquals(0, s.streak)
        assertEquals(0, s.points)
    }
}
