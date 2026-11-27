package app.root_academy.cipher

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.root_academy.core.cipher.Ciphers
import app.root_academy.core.content.CipherLevel
import app.root_academy.core.content.Pack
import app.root_academy.data.ContentRepository
import app.root_academy.data.ProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CipherUi(val hints: Int = 0, val solved: Boolean = false, val wrong: Boolean = false, val reward: Int? = null)

@HiltViewModel
class CipherViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val content: ContentRepository,
    private val progress: ProgressRepository,
) : ViewModel() {
    val id: String = checkNotNull(savedState["id"])
    val pack: Pack<CipherLevel> = checkNotNull(content.catalog.cipher(id))
    val lang get() = content.language()

    private val _ui = MutableStateFlow(CipherUi())
    val ui: StateFlow<CipherUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            progress.state.collect { s -> _ui.update { it.copy(hints = s.hintCounts[id] ?: 0, solved = id in s.completed) } }
        }
    }

    fun takeHint() = viewModelScope.launch { progress.takeHint(id, _ui.value.hints) }

    fun submit(answer: String) {
        if (Ciphers.answerHash(answer) != pack.value.flagSha256) { _ui.update { it.copy(wrong = true) }; return }
        viewModelScope.launch {
            val l = pack.value
            val first = progress.complete(id, "cipher", l.points, l.skills, l.unlocks.codex)
            _ui.update { it.copy(wrong = false, reward = if (first) l.points else 0) }
        }
    }

    fun clearFeedback() = _ui.update { it.copy(wrong = false) }
}
