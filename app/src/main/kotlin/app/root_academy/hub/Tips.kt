package app.root_academy.hub

import androidx.annotation.StringRes
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.root_academy.R

/** Ids of the one-time screens. They are remembered in the settings; "Show the intro again" forgets them all. */
object Tips {
    const val ONBOARDING = "onboarding"
    const val TERMINAL = "terminal_intro"
    const val CIPHER = "cipher_intro"
    const val SOC = "soc_briefing"
    const val DEFENSE = "defense_intro"
}

/** A dialog shown the first time a screen opens, never again once dismissed. */
@Composable
fun FirstTimeTip(hub: HubViewModel, id: String, @StringRes title: Int, @StringRes body: Int) {
    val settings by hub.settings.collectAsState()
    val s = settings ?: return
    if (id in s.seenTips) return
    AlertDialog(
        onDismissRequest = { hub.markTipSeen(id) },
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(body), Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { hub.markTipSeen(id) }) { Text(stringResource(R.string.tip_got_it)) } },
    )
}
