package app.root_academy.core.content

import app.root_academy.core.cipher.Ciphers
import app.root_academy.core.defense.DefenseGame
import app.root_academy.core.defense.Graph
import app.root_academy.core.defense.Rules
import app.root_academy.core.soc.SocEngine
import app.root_academy.core.terminal.COMMAND_NAMES
import app.root_academy.core.terminal.SolutionReplayer

/** Checks a loaded [Catalog] for mistakes that would otherwise only show up on a player's phone. */
object PackValidator {
    private val sha = Regex("^[0-9a-f]{64}$")
    private val octalMode = Regex("^[0-7]{3}$")

    private fun isNormalPath(p: String) =
        p.startsWith("/") && p.length > 1 && !p.endsWith("/") && p.split('/').drop(1).none { it.isEmpty() || it == "." || it == ".." }

    fun validate(catalog: Catalog, source: ContentSource? = null): List<String> {
        val errors = mutableListOf<String>()
        fun err(id: String, msg: String) { errors += "$id: $msg" }

        val ids = catalog.missions.map { it.value.id } + catalog.puzzles.map { it.value.id } + catalog.lessons.map { it.value.id } +
            catalog.ciphers.map { it.value.id } + catalog.maps.map { it.value.id } + catalog.soc?.value?.days.orEmpty().map { it.id }
        catalog.loadProblems.forEach { errors += it }
        ids.groupBy { it }.filter { it.value.size > 1 }.keys.forEach { err(it, "duplicate id") }
        val lessonIds = catalog.lessons.map { it.value.id }.toSet()

        fun commonStrings(id: String, strings: Map<String, StringTable>, required: List<String>) {
            for (lang in LANGUAGES) {
                val t = strings[lang] ?: run { err(id, "missing strings.$lang.json"); return@commonStrings }
                required.filter { it !in t }.forEach { err(id, "[$lang] missing string '$it'") }
                t.filterValues { it.isBlank() }.keys.forEach { err(id, "[$lang] empty string '$it'") }
            }
            val en = strings["en"] ?: return
            for (lang in LANGUAGES.drop(1)) strings[lang]?.let { other ->
                (en.keys - other.keys).forEach { err(id, "[$lang] missing '$it' (present in en)") }
                (other.keys - en.keys).forEach { err(id, "[$lang] extra '$it' (not in en)") }
            }
        }

        fun skills(id: String, map: Map<String, Int>) =
            map.keys.filter { Skill.fromId(it) == null }.forEach { err(id, "unknown skill '$it'") }

        fun unlocks(id: String, u: Unlocks) =
            u.codex.filter { it !in lessonIds }.forEach { err(id, "unlocks unknown lesson '$it'") }

        val hints = (1..3).map(Keys::hint)

        for (p in catalog.missions) {
            val m = p.value
            if (m.schema != SCHEMA_VERSION) err(m.id, "unsupported schema ${m.schema}")
            if (!sha.matches(m.flagSha256)) err(m.id, "flag_sha256 must be 64 lowercase hex chars")
            skills(m.id, m.skills); unlocks(m.id, m.unlocks)
            commonStrings(m.id, p.strings, listOf(Keys.TITLE, Keys.BRIEF, Keys.DEBRIEF, Keys.WALKTHROUGH) + hints + m.objectives.map { Keys.objective(it.id) })
            m.hosts.groupBy { it.name }.filter { it.value.size > 1 }.keys.forEach { err(m.id, "duplicate host '$it'") }
            if (m.hosts.none { it.name == m.startHost }) err(m.id, "startHost '${m.startHost}' not found")
            if (m.objectives.none { it.on == "flag" }) err(m.id, "needs a 'flag' objective")
            if (m.traceLimit <= 0) err(m.id, "traceLimit must be positive")
            if (m.startUser.isBlank()) err(m.id, "startUser must not be empty")
            m.hosts.forEach { h -> h.logins.filter { it.user.isBlank() }.forEach { _ -> err(m.id, "host '${h.name}': login with empty user") } }
            for (h in m.hosts) {
                h.files.keys.filter { !isNormalPath(it) }.forEach { err(m.id, "host '${h.name}': file path '$it' must be absolute and normalised (no '//', '.', '..' or trailing '/')") }
                h.dirs.keys.filter { !isNormalPath(it) }.forEach { err(m.id, "host '${h.name}': directory path '$it' must be absolute and normalised") }
                h.dirs.filter { !octalMode.matches(it.value.mode) }.keys.forEach { err(m.id, "host '${h.name}': directory '$it' has an invalid mode") }
                h.files.filter { !octalMode.matches(it.value.mode) }.keys.forEach { err(m.id, "host '${h.name}': '$it' has mode '${h.files.getValue(it).mode}', expected three octal digits") }
            }
            m.commands.filter { it !in COMMAND_NAMES }.forEach { err(m.id, "unknown chip command '$it'") }
            m.hosts.groupBy { it.ip }.filter { it.value.size > 1 }.keys.forEach { err(m.id, "duplicate host ip '$it'") }
            for (h in m.hosts) {
                if (h.logins.isNotEmpty() && "ssh" !in h.services) err(m.id, "host '${h.name}': has logins but no 'ssh' service")
                h.files.keys.filter { it in h.dirs }.forEach { err(m.id, "host '${h.name}': '$it' is both a file and a directory") }
                h.groups.keys.filter { u -> u != m.startUser && h.logins.none { it.user == u } }
                    .forEach { err(m.id, "host '${h.name}': groups name unknown user '$it'") }
            }
            if (m.objectives.map { it.id }.toSet().size != m.objectives.size) err(m.id, "duplicate objective ids")
            for (o in m.objectives) when (o.on) {
                "connect" -> if (m.hosts.none { it.name == o.target }) err(m.id, "objective ${o.id}: unknown host '${o.target}'")
                "read" -> {
                    val host = m.hosts.firstOrNull { it.name == o.target.substringBefore(':') }
                    if (host == null || o.target.substringAfter(':') !in host.files) err(m.id, "objective ${o.id}: unknown file '${o.target}'")
                }
                "run" -> if (o.target !in COMMAND_NAMES) err(m.id, "objective ${o.id}: unknown command '${o.target}'")
                "flag" -> Unit
                else -> err(m.id, "objective ${o.id}: unknown trigger '${o.on}'")
            }
            if (source != null) {
                val sol = source.read("missions/${m.id}/solution.txt")
                if (sol == null) err(m.id, "missing solution.txt") else {
                    val r = SolutionReplayer.replay(m, sol)
                    r.problems.forEach { err(m.id, "solution replay: $it") }
                }
            }
        }
        for (p in catalog.puzzles) {
            val q = p.value
            if (q.schema != SCHEMA_VERSION) err(q.id, "unsupported schema ${q.schema}")
            if (!sha.matches(q.flagSha256)) err(q.id, "flag_sha256 must be 64 lowercase hex chars")
            if (q.category !in setOf("crypto", "web", "forensics", "reverse")) err(q.id, "unknown category '${q.category}'")
            q.tools.filter { it !in PUZZLE_TOOLS }.forEach { err(q.id, "unknown tool '$it'") }
            skills(q.id, q.skills); unlocks(q.id, q.unlocks)
            commonStrings(q.id, p.strings, listOf(Keys.TITLE, Keys.PROMPT, Keys.WALKTHROUGH) + hints)
        }
        for (p in catalog.lessons) {
            val l = p.value
            if (Skill.fromId(l.skill) == null) err(l.id, "unknown skill '${l.skill}'")
            commonStrings(l.id, p.strings, listOf(Keys.TITLE, Keys.BODY))
        }
        catalog.soc?.let { validateSoc(it, catalog, ::err, ::commonStrings) }
        for (p in catalog.ciphers) {
            val c = p.value
            if (!sha.matches(c.flagSha256)) err(c.id, "flag_sha256 must be 64 lowercase hex chars")
            if (c.chapter !in CHAPTERS) err(c.id, "unknown chapter '${c.chapter}'")
            if (c.cipher !in setOf("caesar", "vigenere", "enigma", "rsa")) err(c.id, "unknown cipher '${c.cipher}'")
            if (c.tool != null && c.tool !in CIPHER_TOOLS) err(c.id, "unknown tool '${c.tool}'")
            skills(c.id, c.skills); unlocks(c.id, c.unlocks)
            commonStrings(c.id, p.strings, listOf(Keys.TITLE, Keys.PROMPT, Keys.WALKTHROUGH) + hints)
            if (source != null) {
                val key = source.read("cipher/${c.id}/solution.txt")?.trim()
                val plain = key?.let { Ciphers.decrypt(c.cipher, it, c.ciphertext) }
                when {
                    key == null -> err(c.id, "missing solution.txt (the key)")
                    plain == null -> err(c.id, "solution key does not decrypt")
                    Ciphers.answerHash(plain) != c.flagSha256 -> err(c.id, "solution key gives '${Ciphers.normalize(plain)}', which does not match flag_sha256")
                }
            }
        }
        for (p in catalog.maps) {
            val m = p.value
            skills(m.id, m.skills); unlocks(m.id, m.unlocks)
            commonStrings(m.id, p.strings, listOf(Keys.TITLE, Keys.BRIEF, Keys.DEBRIEF, Keys.WALKTHROUGH) + hints + m.nodes.map { "node.${it.id}" })
            val ids = m.nodes.map { it.id }.toSet()
            m.links.filter { it.size != 2 || it.any { n -> n !in ids } }.forEach { err(m.id, "bad link $it") }
            if (m.spawn !in ids || m.target !in ids) err(m.id, "spawn/target not a node")
            val graph = Graph(m)
            m.waves.forEachIndexed { w, spawns ->
                spawns.forEach { s ->
                    if (Rules.attack(s.attack) == null) err(m.id, "unknown attack '${s.attack}'")
                    val badNodes = listOfNotNull(s.via, s.from).filter { it !in ids }
                    badNodes.forEach { n -> err(m.id, "unknown node '$n' in a wave") }
                    if (badNodes.isEmpty() && m.spawn in ids && m.target in ids && graph.route(s) == null) {
                        err(m.id, "wave ${w + 1}: no route for ${s.attack} (from ${s.from ?: m.spawn}${s.via?.let { " via $it" } ?: ""} to ${m.target})")
                    }
                }
            }
            if (errors.none { it.startsWith("${m.id}:") } && source != null) {
                val sol = source.read("defense/${m.id}/solution.txt")
                if (sol == null) err(m.id, "missing solution.txt") else DefenseGame.replay(m, sol).forEach { err(m.id, "solution replay: $it") }
            }
        }
        val unlocked = catalog.missions.flatMap { it.value.unlocks.codex } + catalog.puzzles.flatMap { it.value.unlocks.codex } +
            catalog.ciphers.flatMap { it.value.unlocks.codex } + catalog.maps.flatMap { it.value.unlocks.codex } +
            catalog.soc?.value?.days.orEmpty().flatMap { it.unlocks.codex }
        (lessonIds - unlocked.toSet()).forEach { err(it, "lesson is never unlocked by any activity") }
        validateGames(catalog, ::err, ::commonStrings)
        validateCampaigns(catalog, ::err, ::commonStrings)
        return errors
    }

