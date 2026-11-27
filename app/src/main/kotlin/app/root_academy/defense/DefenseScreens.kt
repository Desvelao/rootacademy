package app.root_academy.defense

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import app.root_academy.R
import app.root_academy.core.content.Keys
import app.root_academy.core.defense.Rules
import app.root_academy.data.Settings
import app.root_academy.hub.HubViewModel
import app.root_academy.hub.Routes
import app.root_academy.terminal.HintsDialog
import app.root_academy.ui.design.CardTile
import app.root_academy.hub.FirstTimeTip
import app.root_academy.hub.Tips
import app.root_academy.ui.design.Gap
import app.root_academy.ui.design.HelpButton
import app.root_academy.ui.design.HelpDialog

@StringRes
private fun defName(id: String) = when (id) {
    "firewall" -> R.string.def_firewall; "filter" -> R.string.def_filter; "patch" -> R.string.def_patch
    "backup" -> R.string.def_backup; "mfa" -> R.string.def_mfa; else -> R.string.def_ids
}

@StringRes
private fun defDesc(id: String) = when (id) {
    "firewall" -> R.string.def_firewall_desc; "filter" -> R.string.def_filter_desc; "patch" -> R.string.def_patch_desc
    "backup" -> R.string.def_backup_desc; "mfa" -> R.string.def_mfa_desc; else -> R.string.def_ids_desc
}

@StringRes
private fun atkName(id: String) = when (id) {
    "phishing" -> R.string.atk_phishing; "worm" -> R.string.atk_worm; "ransomware" -> R.string.atk_ransomware
    "ddos" -> R.string.atk_ddos; else -> R.string.atk_bruteforce
}

/** Short codes drawn on the map, so attacks and defenses are told apart by letters and not by colour alone. */
private fun defCode(id: String) = when (id) { "firewall" -> "FW"; "filter" -> "EF"; "patch" -> "PT"; "backup" -> "BK"; "mfa" -> "MF"; else -> "ID" }
private fun atkDesc(id: String) = when (id) {
    "phishing" -> R.string.atk_phishing_desc; "worm" -> R.string.atk_worm_desc; "ransomware" -> R.string.atk_ransomware_desc
    "ddos" -> R.string.atk_ddos_desc; else -> R.string.atk_bruteforce_desc
}

private fun atkCode(id: String) = when (id) { "phishing" -> "P"; "worm" -> "W"; "ransomware" -> "R"; "ddos" -> "D"; else -> "B" }

private fun atkColor(id: String) = when (id) {
    "phishing" -> Color(0xFFFFB74D); "worm" -> Color(0xFF81C784); "ransomware" -> Color(0xFFE57373)
    "ddos" -> Color(0xFF64B5F6); else -> Color(0xFFBA68C8)
}

@Composable
fun DefenseHubScreen(hub: HubViewModel, nav: NavController, gameId: String) {
    val state by hub.progress.collectAsState()
    val maps = hub.catalog.maps.filter { hub.catalog.gameOf[it.value.id] ?: "defense" == gameId }
    val lang = hub.lang
    var showHelp by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            Text(hub.catalog.game(gameId)?.text(hub.lang, "title").orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            HelpButton({ showHelp = true })
        }
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(maps, key = { it.value.id }) { m ->
                val done = m.value.id in state.completed
                val campaignLocks = hub.lockedBy(m.value.id, state.completed)
                val locked = campaignLocks.isNotEmpty() || maps.firstOrNull { it.value.order == m.value.order - 1 }?.let { it.value.id !in state.completed } == true
                val brief = m.text(lang, Keys.BRIEF)
                CardTile(
                    title = m.text(lang, Keys.TITLE),
                    // Cut at a word boundary, and only when the brief is actually longer than the limit.
                    subtitle = if (campaignLocks.isNotEmpty()) stringResource(R.string.campaign_locked, campaignLocks.first())
                    else if (locked) stringResource(R.string.defense_map_locked)
                    else if (brief.length > 100) brief.take(100).substringBeforeLast(' ') + "…" else brief,
                    trailing = if (done) "✓ " + stringResource(R.string.status_done) else if (locked) "🔒 " + stringResource(R.string.status_locked) else stringResource(R.string.pts_fmt, m.value.points),
                    footer = stringResource(R.string.defense_map_sub, m.value.waves.size, m.value.integrity),
                    enabled = !locked,
                    onClick = { nav.navigate(Routes.defenseMap(m.value.id)) },
                )
            }
        }
    }
    if (showHelp) HelpDialog(R.string.help_defense_title, R.string.help_defense_body, { showHelp = false }, { nav.navigate(Routes.GLOSSARY) })
}

