package app.root_academy.soc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import app.root_academy.R
import app.root_academy.core.soc.SocEngine
import app.root_academy.data.SystemClock
import app.root_academy.data.Settings
import app.root_academy.hub.HubViewModel
import app.root_academy.hub.Routes
import app.root_academy.hub.FirstTimeTip
import app.root_academy.hub.Tips
import app.root_academy.ui.design.CardTile
import app.root_academy.ui.design.HelpButton
import app.root_academy.ui.design.HelpDialog
import app.root_academy.ui.design.Gap
import app.root_academy.ui.design.SectionLabel
import app.root_academy.ui.design.SmallBadge
import app.root_academy.ui.design.TerminalText

@Composable
private fun verdictLabel(v: String) = stringResource(
    when (v) { "legit" -> R.string.soc_verdict_legit; "phishing" -> R.string.soc_verdict_phishing; else -> R.string.soc_verdict_escalate },
)

@Composable
private fun BackBar(title: String, nav: NavController, onHelp: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { nav.popBackStack() }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
        }
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        if (onHelp != null) HelpButton(onHelp)
    }
}

/** Entry screen of the SOC sub-game: the daily shift and the ten story days. */
@Composable
fun SocHubScreen(hub: HubViewModel, nav: NavController, gameId: String) {
    val state by hub.progress.collectAsState()
    val soc = hub.catalog.soc ?: return
    val lang = hub.lang
    var showHelp by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        BackBar(stringResource(R.string.soc_title), nav, onHelp = { showHelp = true })
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                val today = "soc-daily-${SystemClock.epochDay()}"
                CardTile(
                    stringResource(R.string.soc_daily), stringResource(R.string.soc_daily_sub, SocViewModel.DAILY_COUNT),
                    trailing = if (today in state.completed) "✓" else null,
                    onClick = { nav.navigate(Routes.socShift(SocViewModel.DAILY)) },
                )
                SectionLabel(stringResource(R.string.soc_story))
            }
            items(soc.value.days, key = { it.id }) { d ->
                val done = d.id in state.completed
                val prev = soc.value.days.firstOrNull { it.order == d.order - 1 }
                val needMission = d.requiresMission?.takeIf { it !in state.completed }
                val needPrev = prev != null && prev.id !in state.completed
                val campaignLocks = hub.lockedBy(d.id, state.completed)
                val locked = needMission != null || needPrev || campaignLocks.isNotEmpty()
                val missionName = needMission?.let { hub.catalog.mission(it)?.text(lang, "title") }.orEmpty()
                CardTile(
                    title = soc.text(lang, "day.${d.id}.title"),
                    subtitle = when {
                        campaignLocks.isNotEmpty() -> stringResource(R.string.campaign_locked, campaignLocks.first())
                        needMission != null -> stringResource(R.string.soc_locked_mission, missionName)
                        needPrev -> stringResource(R.string.soc_locked_prev)
                        else -> soc.text(lang, "day.${d.id}.brief")
                    },
                    trailing = if (done) "✓" else if (locked) "🔒" else stringResource(R.string.pts_fmt, d.points),
                    footer = stringResource(R.string.soc_day_items, d.count),
                    enabled = !locked,
                    onClick = { nav.navigate(Routes.socShift(d.id)) },
                )
            }
        }
    }
    if (showHelp) HelpDialog(R.string.help_soc_title, R.string.help_soc_body, { showHelp = false }, { nav.navigate(Routes.GLOSSARY) })
}

