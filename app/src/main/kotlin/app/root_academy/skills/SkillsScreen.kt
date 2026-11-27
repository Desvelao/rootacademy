package app.root_academy.skills

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.root_academy.R
import app.root_academy.core.content.Skill
import app.root_academy.core.progress.SkillCurve
import app.root_academy.hub.HubViewModel
import app.root_academy.ui.design.Panel
import app.root_academy.ui.design.RowDivider
import app.root_academy.ui.design.SkillBar

@StringRes
fun skillLabel(id: String): Int = when (id) {
    "linux" -> R.string.skill_linux
    "crypto" -> R.string.skill_crypto
    "web" -> R.string.skill_web
    "network" -> R.string.skill_network
    "defense" -> R.string.skill_defense
    else -> R.string.skill_social
}

@Composable
fun skillName(id: String) = stringResource(skillLabel(id))

@Composable
fun SkillsScreen(hub: HubViewModel) {
    val state by hub.progress.collectAsState()
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(stringResource(R.string.skills_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.skills_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
        Panel {
            Skill.entries.forEachIndexed { i, s ->
                val xp = state.skillXp[s.id] ?: 0
                if (i > 0) RowDivider()
                SkillBar(
                    stringResource(skillLabel(s.id)) + " · " + stringResource(R.string.skills_xp_fmt, xp), SkillCurve.level(xp), SkillCurve.fraction(xp),
                    Modifier.heightIn(min = 48.dp).clickable(onClickLabel = stringResource(R.string.skills_details), role = Role.Button) { open = s.id },
                )
            }
        }
    }
    open?.let { id -> SkillDialog(hub, id, state.skillXp[id] ?: 0) { open = null } }
}

/** One place that awards XP to a skill: which group it belongs to, its title and the XP it gives. */
private class XpSource(@StringRes val group: Int, val title: String, val xp: Int)

/** Heading per activity kind, in display order. A kind missing here falls back to an "Other" group instead of crashing. */
internal val XP_GROUPS: Map<String, Int> = linkedMapOf(
    "mission" to R.string.terminal_tab_missions,
    "puzzle" to R.string.terminal_tab_puzzles,
    "soc" to R.string.soc_title,
    "cipher" to R.string.cipher_title,
    "defense" to R.string.defense_title,
)

private fun xpSources(hub: HubViewModel, skill: String): List<XpSource> {
    val c = hub.catalog
    val lang = hub.lang
    val order = XP_GROUPS.keys.toList()
    return c.activities
        .sortedBy { order.indexOf(it.kind).let { i -> if (i < 0) Int.MAX_VALUE else i } }
        .mapNotNull { a -> a.skills[skill]?.let { XpSource(XP_GROUPS[a.kind] ?: R.string.skill_group_other, c.title(a.id, lang), it) } }
}

/** Level, XP to the next level and every mission, puzzle, shift, level or map that feeds the skill. */
@Composable
private fun SkillDialog(hub: HubViewModel, skill: String, xp: Int, onClose: () -> Unit) {
    val level = SkillCurve.level(xp)
    val sources = xpSources(hub, skill)
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(skillLabel(skill))) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.skill_level_line, level, xp), style = MaterialTheme.typography.titleSmall)
                val next = if (level >= SkillCurve.MAX_LEVEL) stringResource(R.string.skill_max_level)
                else SkillCurve.xpForLevel(level + 1).let { stringResource(R.string.skill_next_level, it, it - xp) }
                Text(next, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.skill_earned_from), Modifier.padding(top = 16.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                var lastGroup = -1
                for (src in sources) {
                    if (src.group != lastGroup) {
                        lastGroup = src.group
                        Text(stringResource(src.group), Modifier.padding(top = 10.dp), style = MaterialTheme.typography.titleSmall)
                    }
                    Text("• " + src.title + " (" + stringResource(R.string.skill_xp_plus, src.xp) + ")", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_close)) } },
    )
}
