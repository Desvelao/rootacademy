package app.root_academy.soc

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.root_academy.core.content.Pack
import app.root_academy.core.content.SocPack
import app.root_academy.core.soc.SocEngine
import app.root_academy.core.soc.SocItem
import app.root_academy.data.Clock
import app.root_academy.data.ContentRepository
import app.root_academy.data.DAILY_SOC_POINTS
import app.root_academy.data.ProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SocUi(
    val index: Int = 0,
    /** Verdict chosen for the current item, or null while undecided. */
    val answered: String? = null,
    val correct: Int = 0,
    val finished: Boolean = false,
    val passed: Boolean = false,
    /** Points awarded on this run (0 when already completed before). */
    val reward: Int = 0,
)

@HiltViewModel
class SocViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val content: ContentRepository,
    private val progress: ProgressRepository,
    clock: Clock,
) : ViewModel() {
    val id: String = checkNotNull(savedState["id"])
    val pack: Pack<SocPack> = checkNotNull(content.catalog.soc)
    val lang get() = content.language()
    val isDaily = id == DAILY
    private val epochDay = clock.epochDay()
    val day = pack.value.days.firstOrNull { it.id == id }
    val points = day?.points ?: DAILY_SOC_POINTS
    // Progress through the shift survives process death; the queue is rebuilt from the same seed and attempt.
    private var attempt: Int = savedState[KEY_ATTEMPT] ?: 0
    private fun generate() = SocEngine.generate(
        // The daily queue is the same for everyone; a retried story shift gets a new, still deterministic, queue.
        seed = (day?.seed ?: epochDay) + if (isDaily) 0 else attempt * RETRY_SEED_STEP,
        difficulty = day?.difficulty ?: 2, count = day?.count ?: DAILY_COUNT, templates = pack.value.templates,
    )

    var queue: List<SocItem> = generate()
        private set

    private val _ui = MutableStateFlow(
        SocUi(
            index = (savedState.get<Int>(KEY_INDEX) ?: 0).coerceIn(0, queue.size - 1),
            answered = savedState[KEY_ANSWERED],
            correct = savedState[KEY_CORRECT] ?: 0,
            finished = savedState[KEY_FINISHED] ?: false,
            passed = savedState[KEY_PASSED] ?: false,
        ),
    )
    val ui: StateFlow<SocUi> = _ui.asStateFlow()

    init {
        // The process may have died after the shift was passed but before the completion was written; completing is idempotent.
        if (_ui.value.finished && _ui.value.passed) recordCompletion()
    }

    private fun set(next: SocUi) {
        _ui.value = next
        savedState[KEY_INDEX] = next.index
        savedState[KEY_ANSWERED] = next.answered
        savedState[KEY_CORRECT] = next.correct
        savedState[KEY_FINISHED] = next.finished
        savedState[KEY_PASSED] = next.passed
    }

    fun answer(verdict: String) {
        val cur = _ui.value
        if (cur.answered != null) return
        val ok = verdict == queue[cur.index].template.verdict
        set(cur.copy(answered = verdict, correct = cur.correct + if (ok) 1 else 0))
    }

    fun next() {
        val cur = _ui.value
        if (cur.answered == null) return
        if (cur.index + 1 < queue.size) { set(cur.copy(index = cur.index + 1, answered = null)); return }
        val passed = SocEngine.passed(cur.correct, queue.size)
        set(cur.copy(finished = true, passed = passed))
        if (passed) recordCompletion()
    }

    private fun recordCompletion() = viewModelScope.launch {
        val recordId = if (isDaily) "soc-daily-$epochDay" else id
        val skills = day?.skills ?: mapOf("social" to 8, "defense" to 4)
        val first = progress.complete(recordId, "soc", points, skills, day?.unlocks?.codex.orEmpty())
        _ui.update { it.copy(reward = if (first) points else 0) }
    }

    fun retry() {
        if (!isDaily) { attempt++; savedState[KEY_ATTEMPT] = attempt; queue = generate() }
        set(SocUi())
    }

    companion object {
        const val DAILY = "daily"
        const val DAILY_COUNT = 6
        private const val RETRY_SEED_STEP = 7919L
        private const val KEY_ATTEMPT = "attempt"
        private const val KEY_INDEX = "index"
        private const val KEY_ANSWERED = "answered"
        private const val KEY_CORRECT = "correct"
        private const val KEY_FINISHED = "finished"
        private const val KEY_PASSED = "passed"
        // ui and queue state keys; "id" is the navigation argument
    }
}
