package app.root_academy.terminal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.root_academy.R
import app.root_academy.core.content.Pack
import app.root_academy.core.progress.HintCost

/**
 * Shows the hints taken so far and a button for the next one. Three hints, then the walkthrough: never a dead end.
 * [taken] is 0..4 (4 = walkthrough shown).
 */
@Composable
fun <T> HintsDialog(pack: Pack<T>, lang: String, taken: Int, onTake: () -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.hints_title)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.hint_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                for (n in 1..minOf(taken, 3)) Text("$n. " + pack.text(lang, "hint.$n"), Modifier.padding(top = 12.dp))
                if (taken >= 4) {
                    Text(stringResource(R.string.walkthrough_title), Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleSmall)
                    Text(pack.text(lang, "walkthrough"), Modifier.padding(top = 4.dp))
                }
                if (taken < 4) OutlinedButton(onClick = onTake, Modifier.padding(top = 16.dp)) {
                    Text(
                        if (taken < 3) stringResource(R.string.hint_take, taken + 1, HintCost.forStep(taken))
                        else stringResource(R.string.hint_walkthrough, HintCost.forStep(3)),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.action_close)) } },
    )
}
