package app.root_academy.hub

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.root_academy.R

private class Step(@StringRes val title: Int, @StringRes val body: Int)

private val STEPS = listOf(
    Step(R.string.onboarding_1_title, R.string.onboarding_1_body),
    Step(R.string.onboarding_2_title, R.string.onboarding_2_body),
    Step(R.string.onboarding_3_title, R.string.onboarding_3_body),
    Step(R.string.onboarding_4_title, R.string.onboarding_4_body),
)

/**
 * First-run intro: what the app is, the four sub-games, points and skills, and where to get help.
 * Skippable at any step. [onDone] only dismisses it; [onStart] dismisses it and opens the first mission.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit, onStart: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val last = step == STEPS.lastIndex
    BackHandler { if (step > 0) step-- else onDone() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
            Text(stringResource(R.string.onboarding_step, step + 1, STEPS.size), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 16.dp)) {
                Text(stringResource(STEPS[step].title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(STEPS[step].body), Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodyLarge)
            }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!last) TextButton(onClick = onDone, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.onboarding_skip)) }
                Spacer(Modifier.weight(1f))
                if (step > 0) OutlinedButton(onClick = { step-- }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_back)) }
                if (!last) Button(onClick = { step++ }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.onboarding_next)) }
            }
            if (last) {
                Button(onClick = onStart, Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(top = 8.dp)) { Text(stringResource(R.string.onboarding_start)) }
                OutlinedButton(onClick = onDone, Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(top = 8.dp)) { Text(stringResource(R.string.onboarding_explore)) }
            }
        }
    }
}
