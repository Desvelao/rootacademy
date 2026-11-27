package app.root_academy.puzzle

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import app.root_academy.core.content.Keys
import app.root_academy.data.Settings
import app.root_academy.hub.HubViewModel
import app.root_academy.hub.Routes
import app.root_academy.terminal.HintsDialog
import app.root_academy.terminal.categoryName
import app.root_academy.ui.design.Gap
import app.root_academy.ui.design.SectionLabel
import app.root_academy.ui.design.SmallBadge
import app.root_academy.ui.design.onEnterKey
import app.root_academy.ui.design.TerminalText

@Composable
fun PuzzleScreen(nav: NavController, vm: PuzzleViewModel = hiltViewModel(), hub: HubViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsState()
    val settings by hub.settings.collectAsState()
    val fontSp = (settings ?: Settings()).terminalFontSp
    val puzzle = vm.pack.value
    var flag by rememberSaveable { mutableStateOf("") }
    var showHints by rememberSaveable { mutableStateOf(false) }
    var toolId by rememberSaveable { mutableStateOf(puzzle.tools.firstOrNull()) }
    var toolInput by rememberSaveable { mutableStateOf(puzzle.files.values.firstOrNull().orEmpty()) }
    var shift by rememberSaveable { mutableIntStateOf(0) }
    val tool = toolId?.let(PuzzleTool::byId)

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
                SmallBadge(categoryName(puzzle.category))
                SmallBadge(stringResource(R.string.difficulty_fmt, puzzle.difficulty))
                SmallBadge(stringResource(R.string.pts_fmt, puzzle.points))
            }
            Gap(12)
            Text(vm.pack.text(vm.lang, Keys.PROMPT), style = MaterialTheme.typography.bodyLarge)

            SectionLabel(stringResource(R.string.puzzle_files))
            for ((name, text) in puzzle.files) {
                Column(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp).background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp)).padding(12.dp),
                ) {
                    Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    TerminalText(text, fontSp, Modifier.horizontalScroll(rememberScrollState()))
                    TextButton(onClick = { toolInput = text }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.puzzle_tool_input)) }
                }
            }

            if (puzzle.tools.isNotEmpty()) {
                SectionLabel(stringResource(R.string.puzzle_tools))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (id in puzzle.tools) PuzzleTool.byId(id)?.let { t ->
                        FilterChip(selected = toolId == id, onClick = { toolId = id }, label = { Text(stringResource(t.label)) }, modifier = Modifier.heightIn(min = 48.dp))
                    }
                }
                OutlinedTextField(
                    value = toolInput, onValueChange = { toolInput = it }, Modifier.fillMaxWidth().padding(top = 8.dp),
                    label = { Text(stringResource(R.string.puzzle_tool_input)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                )
                if (tool?.usesShift == true) Row(verticalAlignment = Alignment.CenterVertically) {
                    val downDesc = stringResource(R.string.puzzle_shift_down)
                    val upDesc = stringResource(R.string.puzzle_shift_up)
                    TextButton(onClick = { shift-- }, Modifier.heightIn(min = 48.dp).semantics { contentDescription = downDesc }) { Text("−") }
                    Text(stringResource(R.string.puzzle_shift, shift))
                    TextButton(onClick = { shift++ }, Modifier.heightIn(min = 48.dp).semantics { contentDescription = upDesc }) { Text("+") }
                }
                if (tool?.usesShift == true) Text(stringResource(R.string.cipher_shift_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (tool != null) {
                    Text(stringResource(R.string.puzzle_tool_output), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelMedium)
                    TerminalText(tool.apply(toolInput, shift), fontSp, Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp))
                }
            }

            SectionLabel(stringResource(R.string.puzzle_flag_label))
            OutlinedTextField(
                value = flag, onValueChange = { flag = it; vm.clearFeedback() }, Modifier.fillMaxWidth().onEnterKey { vm.submit(flag) },
                label = { Text(stringResource(R.string.puzzle_flag_label)) }, singleLine = true,
                isError = ui.wrong, supportingText = { if (ui.wrong) Text(stringResource(R.string.puzzle_wrong)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { vm.submit(flag) }),
            )
            Button(onClick = { vm.submit(flag) }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.puzzle_submit)) }
            if (ui.solved || ui.reward != null) {
                Text(
                    "✓ " + if ((ui.reward ?: 0) > 0) stringResource(R.string.puzzle_correct, ui.reward!!) else stringResource(R.string.puzzle_solved),
                    Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall,
                )
            }
            Gap(24)
        }
    }
    if (showHints) HintsDialog(vm.pack, vm.lang, ui.hints, onTake = vm::takeHint, onClose = { showHints = false })
}
