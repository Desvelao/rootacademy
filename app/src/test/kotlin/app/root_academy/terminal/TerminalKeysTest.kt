package app.root_academy.terminal

import androidx.compose.ui.input.key.Key
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

class TerminalKeysTest {
    @Test fun enterAndNumpadEnterRunTheCommand() {
        assertEquals(TerminalKey.RUN, terminalKeyAction(Key.Enter, shift = false))
        assertEquals(TerminalKey.RUN, terminalKeyAction(Key.NumPadEnter, shift = false))
        assertEquals(TerminalKey.RUN, terminalKeyAction(Key.Enter, shift = true))
    }

    @Test fun tabCompletesButShiftTabKeepsMovingFocus() {
        assertEquals(TerminalKey.COMPLETE, terminalKeyAction(Key.Tab, shift = false))
        assertNull(terminalKeyAction(Key.Tab, shift = true))
    }

    @Test fun upAndDownBrowseHistory() {
        assertEquals(TerminalKey.HISTORY_UP, terminalKeyAction(Key.DirectionUp, shift = false))
        assertEquals(TerminalKey.HISTORY_DOWN, terminalKeyAction(Key.DirectionDown, shift = false))
    }

    @Test fun otherKeysAreLeftToTheField() {
        assertNull(terminalKeyAction(Key.A, shift = false))
        assertNull(terminalKeyAction(Key.DirectionLeft, shift = false))
        assertNull(terminalKeyAction(Key.Backspace, shift = false))
    }
}
