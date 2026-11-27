package app.root_academy.data

import app.root_academy.core.content.Catalog
import app.root_academy.core.progress.Badges
import app.root_academy.core.progress.HintCost
import app.root_academy.core.progress.ProgressExport
import app.root_academy.core.progress.Streak
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** Source of "today" so tests and the daily challenge never read the system clock directly. */
fun interface Clock { fun epochDay(): Long }

val SystemClock = Clock { LocalDate.now().toEpochDay() }

/** Points a daily SOC shift pays (story shifts define their own in the pack). */
const val DAILY_SOC_POINTS = 60

/** Completion kinds that count towards the flag badges. */
private val FLAG_KINDS = setOf("mission", "puzzle")

/** Marker row that holds hint points whose per-activity split is unknown (files from older exports). */
private const val IMPORTED_SPENT_ID = "_imported"

data class ProgressState(
    val completed: Set<String> = emptySet(),
    /** Completion kind per completed id ("mission", "puzzle", "cipher", "soc", "defense"). */
    val kinds: Map<String, String> = emptyMap(),
    val hintCounts: Map<String, Int> = emptyMap(),
    val hintSpent: Map<String, Int> = emptyMap(),
    val skillXp: Map<String, Int> = emptyMap(),
    val codex: Set<String> = emptySet(),
    val earned: Int = 0,
    val spent: Int = 0,
    val streak: Int = 0,
    val last: LastActivityEntity? = null,
) {
    val points get() = (earned - spent).coerceAtLeast(0)
    /** Badges count flags only (missions and puzzles), not shifts, cipher levels or maps. */
    val badges get() = Badges.earned(kinds.values.count { it in FLAG_KINDS })
}

class ProgressRepository(private val db: AppDatabase, private val clock: Clock) {
    private val dao = db.progress()

    /** Changes when the calendar day does, so that the streak is re-evaluated even if no table changed. */
    private val day = MutableStateFlow(clock.epochDay())

    /** Call periodically (and on resume): re-evaluates the streak when the day has rolled over. */
    fun refreshDay() { day.value = clock.epochDay() }

    val state: Flow<ProgressState> = combine(
        combine(dao.completions(), dao.hintUses(), dao.skillXp()) { a, b, c -> Triple(a, b, c) },
        combine(dao.codex(), dao.activeDays(), dao.last()) { a, b, c -> Triple(a, b, c) },
        day,
    ) { (comp, hints, xp), (codex, days, last), _ ->
        ProgressState(
            completed = comp.map { it.activityId }.toSet(),
            kinds = comp.associate { it.activityId to it.kind },
            hintCounts = hints.associate { it.activityId to it.count },
            hintSpent = hints.associate { it.activityId to it.spent },
            skillXp = xp.associate { it.skill to it.xp },
            codex = codex.map { it.lessonId }.toSet(),
            earned = comp.sumOf { it.points },
            spent = hints.sumOf { it.spent },
            streak = Streak.current(days.map { it.day }.toSet(), clock.epochDay()),
            last = last,
        )
    }

    /**
     * Remembers the last mission or puzzle opened; only Home's "continue" card reads it, and it can only reopen those
     * two kinds. Cipher, SOC and defense deliberately do not call it: they would replace the row with an id Home
     * cannot render and the card would vanish. Add their routes to Home before calling it from them.
     */
    suspend fun markOpened(id: String, kind: String) = dao.setLast(LastActivityEntity(0, id, kind))

    /** Takes the next hint (step 0..3, 3 = walkthrough) and charges its cost. */
    suspend fun takeHint(id: String, step: Int) = db.withTransaction {
        val cur = dao.hintUse(id)
        if (cur != null && cur.count > step) return@withTransaction
        dao.upsertHint(HintUseEntity(id, step + 1, (cur?.spent ?: 0) + HintCost.forStep(step)))
    }

    /** Records a first completion; returns false when it was already done (no double rewards). */
    suspend fun complete(id: String, kind: String, points: Int, skills: Map<String, Int>, codex: List<String>): Boolean = db.withTransaction {
        val today = clock.epochDay()
        if (dao.insertCompletion(CompletionEntity(id, kind, today, points)) == -1L) return@withTransaction false
        for ((skill, gain) in skills) dao.upsertXp(SkillXpEntity(skill, (dao.xpOf(skill)?.xp ?: 0) + gain))
        codex.forEach { dao.insertCodex(CodexUnlockEntity(it, today)) }
        dao.insertDay(ActiveDayEntity(today))
        true
    }

    suspend fun export(): ProgressExport {
        val s = snapshot()
        return ProgressExport(
            completed = s.completed.toList(),
            hintsUsed = s.hintCounts - IMPORTED_SPENT_ID,
            hintSpent = s.hintSpent - IMPORTED_SPENT_ID,
            skillXp = s.skillXp, codex = s.codex.toList(),
            activeDays = dao.daysList().map { it.day }, points = s.earned, spent = s.spent,
        )
    }

    /** Kind and payout of a known activity id, or null when the id is not in the content. */
    private fun resolve(id: String, catalog: Catalog): Pair<String, Int>? {
        catalog.activity(id)?.let { return it.kind to it.points }
        if (id.startsWith("soc-daily-")) return "soc" to DAILY_SOC_POINTS
        return null
    }

    /** Erases all progress (completions, hints, XP, codex, streak, last opened) in one transaction. Settings are kept. */
    suspend fun reset() = db.withTransaction { clearAll() }

    private suspend fun clearAll() {
        dao.clearCompletions(); dao.clearHints(); dao.clearXp(); dao.clearCodex(); dao.clearDays(); dao.clearLast()
    }

    /** Replaces local progress with [e], all or nothing; unknown activity ids are dropped. */
    suspend fun import(e: ProgressExport, catalog: Catalog) = db.withTransaction {
        clearAll()
        val today = clock.epochDay()
        e.completed.forEach { id ->
            resolve(id, catalog)?.let { (kind, points) -> dao.insertCompletion(CompletionEntity(id, kind, today, points)) }
        }
        // Hint rows only make sense for known activities, with a count and a cost the game can actually produce.
        val known = { id: String -> resolve(id, catalog) != null }
        val counts = e.hintsUsed.filterKeys(known).mapValues { it.value.coerceIn(0, HintCost.maxHints) }
        val spentBy = e.hintSpent.filterKeys(known).mapValues { it.value.coerceIn(0, HintCost.maxSpent) }
        counts.forEach { (id, n) -> dao.upsertHint(HintUseEntity(id, n, spentBy[id] ?: 0)) }
        spentBy.filterKeys { it !in counts }.forEach { (id, spent) -> dao.upsertHint(HintUseEntity(id, 0, spent)) }
        // Files from older exports only carry the total; keep what is not split per activity on a marker row.
        val unsplit = (e.spent - spentBy.values.sum()).coerceAtMost((catalog.activities.size + 1) * HintCost.maxSpent)
        if (unsplit > 0) dao.upsertHint(HintUseEntity(IMPORTED_SPENT_ID, 0, unsplit))
        e.skillXp.forEach { (s, x) -> dao.upsertXp(SkillXpEntity(s, x)) }
        e.codex.filter { catalog.lesson(it) != null }.forEach { dao.insertCodex(CodexUnlockEntity(it, today)) }
        e.activeDays.forEach { dao.insertDay(ActiveDayEntity(it)) }
    }

    private suspend fun snapshot(): ProgressState = state.first()
}
