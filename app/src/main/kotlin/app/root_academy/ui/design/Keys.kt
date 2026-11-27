package app.root_academy.ui.design

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Runs [action] when Enter or numpad Enter is pressed on a hardware keyboard, and consumes the key. A physical Enter
 * does not always reach the on-screen keyboard's Send/Done action, so fields that submit should not rely on that alone.
 * Holding the key down runs [action] once.
 */
fun Modifier.onEnterKey(action: () -> Unit): Modifier = onPreviewKeyEvent { e ->
    if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter)) {
        if (e.nativeKeyEvent.repeatCount == 0) action()
        true
    } else false
}
