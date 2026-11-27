package app.root_academy.codex

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.root_academy.R
import app.root_academy.hub.HubViewModel
import app.root_academy.hub.Routes
import app.root_academy.skills.skillName
import app.root_academy.ui.design.CardTile
import app.root_academy.ui.design.SmallBadge

/** Every lesson, readable offline once unlocked. Same lessons as the companion app. */
@Composable
fun CodexScreen(hub: HubViewModel, nav: NavController) {
    val state by hub.progress.collectAsState()
    val lessons = hub.catalog.lessons
    val lang = hub.lang
    val sources = remember(lang) { unlockSources(hub) }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Text(stringResource(R.string.codex_title), Modifier.padding(start = 16.dp, top = 16.dp), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.codex_count, lessons.count { it.value.id in state.codex }, lessons.size),
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                CardTile(stringResource(R.string.glossary_title), stringResource(R.string.codex_glossary_sub), onClick = { nav.navigate(Routes.GLOSSARY) })
            }
            items(lessons, key = { it.value.id }) { l ->
                val unlocked = l.value.id in state.codex
                val by = sources[l.value.id].orEmpty()
                CardTile(
                    title = if (unlocked) l.text(lang, "title") else "🔒 " + l.text(lang, "title"),
                    subtitle = when {
                        unlocked -> skillName(l.value.skill)
                        by.isEmpty() -> stringResource(R.string.codex_locked)
                        by.size == 1 -> stringResource(R.string.codex_locked_by, by.first().title)
                        else -> stringResource(R.string.codex_locked_by_more, by.first().title, by.size - 1)
                    },
                    // A locked entry still opens what unlocks it, so the player knows where to go.
                    dimmed = !unlocked,
                    onClick = { if (unlocked) nav.navigate(Routes.lesson(l.value.id)) else by.firstOrNull()?.let { nav.navigate(it.route) } },
                )
            }
        }
    }
}

@Composable
fun LessonScreen(hub: HubViewModel, nav: NavController) {
    val id = nav.currentBackStackEntry?.arguments?.getString("id") ?: return
    val lesson = hub.catalog.lesson(id) ?: return
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            SmallBadge(skillName(lesson.value.skill))
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(lesson.text(hub.lang, "title"), style = MaterialTheme.typography.headlineSmall)
            Text(lesson.text(hub.lang, "body"), Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** An activity that unlocks a lesson: its title and where to open it. */
private class UnlockSource(val title: String, val route: String)

/** Lesson id to the activities that unlock it. Missions and puzzles open directly; the other sub-games open their hub. */
private fun unlockSources(hub: HubViewModel): Map<String, List<UnlockSource>> {
    val c = hub.catalog
    val lang = hub.lang
    val out = mutableMapOf<String, MutableList<UnlockSource>>()
    fun add(lessons: List<String>, source: UnlockSource) { for (id in lessons) out.getOrPut(id) { mutableListOf() } += source }
    for (a in c.activities) add(a.unlocks, UnlockSource(c.title(a.id, lang), if (a.kind == "mission" || a.kind == "puzzle") Routes.activity(a) else Routes.game(a.game)))
    for (p in c.campaigns) add(p.value.rewards.codex, UnlockSource(p.text(lang, "title"), Routes.campaign(p.value.id)))
    return out
}
