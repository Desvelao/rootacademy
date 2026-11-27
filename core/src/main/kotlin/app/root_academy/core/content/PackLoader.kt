package app.root_academy.core.content

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

val LANGUAGES = listOf("en", "es")

class Catalog(
    val missions: List<Pack<Mission>>,
    val puzzles: List<Pack<Puzzle>>,
    val lessons: List<Pack<Lesson>>,
    val soc: Pack<SocPack>? = null,
    val ciphers: List<Pack<CipherLevel>> = emptyList(),
    val maps: List<Pack<DefenseMap>> = emptyList(),
    val games: List<Pack<GameDef>> = emptyList(),
    val campaigns: List<Pack<Campaign>> = emptyList(),
    /** Activity id to the id of the game it belongs to; ids not listed belong to the game named like their kind. */
    val gameOf: Map<String, String> = emptyMap(),
    /** Pack directories that were found but have no pack file (for example a misnamed mission.json); reported by the validator. */
    val loadProblems: List<String> = emptyList(),
) {
    fun cipher(id: String) = ciphers.firstOrNull { it.value.id == id }
    fun map(id: String) = maps.firstOrNull { it.value.id == id }
    fun mission(id: String) = missions.firstOrNull { it.value.id == id }
    fun puzzle(id: String) = puzzles.firstOrNull { it.value.id == id }
    fun lesson(id: String) = lessons.firstOrNull { it.value.id == id }
    fun game(id: String) = games.firstOrNull { it.value.id == id }
    fun campaign(id: String) = campaigns.firstOrNull { it.value.id == id }

    /** Every playable thing of every game, in play order within its game. */
    val activities: List<Activity> by lazy {
        fun g(id: String, kind: String) = gameOf[id] ?: if (kind == "mission" || kind == "puzzle") "terminal" else kind
        buildList {
            missions.forEach { p -> p.value.let { add(Activity(it.id, g(it.id, "mission"), "mission", it.order, it.points, it.skills, it.unlocks.codex, pack = p)) } }
            puzzles.forEachIndexed { i, p -> p.value.let { add(Activity(it.id, g(it.id, "puzzle"), "puzzle", 1000 + i, it.points, it.skills, it.unlocks.codex, pack = p)) } }
            ciphers.forEach { p -> p.value.let { add(Activity(it.id, g(it.id, "cipher"), "cipher", it.order, it.points, it.skills, it.unlocks.codex, pack = p)) } }
            maps.forEach { p -> p.value.let { add(Activity(it.id, g(it.id, "defense"), "defense", it.order, it.points, it.skills, it.unlocks.codex, pack = p)) } }
            soc?.value?.days.orEmpty().forEach {
                add(Activity(it.id, g(it.id, "soc"), "soc", it.order, it.points, it.skills, it.unlocks.codex, titleKey = "day.${it.id}.title", pack = soc, requires = listOfNotNull(it.requiresMission)))
            }
        }
    }

    fun title(activityId: String, lang: String): String =
        activity(activityId)?.let { a -> a.pack?.text(lang, a.titleKey) } ?: activityId

    fun activity(id: String) = activities.firstOrNull { it.id == id }
    fun activitiesOf(game: String) = activities.filter { it.game == game }.sortedBy { it.order }
}

/** Reads `games/<id>/game.json`, `campaigns/<id>/campaign.json`, and each game's packs, plus `strings.<lang>.json`. */
class PackLoader(private val source: ContentSource) {
    private val json = Json { ignoreUnknownKeys = false }
    private val stringMap = MapSerializer(String.serializer(), String.serializer())

    private fun join(dir: String, name: String) = if (dir.isEmpty()) name else "$dir/$name"

    private val problems = mutableListOf<String>()

    fun loadAll(): Catalog {
        problems.clear()
        val games = loadDir("games", "game.json") { json.decodeFromString(GameDef.serializer(), it) }.sortedBy { it.value.order }
        // Without game definitions the classic layout applies: one game per engine at the default place.
        val defs = games.map { it.value }.ifEmpty {
            listOf(
                GameDef(id = "terminal", engine = "terminal"),
                GameDef(id = "soc", engine = "soc", contentDir = "soc"),
                GameDef(id = "cipher", engine = "cipher", contentDir = "cipher"),
                GameDef(id = "defense", engine = "defense", contentDir = "defense"),
            )
        }
        val gameOf = mutableMapOf<String, String>()
        fun <T> tag(game: GameDef, packs: List<Pack<T>>, id: (T) -> String): List<Pack<T>> =
            packs.also { l -> l.forEach { gameOf[id(it.value)] = game.id } }

        val missions = mutableListOf<Pack<Mission>>()
        val puzzles = mutableListOf<Pack<Puzzle>>()
        val lessons = mutableListOf<Pack<Lesson>>()
        val ciphers = mutableListOf<Pack<CipherLevel>>()
        val maps = mutableListOf<Pack<DefenseMap>>()
        var soc: Pack<SocPack>? = null
        for (d in defs) when (Engine.fromId(d.engine)) {
            Engine.TERMINAL -> {
                missions += tag(d, loadDir(join(d.contentDir, "missions"), "mission.json") { json.decodeFromString(Mission.serializer(), it) }) { it.id }
                puzzles += tag(d, loadDir(join(d.contentDir, "puzzles"), "puzzle.json") { json.decodeFromString(Puzzle.serializer(), it) }) { it.id }
                lessons += loadDir(join(d.contentDir, "lessons"), "lesson.json") { json.decodeFromString(Lesson.serializer(), it) }
            }
            Engine.SOC -> source.read(join(d.contentDir, "soc.json"))?.let {
                val pack = json.decodeFromString(SocPack.serializer(), it)
                pack.days.forEach { day -> gameOf[day.id] = d.id }
                soc = if (soc == null) Pack(pack, langStrings(d.contentDir)) else soc
            }
            Engine.CIPHER -> ciphers += tag(d, loadDir(d.contentDir, "cipher.json") { json.decodeFromString(CipherLevel.serializer(), it) }) { it.id }
            Engine.DEFENSE -> maps += tag(d, loadDir(d.contentDir, "map.json") { json.decodeFromString(DefenseMap.serializer(), it) }) { it.id }
            null -> Unit // the validator reports unknown engines
        }
        return Catalog(
            missions = missions.sortedBy { it.value.order },
            puzzles = puzzles.sortedBy { it.value.id },
            lessons = lessons.sortedBy { it.value.id },
            soc = soc,
            ciphers = ciphers.sortedBy { it.value.order },
            maps = maps.sortedBy { it.value.order },
            games = games,
            campaigns = loadDir("campaigns", "campaign.json") { json.decodeFromString(Campaign.serializer(), it) }.sortedBy { it.value.order },
            gameOf = gameOf,
            loadProblems = problems.toList(),
        )
    }

    private fun langStrings(dir: String): Map<String, StringTable> = LANGUAGES.mapNotNull { lang ->
        source.read(join(dir, "strings.$lang.json"))?.let { lang to json.decodeFromString(stringMap, it) }
    }.toMap()

    private fun <T> loadDir(dir: String, file: String, parse: (String) -> T): List<Pack<T>> =
        source.list(dir).sorted().mapNotNull { name ->
            val text = source.read("$dir/$name/$file")
            if (text == null) {
                if (source.list("$dir/$name").isNotEmpty()) problems += "$dir/$name: no $file (misnamed or missing); the directory was skipped"
                return@mapNotNull null
            }
            Pack(parse(text), langStrings("$dir/$name"))
        }
}
