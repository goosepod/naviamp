package app.naviamp.desktop

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.naviamp.ui.NaviampNowPlayingActions
import app.naviamp.ui.NaviampNowPlayingContent
import app.naviamp.ui.NaviampNowPlayingPresentationUi

@Composable
internal fun ColumnScope.DesktopPlayerRouteContent(
    appColors: DesktopAppColors,
    presentation: NaviampNowPlayingPresentationUi,
    actions: NaviampNowPlayingActions,
) {
    NaviampNowPlayingContent(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        presentation = presentation,
        colors = appColors,
        actions = actions,
    )
}
