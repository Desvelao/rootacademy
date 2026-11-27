package app.root_academy.hub

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.root_academy.R
import app.root_academy.core.content.CampaignProgress
import app.root_academy.core.content.Keys
import app.root_academy.core.content.StepState
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.root_academy.core.progress.Daily
import app.root_academy.ui.design.CardTile
import app.root_academy.ui.design.Gap
import app.root_academy.ui.design.ellipsize
import app.root_academy.ui.design.SectionLabel

@Composable
fun HomeScreen(hub: HubViewModel, nav: NavController) {
    val state by hub.progress.collectAsState()
    val today by hub.today.collectAsState()
    LifecycleResumeEffect(Unit) { hub.refreshDay(); onPauseOrDispose { } }
    val catalog = hub.catalog
    val lang = hub.lang
    var showStats by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(R.string.home_summary, state.points, state.streak),
                    Modifier.heightIn(min = 48.dp).clickable(onClickLabel = stringResource(R.string.home_stats_open), role = Role.Button) { showStats = true }.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { nav.navigate(Routes.SETTINGS) }, Modifier.size(48.dp)) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_open))
            }
        }

        // The story comes first: the first unfinished campaign and its next open step.
        val current = catalog.campaigns.firstOrNull { !CampaignProgress.state(it.value, state.completed).complete }
        if (current != null) {
            val cs = CampaignProgress.state(current.value, state.completed)
            val step = current.value.steps.firstOrNull { cs.steps[it.id] == StepState.AVAILABLE }
            SectionLabel(stringResource(R.string.home_campaign))
            CardTile(
                title = current.text(lang, Keys.TITLE),
                subtitle = step?.let { catalog.title(it.activity, lang) + ": " + ellipsize(current.text(lang, it.story), 90) } ?: current.text(lang, "intro"),
                footer = stringResource(R.string.campaign_steps_done, cs.done, cs.total),
                onClick = { nav.navigate(Routes.campaign(current.value.id)) },
            )
        }

        // Continue the last unfinished activity; otherwise point at the next mission, so this slot (and the layout below it) stays put.
        val last = state.last
        val lastTitle = last?.let { l ->
            catalog.mission(l.activityId)?.text(lang, "title") ?: catalog.puzzle(l.activityId)?.text(lang, "title")
        }
        if (last != null && lastTitle != null && last.activityId !in state.completed && hub.lockedBy(last.activityId, state.completed).isEmpty()) {
            val points = (catalog.mission(last.activityId)?.value?.points ?: catalog.puzzle(last.activityId)?.value?.points) ?: 0
            val hints = state.hintCounts[last.activityId] ?: 0
            val kind = stringResource(if (last.kind == "mission") R.string.terminal_tab_missions else R.string.terminal_tab_puzzles)
            SectionLabel(stringResource(R.string.home_continue))
            CardTile(
                lastTitle,
                kind + " · " + stringResource(R.string.pts_fmt, points) + if (hints > 0) " · " + stringResource(R.string.home_hints_used, hints) else "",
                onClick = { nav.navigate(if (last.kind == "mission") Routes.mission(last.activityId) else Routes.puzzle(last.activityId)) },
            )
        } else {
            val next = hub.nextMission(state.completed)
            if (next != null) {
                SectionLabel(stringResource(if (state.completed.isEmpty()) R.string.home_start_here else R.string.home_next_up))
                CardTile(
                    next.text(lang, "title"),
                    stringResource(R.string.home_next_sub, next.value.order, next.value.points),
                    onClick = { nav.navigate(Routes.mission(next.value.id)) },
                )
            }
        }

        // Daily challenge: a seeded puzzle, the same for everyone on a given day.
        if (catalog.puzzles.isNotEmpty()) {
            val daily = catalog.puzzles[Daily.index(today, catalog.puzzles.size)]
            val solved = daily.value.id in state.completed
            val dailyLocks = hub.lockedBy(daily.value.id, state.completed)
            SectionLabel(stringResource(R.string.home_daily))
            Text(stringResource(R.string.home_daily_sub), Modifier.padding(start = 4.dp, bottom = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            CardTile(
                daily.text(lang, "title"),
                when {
                    dailyLocks.isNotEmpty() -> stringResource(R.string.campaign_locked, dailyLocks.first())
                    solved -> stringResource(R.string.home_daily_done)
                    else -> ellipsize(daily.text(lang, "prompt"), 90)
                },
                trailing = stringResource(R.string.pts_fmt, daily.value.points),
                enabled = dailyLocks.isEmpty(),
                onClick = { nav.navigate(Routes.puzzle(daily.value.id)) },
            )
        }

        SectionLabel(stringResource(R.string.home_games))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (pack in catalog.games) {
                val g = SubGame(pack)
                CardTile(
                    title = g.title(lang),
                    subtitle = g.subtitle(lang),
                    trailing = null,
                    footer = g.progress(lang, catalog, state),
                    onClick = { nav.navigate(g.route) },
                )
            }
        }
        Gap(24)
    }
    if (showStats) AlertDialog(
        onDismissRequest = { showStats = false },
        title = { Text(stringResource(R.string.home_stats_title)) },
        text = { Text(stringResource(R.string.home_stats_body)) },
        confirmButton = {
            TextButton(onClick = {
                showStats = false
                nav.navigate(Routes.PROFILE) { popUpTo(nav.graph.startDestinationId) { saveState = true }; launchSingleTop = true; restoreState = true }
            }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.home_stats_profile)) }
        },
        dismissButton = { TextButton(onClick = { showStats = false }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_close)) } },
    )
}
