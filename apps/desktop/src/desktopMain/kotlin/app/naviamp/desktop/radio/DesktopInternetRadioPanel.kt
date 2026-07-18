package app.naviamp.desktop

import androidx.compose.runtime.Composable
import app.naviamp.ui.InternetRadioContent
import app.naviamp.ui.NaviampInternetRadioScreenUi
import app.naviamp.ui.NaviampInternetRadioStationEditUi
import app.naviamp.ui.StationRowActionRequest

@Composable
fun DesktopInternetRadioPanel(
    appColors: DesktopAppColors,
    screen: NaviampInternetRadioScreenUi,
    onStationAction: (StationRowActionRequest) -> Unit,
    onSaveStation: (NaviampInternetRadioStationEditUi) -> Unit,
    onRefreshStations: () -> Unit,
) {
    InternetRadioContent(
        colors = appColors,
        screen = screen,
        onStationAction = onStationAction,
        onSaveStation = onSaveStation,
        headerActions = {
            DesktopPageOverflowMenu(
                appColors = appColors,
                onRefresh = onRefreshStations,
            )
        },
    )
}
