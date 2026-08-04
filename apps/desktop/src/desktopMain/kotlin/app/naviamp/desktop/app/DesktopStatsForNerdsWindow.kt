package app.naviamp.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import app.naviamp.desktop.platform.configureDesktopWindowAppearance
import app.naviamp.desktop.platform.configureDesktopWindowIcon
import app.naviamp.ui.NaviampDiagnosticsUi
import app.naviamp.ui.NaviampStatsForNerdsContent

/** Desktop window shell around the authoritative Core diagnostics model and shared content. */
@Composable
internal fun DesktopStatsForNerdsWindow(
    diagnostics: NaviampDiagnosticsUi,
    onClose: () -> Unit,
) {
    Window(
        state = rememberWindowState(size = DpSize(760.dp, 780.dp)),
        title = "Naviamp - Stats for Nerds",
        onCloseRequest = onClose,
        icon = painterResource("icons/naviamp.png"),
    ) {
        val darkTitleBar = androidx.compose.foundation.isSystemInDarkTheme()
        LaunchedEffect(window, darkTitleBar) {
            configureDesktopWindowIcon(window)
            configureDesktopWindowAppearance(window, darkTitleBar)
        }
        MaterialTheme(colorScheme = if (darkTitleBar) darkColorScheme() else lightColorScheme()) {
            Surface(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Stats for Nerds", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Button(onClick = onClose) { Text("Close") }
                    }
                    NaviampStatsForNerdsContent(
                        diagnostics = diagnostics,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }
            }
        }
    }
}
