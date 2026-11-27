package app.root_academy.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

val Mono = FontFamily.Monospace
private val PanelShape = RoundedCornerShape(16.dp)

/** Small uppercase-free section label above a [Panel]. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp),
    )
}

/** Rounded surfaceContainer panel with a 1dp outlineVariant border (docs/APP_STANDARDS.md). */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, PanelShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, PanelShape),
        content = content,
    )
}

@Composable
fun RowDivider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

/** A card tile: title, subtitle, optional trailing text. Tappable when [onClick] is set; dimmed when [enabled] is false. */
@Composable
fun CardTile(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    footer: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    /** Short labels shown under the subtitle (skills, difficulty). Text, so never colour alone. */
    tags: List<String> = emptyList(),
    /** Looks locked but still reacts to taps (for example a locked entry that links to what unlocks it). */
    dimmed: Boolean = false,
) {
    val base = modifier.fillMaxWidth().heightIn(min = 64.dp).alpha(if (enabled && !dimmed) 1f else 0.55f)
        .background(MaterialTheme.colorScheme.surfaceContainer, PanelShape)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, PanelShape)
    val clickable = if (onClick != null && enabled) base.clickable(role = Role.Button, onClick = onClick) else base
    Row(clickable.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (tags.isNotEmpty()) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { tags.forEach { SmallBadge(it) } }
            if (footer != null) Text(footer, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            Text(trailing, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Progress bar with a label and a numeric value so the level is never shown by colour alone. */
@Composable
fun SkillBar(name: String, level: Int, fraction: Float, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, style = MaterialTheme.typography.titleSmall)
            Text("Lv $level", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { fraction }, modifier = Modifier.fillMaxWidth().height(8.dp),
            trackColor = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

/** Equal-width selectable pills for 2-3 short options. */
@Composable
fun <T> Pills(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((value, label) in options) {
            val on = value == selected
            val shape = RoundedCornerShape(16.dp)
            Row(
                Modifier.weight(1f).heightIn(min = 52.dp)
                    .background(if (on) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer, shape)
                    .border(BorderStroke(if (on) 2.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant), shape)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(value) })
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    (if (on) "✓ " else "") + label, style = MaterialTheme.typography.labelLarge,
                    color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

/** Radio row: whole row selectable, min height 64dp, title + optional subtitle. */
@Composable
fun RadioRow(title: String, selected: Boolean, onSelect: () -> Unit, subtitle: String? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RadioButton(selected = selected, onClick = null)
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
fun LinkRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Monospace text used by the terminal and puzzle output. */
@Composable
fun TerminalText(text: String, sizeSp: Int, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Text(text, modifier, color = color, fontFamily = Mono, fontSize = androidx.compose.ui.unit.TextUnit(sizeSp.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp))
}

@Composable
fun SmallBadge(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
fun Gap(h: Int = 12) = Spacer(Modifier.size(h.dp))

/**
 * Shortens [text] to about [limit] characters at a word boundary, and only when it is really longer.
 * Trailing punctuation is dropped before the ellipsis, so it never reads ".…".
 */
fun ellipsize(text: String, limit: Int): String {
    if (text.length <= limit) return text
    val cut = text.take(limit).substringBeforeLast(' ', text.take(limit))
    return cut.trimEnd(' ', '.', ',', ';', ':', '-') + "…"
}
