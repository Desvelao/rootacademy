package app.root_academy.profile

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.root_academy.R
import app.root_academy.core.progress.Badges
import app.root_academy.core.progress.ImportResult
import app.root_academy.core.progress.ProgressExport
import app.root_academy.hub.HubViewModel
import app.root_academy.hub.TransferResult
import app.root_academy.ui.design.LinkRow
import app.root_academy.ui.design.Panel
import app.root_academy.ui.design.RowDivider
import app.root_academy.ui.design.SectionLabel

@Composable
private fun badgeLabel(id: String): String = when (id) {
    "first-flag" -> stringResource(R.string.badge_first_flag)
    "all-flags" -> stringResource(R.string.badge_all_flags)
    else -> stringResource(R.string.badge_n_flags, Badges.all.first { it.id == id }.threshold)
}

@Composable
fun ProfileScreen(hub: HubViewModel, nav: NavController) {
    val state by hub.progress.collectAsState()
    val context = LocalContext.current
    var pending by remember { mutableStateOf<ProgressExport?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    fun toast(res: Int) = Toast.makeText(context, res, Toast.LENGTH_SHORT).show()

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) hub.export(uri) { toast(if (it == TransferResult.EXPORTED) R.string.profile_export_ok else R.string.profile_import_bad) }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) hub.readImport(uri) { parsed ->
            when (parsed) {
                is ImportResult.Ok -> pending = parsed.export
                is ImportResult.UnsupportedVersion -> toast(R.string.profile_import_newer)
                ImportResult.Invalid -> toast(R.string.profile_import_bad)
            }
        }
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(stringResource(R.string.profile_title), style = MaterialTheme.typography.headlineMedium)
        SectionLabel("")
        Panel {
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat(state.points.toString(), stringResource(R.string.profile_points))
                Stat(state.streak.toString(), stringResource(R.string.profile_streak))
                Stat(state.completed.size.toString(), stringResource(R.string.profile_completed))
            }
        }
        Text(stringResource(R.string.profile_stats_help), Modifier.padding(start = 4.dp, top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionLabel(stringResource(R.string.profile_badges))
        Panel {
            // Every badge, earned or not, with what it takes: the state is told by text and a mark, never by colour alone.
            Badges.all.forEachIndexed { i, b ->
                if (i > 0) RowDivider()
                val earned = b.id in state.badges
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text((if (earned) "★ " else "🔒 ") + badgeLabel(b.id), style = MaterialTheme.typography.bodyLarge)
                        Text(context.resources.getQuantityString(R.plurals.badge_criteria, b.threshold, b.threshold), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(stringResource(if (earned) R.string.badge_earned else R.string.badge_locked), style = MaterialTheme.typography.labelLarge, color = if (earned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        SectionLabel(stringResource(R.string.profile_data))
        Panel {
            LinkRow(stringResource(R.string.profile_export), stringResource(R.string.profile_export_sub)) { exporter.launch("progress.json") }
            RowDivider()
            LinkRow(stringResource(R.string.profile_import), stringResource(R.string.profile_import_sub)) { importer.launch(arrayOf("application/json", "text/plain", "*/*")) }
            RowDivider()
            LinkRow(stringResource(R.string.profile_reset), stringResource(R.string.profile_reset_sub)) { confirmReset = true }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.profile_reset_confirm_title)) },
            text = { Text(stringResource(R.string.profile_reset_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    hub.resetProgress { ok -> toast(if (ok) R.string.profile_reset_ok else R.string.profile_reset_failed) }
                }) { Text(stringResource(R.string.profile_reset_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    pending?.let { e ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.profile_import_confirm_title)) },
            text = { Text(stringResource(R.string.profile_import_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    hub.applyImport(e) { toast(if (it == TransferResult.IMPORTED) R.string.profile_import_ok else R.string.profile_import_failed) }
                    pending = null
                }) { Text(stringResource(R.string.profile_import_confirm)) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
