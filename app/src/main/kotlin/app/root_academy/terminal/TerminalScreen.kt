package app.root_academy.terminal

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import app.root_academy.R
import app.root_academy.core.content.Keys
import app.root_academy.data.Settings
import app.root_academy.hub.HubViewModel
import app.root_academy.hub.Routes
import app.root_academy.hub.FirstTimeTip
import app.root_academy.hub.Tips
import app.root_academy.ui.design.Term
import app.root_academy.ui.design.TermHelpButton
import app.root_academy.ui.design.TerminalText

private val DEFAULT_CHIPS = listOf("ls", "ls -a", "cd ", "cat ", "grep ", "pwd", "scan", "connect ", "ssh ", "base64 -d ", "hash ", "strings ", "find ", "submit ")

@Composable
fun TerminalScreen(nav: NavController, vm: TerminalViewModel = hiltViewModel(), hub: HubViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsState()
    val hintsTaken by vm.hints.collectAsState()
    val settings by hub.settings.collectAsState()
    val fontSp = (settings ?: Settings()).terminalFontSp
    var input by rememberSaveable { mutableStateOf("") }
    var showObjectives by rememberSaveable { mutableStateOf(false) }
    var showHints by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val chipsState = rememberLazyListState()
    val mission = vm.pack.value
    val welcome = stringResource(R.string.terminal_welcome)
    val lines = listOf(vm.pack.text(vm.lang, Keys.BRIEF), welcome, "") + ui.lines

    LaunchedEffect(lines.size) { listState.scrollToItem((lines.size - 1).coerceAtLeast(0)) }

    // Keep the command field focused, so a hardware keyboard types into it right away: on opening, and again each
    // time a dialog (first-time tip, objectives, hints, reward, failed) closes and gives the window focus back.
    val inputFocus = remember { FocusRequester() }
    val tipOpen = settings?.let { Tips.TERMINAL !in it.seenTips } ?: true
    val dialogOpen = tipOpen || showObjectives || showHints || ui.reward != null || ui.failed
    LaunchedEffect(dialogOpen) { if (!dialogOpen) inputFocus.requestFocus() }

    val context = LocalContext.current
    val copiedMsg = stringResource(R.string.terminal_copied)
    fun copyAll() {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("terminal", lines.joinToString("\n")))
        Toast.makeText(context, copiedMsg, Toast.LENGTH_SHORT).show()
    }

    fun run() {
        if (input.isBlank() && !ui.passwordMode) return
        vm.submit(input)
        input = ""
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            Text(vm.pack.text(vm.lang, Keys.TITLE), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = { showObjectives = true }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.terminal_objectives) + " ${ui.done.size}/${mission.objectives.size}", maxLines = 2, textAlign = TextAlign.Center) }
            TextButton(onClick = { showHints = true }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.terminal_hints)) }
            val copyDesc = stringResource(R.string.terminal_copy_output)
            TextButton(onClick = ::copyAll, Modifier.heightIn(min = 48.dp).semantics { contentDescription = copyDesc }) { Text(stringResource(R.string.terminal_copy)) }
        }
        val pct = (ui.trace * 100 / mission.traceLimit).coerceIn(0, 100)
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.terminal_trace, pct), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                TermHelpButton(Term.TRACE)
            }
            val traceDesc = stringResource(R.string.terminal_trace, pct)
            LinearProgressIndicator(
                progress = { pct / 100f }, Modifier.fillMaxWidth().padding(vertical = 4.dp).heightIn(min = 6.dp).semantics { contentDescription = traceDesc },
                color = if (pct >= 70) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outlineVariant,
            )
        }
        // Long-press selects output text; "Copy" in the top row copies all of it.
        SelectionContainer(Modifier.weight(1f)) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp)),
            state = listState, contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        ) {
            items(lines.size) { i ->
                val l = lines[i]
                // The newest line is a polite live region, so a screen reader announces each command's output.
                val live = if (i == lines.lastIndex) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier
                TerminalText(if (l.isEmpty()) " " else l, fontSp, live, color = if (i < 2) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            }
        }
        }
        Box {
            LazyRow(state = chipsState, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(mission.commands.ifEmpty { DEFAULT_CHIPS }) { c ->
                    CommandChip(c.trim(), fontSp, onClick = { input = c }, onLongClick = { if (!ui.passwordMode) vm.submit("help " + c.trim().substringBefore(' ')) })
                }
            }
            // A fade and an arrow at the right edge tell the player the row scrolls.
            if (chipsState.canScrollForward) Box(
                Modifier.align(Alignment.CenterEnd).height(48.dp).width(48.dp)
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background))),
                contentAlignment = Alignment.CenterEnd,
            ) { Text("›", Modifier.padding(end = 10.dp), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary) }
        }
        if (!ui.passwordMode) TerminalText(ui.prompt, (fontSp - 2).coerceAtLeast(10), Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.primary)
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = input, onValueChange = { input = it },
                Modifier.weight(1f).focusRequester(inputFocus).onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) false else when (terminalKeyAction(e.key, e.isShiftPressed)) {
                        TerminalKey.HISTORY_UP -> { input = vm.historyUp(input); true }
                        TerminalKey.HISTORY_DOWN -> { input = vm.historyDown(input); true }
                        // The Tab key completes a command, as the on-screen Tab button does, instead of moving focus away.
                        TerminalKey.COMPLETE -> { input = vm.complete(input); true }
                        // Enter is handled here, not left to the keyboard's Send action: a physical Enter does not always
                        // reach it. Holding the key down must not run the command over and over.
                        TerminalKey.RUN -> { if (e.nativeKeyEvent.repeatCount == 0) run(); true }
                        null -> false
                    }
                },
                singleLine = true,
                placeholder = { Text(stringResource(if (ui.passwordMode) R.string.terminal_password_hint else R.string.terminal_input_hint)) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                visualTransformation = if (ui.passwordMode) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { run() }),
                colors = TextFieldDefaults.colors(),
            )
            val runDesc = stringResource(R.string.terminal_run)
            Button(onClick = { run() }, Modifier.heightIn(min = 48.dp).semantics { contentDescription = runDesc }) { Text("⏎") }
        }
        Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val upDesc = stringResource(R.string.terminal_history_up)
            val downDesc = stringResource(R.string.terminal_history_down)
            TextButton(onClick = { input = vm.complete(input) }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.terminal_tab)) }
            TextButton(onClick = { input = vm.historyUp(input) }, Modifier.heightIn(min = 48.dp).semantics { contentDescription = upDesc }) { Text("↑ " + stringResource(R.string.terminal_history_up_short)) }
            TextButton(onClick = { input = vm.historyDown(input) }, Modifier.heightIn(min = 48.dp).semantics { contentDescription = downDesc }) { Text("↓ " + stringResource(R.string.terminal_history_down_short)) }
        }
    }

    if (showObjectives) AlertDialog(
        onDismissRequest = { showObjectives = false },
        title = { Text(stringResource(R.string.terminal_objectives)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (o in mission.objectives) {
                    val done = o.id in ui.done
                    Text((if (done) "✓ " else "○ ") + vm.pack.text(vm.lang, Keys.objective(o.id)) + " (" + stringResource(if (done) R.string.objective_done else R.string.objective_todo) + ")")
                }
            }
        },
        confirmButton = { TextButton(onClick = { showObjectives = false }) { Text(stringResource(R.string.action_close)) } },
    )
    if (showHints) HintsDialog(vm.pack, vm.lang, hintsTaken, onTake = vm::takeHint, onClose = { showHints = false })
    FirstTimeTip(hub, Tips.TERMINAL, R.string.tip_terminal_title, R.string.tip_terminal_body)

    if (ui.reward != null) AlertDialog(
        onDismissRequest = vm::dismissReward,
        title = { Text(stringResource(R.string.terminal_complete_title)) },
        text = { if (ui.reward!! > 0) Text(stringResource(R.string.terminal_complete_body, ui.reward!!)) },
        confirmButton = {
            Button(onClick = { vm.dismissReward(); nav.navigate(Routes.debrief(vm.id)) { popUpTo(Routes.GAME) } }) { Text(stringResource(R.string.terminal_debrief)) }
        },
        dismissButton = { TextButton(onClick = vm::dismissReward) { Text(stringResource(R.string.action_close)) } },
    )
    if (ui.failed) AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.terminal_failed_title)) },
        text = { Text(stringResource(R.string.terminal_failed_body)) },
        confirmButton = { Button(onClick = { vm.reset(); input = "" }) { Text(stringResource(R.string.terminal_restart)) } },
        dismissButton = { OutlinedButton(onClick = { nav.popBackStack() }) { Text(stringResource(R.string.action_back)) } },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommandChip(text: String, fontSp: Int, onClick: () -> Unit, onLongClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier.heightIn(min = 48.dp).clip(shape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .combinedClickable(onLongClickLabel = stringResource(R.string.terminal_chip_help), role = Role.Button, onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { TerminalText(text, (fontSp - 1).coerceAtLeast(11)) }
}
