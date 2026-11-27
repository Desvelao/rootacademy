package app.root_academy.hub

import app.root_academy.core.content.Activity
import app.root_academy.core.content.Catalog
import app.root_academy.core.content.GameDef
import app.root_academy.core.content.Keys
import app.root_academy.core.content.Pack
import app.root_academy.data.ProgressState

/** A sub-game as declared by `games/<id>/game.json`: the hub shows whatever the catalog lists, nothing is hardcoded. */
class SubGame(val pack: Pack<GameDef>) {
    val def get() = pack.value
    val id get() = def.id
    val route get() = Routes.game(def.id)
    fun title(lang: String) = pack.text(lang, Keys.TITLE)
    fun subtitle(lang: String) = pack.text(lang, "subtitle")

    /** Progress line for the Home tile: the game's `progress` string filled with done and total activities. */
    fun progress(lang: String, catalog: Catalog, state: ProgressState): String {
        val all = catalog.activitiesOf(id)
        return pack.text(lang, "progress").format(all.count { it.id in state.completed }, all.size)
    }
}

object Routes {
    const val HOME = "home"
    const val SKILLS = "skills"
    const val CODEX = "codex"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val GLOSSARY = "glossary"
    const val GAME = "game/{id}"
    const val CAMPAIGNS = "campaigns"
    const val CAMPAIGN = "campaign/{id}"
    const val MISSION = "mission/{id}"
    const val DEBRIEF = "debrief/{id}"
    const val PUZZLE = "puzzle/{id}"
    const val LESSON = "lesson/{id}"
    const val SOC_SHIFT = "soc/{id}"
    const val CIPHER_LEVEL = "cipher/{id}"
    const val DEFENSE_MAP = "defense/{id}"
    fun game(id: String) = "game/$id"
    fun campaign(id: String) = "campaign/$id"

    /** Where an activity is played, by its kind. */
    fun activity(a: Activity) = when (a.kind) {
        "mission" -> mission(a.id)
        "puzzle" -> puzzle(a.id)
        "cipher" -> cipherLevel(a.id)
        "defense" -> defenseMap(a.id)
        else -> socShift(a.id)
    }
    fun socShift(id: String) = "soc/$id"
    fun cipherLevel(id: String) = "cipher/$id"
    fun defenseMap(id: String) = "defense/$id"
    fun mission(id: String) = "mission/$id"
    fun debrief(id: String) = "debrief/$id"
    fun puzzle(id: String) = "puzzle/$id"
    fun lesson(id: String) = "lesson/$id"
}
