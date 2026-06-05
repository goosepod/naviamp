package app.naviamp.desktop

import androidx.compose.runtime.Composable
import app.naviamp.domain.InternetRadioStation
import app.naviamp.ui.InternetRadioContent

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
        onStationSelected = onPlayStation,
        onSaveStation = onSaveStation,
        onDeleteStation = onDeleteStation,
    )
}
