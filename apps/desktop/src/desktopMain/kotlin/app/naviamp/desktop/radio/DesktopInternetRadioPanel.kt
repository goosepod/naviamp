package app.naviamp.desktop

import androidx.compose.runtime.Composable
import app.naviamp.ui.InternetRadioContent
import app.naviamp.ui.NaviampInternetRadioActions
import app.naviamp.ui.NaviampInternetRadioScreenUi

@Composable
fun DesktopInternetRadioPanel(
    appColors: DesktopAppColors,
    screen: NaviampInternetRadioScreenUi,
    actions: NaviampInternetRadioActions,
) {
    InternetRadioContent(
        colors = appColors,
        screen = screen,
        onStationAction = actions.onStationAction,
        onSaveStation = actions.onSaveStation,
        headerActions = {
            DesktopPageOverflowMenu(
                appColors = appColors,
                onRefresh = actions.onRefresh,
            )
        },
    )
}
