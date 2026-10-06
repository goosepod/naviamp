package app.naviamp.desktop

import androidx.compose.runtime.State
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import app.naviamp.desktop.platform.configureDesktopWindowAppearance
import app.naviamp.desktop.platform.configureDesktopWindowIcon
import app.naviamp.ui.NaviampStatsForNerdsWindowContent
import app.naviamp.ui.NaviampDiagnosticsUi
import app.naviamp.ui.naviampAppIconPainter

/** Desktop window shell around the authoritative Core diagnostics model and shared content. */
@Composable
internal fun DesktopStatsForNerdsWindow(
    diagnostics: State<NaviampDiagnosticsUi>,
    onClose: () -> Unit,
) {
    Window(
        state = rememberWindowState(size = DpSize(760.dp, 780.dp)),
        title = "Naviamp - Stats for Nerds",
        onCloseRequest = onClose,
        icon = naviampAppIconPainter(),
    ) {
        val darkTitleBar = androidx.compose.foundation.isSystemInDarkTheme()
        LaunchedEffect(window, darkTitleBar) {
            configureDesktopWindowIcon(window)
            configureDesktopWindowAppearance(window, darkTitleBar)
        }
        NaviampStatsForNerdsWindowContent(diagnostics, onClose, darkTitleBar)
    }
}
