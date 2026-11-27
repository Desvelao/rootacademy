package app.root_academy.core.defense

import app.root_academy.core.content.DefenseMap
import app.root_academy.core.content.DefSpawn

const val TICKS_PER_SECOND = 10

data class DefenseSpec(val id: String, val cost: Int)
data class AttackSpec(val id: String, val hp: Int, val hopTicks: Int, val damage: Int)

/** Static rules: what each defense costs, what each attack is like, and who counters whom. */
object Rules {
    val defenses = listOf(
        DefenseSpec("firewall", 50), DefenseSpec("filter", 40), DefenseSpec("patch", 30),
        DefenseSpec("backup", 40), DefenseSpec("mfa", 40), DefenseSpec("ids", 25),
    )
    val attacks = listOf(
        AttackSpec("phishing", hp = 30, hopTicks = 20, damage = 1),
        AttackSpec("worm", hp = 20, hopTicks = 12, damage = 1),
        AttackSpec("ransomware", hp = 60, hopTicks = 25, damage = 2),
        AttackSpec("ddos", hp = 40, hopTicks = 10, damage = 1),
        AttackSpec("bruteforce", hp = 35, hopTicks = 18, damage = 1),
    )

    /** Damage per tick a defense deals to every attack of that type on its node. Missing = no effect. */
    private val counters: Map<String, Map<String, Int>> = mapOf(
        "firewall" to mapOf("ddos" to 4, "bruteforce" to 1, "worm" to 1, "ransomware" to 1), // phishing ignores firewalls
        "filter" to mapOf("phishing" to 2, "ransomware" to 1),
        "patch" to mapOf("worm" to 20),                                                       // worms need unpatched nodes
        "backup" to mapOf("ransomware" to 60),                                                // restore beats ransomware
        "mfa" to mapOf("bruteforce" to 10, "phishing" to 1),
        "ids" to mapOf("phishing" to 1, "worm" to 1, "ransomware" to 1, "ddos" to 1, "bruteforce" to 1),
    )

    fun cost(defense: String) = defenses.firstOrNull { it.id == defense }?.cost
    fun attack(id: String) = attacks.firstOrNull { it.id == id }
    fun damagePerTick(defense: String, attack: String) = counters[defense]?.get(attack) ?: 0
    fun counters(defense: String): Set<String> = counters[defense]?.keys ?: emptySet()
}

/** A running attack on its way along [path]. */
class Attacker(val type: String, val path: List<String>, var hp: Int) {
    var index = 0
    var dwell = 0
    val node get() = path[index]
}

/** Shortest paths over the map graph. Neighbours are visited in link order, so results are deterministic. */
class Graph(private val map: DefenseMap) {
    private val adj: Map<String, List<String>> = buildMap<String, MutableList<String>> {
        map.nodes.forEach { put(it.id, mutableListOf()) }
        map.links.forEach { (a, b) -> getOrPut(a) { mutableListOf() } += b; getOrPut(b) { mutableListOf() } += a }
    }

    fun path(from: String, to: String): List<String>? {
        if (from == to) return listOf(from)
        val prev = mutableMapOf<String, String>()
        val queue = ArrayDeque(listOf(from))
        val seen = mutableSetOf(from)
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            for (n in adj[cur].orEmpty()) if (seen.add(n)) {
                prev[n] = cur
                if (n == to) return generateSequence(to) { prev[it] }.toList().reversed()
                queue.addLast(n)
            }
        }
        return null
    }

    fun route(spawn: DefSpawn): List<String>? {
        val start = spawn.from ?: map.spawn
        val via = spawn.via ?: return path(start, map.target)
        val a = path(start, via) ?: return null
        val b = path(via, map.target) ?: return null
        return a + b.drop(1)
    }
}

/** One wave: deterministic, fixed 10 ticks per second. Call [step] until [finished]. */
class WaveSim(private val map: DefenseMap, val wave: Int, private val placements: Map<String, Set<String>>, integrity: Int) {
    private val graph = Graph(map)
    private data class Pending(val tick: Int, val type: String, val route: List<String>)
    private val pending = map.waves[wave].flatMap { s ->
        val route = graph.route(s) ?: error("no route for ${s.attack} in ${map.id}")
        (0 until s.count).map { Pending(s.at + it * s.gap, s.attack, route) }
    }.sortedBy { it.tick }
    private var next = 0

    var tick = 0
        private set
    var integrity = integrity
        private set
    var stopped = 0
        private set
    val units = mutableListOf<Attacker>()
    val finished get() = next >= pending.size && units.isEmpty()
    val won get() = finished && integrity > 0
    val lost get() = integrity <= 0

