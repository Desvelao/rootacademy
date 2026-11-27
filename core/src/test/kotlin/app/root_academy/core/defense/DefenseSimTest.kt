package app.root_academy.core.defense

import app.root_academy.core.content.*
import kotlin.test.*

class DefenseSimTest {
    private fun map(vararg waves: List<DefSpawn>, integrity: Int = 3) = DefenseMap(
        id = "t", order = 1, integrity = integrity, startMoney = 100, income = 50,
        nodes = listOf(DefNode("net", 0f, 0f), DefNode("fw", .5f, 0f), DefNode("mail", .5f, 1f), DefNode("db", 1f, .5f)),
        links = listOf(listOf("net", "fw"), listOf("fw", "db"), listOf("fw", "mail"), listOf("mail", "db")),
        spawn = "net", target = "db", waves = waves.toList(),
    )

    @Test fun undefendedAttacksHitTheTarget() {
        val g = DefenseGame(map(listOf(DefSpawn(0, "ddos", 2, 10))))
        val sim = g.startWave().runToEnd()
        assertEquals(1, sim.integrity)
        assertTrue(sim.won)
    }

    @Test fun cannotStartOrFinishWavesOutOfOrder() {
        val g = DefenseGame(map(listOf(DefSpawn(0, "ddos", 1, 10)), listOf(DefSpawn(0, "ddos", 1, 10))))
        val first = g.startWave()
        assertFailsWith<IllegalArgumentException> { g.finishWave(first) }
        g.finishWave(first.runToEnd())
        val second = g.startWave()
        assertFailsWith<IllegalArgumentException> { g.finishWave(first) }
        g.finishWave(second.runToEnd())
        assertTrue(g.over)
        assertFailsWith<IllegalStateException> { g.startWave() }
    }

    @Test fun graphToleratesLinksToUnknownNodes() {
        val bad = map(listOf(DefSpawn(0, "ddos", 1, 10))).let { it.copy(links = it.links + listOf(listOf("fw", "ghost"))) }
        assertEquals(listOf("net", "fw", "db"), Graph(bad).path("net", "db"))
    }

    @Test fun firewallStopsDdosButNotPhishing() {
        val m = map(listOf(DefSpawn(0, "ddos", 3, 5), DefSpawn(0, "phishing", 1, 5)))
        val g = DefenseGame(m)
        assertTrue(g.place("firewall", "fw"))
        val sim = g.startWave().runToEnd()
        assertEquals(3, sim.stopped)
        assertEquals(2, sim.integrity, "phishing ignores firewalls and hits once")
    }

    @Test fun backupBeatsRansomwareOnTheTarget() {
        val g = DefenseGame(map(listOf(DefSpawn(0, "ransomware", 2, 20))))
        g.place("backup", "db")
        assertEquals(3, g.startWave().runToEnd().integrity)
    }

    @Test fun patchKillsWormsAndViaRoutesThroughNode() {
        val g = DefenseGame(map(listOf(DefSpawn(0, "worm", 1, 1, via = "mail"))))
        val sim = g.startWave()
        assertEquals(listOf("net", "fw", "mail", "db"), sim.also { it.step() }.units.single().path)
        g.place("patch", "mail")
        assertEquals(3, g.startWave().runToEnd().integrity)
    }

    @Test fun moneyAndPlacementRules() {
        val g = DefenseGame(map(listOf(DefSpawn(0, "ddos"))))
        assertTrue(g.place("firewall", "fw"))
        assertFalse(g.place("firewall", "fw"), "no duplicates")
        assertFalse(g.place("nonsense", "fw"))
        assertFalse(g.place("mfa", "nowhere"))
        assertEquals(50, g.money)
        assertTrue(g.sell("firewall", "fw"))
        assertEquals(75, g.money)
        assertFalse(g.place("backup", "db") && g.place("backup", "fw") && g.place("filter", "fw"), "runs out of money")
    }

    @Test fun losingEndsTheGame() {
        val g = DefenseGame(map(listOf(DefSpawn(0, "ransomware", 2, 10)), integrity = 3))
        g.finishWave(g.startWave().runToEnd())
        assertTrue(g.over)
        assertFalse(g.won)
        g.restart()
        assertEquals(3, g.integrity)
    }

    @Test fun replayRejectsBadSolutions() {
        val m = map(listOf(DefSpawn(0, "ddos", 3, 5)))
        assertTrue(DefenseGame.replay(m, "wave 1: firewall@fw").isEmpty())
        assertTrue(DefenseGame.replay(m, "wave 1: ").isNotEmpty())
        assertTrue(DefenseGame.replay(m, "wave 1: firewall@fw firewall@db backup@db").isNotEmpty())
    }

