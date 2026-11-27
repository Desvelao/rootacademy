package app.root_academy.cipher

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import app.root_academy.R
import app.root_academy.core.cipher.Ciphers
import app.root_academy.core.content.Keys
import app.root_academy.core.content.PackValidator
import app.root_academy.data.Settings
import app.root_academy.hub.HubViewModel
import app.root_academy.hub.Routes
import app.root_academy.terminal.HintsDialog
import app.root_academy.hub.FirstTimeTip
import app.root_academy.hub.Tips
import app.root_academy.ui.design.CardTile
import app.root_academy.ui.design.HelpButton
import app.root_academy.ui.design.HelpDialog
import app.root_academy.ui.design.Term
import app.root_academy.ui.design.TermHelpButton
import app.root_academy.ui.design.ellipsize
import app.root_academy.ui.design.onEnterKey
import app.root_academy.ui.design.Gap
import app.root_academy.ui.design.SectionLabel
import app.root_academy.ui.design.SmallBadge
import app.root_academy.ui.design.TerminalText

@StringRes
private fun chapterName(id: String) = when (id) {
    "rome" -> R.string.chapter_rome
    "renaissance" -> R.string.chapter_renaissance
    "wwii" -> R.string.chapter_wwii
    else -> R.string.chapter_today
}

/** Tools a chapter brings. A level can use the tools of its own chapter and every earlier one. */
private enum class CipherTool(val chapterIndex: Int, @StringRes val label: Int, @StringRes val description: Int) {
    FREQ(0, R.string.cipher_tool_freq, R.string.cipher_tool_freq_desc),
    CAESAR(0, R.string.cipher_tool_caesar, R.string.cipher_tool_caesar_desc),
    VIGENERE(1, R.string.cipher_tool_vigenere, R.string.cipher_tool_vigenere_desc),
    KEYLEN(1, R.string.cipher_tool_keylen, R.string.cipher_tool_keylen_desc),
    ENIGMA(2, R.string.cipher_tool_enigma, R.string.cipher_tool_enigma_desc),
    RSA(3, R.string.cipher_tool_rsa, R.string.cipher_tool_rsa_desc);

    val id get() = name.lowercase()
}

@Composable
fun CipherHubScreen(hub: HubViewModel, nav: NavController, gameId: String) {
    val state by hub.progress.collectAsState()
    val levels = hub.catalog.ciphers.filter { hub.catalog.gameOf[it.value.id] ?: "cipher" == gameId }
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
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            PackValidator.CHAPTERS.forEachIndexed { ci, chapter ->
                val inChapter = levels.filter { it.value.chapter == chapter }
                val prev = levels.filter { it.value.chapter == PackValidator.CHAPTERS.getOrNull(ci - 1) }
                val locked = prev.any { it.value.id !in state.completed }
                SectionLabel(stringResource(chapterName(chapter)) + "  ·  " + stringResource(R.string.chapter_progress, inChapter.count { it.value.id in state.completed }, inChapter.size))
                if (locked) Text(stringResource(R.string.cipher_chapter_locked), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (l in inChapter) {
                        val done = l.value.id in state.completed
                        val locks = hub.lockedBy(l.value.id, state.completed)
                        CardTile(
                            title = l.text(lang, Keys.TITLE),
                            subtitle = if (locks.isNotEmpty()) stringResource(R.string.campaign_locked, locks.first()) else ellipsize(l.text(lang, Keys.PROMPT), 90),
                            trailing = if (done) "✓" else if (locked || locks.isNotEmpty()) "🔒" else stringResource(R.string.pts_fmt, l.value.points),
                            enabled = !locked && locks.isEmpty(),
                            onClick = { nav.navigate(Routes.cipherLevel(l.value.id)) },
                        )
                    }
                }
            }
            Gap(24)
        }
    }
    if (showHelp) HelpDialog(R.string.help_cipher_title, R.string.help_cipher_body, { showHelp = false }, { nav.navigate(Routes.GLOSSARY) })
}

@Composable
fun CipherLevelScreen(nav: NavController, vm: CipherViewModel = hiltViewModel(), hub: HubViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsState()
    val settings by hub.settings.collectAsState()
    val fontSp = (settings ?: Settings()).terminalFontSp
    val level = vm.pack.value
    var answer by rememberSaveable { mutableStateOf("") }
    var showHints by rememberSaveable { mutableStateOf(false) }
    val chapterIdx = PackValidator.CHAPTERS.indexOf(level.chapter)
    val tools = CipherTool.entries.filter { it.chapterIndex <= chapterIdx }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            Text(vm.pack.text(vm.lang, Keys.TITLE), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { showHints = true }) { Text(stringResource(R.string.terminal_hints)) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallBadge(stringResource(chapterName(level.chapter)))
                SmallBadge(stringResource(R.string.pts_fmt, level.points))
            }
            Gap(12)
            Text(vm.pack.text(vm.lang, Keys.PROMPT), style = MaterialTheme.typography.bodyLarge)

            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(stringResource(R.string.cipher_ciphertext))
                TermHelpButton(Term.CIPHERTEXT, Modifier.offset(y = 6.dp))
            }
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp)).padding(12.dp)) {
                TerminalText(level.ciphertext, fontSp)
            }

            SectionLabel(stringResource(R.string.cipher_tools_title))
            ToolPanel(tools, level.ciphertext, fontSp, level.tool ?: when (level.cipher) { "rsa" -> "rsa"; "enigma" -> "enigma"; "vigenere" -> "vigenere"; else -> "caesar" })

            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(stringResource(R.string.cipher_answer))
                TermHelpButton(Term.PLAINTEXT, Modifier.offset(y = 6.dp))
            }
            OutlinedTextField(
                value = answer, onValueChange = { answer = it; vm.clearFeedback() }, Modifier.fillMaxWidth().onEnterKey { vm.submit(answer) },
                label = { Text(stringResource(R.string.cipher_answer)) }, singleLine = true,
                isError = ui.wrong, supportingText = { if (ui.wrong) Text(stringResource(R.string.cipher_wrong)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { vm.submit(answer) }),
            )
            Button(onClick = { vm.submit(answer) }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.cipher_check)) }
            if (ui.solved) Text(
                "✓ " + if ((ui.reward ?: 0) > 0) stringResource(R.string.cipher_solved, ui.reward!!) else stringResource(R.string.cipher_already),
                Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall,
            )
            Gap(24)
        }
    }
    if (showHints) HintsDialog(vm.pack, vm.lang, ui.hints, onTake = vm::takeHint, onClose = { showHints = false })
    FirstTimeTip(hub, Tips.CIPHER, R.string.tip_cipher_title, R.string.tip_cipher_body)
}