    fun step() {
        if (finished || lost) return
        while (next < pending.size && pending[next].tick <= tick) {
            val p = pending[next++]
            units += Attacker(p.type, p.route, Rules.attack(p.type)!!.hp)
        }
        val it = units.iterator()
        while (it.hasNext()) {
            val u = it.next()
            val spec = Rules.attack(u.type)!!
            u.hp -= placements[u.node].orEmpty().sumOf { d -> Rules.damagePerTick(d, u.type) }
            if (u.hp <= 0) { stopped++; it.remove(); continue }
            if (++u.dwell >= spec.hopTicks) {
                if (u.index == u.path.lastIndex) { integrity -= spec.damage; it.remove() }
                else { u.index++; u.dwell = 0 }
            }
        }
        tick++
    }

    fun runToEnd(): WaveSim { while (!finished && !lost) step(); return this }
}

/** The player's side of a map: money, placements and wave progress. */
class DefenseGame(val map: DefenseMap) {
    var wave = 0
        private set
    var money = map.startMoney
        private set
    var integrity = map.integrity
        private set
    private val placed = mutableMapOf<String, MutableSet<String>>()
    val placements: Map<String, Set<String>> get() = placed

    val over get() = integrity <= 0 || wave >= map.waves.size
    val won get() = integrity > 0 && wave >= map.waves.size

    fun has(defense: String, node: String) = placed[node]?.contains(defense) == true

    /** Places [defense] on [node]. False when it is unknown, duplicated or too expensive. */
    fun place(defense: String, node: String): Boolean {
        val cost = Rules.cost(defense) ?: return false
        if (map.nodes.none { it.id == node } || has(defense, node) || cost > money) return false
        money -= cost
        placed.getOrPut(node) { mutableSetOf() } += defense
        return true
    }

    /** Removes a defense for half its cost back. */
    fun sell(defense: String, node: String): Boolean {
        if (!has(defense, node)) return false
        placed[node]!!.remove(defense)
        money += Rules.cost(defense)!! / 2
        return true
    }

    /** Starts the current wave. Throws when the game is already [over]. */
    fun startWave(): WaveSim {
        check(!over) { "the game is over" }
        return WaveSim(map, wave, placements.mapValues { it.value.toSet() }, integrity)
    }

    /** Records a finished wave: updates integrity, pays income and moves on. */
    fun finishWave(sim: WaveSim) {
        require(sim.wave == wave) { "simulation is for wave ${sim.wave + 1}, the game is on wave ${wave + 1}" }
        require(sim.finished || sim.lost) { "the wave is still running" }
        integrity = sim.integrity
        wave++
        if (integrity > 0 && wave < map.waves.size) money += map.income
    }

    fun restart() { wave = 0; money = map.startMoney; integrity = map.integrity; placed.clear() }

    companion object {
        /**
         * Replays a reference solution: one line per wave, "wave N: defense@node defense@node". Returns problems
         * (empty when the map is won).
         */
        fun replay(map: DefenseMap, solution: String): List<String> {
            val game = DefenseGame(map)
            val lines = solution.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            if (lines.size != map.waves.size) return listOf("solution has ${lines.size} wave lines, map has ${map.waves.size}")
            for ((i, line) in lines.withIndex()) {
                val label = Regex("^wave\\s+(\\d+)\\s*:(.*)$", RegexOption.IGNORE_CASE).matchEntire(line)
                    ?: return listOf("line ${i + 1}: expected 'wave N: defense@node ...', got '$line'")
                if (label.groupValues[1].toInt() != i + 1) return listOf("line ${i + 1}: labelled 'wave ${label.groupValues[1]}', expected 'wave ${i + 1}'")
                for (tok in label.groupValues[2].trim().split(Regex("\\s+")).filter { it.isNotEmpty() }) {
                    val parts = tok.split('@')
                    if (parts.size != 2 || parts.any { it.isEmpty() }) return listOf("wave ${i + 1}: malformed placement '$tok' (use defense@node)")
                    val (d, n) = parts
                    when {
                        Rules.cost(d) == null -> return listOf("wave ${i + 1}: unknown defense '$d'")
                        map.nodes.none { it.id == n } -> return listOf("wave ${i + 1}: unknown node '$n'")
                        game.has(d, n) -> return listOf("wave ${i + 1}: '$tok' is already placed")
                        Rules.cost(d)!! > game.money -> return listOf("wave ${i + 1}: cannot afford '$tok' (money ${game.money}, cost ${Rules.cost(d)})")
                    }
                    game.place(d, n)
                }
                val sim = game.startWave().runToEnd()
                game.finishWave(sim)
                if (game.integrity <= 0) return listOf("wave ${i + 1}: base destroyed")
            }
            return emptyList()
        }
    }
}
