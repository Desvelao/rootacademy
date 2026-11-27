package app.root_academy.core.progress

import app.root_academy.core.content.Skill
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** XP needed to reach a level grows quadratically: level n needs 50 * n^2 XP in total. */
object SkillCurve {
    const val XP_UNIT = 50
    const val MAX_LEVEL = 10

    fun level(xp: Int): Int {
        var l = 0
        while (l < MAX_LEVEL && xp >= XP_UNIT * (l + 1) * (l + 1)) l++
        return l
    }

    fun xpForLevel(level: Int) = XP_UNIT * level * level

    /** Progress 0..1 inside the current level (1 at max level). */
    fun fraction(xp: Int): Float {
        val l = level(xp)
        if (l >= MAX_LEVEL) return 1f
        val lo = xpForLevel(l); val hi = xpForLevel(l + 1)
        return (xp - lo).toFloat() / (hi - lo)
    }
}

/** Hint costs in points: hint 1, 2, 3, then the walkthrough. Never blocks progress. */
object HintCost {
    private val costs = listOf(5, 10, 20, 40)
    fun forStep(step: Int) = costs[step.coerceIn(0, costs.lastIndex)]

    /** Hints one activity offers (steps 0..3) and the most they can cost in total. */
    val maxHints = costs.size
    val maxSpent = costs.sum()
}

/** Points balance: points earned minus points spent on hints (paid when a hint is taken). Clamped at 0, so over-spend is not shown as a debt. */
object Scoring {
    fun balance(earned: Int, spentOnHints: Int) = (earned - spentOnHints).coerceAtLeast(0)
}

data class Badge(val id: String, val threshold: Int)

/** Badges by completed activity count. */
object Badges {
    val all = listOf(Badge("first-flag", 1), Badge("five-flags", 5), Badge("ten-flags", 10), Badge("twenty-flags", 20), Badge("all-flags", 35))
    fun earned(completed: Int) = all.filter { completed >= it.threshold }.map { it.id }
}

/**
 * Consecutive-day streak ending today (or yesterday, if nothing was solved yet today), from the set of epoch days
 * the player solved something on. Days after [today] are ignored.
 */
object Streak {
    fun current(days: Set<Long>, today: Long): Int {
        var d = if (today in days) today else today - 1
        var n = 0
        while (d in days) { n++; d-- }
        return n
    }
}

/** Seeded choice of the daily puzzle: everybody gets the same one on the same day. */
object Daily {
    fun index(epochDay: Long, size: Int): Int = if (size == 0) 0 else Math.floorMod(java.util.Random(epochDay * 7919).nextInt(), size)
}

/** Outcome of reading a progress export file. */
sealed interface ImportResult {
    data class Ok(val export: ProgressExport) : ImportResult
    /** Written by a newer app version. */
    data class UnsupportedVersion(val version: Int) : ImportResult
    /** Not JSON, or not a progress export. */
    data object Invalid : ImportResult
}

@Serializable
data class ProgressExport(
    val version: Int = 1,
    val completed: List<String> = emptyList(),
    val hintsUsed: Map<String, Int> = emptyMap(),
    /** Points spent on hints per activity (absent in older files, which only have [spent]). */
    val hintSpent: Map<String, Int> = emptyMap(),
    val skillXp: Map<String, Int> = emptyMap(),
    val codex: List<String> = emptyList(),
    val activeDays: List<Long> = emptyList(),
    val points: Int = 0,
    val spent: Int = 0,
) {
    fun encode(): String = PRETTY.encodeToString(serializer(), this)

    /** A copy with negative numbers raised to 0, unknown skill ids dropped and lists de-duplicated. */
    fun sanitized() = copy(
        completed = completed.distinct(),
        hintsUsed = hintsUsed.mapValues { it.value.coerceAtLeast(0) },
        hintSpent = hintSpent.mapValues { it.value.coerceAtLeast(0) },
        skillXp = skillXp.filterKeys { it in SKILL_IDS }.mapValues { it.value.coerceAtLeast(0) },
        codex = codex.distinct(),
        activeDays = activeDays.distinct().sorted(),
        points = points.coerceAtLeast(0),
        spent = spent.coerceAtLeast(0),
    )

    companion object {
        const val VERSION = 1

        fun parse(text: String): ImportResult {
            val e = try { LENIENT.decodeFromString(serializer(), text) } catch (_: Exception) { return ImportResult.Invalid }
            return when {
                e.version < 1 -> ImportResult.Invalid
                e.version == VERSION -> ImportResult.Ok(e.sanitized())
                else -> ImportResult.UnsupportedVersion(e.version)
            }
        }

        /** Returns null when [text] is not a valid export of a supported version. */
        fun decode(text: String): ProgressExport? = (parse(text) as? ImportResult.Ok)?.export
    }
}

private val PRETTY = Json { prettyPrint = true }
private val LENIENT = Json { ignoreUnknownKeys = true; coerceInputValues = true }

val SKILL_IDS = Skill.entries.map { it.id }
