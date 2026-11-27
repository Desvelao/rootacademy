package app.root_academy.core.progress

import kotlin.test.*

class ProgressTest {
    @Test fun skillLevels() {
        assertEquals(0, SkillCurve.level(0))
        assertEquals(1, SkillCurve.level(50))
        assertEquals(2, SkillCurve.level(200))
        assertEquals(SkillCurve.MAX_LEVEL, SkillCurve.level(1_000_000))
        assertEquals(1f, SkillCurve.fraction(1_000_000))
    }

    @Test fun streakCountsConsecutiveDays() {
        assertEquals(3, Streak.current(setOf(8L, 9L, 10L), 10L))
        assertEquals(2, Streak.current(setOf(8L, 9L), 10L))
        assertEquals(0, Streak.current(setOf(5L), 10L))
        assertEquals(1, Streak.current(setOf(10L, 7L), 10L))
    }

    @Test fun dailyIsStableAndInRange() {
        assertEquals(Daily.index(100, 20), Daily.index(100, 20))
        assertTrue(Daily.index(101, 20) in 0 until 20)
    }

    @Test fun hintCostsGrowAndCap() {
        assertTrue(HintCost.forStep(0) < HintCost.forStep(3))
        assertEquals(HintCost.forStep(3), HintCost.forStep(9))
    }

    @Test fun badges() {
        assertEquals(listOf("first-flag"), Badges.earned(2))
    }

    @Test fun exportRoundTrip() {
        val e = ProgressExport(completed = listOf("m01"), skillXp = mapOf("linux" to 20), points = 100, spent = 5, activeDays = listOf(20L))
        assertEquals(e, ProgressExport.decode(e.encode()))
        assertNull(ProgressExport.decode("not json"))
        assertNull(ProgressExport.decode("""{"version":99}"""))
    }

    @Test fun decodeSanitizesValues() {
        val text = """{"completed":["a","a"],"skillXp":{"linux":-5,"bogus":9},"points":-1,"spent":-2,"activeDays":[3,3,1],"hintsUsed":{"a":-1}}"""
        val e = ProgressExport.decode(text)!!
        assertEquals(listOf("a"), e.completed)
        assertEquals(mapOf("linux" to 0), e.skillXp)
        assertEquals(0, e.points); assertEquals(0, e.spent)
        assertEquals(listOf(1L, 3L), e.activeDays)
        assertEquals(mapOf("a" to 0), e.hintsUsed)
    }

    @Test fun parseDistinguishesFailures() {
        assertEquals(ImportResult.UnsupportedVersion(99), ProgressExport.parse("""{"version":99}"""))
        assertEquals(ImportResult.Invalid, ProgressExport.parse("nope"))
        assertTrue(ProgressExport.parse("""{"points":null}""") is ImportResult.Ok)
    }

    @Test fun nonPositiveVersionsAreInvalidNotNewer() {
        assertEquals(ImportResult.Invalid, ProgressExport.parse("""{"version":0}"""))
        assertEquals(ImportResult.Invalid, ProgressExport.parse("""{"version":-3}"""))
    }

    @Test fun scoringAndBadgeBoundaries() {
        assertEquals(0, Scoring.balance(10, 50))
        assertEquals(40, Scoring.balance(100, 60))
        assertEquals(listOf("first-flag", "five-flags"), Badges.earned(5))
        assertEquals(Badges.all.map { it.id }, Badges.earned(Badges.all.last().threshold))
        assertEquals(SkillCurve.xpForLevel(1), 50)
        assertEquals(0f, SkillCurve.fraction(50))
        assertEquals(0, SkillCurve.level(49))
        assertEquals(SkillCurve.MAX_LEVEL, SkillCurve.level(SkillCurve.xpForLevel(10)))
        assertEquals(5, HintCost.forStep(0)); assertEquals(5, HintCost.forStep(-3))
    }

    @Test fun streakIgnoresFutureDays() {
        assertEquals(1, Streak.current(setOf(10L, 11L, 12L), 10L))
    }

    @Test fun dailyEdgeCasesAndGoldenValue() {
        assertEquals(0, Daily.index(5, 0))
        assertTrue(Daily.index(-5, 7) in 0 until 7)
        assertEquals(GOLDEN, (100L..104L).map { Daily.index(it, 20) })
    }

    private companion object { val GOLDEN = listOf(5, 15, 13, 12, 13) }
}
