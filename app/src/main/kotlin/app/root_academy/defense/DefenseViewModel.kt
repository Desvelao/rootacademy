package app.root_academy.defense

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.root_academy.core.content.DefenseMap
import app.root_academy.core.content.Pack
import app.root_academy.core.defense.DefenseGame
import app.root_academy.core.defense.WaveSim
import app.root_academy.data.ContentRepository
import app.root_academy.data.ProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class Phase { PLANNING, RUNNING, WON, LOST }

/** An attack as drawn: its type and a position on the map in 0..1 coordinates. */
data class AttackView(val type: String, val x: Float, val y: Float)

data class DefenseUi(
    val phase: Phase = Phase.PLANNING,
    val wave: Int = 0,
    val money: Int = 0,
    val integrity: Int = 0,
    val placements: Map<String, Set<String>> = emptyMap(),
    val attacks: List<AttackView> = emptyList(),
    /** Result of the last finished wave: (stopped, leaked), or null. */
    val lastWave: Pair<Int, Int>? = null,
    val reward: Int = 0,
    val hints: Int = 0,
)

@HiltViewModel
class DefenseViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val content: ContentRepository,
    private val progress: ProgressRepository,
) : ViewModel() {
    val id: String = checkNotNull(savedState["id"])
    val pack: Pack<DefenseMap> = checkNotNull(content.catalog.map(id))
    val map get() = pack.value
    val lang get() = content.language()

    private val game = DefenseGame(map)

    /**
     * Player actions so far ("b:defense@node", "s:defense@node", "w" for a finished wave). The simulation is
     * deterministic, so replaying them rebuilds the game after the process was killed; a wave that was still
     * running restarts from planning.
     */
    private val log = ArrayList<String>()

    private fun record(action: String) { log += action; savedState[KEY_LOG] = ArrayList(log) }

    private fun restore() {
        for (a in savedState.get<ArrayList<String>>(KEY_LOG).orEmpty()) {
            when {
                a == "w" -> game.finishWave(game.startWave().runToEnd())
                a.startsWith("b:") -> a.drop(2).split('@').let { game.place(it[0], it[1]) }
                a.startsWith("s:") -> a.drop(2).split('@').let { game.sell(it[0], it[1]) }
                else -> continue
            }
            log += a
        }
    }

    private fun phaseNow() = when {
        game.won -> Phase.WON
        game.integrity <= 0 -> Phase.LOST
        else -> Phase.PLANNING
    }

    private val _ui = MutableStateFlow(snapshot(Phase.PLANNING))
    val ui: StateFlow<DefenseUi> = _ui.asStateFlow()

    init {
        restore()
        _ui.value = snapshot(phaseNow())
        viewModelScope.launch { progress.state.collect { s -> _ui.update { it.copy(hints = s.hintCounts[id] ?: 0) } } }
    }

    private fun snapshot(phase: Phase, sim: WaveSim? = null, last: Pair<Int, Int>? = null, hints: Int = 0) = DefenseUi(
        phase = phase, wave = game.wave, money = game.money, integrity = sim?.integrity ?: game.integrity,
        placements = game.placements.mapValues { it.value.toSet() }, attacks = sim?.let(::views).orEmpty(),
        lastWave = last, hints = hints,
    )

    private fun views(sim: WaveSim): List<AttackView> = sim.units.map { u ->
        val a = map.nodes.first { it.id == u.path[u.index] }
        val next = u.path.getOrNull(u.index + 1)?.let { n -> map.nodes.first { it.id == n } }
        val hop = app.root_academy.core.defense.Rules.attack(u.type)!!.hopTicks
        val t = if (next == null) 0f else u.dwell / hop.toFloat()
        AttackView(u.type, a.x + ((next?.x ?: a.x) - a.x) * t, a.y + ((next?.y ?: a.y) - a.y) * t)
    }

    fun build(defense: String, node: String) {
        if (_ui.value.phase != Phase.PLANNING) return
        if (game.place(defense, node)) record("b:$defense@$node")
        _ui.update { snapshot(Phase.PLANNING, null, it.lastWave, it.hints) }
    }

    fun sell(defense: String, node: String) {
        if (_ui.value.phase != Phase.PLANNING) return
        if (game.sell(defense, node)) record("s:$defense@$node")
        _ui.update { snapshot(Phase.PLANNING, null, it.lastWave, it.hints) }
    }

    /** Runs the wave. [instant] skips the animation (reduce motion); [speed] is ticks per frame otherwise. */
    fun start(speed: Int, instant: Boolean) {
        if (_ui.value.phase != Phase.PLANNING || game.over) return
        val sim = game.startWave()
        _ui.update { it.copy(phase = Phase.RUNNING, lastWave = null) }
        viewModelScope.launch {
            if (instant) sim.runToEnd() else while (!sim.finished && !sim.lost) {
                repeat(speed) { sim.step() }
                _ui.update { it.copy(integrity = sim.integrity, attacks = views(sim)) }
                delay(100)
            }
            finish(sim)
        }
    }

    private suspend fun finish(sim: WaveSim) {
        val leaked = map.waves[game.wave].sumOf { it.count } - sim.stopped
        game.finishWave(sim)
        record("w")
        val hints = _ui.value.hints
        when {
            game.won -> {
                val first = progress.complete(id, "defense", map.points, map.skills, map.unlocks.codex)
                _ui.value = snapshot(Phase.WON, null, sim.stopped to leaked, hints).copy(reward = if (first) map.points else 0)
            }
            game.integrity <= 0 -> _ui.value = snapshot(Phase.LOST, null, sim.stopped to leaked, hints)
            else -> _ui.value = snapshot(Phase.PLANNING, null, sim.stopped to leaked, hints)
        }
    }

    fun restart() {
        game.restart()
        log.clear(); savedState[KEY_LOG] = ArrayList(log)
        _ui.update { snapshot(Phase.PLANNING, null, null, it.hints) }
    }

    fun takeHint() = viewModelScope.launch { progress.takeHint(id, _ui.value.hints) }

    private companion object { const val KEY_LOG = "log" }
}
