package app.root_academy.hub

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.root_academy.core.content.Catalog
import app.root_academy.core.content.CampaignProgress
import app.root_academy.core.progress.ImportResult
import app.root_academy.core.progress.ProgressExport
import app.root_academy.data.Clock
import app.root_academy.data.ContentRepository
import app.root_academy.data.ProgressRepository
import app.root_academy.data.ProgressState
import app.root_academy.data.Settings
import app.root_academy.data.SettingsRepository
import app.root_academy.data.TextSize
import app.root_academy.data.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class TransferResult { EXPORTED, IMPORTED, BAD_FILE, FAILED }

/** State shared by the hub screens: content, progress, settings, and progress export/import. */
@HiltViewModel
class HubViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contentRepo: ContentRepository,
    private val progressRepo: ProgressRepository,
    private val settingsRepo: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    val catalog: Catalog get() = contentRepo.catalog
    val lang: String get() = contentRepo.language()

    val progress: StateFlow<ProgressState> =
        progressRepo.state.stateIn(viewModelScope, SharingStarted.Eagerly, ProgressState())
    val settings: StateFlow<Settings?> =
        settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _today = MutableStateFlow(clock.epochDay())

    /** The current epoch day; follows the clock, so the daily challenge and the streak roll over at midnight. */
    val today: StateFlow<Long> = _today

    init {
        viewModelScope.launch {
            while (true) {
                delay(DAY_CHECK_MS)
                refreshDay()
            }
        }
    }

    /** Re-reads the day; also called when the app returns to the foreground. */
    fun refreshDay() {
        _today.value = clock.epochDay()
        progressRepo.refreshDay()
    }

    /** Titles of the activities that must be finished before [activityId] opens, according to the campaigns. Empty = free. */
    fun lockedBy(activityId: String, completed: Set<String>): List<String> =
        CampaignProgress.blockers(catalog.campaigns, activityId, completed)
            .map { catalog.title(it.second.activity, lang) }.distinct()

    /** The first unfinished mission that no campaign gate blocks, or null when none is open. */
    fun nextMission(completed: Set<String>) =
        catalog.missions.firstOrNull { it.value.id !in completed && lockedBy(it.value.id, completed).isEmpty() }

    fun setTheme(v: ThemeMode) = viewModelScope.launch { settingsRepo.setTheme(v) }
    fun setTextSize(v: TextSize) = viewModelScope.launch { settingsRepo.setTextSize(v) }
    fun setTerminalFont(v: Int) = viewModelScope.launch { settingsRepo.setTerminalFont(v) }
    fun setReduceMotion(v: Boolean) = viewModelScope.launch { settingsRepo.setReduceMotion(v) }
    fun setHighContrast(v: Boolean) = viewModelScope.launch { settingsRepo.setHighContrast(v) }
    fun markTipSeen(id: String) = viewModelScope.launch { settingsRepo.markTipSeen(id) }
    fun resetTips() = viewModelScope.launch { settingsRepo.resetTips() }

    fun export(uri: Uri, onDone: (TransferResult) -> Unit) = viewModelScope.launch {
        val result = try {
            val text = progressRepo.export().encode()
            val stream = withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } }
            if (stream == null) TransferResult.FAILED else TransferResult.EXPORTED
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            TransferResult.FAILED
        }
        onDone(result)
    }

    /** Reads and parses the file first; [onParsed] lets the UI confirm before progress is replaced. */
    fun readImport(uri: Uri, onParsed: (ImportResult) -> Unit) = viewModelScope.launch {
        val parsed = try {
            val text = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }
            text?.let(ProgressExport::parse) ?: ImportResult.Invalid
        } catch (_: Exception) {
            ImportResult.Invalid
        }
        onParsed(parsed)
    }

    /** Erases all progress (settings are kept); [onDone] gets whether it worked. */
    fun resetProgress(onDone: (Boolean) -> Unit = {}) = viewModelScope.launch {
        val ok = try {
            progressRepo.reset(); true
        } catch (c: CancellationException) {
            throw c
        } catch (_: Exception) {
            false
        }
        onDone(ok)
    }

    /** Replaces the progress with [e]; [onDone] gets IMPORTED, or FAILED when the database write throws (nothing is changed then). */
    fun applyImport(e: ProgressExport, onDone: (TransferResult) -> Unit = {}) = viewModelScope.launch {
        val result = try {
            progressRepo.import(e, catalog)
            TransferResult.IMPORTED
        } catch (c: CancellationException) {
            throw c
        } catch (_: Exception) {
            TransferResult.FAILED
        }
        onDone(result)
    }

    private companion object { const val DAY_CHECK_MS = 60_000L }
}
