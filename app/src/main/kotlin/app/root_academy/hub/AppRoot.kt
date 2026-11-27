package app.root_academy.hub

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.root_academy.R
import app.root_academy.campaign.CampaignListScreen
import app.root_academy.campaign.CampaignScreen
import app.root_academy.codex.CodexScreen
import app.root_academy.codex.GlossaryScreen
import app.root_academy.codex.LessonScreen
import app.root_academy.cipher.CipherHubScreen
import app.root_academy.cipher.CipherLevelScreen
import app.root_academy.defense.DefenseHubScreen
import app.root_academy.defense.DefenseMapScreen
import app.root_academy.profile.ProfileScreen
import app.root_academy.soc.SocHubScreen
import app.root_academy.soc.SocShiftScreen
import app.root_academy.puzzle.PuzzleScreen
import app.root_academy.settings.SettingsScreen
import app.root_academy.skills.SkillsScreen
import app.root_academy.terminal.DebriefScreen
import app.root_academy.terminal.TerminalHubScreen
import app.root_academy.terminal.TerminalScreen

private data class Tab(val route: String, val label: Int, val icon: ImageVector)

private val TABS = listOf(
    Tab(Routes.HOME, R.string.nav_home, Icons.Filled.Home),
    Tab(Routes.CAMPAIGNS, R.string.nav_campaigns, Icons.Filled.Place),
    Tab(Routes.SKILLS, R.string.nav_skills, Icons.Filled.Star),
    Tab(Routes.CODEX, R.string.nav_codex, Icons.Filled.Info),
    Tab(Routes.PROFILE, R.string.nav_profile, Icons.Filled.Person),
)

@Composable
fun AppRoot(hub: HubViewModel = hiltViewModel()) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val settings by hub.settings.collectAsState()
    // The first-run intro covers the whole screen, bottom bar included (settings are null while loading).
    val showIntro = settings?.let { Tips.ONBOARDING !in it.seenTips } == true
    Scaffold(
        bottomBar = {
            if (!showIntro && TABS.any { it.route == route }) {
                NavigationBar {
                    for (t in TABS) NavigationBarItem(
                        selected = route == t.route,
                        onClick = {
                            nav.navigate(t.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(stringResource(t.label)) },
                        alwaysShowLabel = true, // the icon is decorative; the label is the accessible name
                    )
                }
            }
        },
    ) { padding ->
        // Consume what the Scaffold already padded, so the screens' safeDrawingPadding() does not add it again.
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            NavHost(nav, startDestination = Routes.HOME) {
                composable(Routes.HOME) { HomeScreen(hub, nav) }
                composable(Routes.SKILLS) { SkillsScreen(hub) }
                composable(Routes.CODEX) { CodexScreen(hub, nav) }
                composable(Routes.PROFILE) { ProfileScreen(hub, nav) }
                composable(Routes.SETTINGS) { SettingsScreen(hub, nav) }
                composable(Routes.GAME) { entry -> GameHub(hub, nav, entry.arguments?.getString("id").orEmpty()) }
                composable(Routes.CAMPAIGNS) { CampaignListScreen(hub, nav) }
                composable(Routes.CAMPAIGN) { entry -> CampaignScreen(hub, nav, entry.arguments?.getString("id").orEmpty()) }
                composable(Routes.MISSION) { TerminalScreen(nav) }
                composable(Routes.DEBRIEF) { DebriefScreen(hub, nav) }
                composable(Routes.PUZZLE) { PuzzleScreen(nav) }
                composable(Routes.SOC_SHIFT) { SocShiftScreen(nav) }
                composable(Routes.CIPHER_LEVEL) { CipherLevelScreen(nav) }
                composable(Routes.DEFENSE_MAP) { DefenseMapScreen(nav) }
                composable(Routes.LESSON) { LessonScreen(hub, nav) }
                composable(Routes.GLOSSARY) { GlossaryScreen(nav) }
            }
            // First-run intro, drawn over everything until it is dismissed (settings are null while loading).
            if (showIntro) {
                OnboardingScreen(
                    onDone = { hub.markTipSeen(Tips.ONBOARDING) },
                    onStart = {
                        hub.markTipSeen(Tips.ONBOARDING)
                        val c = hub.catalog
                        val first = c.campaigns.firstOrNull()?.value?.steps?.firstOrNull { it.requires.isEmpty() }?.let { c.activity(it.activity) }
                            ?: c.games.firstOrNull()?.let { g -> c.activitiesOf(g.value.id).firstOrNull() }
                        first?.let { nav.navigate(Routes.activity(it)) }
                    },
                )
            }
        }
    }
}