    // ----- replay validation -----
    private val twoWaves = map(listOf(DefSpawn(0, "ddos")), listOf(DefSpawn(0, "ddos")))

    @Test fun replayAcceptsAGoodSolution() {
        assertEquals(emptyList(), DefenseGame.replay(twoWaves, "# comment\nwave 1: firewall@fw\n  # indented comment\nwave 2:\n"))
    }

    @Test fun replayReportsEachKindOfMistake() {
        fun problem(sol: String) = DefenseGame.replay(twoWaves, sol).single()
        assertTrue(problem("wave 2: firewall@fw\nwave 1:").contains("labelled"))
        assertTrue(problem("firewall@fw\nwave 2:").contains("expected 'wave N"))
        assertTrue(problem("wave 1: firewall\nwave 2:").contains("malformed"))
        assertTrue(problem("wave 1: moat@fw\nwave 2:").contains("unknown defense"))
        assertTrue(problem("wave 1: firewall@nowhere\nwave 2:").contains("unknown node"))
        assertTrue(problem("wave 1: firewall@fw\nwave 2: firewall@fw").contains("already placed"))
        assertTrue(problem("wave 1: firewall@fw backup@db filter@mail\nwave 2:").contains("cannot afford"))
        assertTrue(problem("wave 1:").contains("wave lines"))
    }

    // ----- graph -----
    @Test fun graphPathsAndRoutes() {
        val m = map(listOf(DefSpawn(0, "ddos")))
        val g = Graph(m)
        assertEquals(listOf("net"), g.path("net", "net"))
        assertEquals(listOf("net", "fw", "db"), g.path("net", "db"))
        assertEquals(listOf("mail", "db"), g.route(DefSpawn(0, "ddos", from = "mail")))
        assertEquals(listOf("net", "fw", "mail", "db"), g.route(DefSpawn(0, "ddos", via = "mail")))
        val island = m.copy(nodes = m.nodes + DefNode("lonely", 0f, 0f))
        assertNull(Graph(island).path("net", "lonely"))
        assertNull(Graph(island).route(DefSpawn(0, "ddos", via = "lonely")))
    }

    // ----- economy and outcomes -----
    @Test fun sellRefundsHalfRoundedDown() {
        val g = DefenseGame(map(listOf(DefSpawn(0, "ddos"))))
        g.place("ids", "fw"); assertEquals(75, g.money)
        g.sell("ids", "fw"); assertEquals(87, g.money, "25 / 2 = 12")
        g.place("patch", "fw"); g.sell("patch", "fw"); assertEquals(87 - 30 + 15, g.money)
    }

    @Test fun incomeIsPaidBetweenWavesButNotAfterTheLastOrALoss() {
        val g = DefenseGame(twoWaves)
        g.finishWave(g.startWave().runToEnd())
        assertEquals(150, g.money)
        assertFalse(g.over)
        g.finishWave(g.startWave().runToEnd())
        assertEquals(150, g.money, "no income after the last wave")
        assertTrue(g.over && g.won)

        val lose = DefenseGame(map(listOf(DefSpawn(0, "ransomware", 2, 10)), listOf(DefSpawn(0, "ddos")), integrity = 3))
        lose.finishWave(lose.startWave().runToEnd())
        assertEquals(100, lose.money, "no income after losing")
        assertTrue(lose.over && !lose.won)
    }

    @Test fun restartClearsEverything() {
        val g = DefenseGame(twoWaves)
        g.place("firewall", "fw")
        g.finishWave(g.startWave().runToEnd())
        g.restart()
        assertEquals(0, g.wave); assertEquals(100, g.money); assertEquals(3, g.integrity)
        assertTrue(g.placements.values.all { it.isEmpty() })
    }

    @Test fun everyCounterDoesWhatTheRulesSay() {
        fun integrityWith(defense: String?, attack: String, count: Int = 1): Int {
            val g = DefenseGame(map(listOf(DefSpawn(0, attack, count, 30)), integrity = 10))
            if (defense != null) g.place(defense, "db")
            return g.startWave().runToEnd().integrity
        }
        assertEquals(10, integrityWith("mfa", "bruteforce"))
        assertEquals(9, integrityWith("mfa", "ddos"), "mfa does not stop ddos")
        assertEquals(10, integrityWith("filter", "phishing"))
        assertEquals(9, integrityWith(null, "phishing"))
        assertEquals(10, integrityWith("patch", "worm"))
        assertEquals(9, integrityWith("patch", "ddos"))
        assertEquals(9, integrityWith("ids", "worm"), "ids alone is too weak to stop a worm in time")
    }
}
