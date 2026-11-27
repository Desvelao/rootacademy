package app.root_academy.core.content

import kotlin.test.*

class CampaignTest {
    private val camp = Campaign(
        id = "c",
        steps = listOf(
            CampaignStep("a", "x1", emptyList(), "a.story"),
            CampaignStep("b", "x2", listOf("a"), "b.story"),
        ),
    )

    @Test fun stepsUnlockInOrder() {
        assertEquals(StepState.AVAILABLE, CampaignProgress.state(camp, emptySet()).steps["a"])
        assertEquals(StepState.LOCKED, CampaignProgress.state(camp, emptySet()).steps["b"])
        val s = CampaignProgress.state(camp, setOf("x1"))
        assertEquals(StepState.DONE, s.steps["a"])
        assertEquals(StepState.AVAILABLE, s.steps["b"])
        assertTrue(CampaignProgress.state(camp, setOf("x1", "x2")).complete)
    }

    @Test fun blockersListUnmetPrerequisitesOnly() {
        val packs = listOf(Pack(camp, emptyMap()))
        assertEquals(listOf("a"), CampaignProgress.blockers(packs, "x2", emptySet()).map { it.second.id })
        assertTrue(CampaignProgress.blockers(packs, "x2", setOf("x1")).isEmpty())
        assertTrue(CampaignProgress.blockers(packs, "other", emptySet()).isEmpty())
    }

    @Test fun blockersCombineSeveralCampaigns() {
        val other = Campaign(id = "d", steps = listOf(
            CampaignStep("p", "y1", emptyList(), "p.story"),
            CampaignStep("q", "x2", listOf("p"), "q.story"),
        ))
        val packs = listOf(Pack(camp, emptyMap()), Pack(other, emptyMap()))
        assertEquals(setOf("c:a", "d:p"), CampaignProgress.blockers(packs, "x2", emptySet()).map { "${it.first.id}:${it.second.id}" }.toSet())
        assertEquals(listOf("d:p"), CampaignProgress.blockers(packs, "x2", setOf("x1")).map { "${it.first.id}:${it.second.id}" })
    }

    @Test fun structureProblemsFound() {
        val bad = camp.copy(steps = listOf(
            CampaignStep("a", "x1", listOf("b"), "s"),
            CampaignStep("b", "x1", emptyList(), "s"),
            CampaignStep("b", "x3", emptyList(), "s"),
        ))
        val p = CampaignProgress.structureProblems(bad)
        assertTrue(p.any { it.contains("not an earlier step") }, p.toString())
        assertTrue(p.any { it.contains("duplicate step") }, p.toString())
        assertTrue(p.any { it.contains("several steps") }, p.toString())
    }

    // ---- loading games and campaigns from data ----
    private val mission = """{"id":"m1","startHost":"a","hosts":[{"ip":"1.1.1.1","name":"a"}],
        "objectives":[{"id":"f","on":"flag"}],"flag_sha256":"${"a".repeat(64)}"}"""
    private val mStrings = """{"title":"T","brief":"B","debrief":"D","walkthrough":"W","hint.1":"1","hint.2":"2","hint.3":"3","objective.f":"Flag"}"""
    private val gStrings = """{"title":"G","subtitle":"S","progress":"%1${'$'}d/%2${'$'}d"}"""
    private fun both(dir: String, s: String) = mapOf("$dir/strings.en.json" to s, "$dir/strings.es.json" to s)

    private fun src(extra: Map<String, String> = emptyMap()) = MapSource(
        mapOf(
            "games/ops/game.json" to """{"id":"ops","engine":"terminal","contentDir":"ops"}""",
            "ops/missions/m1/mission.json" to mission,
            "ops/missions/m1/solution.txt" to "submit x",
        ) + both("games/ops", gStrings) + both("ops/missions/m1", mStrings) + extra,
    )

    @Test fun gameDefinedInDataLoadsItsOwnContentDir() {
        val c = PackLoader(src()).loadAll()
        assertEquals("ops", c.activity("m1")!!.game)
        assertEquals(listOf("m1"), c.activitiesOf("ops").map { it.id })
        assertEquals(listOf("G"), c.games.map { it.text("en", "title") })
    }

    @Test fun validatorChecksGamesAndCampaigns() {
        val badGame = src(mapOf("games/ops/game.json" to """{"id":"ops","engine":"nope"}"""))
        assertTrue(PackValidator.validate(PackLoader(badGame).loadAll()).any { it.contains("unknown engine 'nope'") })

        val camp = """{"id":"k","steps":[{"id":"a","activity":"m1","story":"a.story"},{"id":"b","activity":"ghost","requires":["a"],"story":"b.story"}]}"""
        val cs = """{"title":"t","intro":"i","outro":"o","a.story":"x","b.story":"y"}"""
        val s = src(mapOf("campaigns/k/campaign.json" to camp) + both("campaigns/k", cs))
        val errors = PackValidator.validate(PackLoader(s).loadAll())
        assertTrue(errors.any { it.contains("unknown activity 'ghost'") }, errors.toString())

        val ok = src(mapOf("campaigns/k/campaign.json" to """{"id":"k","steps":[{"id":"a","activity":"m1","story":"a.story"}]}""") + both("campaigns/k", cs))
        assertTrue(PackValidator.validate(PackLoader(ok).loadAll()).none { it.startsWith("k:") })
    }

    @Test fun crossCampaignCycleIsReported() {
        val m2 = mapOf(
            "ops/missions/m2/mission.json" to mission.replace("\"m1\"", "\"m2\""),
            "ops/missions/m2/solution.txt" to "submit x",
        ) + both("ops/missions/m2", mStrings)
        fun camp(id: String, first: String, second: String) = mapOf(
            "campaigns/$id/campaign.json" to """{"id":"$id","steps":[{"id":"a","activity":"$first","story":"a.story"},{"id":"b","activity":"$second","requires":["a"],"story":"a.story"}]}""",
        ) + both("campaigns/$id", """{"title":"t","intro":"i","outro":"o","a.story":"x"}""")
        val errors = PackValidator.validate(PackLoader(src(m2 + camp("p", "m1", "m2") + camp("q", "m2", "m1"))).loadAll())
        assertTrue(errors.any { it.contains("cycle across campaigns") }, errors.toString())
    }
}
