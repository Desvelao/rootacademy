package app.root_academy

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import app.root_academy.hub.AppRoot
import app.root_academy.hub.HubViewModel
import app.root_academy.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val hub: HubViewModel = hiltViewModel()
            val settings by hub.settings.collectAsState()
            // Draw nothing until the saved settings are read, so a dark-theme or large-text user never sees the
            // defaults flash by (the window background already follows the system theme).
            settings?.let { loaded ->
                AppTheme(loaded) {
                    // The surface fills the whole window (including behind the system bars); only content is inset.
                    Surface(Modifier.fillMaxSize()) { AppRoot(hub) }
                }
            }
        }
    }
}
