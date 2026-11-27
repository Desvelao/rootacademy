package app.root_academy.ui.design

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.root_academy.R

/** Technical terms the game uses, each with a plain-language definition (strings `term_<id>` and `term_<id>_def`). */
enum class Term(@StringRes val label: Int, @StringRes val definition: Int) {
    SOC(R.string.term_soc, R.string.term_soc_def),
    PHISHING(R.string.term_phishing, R.string.term_phishing_def),
    ESCALATE(R.string.term_escalate, R.string.term_escalate_def),
    FLAG(R.string.term_flag, R.string.term_flag_def),
    TRACE(R.string.term_trace, R.string.term_trace_def),
    SHELL(R.string.term_shell, R.string.term_shell_def),
    RECON(R.string.term_recon, R.string.term_recon_def),
    HASH(R.string.term_hash, R.string.term_hash_def),
    CIPHER(R.string.term_cipher, R.string.term_cipher_def),
    CIPHERTEXT(R.string.term_ciphertext, R.string.term_ciphertext_def),
    PLAINTEXT(R.string.term_plaintext, R.string.term_plaintext_def),
    CAESAR(R.string.term_caesar, R.string.term_caesar_def),
    FREQUENCY(R.string.term_frequency, R.string.term_frequency_def),
    BRUTE_FORCE(R.string.term_brute_force, R.string.term_brute_force_def),
    MFA(R.string.term_mfa, R.string.term_mfa_def),
    DDOS(R.string.term_ddos, R.string.term_ddos_def),
    RANSOMWARE(R.string.term_ransomware, R.string.term_ransomware_def),
    WORM(R.string.term_worm, R.string.term_worm_def),
    FIREWALL(R.string.term_firewall, R.string.term_firewall_def),
    IDS(R.string.term_ids, R.string.term_ids_def),
    PATCHING(R.string.term_patching, R.string.term_patching_def),
    BACKUP(R.string.term_backup, R.string.term_backup_def),
    BUDGET(R.string.term_budget, R.string.term_budget_def),
    INTEGRITY(R.string.term_integrity, R.string.term_integrity_def),
}

/** Dialog that explains one term. */
@Composable
fun TermDialog(term: Term, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(term.label)) },
        text = { Text(stringResource(term.definition)) },
        confirmButton = { TextButton(onClick = onDismiss, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_close)) } },
    )
}

/** Small "?" button that opens the definition of [term]. Meets the 48dp touch target. */
@Composable
fun TermHelpButton(term: Term, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    val desc = stringResource(R.string.term_explain, stringResource(term.label))
    TextButton(onClick = { open = true }, modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).semantics { contentDescription = desc }) { Text("?") }
    if (open) TermDialog(term) { open = false }
}

/** "?" button for the top bar of a screen: opens that screen's how-to-play text. */
@Composable
fun HelpButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val desc = stringResource(R.string.help_open)
    TextButton(onClick = onClick, modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).semantics { contentDescription = desc }) { Text("?") }
}

/** How-to-play dialog. [onGlossary] adds a button that opens the glossary. */
@Composable
fun HelpDialog(@StringRes title: Int, @StringRes body: Int, onDismiss: () -> Unit, onGlossary: (() -> Unit)? = null) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(body), Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = onDismiss, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_close)) } },
        dismissButton = onGlossary?.let { g ->
            { TextButton(onClick = { onDismiss(); g() }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.glossary_title)) } }
        },
    )
}
