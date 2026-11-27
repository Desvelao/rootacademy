package app.root_academy

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.root_academy.cipher.CipherViewModel
import app.root_academy.core.cipher.Ciphers
import app.root_academy.core.content.ContentSource
import app.root_academy.data.AppDatabase
import app.root_academy.data.Clock
import app.root_academy.data.ContentRepository
import app.root_academy.data.ProgressRepository
import app.root_academy.defense.DefenseViewModel
import app.root_academy.defense.Phase
import app.root_academy.soc.SocViewModel
import app.root_academy.terminal.TerminalViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Drives the sub-game view models against the real content packs and an in-memory database. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SubGameViewModelsTest {
    private lateinit var db: AppDatabase
    private lateinit var content: ContentRepository
    private var today = 20_000L
    private lateinit var progress: ProgressRepository

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        content = ContentRepository(object : ContentSource {
            private val root = File("../content")
            override fun list(dir: String) = File(root, dir).list()?.toList() ?: emptyList()
            override fun read(path: String) = File(root, path).takeIf { it.isFile }?.readText()
        })
        progress = ProgressRepository(db, Clock { today })
    }

    @After fun tearDown() { db.close(); Dispatchers.resetMain() }

    private fun handle(id: String) = SavedStateHandle(mapOf("id" to id))
    private fun contentFile(path: String) = File("../content/$path").readText()

    private fun <T> await(timeoutMs: Long = 120_000, block: suspend () -> T): T = runBlocking { withTimeout(timeoutMs) { block() } }
    private fun completed() = await { progress.state.first().completed }
    private suspend fun waitUntil(cond: suspend () -> Boolean) { while (!cond()) delay(10) }

    // ----- SOC -----
    private fun playSoc(vm: SocViewModel) {
        repeat(vm.queue.size) { i ->
            vm.answer(vm.queue[i].template.verdict)
            vm.next()
        }
    }

    @Test fun socStoryShiftPaysOnceThenNothing() {
        val day = content.catalog.soc!!.value.days.first()
        val first = SocViewModel(handle(day.id), content, progress, Clock { today })
        playSoc(first)
        assertTrue(first.ui.value.passed)
        await { waitUntil { first.ui.value.reward > 0 } }
        assertEquals(day.points, first.ui.value.reward)
        assertTrue(day.id in completed())

        val again = SocViewModel(handle(day.id), content, progress, Clock { today })
        playSoc(again)
        await { waitUntil { again.ui.value.finished } }
        assertEquals(0, again.ui.value.reward)
    }

    @Test fun socShiftProgressSurvivesProcessDeath() {
        val day = content.catalog.soc!!.value.days.first()
        val saved = handle(day.id)
        val first = SocViewModel(saved, content, progress, Clock { today })
        first.answer(first.queue[0].template.verdict); first.next()
        first.answer(first.queue[1].template.verdict)
        val restored = SocViewModel(saved, content, progress, Clock { today })
        assertEquals(1, restored.ui.value.index)
        assertEquals(2, restored.ui.value.correct)
        assertEquals(first.queue[1].template.verdict, restored.ui.value.answered)
    }

    @Test fun socPassedShiftIsCreditedAgainOnRestore() {
        val day = content.catalog.soc!!.value.days.first()
        val saved = handle(day.id)
        val first = SocViewModel(saved, content, progress, Clock { today })
        playSoc(first)
        await { waitUntil { first.ui.value.reward > 0 } }
        val restored = SocViewModel(saved, content, progress, Clock { today })
        assertTrue(restored.ui.value.finished && restored.ui.value.passed)
        await { waitUntil { day.id in progress.state.first().completed } }
    }

    @Test fun socDailyRecordsTheEpochDayAndFailingDoesNotComplete() {
        val vm = SocViewModel(handle(SocViewModel.DAILY), content, progress, Clock { today })
        assertEquals(SocViewModel.DAILY_COUNT, vm.queue.size)
        playSoc(vm)
        await { waitUntil { "soc-daily-$today" in progress.state.first().completed } }

        today += 1
        val tomorrow = SocViewModel(handle(SocViewModel.DAILY), content, progress, Clock { today })
        // Always pick a wrong verdict.
        repeat(tomorrow.queue.size) { i ->
            tomorrow.answer(SocEngineVerdicts.wrongFor(tomorrow.queue[i].template.verdict)); tomorrow.next()
        }
        assertTrue(tomorrow.ui.value.finished)
        assertFalse(tomorrow.ui.value.passed)
        assertEquals(setOf("soc-daily-${today - 1}"), completed())
    }

    @Test fun socRetryRegeneratesStoryQueuesDeterministicallyButNotTheDaily() {
        val day = content.catalog.soc!!.value.days.first()
        fun ids(vm: SocViewModel) = vm.queue.map { it.template.id to it.params }
        val a = SocViewModel(handle(day.id), content, progress, Clock { today })
        val b = SocViewModel(handle(day.id), content, progress, Clock { today })
        val first = ids(a)
        a.retry(); b.retry()
        assertNotEquals(first, ids(a))
        assertEquals(ids(a), ids(b))

        val daily = SocViewModel(handle(SocViewModel.DAILY), content, progress, Clock { today })
        val before = ids(daily)
        daily.retry()
        assertEquals(before, ids(daily))
    }

    // ----- Cipher -----
    @Test fun cipherRewardsFirstSolveOnly() {
        val level = content.catalog.ciphers.first().value
        val key = contentFile("cipher/${level.id}/solution.txt").trim()
        val answer = Ciphers.decrypt(level.cipher, key, level.ciphertext)!!

        val vm = CipherViewModel(handle(level.id), content, progress)
        vm.submit("definitely wrong")
        assertTrue(vm.ui.value.wrong)
        vm.submit(answer)
        await { waitUntil { (vm.ui.value.reward ?: 0) > 0 } }
        assertEquals(level.points, vm.ui.value.reward)
        assertFalse(vm.ui.value.wrong)

        val again = CipherViewModel(handle(level.id), content, progress)
        again.submit(answer)
        await { waitUntil { again.ui.value.reward != null } }
        assertEquals(0, again.ui.value.reward)
    }

    // ----- Terminal -----
    @Test fun terminalRestoreCreditsAMissionThatWasNeverPersisted() {
        val mission = content.catalog.missions.first().value
        val solution = contentFile("missions/${mission.id}/solution.txt").lines().filter { it.isNotBlank() && !it.startsWith("#") }
        // The process died after the last command was saved, before the completion was written.
        val restored = TerminalViewModel(handle(mission.id).also { it["lines"] = ArrayList(solution) }, content, progress)
        assertTrue(restored.ui.value.completed)
        await { waitUntil { restored.ui.value.reward != null } }
        assertEquals(mission.points, restored.ui.value.reward)
        assertTrue(mission.id in completed())
    }

    @Test fun terminalMissionRewardsOnceAndResetClears() {
        val mission = content.catalog.missions.first().value
        val solution = contentFile("missions/${mission.id}/solution.txt").lines().filter { it.isNotBlank() && !it.startsWith("#") }
        val vm = TerminalViewModel(handle(mission.id), content, progress)
        solution.forEach { vm.submit(it) }
        assertTrue(vm.ui.value.completed)
        await { waitUntil { vm.ui.value.reward != null } }
        assertEquals(mission.points, vm.ui.value.reward)

        // Restoring the same saved state replays the session without paying again.
        val restored = TerminalViewModel(handle(mission.id).also { it["lines"] = ArrayList(solution) }, content, progress)
        assertTrue(restored.ui.value.completed)
        assertEquals(null, restored.ui.value.reward)

        vm.reset()
        assertFalse(vm.ui.value.completed)
        assertTrue(vm.ui.value.lines.size <= 3)
    }

    // ----- Defense -----
    private fun runWave(vm: DefenseViewModel, wave: Int) {
        vm.start(speed = 1, instant = true)
        await { waitUntil { vm.ui.value.phase != Phase.RUNNING && vm.ui.value.wave > wave || vm.ui.value.phase == Phase.LOST } }
    }

    @Test fun defenseReferenceSolutionWinsAndPaysOnce() {
        val map = content.catalog.maps.first().value
        val waves = contentFile("defense/${map.id}/solution.txt").lines().filter { it.isNotBlank() && !it.startsWith("#") }
        val vm = DefenseViewModel(handle(map.id), content, progress)
        waves.forEachIndexed { i, line ->
            line.substringAfter(':').trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                .forEach { tok -> tok.split('@').let { vm.build(it[0], it[1]) } }
            runWave(vm, i)
        }
        assertEquals(Phase.WON, vm.ui.value.phase)
        await { waitUntil { vm.ui.value.reward > 0 } }
        assertEquals(map.points, vm.ui.value.reward)
    }

    @Test fun defenseBuildsAreRestoredFromSavedState() {
        val map = content.catalog.maps.first().value
        val saved = handle(map.id)
        val vm = DefenseViewModel(saved, content, progress)
        val node = map.nodes.first { it.id != map.spawn }.id
        vm.build("firewall", node)
        assertTrue(vm.ui.value.placements[node].orEmpty().contains("firewall"))
        val restored = DefenseViewModel(saved, content, progress)
        assertTrue(restored.ui.value.placements[node].orEmpty().contains("firewall"))
        assertEquals(vm.ui.value.money, restored.ui.value.money)
    }

    @Test fun defenseUndefendedMapIsLost() {
        val map = content.catalog.maps.first().value
        val vm = DefenseViewModel(handle(map.id), content, progress)
        for (w in map.waves.indices) { if (vm.ui.value.phase == Phase.LOST) break; runWave(vm, w) }
        assertEquals(Phase.LOST, vm.ui.value.phase)
        assertNotEquals(Phase.WON, vm.ui.value.phase)
        assertTrue(completed().isEmpty())
    }
}

private object SocEngineVerdicts {
    fun wrongFor(v: String) = app.root_academy.core.soc.SocEngine.VERDICTS.first { it != v }
}