@Composable
private fun ToolPanel(tools: List<CipherTool>, ciphertext: String, fontSp: Int, preselected: String) {
    // The level names the tool it is about; fall back to the first one when the chapter has not unlocked it.
    var selected by rememberSaveable { mutableStateOf((tools.firstOrNull { it.id == preselected } ?: tools.first()).name) }
    var input by rememberSaveable { mutableStateOf(ciphertext) }
    var shift by rememberSaveable { mutableIntStateOf(0) }
    var key by rememberSaveable { mutableStateOf("") }
    var n by rememberSaveable { mutableStateOf("") }
    var e by rememberSaveable { mutableStateOf("") }
    val tool = CipherTool.valueOf(selected)
    val plain = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false)

    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (t in tools) FilterChip(selected = t == tool, onClick = { selected = t.name }, label = { Text(stringResource(t.label)) }, modifier = Modifier.heightIn(min = 48.dp))
    }
    Text(stringResource(tool.description), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(input, { input = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text(stringResource(R.string.cipher_input)) }, keyboardOptions = plain)
    when (tool) {
        CipherTool.CAESAR -> Row(verticalAlignment = Alignment.CenterVertically) {
            val downDesc = stringResource(R.string.puzzle_shift_down)
            val upDesc = stringResource(R.string.puzzle_shift_up)
            TextButton(onClick = { shift-- }, Modifier.heightIn(min = 48.dp).semantics { contentDescription = downDesc }) { Text("−") }
            Text(stringResource(R.string.puzzle_shift, shift))
            TextButton(onClick = { shift++ }, Modifier.heightIn(min = 48.dp).semantics { contentDescription = upDesc }) { Text("+") }
            Text(stringResource(R.string.cipher_shift_hint), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        CipherTool.VIGENERE -> OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.cipher_key)) }, singleLine = true, keyboardOptions = plain)
        CipherTool.ENIGMA -> OutlinedTextField(key, { key = it.take(3) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.cipher_positions)) }, singleLine = true, keyboardOptions = plain)
        CipherTool.RSA -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(n, { n = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("n") }, singleLine = true)
            OutlinedTextField(e, { e = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("e") }, singleLine = true)
        }
        else -> Unit
    }
    Text(stringResource(R.string.cipher_output), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelMedium)
    val box = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp)
    when (tool) {
        CipherTool.FREQ -> {
            val freq = remember(input) { Ciphers.frequency(input) }
            val total = freq.sumOf { it.second }.coerceAtLeast(1)
            val rows = mutableListOf<String>()
            for (f in freq.take(8)) rows += stringResource(R.string.cipher_freq_row, f.first.toString(), f.second, f.second * 100 / total)
            TerminalText(rows.joinToString("\n"), fontSp, box)
        }
        CipherTool.CAESAR -> TerminalText(Ciphers.caesar(input, shift), fontSp, box)
        CipherTool.VIGENERE -> TerminalText(Ciphers.vigenere(input, key, decrypt = true), fontSp, box)
        CipherTool.KEYLEN -> {
            val scores = remember(input) { Ciphers.keyLengthScores(input) }
            TerminalText(scores.joinToString("\n") { "%d: %.3f".format(it.first, it.second) }, fontSp, box)
            Text(stringResource(R.string.cipher_keylen_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        CipherTool.ENIGMA -> TerminalText(Ciphers.enigma(input, key), fontSp, box)
        CipherTool.RSA -> {
            val nv = n.toLongOrNull(); val ev = e.toLongOrNull()
            val factors = nv?.let { Ciphers.factor(it) }
            val d = if (nv != null && ev != null) Ciphers.rsaPrivateExponent(nv, ev) else null
            val out = if (nv != null && ev != null) Ciphers.rsaDecrypt(input, nv, ev) else null
            if (factors != null) Text(stringResource(R.string.cipher_rsa_factors, factors.first, factors.second))
            if (d != null) Text(stringResource(R.string.cipher_rsa_d, d))
            if (nv != null && ev != null) TerminalText(out ?: stringResource(R.string.cipher_rsa_fail), fontSp, box)
        }
    }
}