    private fun validateGames(
        catalog: Catalog,
        err: (String, String) -> Unit,
        strings: (String, Map<String, StringTable>, List<String>) -> Unit,
    ) {
        catalog.games.map { it.value.id }.groupBy { it }.filter { it.value.size > 1 }.keys.forEach { err(it, "duplicate game id") }
        for (p in catalog.games) {
            val g = p.value
            if (g.schema != SCHEMA_VERSION) err(g.id, "unsupported schema ${g.schema}")
            val engine = Engine.fromId(g.engine)
            if (engine == null) err(g.id, "unknown engine '${g.engine}' (expected ${Engine.entries.joinToString { it.id }})")
            if (engine != null && engine != Engine.TERMINAL && g.contentDir.isBlank()) err(g.id, "contentDir is required for engine '${g.engine}'")
            if (g.contentDir.startsWith("/") || g.contentDir.split('/').any { it == ".." }) err(g.id, "contentDir must be relative and inside the content root")
            strings(g.id, p.strings, listOf(Keys.TITLE, "subtitle", "progress"))
        }
        for (e in Engine.entries) {
            val dirs = catalog.games.map { it.value }.filter { it.engine == e.id }.groupBy { it.contentDir }
            dirs.filter { it.value.size > 1 }.forEach { (dir, gs) -> err(gs.first().id, "games ${gs.joinToString { it.id }} share contentDir '$dir'") }
        }
        if (Engine.SOC.let { e -> catalog.games.count { it.value.engine == e.id } } > 1) err("games", "only one soc game is supported")
        if (catalog.games.isNotEmpty()) for (a in catalog.activities) {
            if (catalog.game(a.game) == null) err(a.id, "belongs to unknown game '${a.game}' (no games/${a.game}/game.json)")
        }
    }

