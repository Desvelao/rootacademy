package app.root_academy.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** UI text scale factors. */
enum class TextSize(val scale: Float) { SMALL(0.9f), NORMAL(1f), LARGE(1.15f), XLARGE(1.3f) }

data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val textSize: TextSize = TextSize.NORMAL,
    val terminalFontSp: Int = 14,
    val reduceMotion: Boolean = false,
    val highContrast: Boolean = false,
    /** Ids of the one-time intro screens and tips the player has already dismissed. */
    val seenTips: Set<String> = emptySet(),
)

private val Context.store by preferencesDataStore("settings")

/** Settings kept in DataStore. The language choice is kept by the platform (see docs/APP_STANDARDS.md). */
class SettingsRepository(private val context: Context) {
    private val theme = stringPreferencesKey("theme")
    private val textSize = stringPreferencesKey("text_size")
    private val termFont = intPreferencesKey("terminal_font")
    private val motion = booleanPreferencesKey("reduce_motion")
    private val contrast = booleanPreferencesKey("high_contrast")
    private val tips = stringSetPreferencesKey("seen_tips")

    val settings: Flow<Settings> = context.store.data.map { p ->
        Settings(
            theme = p[theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            textSize = p[textSize]?.let { runCatching { TextSize.valueOf(it) }.getOrNull() } ?: TextSize.NORMAL,
            terminalFontSp = (p[termFont] ?: 14).coerceIn(10, 22),
            reduceMotion = p[motion] ?: false,
            highContrast = p[contrast] ?: false,
            seenTips = p[tips] ?: emptySet(),
        )
    }

    suspend fun setTheme(v: ThemeMode) = context.store.edit { it[theme] = v.name }
    suspend fun setTextSize(v: TextSize) = context.store.edit { it[textSize] = v.name }
    suspend fun setTerminalFont(v: Int) = context.store.edit { it[termFont] = v.coerceIn(10, 22) }
    suspend fun setReduceMotion(v: Boolean) = context.store.edit { it[motion] = v }
    suspend fun setHighContrast(v: Boolean) = context.store.edit { it[contrast] = v }
    suspend fun markTipSeen(id: String) = context.store.edit { it[tips] = (it[tips] ?: emptySet()) + id }

    /** Forgets every dismissed tip, so the intro and the first-time tips show again. */
    suspend fun resetTips() = context.store.edit { it.remove(tips) }
}
