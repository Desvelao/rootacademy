package app.root_academy.terminal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.runtime.getValue
import app.root_academy.R
import app.root_academy.hub.HubViewModel
import app.root_academy.hub.Routes
import app.root_academy.ui.design.CardTile
import app.root_academy.ui.design.Gap
import app.root_academy.ui.design.SectionLabel
import app.root_academy.skills.skillName

/** "How it works in real life": the closing explanation of a mission, with its rewards. */
@Composable
fun DebriefScreen(hub: HubViewModel, nav: NavController) {
    val id = nav.currentBackStackEntry?.arguments?.getString("id") ?: return
    val pack = hub.catalog.mission(id) ?: return
    val lang = hub.lang
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(pack.text(lang, "title"), style = MaterialTheme.typography.headlineSmall)
        SectionLabel(stringResource(R.string.debrief_how))
        Text(pack.text(lang, "debrief"), style = MaterialTheme.typography.bodyLarge)
        SectionLabel(stringResource(R.string.debrief_skills))
        pack.value.skills.forEach { (s, xp) -> Text("${skillName(s)}  +$xp", style = MaterialTheme.typography.bodyMedium) }
        pack.value.unlocks.codex.forEach { lessonId ->
            hub.catalog.lesson(lessonId)?.let { l ->
                Gap(16)
                CardTile(stringResource(R.string.debrief_codex, l.text(lang, "title")), "", onClick = { nav.navigate(Routes.lesson(lessonId)) })
            }
        }
        Gap(24)
        Button(onClick = { nav.popBackStack() }, Modifier.padding(bottom = 16.dp)) { Text(stringResource(R.string.action_back)) }
    }
}
