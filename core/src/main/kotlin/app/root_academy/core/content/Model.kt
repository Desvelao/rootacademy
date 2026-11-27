package app.root_academy.core.content

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val SCHEMA_VERSION = 1

/** The six skills of the skill tree. XP in packs refers to these ids. */
enum class Skill(val id: String) {
    LINUX("linux"), CRYPTO("crypto"), WEB("web"), NETWORK("network"), DEFENSE("defense"), SOCIAL("social");

    companion object {
        fun fromId(id: String): Skill? = entries.firstOrNull { it.id == id }
    }
}

@Serializable
data class Unlocks(val codex: List<String> = emptyList())

/** A file on a virtual host. [owner] and [mode] (octal string, e.g. "640") drive read permission checks. */
@Serializable
data class VFile(val text: String, val mode: String = "644", val owner: String = "root", val group: String = "")

/** An explicit directory entry: lets a host have empty directories and directory permissions (default 755, root). */
@Serializable
data class VDir(val mode: String = "755", val owner: String = "root", val group: String = "")

@Serializable
data class Login(val user: String, val password: String)

@Serializable
data class Host(
    val ip: String,
    val name: String,
    val services: List<String> = emptyList(),
    /** Banner text per service, shown by `connect <host> <service>`. */
    val banners: Map<String, String> = emptyMap(),
    /** Accounts accepted by `ssh`. Empty = host does not accept logins. */
    val logins: List<Login> = emptyList(),
    val files: Map<String, VFile> = emptyMap(),
    /** Optional directory entries (path to owner and mode); directories not listed here are open to everyone. */
    val dirs: Map<String, VDir> = emptyMap(),
    /** Extra group memberships per user. Every user is always in a group named like themselves. */
    val groups: Map<String, List<String>> = emptyMap(),
    /** Hidden hosts never appear in `scan`; the player learns their name from files or banners. `ssh` still works. */
    val hidden: Boolean = false,
)

/**
 * Objective triggers. [on] is one of `run` (a command name ran), `connect` (logged in to host [target]),
 * `read` (a file was read, target `host:/path`) or `flag` (correct flag submitted).
 */
@Serializable
data class Objective(val id: String, val on: String, val target: String = "")

@Serializable
data class Mission(
    val schema: Int = SCHEMA_VERSION,
    val id: String,
    val type: String = "terminal-mission",
    val order: Int = 0,
    val difficulty: Int = 1,
    val points: Int = 100,
    val skills: Map<String, Int> = emptyMap(),
    val unlocks: Unlocks = Unlocks(),
    /** Host the player starts on (by name) and the user they are logged in as. */
    val startHost: String,
    val startUser: String = "guest",
    val hosts: List<Host>,
    val objectives: List<Objective>,
    @SerialName("flag_sha256") val flagSha256: String,
    val traceLimit: Int = 100,
    /** Command names offered as chips; empty = a default set. */
    val commands: List<String> = emptyList(),
)

@Serializable
data class Puzzle(
    val schema: Int = SCHEMA_VERSION,
    val id: String,
    val type: String = "puzzle",
    /** crypto, web, forensics or reverse. */
    val category: String,
    val difficulty: Int = 1,
    val points: Int = 50,
    val skills: Map<String, Int> = emptyMap(),
    val unlocks: Unlocks = Unlocks(),
    /** Attached files: display name -> text content. */
    val files: Map<String, String> = emptyMap(),
    /** Helper tools the puzzle screen offers: base64, rot13, hex, sha256, caesar, url. */
    val tools: List<String> = emptyList(),
    @SerialName("flag_sha256") val flagSha256: String,
)

@Serializable
data class Lesson(
    val schema: Int = SCHEMA_VERSION,
    val id: String,
    val type: String = "lesson",
    val skill: String,
)

/** Translatable text of one pack in one language: key -> text. */
typealias StringTable = Map<String, String>

/** A pack with its logic plus text per language code. */
data class Pack<T>(val value: T, val strings: Map<String, StringTable>) {
    fun text(lang: String, key: String): String =
        strings[lang]?.get(key) ?: strings["en"]?.get(key) ?: key
}

/** Key conventions shared by packs and UI. [n] is 1-based. */
object Keys {
    const val TITLE = "title"
    const val BRIEF = "brief"
    const val DEBRIEF = "debrief"
    const val WALKTHROUGH = "walkthrough"
    const val PROMPT = "prompt"
    const val BODY = "body"
    fun hint(n: Int) = "hint.$n"
    fun objective(id: String) = "objective.$id"
}

// ---------- SOC analyst ----------

/**
 * One kind of item in a SOC queue. [kind] is email, login or log; [verdict] is legit, phishing or escalate.
 * [sender] picks the sender domain (internal, lookalike, external, none) and [link] the link style
 * (none, good, lookalike, mismatch). [clues] are keys of `clue.<key>` strings shown after the verdict.
 */
@Serializable
data class SocTemplate(
    val id: String,
    val kind: String,
    val verdict: String,
    val level: Int = 1,
    val sender: String = "none",
    val link: String = "none",
    val clues: List<String> = emptyList(),
)

/** A story shift: [seed] fixes the queue, so everybody gets the same one. */
@Serializable
data class SocDay(
    val id: String,
    val order: Int,
    val difficulty: Int = 1,
    val count: Int = 8,
    val seed: Long,
    val points: Int = 100,
    val skills: Map<String, Int> = emptyMap(),
    val unlocks: Unlocks = Unlocks(),
    /** Terminal mission that must be done first (the unlock link between sub-games). */
    val requiresMission: String? = null,
)

@Serializable
data class SocPack(val schema: Int = SCHEMA_VERSION, val templates: List<SocTemplate>, val days: List<SocDay>)

// ---------- Cipher Quest ----------

/** One cipher puzzle. [chapter] is rome, renaissance, wwii or today; [cipher] is caesar, vigenere, enigma or rsa. */
@Serializable
data class CipherLevel(
    val schema: Int = SCHEMA_VERSION,
    val id: String,
    val type: String = "cipher-level",
    val order: Int,
    val chapter: String,
    val cipher: String,
    val ciphertext: String,
    /** Tool preselected on the level screen: freq, caesar, vigenere, keylen, enigma or rsa. Null = the tool of [cipher]. */
    val tool: String? = null,
    val points: Int = 80,
    val skills: Map<String, Int> = emptyMap(),
    val unlocks: Unlocks = Unlocks(),
    /** Hex SHA-256 of the plaintext reduced to upper-case letters only. */
    @SerialName("flag_sha256") val flagSha256: String,
)

// ---------- Tower defense ----------

@Serializable
data class DefNode(val id: String, val x: Float, val y: Float)

/** [count] attacks of type [attack] enter at tick [at], [gap] ticks apart, from [from] (default: the spawn), through [via]. */
@Serializable
data class DefSpawn(val at: Int, val attack: String, val count: Int = 1, val gap: Int = 10, val via: String? = null, val from: String? = null)

@Serializable
data class DefenseMap(
    val schema: Int = SCHEMA_VERSION,
    val id: String,
    val type: String = "defense-map",
    val order: Int,
    val points: Int = 150,
    val skills: Map<String, Int> = emptyMap(),
    val unlocks: Unlocks = Unlocks(),
    val integrity: Int,
    val startMoney: Int,
    val income: Int,
    val nodes: List<DefNode>,
    val links: List<List<String>>,
    val spawn: String,
    val target: String,
    val waves: List<List<DefSpawn>>,
)