@Composable
fun SocShiftScreen(nav: NavController, vm: SocViewModel = hiltViewModel(), hub: HubViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsState()
    val settings by hub.settings.collectAsState()
    val fontSp = (settings ?: Settings()).terminalFontSp
    val title = if (vm.isDaily) stringResource(R.string.soc_daily) else vm.pack.text(vm.lang, "day.${vm.id}.title")
    var showHelp by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        BackBar(title, nav, onHelp = { showHelp = true })
        if (ui.finished) { ResultView(vm, ui, nav); return@Column }
        val item = vm.queue[ui.index]
        val t = item.template
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Text(
                stringResource(R.string.soc_item_count, ui.index + 1, vm.queue.size) + " · " + stringResource(R.string.soc_correct_so_far, ui.correct),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val itemCount = stringResource(R.string.soc_item_count, ui.index + 1, vm.queue.size)
            LinearProgressIndicator(
                progress = { (ui.index + (if (ui.answered != null) 1 else 0)) / vm.queue.size.toFloat() },
                Modifier.fillMaxWidth().padding(vertical = 8.dp).semantics { contentDescription = itemCount }, trackColor = MaterialTheme.colorScheme.outlineVariant,
            )
            SmallBadge(stringResource(when (t.kind) { "email" -> R.string.soc_kind_email; "login" -> R.string.soc_kind_login; else -> R.string.soc_kind_log }))
            Gap(8)
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp)).padding(14.dp)) {
                if (t.kind == "email") Text(SocEngine.render(vm.pack.text(vm.lang, "t.${t.id}.subject"), item.params), style = MaterialTheme.typography.titleMedium)
                TerminalText(SocEngine.render(vm.pack.text(vm.lang, "t.${t.id}.body"), item.params), fontSp, Modifier.padding(top = 6.dp))
            }
            Gap(12)
            val answered = ui.answered
            if (answered == null) {
                Text(stringResource(R.string.soc_prompt), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.soc_verdicts_help), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val ok = answered == t.verdict
                Text(
                    (if (ok) "✓ " else "✗ ") + if (ok) stringResource(R.string.soc_correct) else stringResource(R.string.soc_wrong, verdictLabel(t.verdict)),
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.titleSmall, color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                if (t.clues.isNotEmpty()) {
                    SectionLabel(stringResource(R.string.soc_clues))
                    t.clues.forEach { Text("• " + vm.pack.text(vm.lang, "clue.$it"), Modifier.padding(bottom = 4.dp)) }
                }
            }
            Gap(16)
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (ui.answered == null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (v in SocEngine.VERDICTS) OutlinedButton(onClick = { vm.answer(v) }, Modifier.weight(1f).heightIn(min = 52.dp)) { Text(verdictLabel(v), textAlign = TextAlign.Center) }
                }
            } else {
                Button(onClick = vm::next, Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(stringResource(if (ui.index + 1 < vm.queue.size) R.string.soc_next else R.string.soc_finish))
                }
            }
        }
    }
    if (showHelp) HelpDialog(R.string.help_soc_title, R.string.help_soc_body, { showHelp = false }, { nav.navigate(Routes.GLOSSARY) })
    FirstTimeTip(hub, Tips.SOC, R.string.tip_soc_title, R.string.tip_soc_body)
}

@Composable
private fun ResultView(vm: SocViewModel, ui: SocUi, nav: NavController) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(stringResource(R.string.soc_result_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.soc_result_score, ui.correct, vm.queue.size), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 8.dp))
        if (ui.passed) {
            Text("✓ " + if (ui.reward > 0) stringResource(R.string.soc_result_pass, ui.reward) else stringResource(R.string.cipher_already), style = MaterialTheme.typography.titleSmall)
            if (!vm.isDaily) {
                SectionLabel(stringResource(R.string.soc_debrief))
                Text(vm.pack.text(vm.lang, "day.${vm.id}.debrief"), style = MaterialTheme.typography.bodyLarge)
            }
            Gap(16)
            Button(onClick = { nav.popBackStack() }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_back)) }
        } else {
            Text(stringResource(R.string.soc_result_fail, SocEngine.needed(vm.queue.size), vm.queue.size, SocEngine.PASS_PERCENT), style = MaterialTheme.typography.bodyLarge)
            Gap(16)
            Button(onClick = vm::retry, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.soc_retry)) }
        }
    }
}
