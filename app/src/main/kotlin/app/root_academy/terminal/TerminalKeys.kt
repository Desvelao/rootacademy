package app.root_academy.terminal

import androidx.compose.ui.input.key.Key

/** What a hardware key does in the terminal's command field. */
enum class TerminalKey { RUN, COMPLETE, HISTORY_UP, HISTORY_DOWN }

/**
 * Maps a hardware key to a terminal action, or null when the field should handle it as usual (letters, arrows left and
 * right, Shift+Tab to move focus backwards...). Enter is handled here and not left to the on-screen keyboard's Send
 * action, because a physical keyboard's Enter does not always reach that action.
 */
fun terminalKeyAction(key: Key, shift: Boolean): TerminalKey? = when (key) {
    Key.Enter, Key.NumPadEnter -> TerminalKey.RUN
    Key.Tab -> if (shift) null else TerminalKey.COMPLETE
    Key.DirectionUp -> TerminalKey.HISTORY_UP
    Key.DirectionDown -> TerminalKey.HISTORY_DOWN
    else -> null
}
