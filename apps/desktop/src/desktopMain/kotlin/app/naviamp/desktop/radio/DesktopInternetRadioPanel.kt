package app.naviamp.desktop

import androidx.compose.runtime.Composable
import app.naviamp.domain.InternetRadioStation
import app.naviamp.ui.InternetRadioContent
import app.naviamp.ui.StationRowAction

@Composable
fun DesktopInternetRadioPanel(
    appColors: DesktopAppColors,
    stations: List<InternetRadioStation>,
    status: String?,
    onPlayStation: (InternetRadioStation) -> Unit,
    onSaveStation: (InternetRadioStation) -> Unit,
    onDeleteStation: (InternetRadioStation) -> Unit,
) {
    InternetRadioContent(
        colors = appColors,
        stations = stations,
        status = status,
        onStationAction = { request ->
            stations.firstOrNull { station -> station.id == request.station.id }?.let { station ->
                when (request.action) {
                    StationRowAction.Select -> onPlayStation(station)
                    StationRowAction.Edit -> Unit
                    StationRowAction.Delete -> onDeleteStation(station)
                }
            }
        },
        onSaveStation = onSaveStation,
    )
}
