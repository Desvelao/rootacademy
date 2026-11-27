package app.root_academy.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import app.root_academy.data.Settings
import app.root_academy.data.ThemeMode

private val Accent = Color(0xFFB4E33D)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF1B2600),
    primaryContainer = Color(0xFF354D00),
    onPrimaryContainer = Color(0xFFD3F871),
    secondaryContainer = Color(0xFF26331A),
    onSecondaryContainer = Color(0xFFD6EDB6),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE3E6EA),
    surface = Color(0xFF0E1116),
    onSurface = Color(0xFFE3E6EA),
    surfaceContainer = Color(0xFF171B22),
    surfaceContainerHigh = Color(0xFF1E232B),
    onSurfaceVariant = Color(0xFFA9B0BA),
    outlineVariant = Color(0xFF2E343E),
    error = Color(0xFFFF8A80),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF4D6B00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3F871),
    onPrimaryContainer = Color(0xFF141F00),
    secondaryContainer = Color(0xFFE2EFC9),
    onSecondaryContainer = Color(0xFF1A2400),
    background = Color(0xFFF7F9F2),
    surface = Color(0xFFF7F9F2),
    surfaceContainer = Color(0xFFEBEFE3),
    surfaceContainerHigh = Color(0xFFE3E8DA),
    outlineVariant = Color(0xFFC3C9B8),
)

/** Maximum-contrast variants used when the "high contrast" setting is on. */
private fun ColorScheme.highContrast(dark: Boolean) = if (dark) copy(
    background = Color.Black, surface = Color.Black, surfaceContainer = Color(0xFF0B0B0B), surfaceContainerHigh = Color(0xFF141414),
    onSurface = Color.White, onBackground = Color.White, onSurfaceVariant = Color(0xFFE0E0E0),
    outlineVariant = Color(0xFF9E9E9E), primary = Color(0xFFD7FF5A),
) else copy(
    background = Color.White, surface = Color.White, surfaceContainer = Color(0xFFF2F2F2), surfaceContainerHigh = Color(0xFFE8E8E8),
    onSurface = Color.Black, onBackground = Color.Black, onSurfaceVariant = Color(0xFF1F1F1F),
    outlineVariant = Color(0xFF4A4A4A), primary = Color(0xFF2F4400),
)

/** Whether decorative animation should be skipped. Read by components with motion. */
val LocalReduceMotion = compositionLocalOf { false }

/** App-wide Material 3 theme; mode, text size, contrast and motion come from [settings]. */
@Composable
fun AppTheme(settings: Settings = Settings(), content: @Composable () -> Unit) {
    val dark = when (settings.theme) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    var scheme = if (dark) DarkColors else LightColors
    if (settings.highContrast) scheme = scheme.highContrast(dark)
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale * settings.textSize.scale),
        LocalReduceMotion provides settings.reduceMotion,
    ) {
        MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
    }
}