    private fun validateCampaigns(
        catalog: Catalog,
        err: (String, String) -> Unit,
        strings: (String, Map<String, StringTable>, List<String>) -> Unit,
    ) {
        catalog.campaigns.map { it.value.id }.groupBy { it }.filter { it.value.size > 1 }.keys.forEach { err(it, "duplicate campaign id") }
        val lessonIds = catalog.lessons.map { it.value.id }.toSet()
        for (p in catalog.campaigns) {
            val c = p.value
            if (c.schema != SCHEMA_VERSION) err(c.id, "unsupported schema ${c.schema}")
            CampaignProgress.structureProblems(c).forEach { err(c.id, it) }
            c.steps.filter { catalog.activity(it.activity) == null }.forEach { err(c.id, "step '${it.id}': unknown activity '${it.activity}'") }
            c.rewards.codex.filter { it !in lessonIds }.forEach { err(c.id, "rewards unknown lesson '$it'") }
            strings(c.id, p.strings, listOf(Keys.TITLE, "intro", "outro") + c.steps.map { it.story })
        }
        // A step's activity may be gated by several campaigns; the union of gates must still be solvable.
        val gates = mutableMapOf<String, MutableSet<String>>()
        for (p in catalog.campaigns) {
            val byId = p.value.steps.associateBy { it.id }
            for (s in p.value.steps) gates.getOrPut(s.activity) { mutableSetOf() } += s.requires.mapNotNull { byId[it]?.activity }
        }
        catalog.activities.forEach { a -> a.requires.forEach { r -> gates.getOrPut(a.id) { mutableSetOf() } += r } }
        val done = mutableSetOf<String>()
        var progressed = true
        while (progressed) {
            progressed = false
            for ((act, req) in gates) if (act !in done && req.all { it in done || it !in gates }) { done += act; progressed = true }
        }
        (gates.keys - done).sorted().forEach { err(it, "can never be unlocked: its prerequisites form a cycle across campaigns") }
    }