@Composable
fun DefenseMapScreen(nav: NavController, vm: DefenseViewModel = hiltViewModel(), hub: HubViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsState()
    val settings by hub.settings.collectAsState()
    val reduceMotion = (settings ?: Settings()).reduceMotion
    val map = vm.map
    var speed by rememberSaveable { mutableIntStateOf(1) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var showHints by rememberSaveable { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    fun label(id: String) = vm.pack.text(vm.lang, "node.$id")
    val planning = ui.phase == Phase.PLANNING

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            Text(vm.pack.text(vm.lang, Keys.TITLE), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1)
            TextButton(onClick = { showHints = true }) { Text(stringResource(R.string.terminal_hints)) }
            HelpButton({ showHelp = true })
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.defense_wave, (ui.wave + 1).coerceAtMost(map.waves.size), map.waves.size), style = MaterialTheme.typography.labelLarge)
                Text(stringResource(R.string.defense_money, ui.money), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.defense_integrity, ui.integrity), Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.labelLarge, color = if (ui.integrity <= 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            }
            Gap(8)
            MapCanvas(vm, ui, ::label) { if (planning) selected = it }
            Gap(8)
            val status = when {
                ui.phase == Phase.RUNNING -> stringResource(R.string.defense_running)
                ui.phase == Phase.WON -> stringResource(R.string.defense_won, ui.reward)
                ui.phase == Phase.LOST -> stringResource(R.string.defense_lost)
                ui.lastWave != null -> stringResource(R.string.defense_wave_clear, ui.lastWave!!.first, ui.lastWave!!.second)
                else -> stringResource(R.string.defense_planning)
            }
            Text(status, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyLarge, color = if (ui.phase == Phase.LOST) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            if (ui.phase == Phase.WON) Text(vm.pack.text(vm.lang, Keys.DEBRIEF), Modifier.padding(top = 8.dp))
            if (ui.phase == Phase.PLANNING && ui.wave == 0 && ui.lastWave == null) {
                Text(vm.pack.text(vm.lang, Keys.BRIEF), Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.defense_howto_inline), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
                if (map.order == 1 && ui.placements.values.all { it.isEmpty() }) {
                    Text(stringResource(R.string.defense_first_try), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            // What the next wave brings, so a defense can be chosen on purpose and not by guessing.
            val incoming = map.waves.getOrNull(ui.wave).orEmpty().groupBy { it.attack }.mapValues { (_, v) -> v.sumOf { it.count } }
            if (planning && incoming.isNotEmpty()) {
                val parts = mutableListOf<String>()
                for ((attack, n) in incoming) parts += "$n × " + stringResource(atkName(attack))
                Text(stringResource(R.string.defense_next_wave, parts.joinToString(", ")), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
            }

            Gap(8)
            Text(stringResource(R.string.defense_nodes), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(map.nodes.filter { it.id != map.spawn }, key = { it.id }) { n ->
                    val built = ui.placements[n.id].orEmpty()
                    AssistChip(
                        onClick = { if (planning) selected = n.id },
                        label = { Text(label(n.id) + if (built.isNotEmpty()) "  " + built.joinToString(" ") { defCode(it) } else "") },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
            Gap(8)
            Text(stringResource(R.string.defense_legend), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Rules.attacks.forEach { Text("${atkCode(it.id)} = " + stringResource(atkName(it.id)) + ": " + stringResource(atkDesc(it.id)), style = MaterialTheme.typography.bodySmall) }
            Gap(16)
        }
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            when (ui.phase) {
                Phase.WON -> Button(onClick = { nav.popBackStack() }, Modifier.weight(1f).heightIn(min = 52.dp)) { Text(stringResource(R.string.action_back)) }
                Phase.LOST -> Button(onClick = vm::restart, Modifier.weight(1f).heightIn(min = 52.dp)) { Text(stringResource(R.string.defense_retry)) }
                else -> {
                    OutlinedButton(onClick = { speed = if (speed == 1) 3 else 1 }, Modifier.heightIn(min = 52.dp), enabled = !reduceMotion) { Text(stringResource(R.string.defense_speed, speed)) }
                    Button(onClick = { vm.start(speed, reduceMotion) }, Modifier.weight(1f).heightIn(min = 52.dp), enabled = planning) { Text(stringResource(R.string.defense_start)) }
                }
            }
        }
    }

    selected?.let { node ->
        NodeDialog(vm, ui, node, label(node), onClose = { selected = null })
    }
    if (showHints) HintsDialog(vm.pack, vm.lang, ui.hints, onTake = vm::takeHint, onClose = { showHints = false })
    if (showHelp) HelpDialog(R.string.help_defense_title, R.string.help_defense_body, { showHelp = false }, { nav.navigate(Routes.GLOSSARY) })
    FirstTimeTip(hub, Tips.DEFENSE, R.string.tip_defense_title, R.string.tip_defense_body)
}

@Composable
private fun MapCanvas(vm: DefenseViewModel, ui: DefenseUi, label: (String) -> String, onNode: (String) -> Unit) {
    val map = vm.map
    val measurer = rememberTextMeasurer()
    val line = MaterialTheme.colorScheme.outline
    val nodeFill = MaterialTheme.colorScheme.surfaceContainerHigh
    val primary = MaterialTheme.colorScheme.primary
    val text = MaterialTheme.colorScheme.onSurface
    val target = MaterialTheme.colorScheme.error
    val nodesDesc = map.nodes.joinToString("; ") { n ->
        label(n.id) + (ui.placements[n.id].orEmpty().takeIf { it.isNotEmpty() }?.joinToString(prefix = " (", postfix = ")") { defCode(it) } ?: "")
    }
    val attackNames = ui.attacks.groupingBy { it.type }.eachCount().map { (t, n) -> "$n ${stringResource(atkName(t))}" }
    val desc = if (attackNames.isEmpty()) nodesDesc else nodesDesc + ". " + stringResource(R.string.defense_attacks_active, attackNames.joinToString(", "))
    Canvas(
        Modifier.fillMaxWidth().height(280.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
            .semantics { contentDescription = desc }
            .pointerInput(map.id) {
                detectTapGestures { p ->
                    val pad = 28.dp.toPx()
                    val w = size.width - 2 * pad; val h = size.height - 2 * pad
                    map.nodes.filter { it.id != map.spawn }
                        .minByOrNull { (Offset(pad + it.x * w, pad + it.y * h) - p).getDistance() }
                        ?.takeIf { (Offset(pad + it.x * w, pad + it.y * h) - p).getDistance() < 48.dp.toPx() }
                        ?.let { onNode(it.id) }
                }
            },
    ) {
        val pad = 28.dp.toPx()
        val w = size.width - 2 * pad; val h = size.height - 2 * pad
        fun pos(x: Float, y: Float) = Offset(pad + x * w, pad + y * h)
        val byId = map.nodes.associateBy { it.id }
        for ((a, b) in map.links) drawLine(line, pos(byId.getValue(a).x, byId.getValue(a).y), pos(byId.getValue(b).x, byId.getValue(b).y), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        for (n in map.nodes) {
            val c = pos(n.x, n.y)
            val isTarget = n.id == map.target
            drawCircle(nodeFill, radius = 17.dp.toPx(), center = c)
            drawCircle(if (isTarget) target else if (n.id == map.spawn) line else primary, radius = 17.dp.toPx(), center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = if (isTarget) 4.dp.toPx() else 2.dp.toPx()))
            val name = measurer.measure(label(n.id), TextStyle(color = text, fontSize = 11.sp))
            drawText(name, topLeft = Offset((c.x - name.size.width / 2f).coerceIn(0f, size.width - name.size.width), c.y + 20.dp.toPx()))
            val built = ui.placements[n.id].orEmpty().joinToString(" ") { defCode(it) }
            if (built.isNotEmpty()) {
                val t = measurer.measure(built, TextStyle(color = primary, fontSize = 10.sp))
                drawText(t, topLeft = Offset((c.x - t.size.width / 2f).coerceIn(0f, size.width - t.size.width), c.y - 17.dp.toPx() - t.size.height))
            }
        }
        for (a in ui.attacks) {
            val c = pos(a.x, a.y)
            drawCircle(atkColor(a.type), radius = 9.dp.toPx(), center = c)
            val t = measurer.measure(atkCode(a.type), TextStyle(color = Color.Black, fontSize = 11.sp))
            drawText(t, topLeft = Offset(c.x - t.size.width / 2f, c.y - t.size.height / 2f))
        }
    }
}

@Composable
private fun NodeDialog(vm: DefenseViewModel, ui: DefenseUi, node: String, name: String, onClose: () -> Unit) {
    // A defense is recommended when it stops an attack of the next wave that nothing built so far stops.
    val incoming = vm.map.waves.getOrNull(ui.wave).orEmpty().map { it.attack }.toSet()
    val covered = ui.placements.values.flatten().flatMap { Rules.counters(it) }.toSet()
    val uncovered = incoming - covered
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                for (spec in Rules.defenses) {
                    val built = ui.placements[node].orEmpty().contains(spec.id)
                    val affordable = ui.money >= spec.cost
                    val recommended = !built && Rules.counters(spec.id).any { it in uncovered }
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(defCode(spec.id) + "  " + stringResource(defName(spec.id)) + " · " + stringResource(R.string.defense_cost, spec.cost), style = MaterialTheme.typography.titleSmall)
                            if (recommended) Text("★ " + stringResource(R.string.defense_recommended), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Text(stringResource(defDesc(spec.id)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val names = mutableListOf<String>()
                            for (a in Rules.counters(spec.id)) names += stringResource(atkName(a))
                            Text(stringResource(R.string.defense_stops, names.joinToString()), style = MaterialTheme.typography.bodySmall)
                            if (!built && !affordable) Text(stringResource(R.string.defense_no_budget), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        if (built) OutlinedButton(onClick = { vm.sell(spec.id, node) }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.defense_sell, spec.cost / 2)) }
                        else OutlinedButton(onClick = { vm.build(spec.id, node) }, Modifier.heightIn(min = 48.dp), enabled = affordable) { Text(stringResource(R.string.defense_build)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.action_close)) } },
    )
}
