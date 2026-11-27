package app.root_academy.core.content

import kotlinx.serialization.Serializable

/** The built-in play mechanics a data-defined game can pick. New mechanics need Kotlin; new games on them do not. */
enum class Engine(val id: String) {
    /** Missions, puzzles and lessons under `<contentDir>/missions|puzzles|lessons` (an empty contentDir is the content root). */
    TERMINAL("terminal"),

    /** Phishing/log triage queue: `<contentDir>/soc.json`. */
    SOC("soc"),

    /** Classical ciphers: `<contentDir>/<id>/cipher.json`. */
    CIPHER("cipher"),

    /** Tower defense maps: `<contentDir>/<id>/map.json`. */
    DEFENSE("defense");

    companion object {
        fun fromId(id: String): Engine? = entries.firstOrNull { it.id == id }
    }
}

/**
 * A sub-game declared as data: `games/<id>/game.json` plus `strings.<lang>.json` with `title`, `subtitle` and `progress`
 * (a format taking done and total, e.g. "%1$d/%2$d levels").
 */
@Serializable
data class GameDef(
    val schema: Int = SCHEMA_VERSION,
    val id: String,
    val type: String = "game",
    val engine: String,
    val order: Int = 0,
    /** Where this game's packs live, relative to the content root. */
    val contentDir: String = "",
    /** Icon name from the app's icon set; unknown names fall back to a generic one. */
    val icon: String = "extension",
    val release: String = "1.0",
)

/**
 * One playable thing, whatever its game: the common view the hub, skills, codex and progress use.
 * [kind] is the string stored with completions (mission, puzzle, cipher, soc, defense).
 */
data class Activity(
    val id: String,
    val game: String,
    val kind: String,
    val order: Int,
    val points: Int,
    val skills: Map<String, Int>,
    val unlocks: List<String>,
    val titleKey: String = Keys.TITLE,
    /** Pack holding the title text; null for activities that share one pack (SOC days). */
    val pack: Pack<*>? = null,
    /** Activity ids that must be completed first (from a SOC day's requiresMission). */
    val requires: List<String> = emptyList(),
)

// ---------- Campaigns ----------

/**
 * A story that links activities of any games. Steps are ordered; a step's activity stays locked until every step it
 * [requires] is done. Strings: `title`, `intro`, `outro` and each step's [story] key.
 */
@Serializable
data class Campaign(
    val schema: Int = SCHEMA_VERSION,
    val id: String,
    val type: String = "campaign",
    val order: Int = 0,
    val steps: List<CampaignStep>,
    val rewards: Unlocks = Unlocks(),
)

@Serializable
data class CampaignStep(
    val id: String,
    /** Id of a mission, puzzle, cipher level, defense map or SOC day. */
    val activity: String,
    /** Ids of earlier steps of the same campaign. */
    val requires: List<String> = emptyList(),
    /** String key of the narrative shown with the step. */
    val story: String,
)

enum class StepState { LOCKED, AVAILABLE, DONE }

data class CampaignState(val steps: Map<String, StepState>) {
    val done get() = steps.values.count { it == StepState.DONE }
    val total get() = steps.size
    val complete get() = total > 0 && done == total
}

object CampaignProgress {
    fun state(c: Campaign, completed: Set<String>): CampaignState {
        val done = c.steps.filter { it.activity in completed }.map { it.id }.toSet()
        return CampaignState(
            c.steps.associate { s ->
                s.id to when {
                    s.id in done -> StepState.DONE
                    s.requires.all { it in done } -> StepState.AVAILABLE
                    else -> StepState.LOCKED
                }
            },
        )
    }

    /** Steps that block [activityId]: the unmet prerequisites over all campaigns (empty = free to play). */
    fun blockers(campaigns: List<Pack<Campaign>>, activityId: String, completed: Set<String>): List<Pair<Campaign, CampaignStep>> =
        campaigns.flatMap { p ->
            val c = p.value
            val byId = c.steps.associateBy { it.id }
            c.steps.filter { it.activity == activityId }.flatMap { s ->
                s.requires.mapNotNull { byId[it] }.filter { it.activity !in completed }.map { c to it }
            }
        }

    /** Validation: step problems of one campaign (unknown activities are checked by the validator). */
    fun structureProblems(c: Campaign): List<String> {
        val out = mutableListOf<String>()
        if (c.steps.isEmpty()) out += "no steps"
        c.steps.groupBy { it.id }.filter { it.value.size > 1 }.keys.forEach { out += "duplicate step '$it'" }
        c.steps.groupBy { it.activity }.filter { it.value.size > 1 }.keys.forEach { out += "activity '$it' used by several steps" }
        val seen = mutableSetOf<String>()
        for (s in c.steps) {
            s.requires.filter { it !in seen }.forEach { out += "step '${s.id}' requires '$it', which is not an earlier step" }
            seen += s.id
        }
        return out
    }
}
