package app.naviamp.desktop

import androidx.compose.runtime.Composable
import app.naviamp.domain.InternetRadioStation
import app.naviamp.ui.InternetRadioContent
import app.naviamp.ui.StationRowActionRequest

@Composable
fun DesktopInternetRadioPanel(
    appColors: DesktopAppColors,
    stations: List<InternetRadioStation>,
    status: String?,
    onStationAction: (StationRowActionRequest) -> Unit,
    onSaveStation: (InternetRadioStation) -> Unit,
    onRefreshStations: () -> Unit,
) {
    InternetRadioContent(
        colors = appColors,
        stations = stations,
        status = status,
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