    val PUZZLE_TOOLS = setOf("base64", "rot13", "hex", "url", "md5", "sha256", "caesar")
    val CHAPTERS = listOf("rome", "renaissance", "wwii", "today")
    val CIPHER_TOOLS = setOf("freq", "caesar", "vigenere", "keylen", "enigma", "rsa")

    private fun validateSoc(
        p: Pack<SocPack>, catalog: Catalog,
        err: (String, String) -> Unit,
        strings: (String, Map<String, StringTable>, List<String>) -> Unit,
    ) {
        val soc = p.value
        val required = mutableListOf<String>()
        for (t in soc.templates) {
            if (t.kind !in setOf("email", "login", "log")) err(t.id, "unknown kind '${t.kind}'")
            if (t.verdict !in SocEngine.VERDICTS) err(t.id, "unknown verdict '${t.verdict}'")
            required += "t.${t.id}.body"
            if (t.kind == "email") required += "t.${t.id}.subject"
            required += t.clues.map { "clue.$it" }
        }
        for (v in SocEngine.VERDICTS) if (soc.templates.none { it.verdict == v }) err("soc", "no template with verdict '$v'")
        for (d in soc.days) {
            required += listOf("day.${d.id}.title", "day.${d.id}.brief", "day.${d.id}.debrief")
            d.skills.keys.filter { Skill.fromId(it) == null }.forEach { err(d.id, "unknown skill '$it'") }
            d.unlocks.codex.filter { catalog.lesson(it) == null }.forEach { err(d.id, "unlocks unknown lesson '$it'") }
            d.requiresMission?.takeIf { catalog.mission(it) == null }?.let { err(d.id, "requires unknown mission '$it'") }
            for (v in SocEngine.VERDICTS) if (soc.templates.none { it.verdict == v && it.level <= d.difficulty }) {
                err(d.id, "no '$v' template at difficulty ${d.difficulty} or below (the mix would silently change)")
            }
            if (SocEngine.generate(d.seed, d.difficulty, d.count, soc.templates).size != d.count) err(d.id, "cannot build a queue")
        }
        strings("soc", p.strings, required.distinct())
    }
}
