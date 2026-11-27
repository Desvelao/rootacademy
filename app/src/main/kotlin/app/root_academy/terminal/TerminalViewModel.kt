package app.root_academy.terminal

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.root_academy.core.content.Pack
import app.root_academy.core.content.Mission
import app.root_academy.core.terminal.Completer
import app.root_academy.core.terminal.MissionRunner
import app.root_academy.data.ContentRepository
import app.root_academy.data.ProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TerminalUi(
    val lines: List<String> = emptyList(),
    val prompt: String = "",
    val passwordMode: Boolean = false,
    val done: Set<String> = emptySet(),
    val trace: Int = 0,
    val completed: Boolean = false,
    val failed: Boolean = false,
    /** Points shown in the completion dialog; null until the mission is first completed. */
    val reward: Int? = null,
)

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val content: ContentRepository,
    private val progress: ProgressRepository,
) : ViewModel() {
    val id: String = checkNotNull(savedState["id"])
    val pack: Pack<Mission> = checkNotNull(content.catalog.mission(id))
    val lang get() = content.language()

    private var runner = MissionRunner(pack.value)
    private var historyIndex = -1
    private var draft = ""
    private var rewarded = false
    private val _hints = MutableStateFlow(0)
    /** Hints taken so far (0..4, the 4th is the walkthrough). */
    val hints: StateFlow<Int> = _hints.asStateFlow()
    private val _ui = MutableStateFlow(TerminalUi())
    val ui: StateFlow<TerminalUi> = _ui.asStateFlow()

    /** Every submitted line, kept in the saved state so a killed process can replay the session. */
    private val submitted = ArrayList<String>(savedState.get<ArrayList<String>>(KEY_LINES).orEmpty())

    init {
        viewModelScope.launch { progress.markOpened(id, "mission") }
        viewModelScope.launch { progress.state.collect { s -> _hints.value = s.hintCounts[id] ?: 0 } }
        val restored = submitted.toList()
        reset() // clears `submitted`
        if (restored.isNotEmpty()) {
            restored.forEach { line -> apply(line, record = false) }
            submitted += restored
            savedState[KEY_LINES] = ArrayList(submitted)
            // The previous process may have died after the last command was saved but before the reward was written.
            // Completing is idempotent: it only pays on a first completion, and only then is the dialog shown.
            if (runner.completed) { rewarded = true; award(showWhenAlreadyDone = false) }
        }
    }

    private fun snapshot(lines: List<String>, reward: Int? = _ui.value.reward) = TerminalUi(
        lines = lines, prompt = runner.session.prompt(), passwordMode = runner.session.pending != null,
        done = runner.doneObjectives, trace = runner.session.trace, completed = runner.completed, failed = runner.failed, reward = reward,
    )

    fun reset() {
        runner = MissionRunner(pack.value)
        historyIndex = -1
        draft = ""
        rewarded = false
        submitted.clear()
        savedState[KEY_LINES] = ArrayList(submitted)
        _ui.value = snapshot(emptyList(), null)
    }

    fun submit(line: String) = apply(line, record = true)

    private fun apply(line: String, record: Boolean) {
        if (record) { submitted += line; savedState[KEY_LINES] = ArrayList(submitted) }
        val before = _ui.value
        val echo = if (before.passwordMode) "password: ••••••" else before.prompt + " " + line
        val step = runner.submitLine(line)
        val lines = (if (step.output.clear) emptyList() else before.lines + echo) + step.output.lines
        historyIndex = -1
        draft = ""
        _ui.value = snapshot(lines)
        if (step.completed && !rewarded && record) {
            rewarded = true
            award(showWhenAlreadyDone = true)
        }
    }

    private fun award(showWhenAlreadyDone: Boolean) {
        viewModelScope.launch {
            val m = pack.value
            val first = progress.complete(id, "mission", m.points, m.skills, m.unlocks.codex)
            if (first || showWhenAlreadyDone) _ui.update { it.copy(reward = if (first) m.points else 0) }
        }
    }

    /** Previous command from history, or the current input when none. The text being typed is kept as a draft. */
    fun historyUp(current: String): String {
        val h = runner.session.history
        if (h.isEmpty()) return current
        if (historyIndex < 0) draft = current
        historyIndex = if (historyIndex < 0) h.lastIndex else (historyIndex - 1).coerceAtLeast(0)
        return h[historyIndex]
    }

    /** Next (newer) command, and after the newest one the draft that was being typed. */
    fun historyDown(current: String): String {
        val h = runner.session.history
        if (historyIndex < 0) return current
        if (historyIndex >= h.lastIndex) { historyIndex = -1; return draft }
        return h[++historyIndex]
    }

    /** Completes [input] as far as possible. */
    fun complete(input: String): String {
        val options = Completer.complete(runner.session, input)
        if (options.isEmpty()) return input
        val common = Completer.commonPrefix(options)
        val word = input.substringAfterLast(' ').substringAfterLast('|').trimStart()
        return input.removeSuffix(word) + common + if (options.size == 1 && !common.endsWith("/")) " " else ""
    }

    fun takeHint() = viewModelScope.launch {
        progress.takeHint(id, _hints.value)
    }

    fun dismissReward() = _ui.update { it.copy(reward = null) }

    private companion object { const val KEY_LINES = "lines" }
}
