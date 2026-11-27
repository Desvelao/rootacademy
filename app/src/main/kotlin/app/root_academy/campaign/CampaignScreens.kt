package app.root_academy.campaign

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.root_academy.R
import app.root_academy.core.content.CampaignProgress
import app.root_academy.core.content.Keys
import app.root_academy.core.content.StepState
import app.root_academy.hub.HubViewModel
import app.root_academy.hub.Routes
import app.root_academy.ui.design.CardTile
import app.root_academy.ui.design.Gap
import app.root_academy.ui.design.SectionLabel

@Composable
private fun TopBar(title: String, nav: NavController) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { nav.popBackStack() }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
        }
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
    }
}

/** All campaigns with their progress. */
@Composable
fun CampaignListScreen(hub: HubViewModel, nav: NavController) {
    val state by hub.progress.collectAsState()
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Text(stringResource(R.string.campaigns_title), Modifier.padding(start = 16.dp, top = 16.dp), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.campaigns_sub), Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (p in hub.catalog.campaigns) {
                val s = CampaignProgress.state(p.value, state.completed)
                CardTile(
                    title = p.text(hub.lang, Keys.TITLE),
                    subtitle = p.text(hub.lang, "intro"),
                    footer = stringResource(R.string.campaign_steps_done, s.done, s.total),
                    trailing = if (s.complete) "✓" else null,
                    onClick = { nav.navigate(Routes.campaign(p.value.id)) },
                )
            }
            Gap(24)
        }
    }
}

/** One campaign as a story: every step names its game and links to the activity, locked until its prerequisites are done. */
@Composable
fun CampaignScreen(hub: HubViewModel, nav: NavController, id: String) {
    val state by hub.progress.collectAsState()
    val pack = hub.catalog.campaign(id) ?: return
    val lang = hub.lang
    val progress = CampaignProgress.state(pack.value, state.completed)
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        TopBar(pack.text(lang, Keys.TITLE), nav)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Text(pack.text(lang, "intro"), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.campaign_steps_done, progress.done, progress.total), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelLarge)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for ((i, step) in pack.value.steps.withIndex()) {
                    val a = hub.catalog.activity(step.activity) ?: continue
                    val st = progress.steps.getValue(step.id)
                    val gameTitle = hub.catalog.game(a.game)?.text(lang, Keys.TITLE).orEmpty()
                    SectionLabel(stringResource(R.string.campaign_step_label, i + 1, gameTitle))
                    val needs = step.requires.mapNotNull { r -> pack.value.steps.firstOrNull { it.id == r } }
                        .filter { progress.steps[it.id] != StepState.DONE }.map { hub.catalog.title(it.activity, lang) }
                    CardTile(
                        title = hub.catalog.title(a.id, lang),
                        subtitle = if (st == StepState.LOCKED) stringResource(R.string.campaign_locked, needs.firstOrNull().orEmpty()) else pack.text(lang, step.story),
                        trailing = when (st) {
                            StepState.DONE -> "✓ " + stringResource(R.string.status_done)
                            StepState.LOCKED -> "🔒 " + stringResource(R.string.status_locked)
                            StepState.AVAILABLE -> stringResource(R.string.pts_fmt, a.points)
                        },
                        enabled = st != StepState.LOCKED,
                        onClick = { nav.navigate(Routes.activity(a)) },
                    )
                }
            }
            if (progress.complete) {
                SectionLabel(stringResource(R.string.campaign_complete))
                Text(pack.text(lang, "outro"), style = MaterialTheme.typography.bodyLarge)
            }
            Gap(24)
        }
    }
}
