package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.naviamp.domain.settings.DefaultSplitPaneBackgroundOpacityPercent
import app.naviamp.domain.settings.WideNowPlayingLayout
import app.naviamp.ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

enum class NaviampPlayerPanelLayout { Adaptive, Standalone, Full }

internal fun supportsPlayerWorkspace(width: Float, height: Float): Boolean = width >= 900f && height >= 480f
internal fun readableSurfaceColor(
    colors: NaviampColors,
    opacity: Float = DefaultSplitPaneBackgroundOpacityPercent / 100f,
) = colors.background.copy(alpha = opacity.coerceIn(0f, 1f))

/** One bounded reading surface for every host and for either workspace pane. */
@Composable
internal fun NaviampReadableContent(
    colors: NaviampColors,
    keepDarkSurface: Boolean = false,
    surfaceOpacity: Float = DefaultSplitPaneBackgroundOpacityPercent / 100f,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val surface = if (keepDarkSurface || maxWidth >= 900.dp) {
            readableSurfaceColor(colors, surfaceOpacity)
        } else {
            androidx.compose.ui.graphics.Color.Transparent
        }
        Box(Modifier.widthIn(max = 1120.dp).fillMaxSize()
            .background(surface).padding(12.dp)) { content() }
    }
}

@Composable
internal fun NaviampPlayerWorkspace(
    wide: Boolean,
    layout: WideNowPlayingLayout,
    onLayoutChanged: (WideNowPlayingLayout) -> Unit,
    player: @Composable (NaviampPlayerPanelLayout) -> Unit,
    browser: @Composable () -> Unit,
) {
    if (!wide) {
        player(NaviampPlayerPanelLayout.Adaptive)
        return
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            val next = if (layout == WideNowPlayingLayout.Split) WideNowPlayingLayout.Full else WideNowPlayingLayout.Split
            TextButton(onClick = { onLayoutChanged(next) }, colors = ButtonDefaults.textButtonColors(
                contentColor = NaviampColors.Dark.primaryText,
                containerColor = NaviampColors.Dark.controlSurface,
            ), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text(stringResource(if (next == WideNowPlayingLayout.Full) Res.string.player_full_view else Res.string.player_split_view))
            }
        }
        if (layout == WideNowPlayingLayout.Full) {
            Box(Modifier.weight(1f).fillMaxWidth().testTag("full-player")) { player(NaviampPlayerPanelLayout.Full) }
        } else {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Box(Modifier.weight(1f).fillMaxHeight().testTag("docked-player")) { player(NaviampPlayerPanelLayout.Standalone) }
                Box(
                    Modifier.weight(2f).fillMaxHeight().padding(end = 12.dp).testTag("browser-pane"),
                ) { browser() }
            }
        }
    }
}
