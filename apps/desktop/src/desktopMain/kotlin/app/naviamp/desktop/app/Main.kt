package app.naviamp.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.naviamp.desktop.generated.resources.Res
import app.naviamp.desktop.generated.resources.naviamp
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.painterResource
import java.awt.Dimension

fun main() {
    configureDesktopApplicationName()
    configureDesktopAppearance()
    configureDesktopIcon()

    application {
        val appDependencies = remember { DesktopAppDependencies() }
        val settingsStore = appDependencies.settingsStore
        val windowSettings = remember { settingsStore.loadWindowSettings() }
        val windowState = rememberWindowState(
            size = DpSize(windowSettings.widthDp.dp, windowSettings.heightDp.dp),
        )
        val playbackEngine = appDependencies.playbackEngine
        val appIcon = painterResource(Res.drawable.naviamp)
        LaunchedEffect(windowState) {
            snapshotFlow { windowState.size }
                .distinctUntilChanged()
                .collect { size ->
                    if (size.width.value >= 320f && size.height.value >= 420f) {
                        settingsStore.saveWindowSettings(windowState.toWindowSettings())
                    }
                }
        }
        Window(
            state = windowState,
            icon = appIcon,
            onCloseRequest = {
                settingsStore.saveWindowSettings(windowState.toWindowSettings())
                runCatching {
                    playbackEngine.stop()
                }
                exitApplication()
            },
            title = "Naviamp",
        ) {
            val darkTitleBar = isSystemInDarkTheme()
            LaunchedEffect(window, darkTitleBar) {
                configureNativeTitleBar(window, darkTitleBar)
            }
            window.minimumSize = Dimension(320, 430)
            NaviampApp(
                dependencies = appDependencies,
            )
        }
    }
}
