package app.root_academy.terminal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.root_academy.R
import app.root_academy.hub.HubViewModel
import app.root_academy.hub.Routes
import app.root_academy.skills.skillName
import app.root_academy.ui.design.CardTile
import app.root_academy.ui.design.HelpButton
import app.root_academy.ui.design.HelpDialog
import app.root_academy.ui.design.ellipsize
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment

@Composable
fun categoryName(id: String) = stringResource(
    when (id) {
        "crypto" -> R.string.category_crypto
        "web" -> R.string.category_web
        "forensics" -> R.string.category_forensics
        else -> R.string.category_reverse
    },
)

/** Entry screen of the terminal sub-game: story missions and standalone CTF puzzles. */
@Composable
fun TerminalHubScreen(hub: HubViewModel, nav: NavController, gameId: String) {
    val state by hub.progress.collectAsState()
    val catalog = hub.catalog
    val lang = hub.lang
    val missions = catalog.missions.filter { catalog.gameOf[it.value.id] ?: "terminal" == gameId }
    val puzzles = catalog.puzzles.filter { catalog.gameOf[it.value.id] ?: "terminal" == gameId }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            Text(catalog.game(gameId)?.text(lang, "title").orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            HelpButton({ showHelp = true })
        }
        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.terminal_tab_missions)) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.terminal_tab_puzzles)) })
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (tab == 0) {
                items(missions, key = { it.value.id }) { m ->
                    val done = m.value.id in state.completed
                    val tags = listOf(stringResource(R.string.difficulty_fmt, m.value.difficulty)) +
                        m.value.skills.entries.sortedByDescending { it.value }.take(2).map { skillName(it.key) }
                    val locks = hub.lockedBy(m.value.id, state.completed)
                    CardTile(
                        title = "%02d  %s".format(m.value.order, m.text(lang, "title")),
                        subtitle = if (locks.isNotEmpty()) stringResource(R.string.campaign_locked, locks.first()) else ellipsize(m.text(lang, "brief"), 120),
                        enabled = locks.isEmpty(),
                        tags = tags,
                        trailing = if (done) "✓ " + stringResource(R.string.mission_done) else stringResource(R.string.pts_fmt, m.value.points),
                        onClick = { nav.navigate(Routes.mission(m.value.id)) },
                    )
                }
            } else {
                items(puzzles, key = { it.value.id }) { p ->
                    val done = p.value.id in state.completed
                    val locks = hub.lockedBy(p.value.id, state.completed)
                    CardTile(
                        title = p.text(lang, "title"),
                        subtitle = if (locks.isNotEmpty()) stringResource(R.string.campaign_locked, locks.first()) else categoryName(p.value.category) + " · " + stringResource(R.string.difficulty_fmt, p.value.difficulty),
                        enabled = locks.isEmpty(),
                        trailing = if (done) "✓ " + stringResource(R.string.puzzle_solved) else stringResource(R.string.pts_fmt, p.value.points),
                        onClick = { nav.navigate(Routes.puzzle(p.value.id)) },
                    )
                }
            }
        }
    }
    if (showHelp) HelpDialog(R.string.help_terminal_title, R.string.help_terminal_body, { showHelp = false }, { nav.navigate(Routes.GLOSSARY) })
}
