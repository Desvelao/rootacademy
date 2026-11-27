package app.root_academy.settings

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.os.LocaleListCompat
import androidx.navigation.NavController
import app.root_academy.BuildConfig
import app.root_academy.R
import app.root_academy.data.Settings
import app.root_academy.data.TextSize
import app.root_academy.data.ThemeMode
import app.root_academy.hub.HubViewModel
import app.root_academy.hub.Routes
import app.root_academy.ui.design.LinkRow
import app.root_academy.ui.design.Panel
import app.root_academy.ui.design.Pills
import app.root_academy.ui.design.RadioRow
import app.root_academy.ui.design.RowDivider
import app.root_academy.ui.design.SectionLabel
import app.root_academy.ui.design.SwitchRow

/** Shipped languages as (language tag, own name). Empty tag = follow the system. Keep in sync with locales_config.xml. */
private val LANGUAGES = listOf("en" to "English", "es" to "Español")

@Composable
fun SettingsScreen(hub: HubViewModel, nav: NavController) {
    val s = hub.settings.collectAsState().value ?: Settings()
    val context = LocalContext.current
    var showAbout by remember { mutableStateOf(false) }
    val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SectionLabel(stringResource(R.string.settings_section_language))
            Panel {
                RadioRow(stringResource(R.string.language_system), current.isEmpty(), { AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList()) })
                for ((tag, name) in LANGUAGES) {
                    RowDivider()
                    RadioRow(name, current.startsWith(tag), { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)) })
                }
            }

            SectionLabel(stringResource(R.string.settings_section_theme))
            Pills(
                listOf(
                    ThemeMode.SYSTEM to stringResource(R.string.theme_system),
                    ThemeMode.LIGHT to stringResource(R.string.theme_light),
                    ThemeMode.DARK to stringResource(R.string.theme_dark),
                ),
                s.theme, hub::setTheme,
            )

            SectionLabel(stringResource(R.string.settings_section_reading))
            Panel {
                val sizes = listOf(
                    TextSize.SMALL to R.string.text_size_small, TextSize.NORMAL to R.string.text_size_normal,
                    TextSize.LARGE to R.string.text_size_large, TextSize.XLARGE to R.string.text_size_xlarge,
                )
                Text(stringResource(R.string.settings_text_size), Modifier.padding(start = 16.dp, top = 12.dp), style = MaterialTheme.typography.bodyLarge)
                sizes.forEach { (v, label) ->
                    RadioRow(stringResource(label), s.textSize == v, { hub.setTextSize(v) })
                }
                RowDivider()
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(stringResource(R.string.settings_terminal_font, s.terminalFontSp), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.settings_terminal_font_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        value = s.terminalFontSp.toFloat(), onValueChange = { hub.setTerminalFont(it.toInt()) },
                        valueRange = 10f..22f, steps = 11, modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }

            SectionLabel(stringResource(R.string.settings_section_accessibility))
            Panel {
                SwitchRow(stringResource(R.string.settings_reduce_motion), stringResource(R.string.settings_reduce_motion_sub), s.reduceMotion, hub::setReduceMotion)
                RowDivider()
                SwitchRow(stringResource(R.string.settings_high_contrast), stringResource(R.string.settings_high_contrast_sub), s.highContrast, hub::setHighContrast)
            }

            SectionLabel(stringResource(R.string.settings_section_help))
            Panel {
                LinkRow(stringResource(R.string.glossary_title), stringResource(R.string.settings_glossary_sub)) { nav.navigate(Routes.GLOSSARY) }
                RowDivider()
                LinkRow(stringResource(R.string.settings_intro), stringResource(R.string.settings_intro_sub)) {
                    hub.resetTips()
                    nav.popBackStack()
                }
            }

            TextButton(onClick = { showAbout = true }, Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 16.dp)) {
                Text(
                    stringResource(R.string.settings_version, stringResource(R.string.app_name), BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (showAbout) AboutDialog(context) { showAbout = false }
}

@Composable
private fun AboutDialog(context: Context, onDismiss: () -> Unit) {
    val url = BuildConfig.APP_URL
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
                if (url.isNotBlank()) Text(url, color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = {
            if (url.isNotBlank()) TextButton(onClick = {
                try { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
                catch (_: ActivityNotFoundException) { Toast.makeText(context, R.string.about_no_browser, Toast.LENGTH_SHORT).show() }
                onDismiss()
            }) { Text(stringResource(R.string.about_open)) }
        },
        dismissButton = {
            Row {
                if (url.isNotBlank()) TextButton(onClick = {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("url", url))
                    onDismiss()
                }) { Text(stringResource(R.string.about_copy)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}
