package app.naviamp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

/** Interactive shared-component probe only; no owner profile, server, or playback is used. */
fun main() = application {
    val screen = remember { mutableStateOf(discoveryPageFixture()) }
    val actions = remember { mutableListOf<String>() }
    Window(onCloseRequest = ::exitApplication, title = "Naviamp Aurral artist review",
        state = rememberWindowState(width = 1080.dp, height = 850.dp)) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            SharedHomeRoute(NaviampColors.Dark, NaviampHomeScreenUi(providerDiscovery = screen.value),
                discoveryPageFixtureActions(screen, actions), NaviampMediaActions({}, {}), DiscoverySamplePlaybackUi())
        }
    }
}
