package app.root_academy.hub

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import app.root_academy.cipher.CipherHubScreen
import app.root_academy.core.content.Engine
import app.root_academy.defense.DefenseHubScreen
import app.root_academy.soc.SocHubScreen
import app.root_academy.terminal.TerminalHubScreen

/** Opens the hub screen of the engine a game declares in its `game.json`. New engines register here. */
@Composable
fun GameHub(hub: HubViewModel, nav: NavController, gameId: String) {
    when (hub.catalog.game(gameId)?.value?.engine?.let(Engine::fromId)) {
        Engine.TERMINAL -> TerminalHubScreen(hub, nav, gameId)
        Engine.SOC -> SocHubScreen(hub, nav, gameId)
        Engine.CIPHER -> CipherHubScreen(hub, nav, gameId)
        Engine.DEFENSE -> DefenseHubScreen(hub, nav, gameId)
        null -> Unit
    }
}
