package app.naviamp.domain.radio

import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.settings.SavedInternetRadioStation

const val MaxRecentInternetRadioStations = 12

fun recentInternetRadioStationsWith(
    recentStations: List<InternetRadioStation>,
    station: InternetRadioStation,
    limit: Int = MaxRecentInternetRadioStations,
): List<InternetRadioStation> =
    (listOf(station) + recentStations.filterNot { it.id == station.id }).take(limit)

fun recentSavedInternetRadioStationsWith(
    recentStations: List<SavedInternetRadioStation>,
    station: InternetRadioStation,
    limit: Int = MaxRecentInternetRadioStations,
): List<SavedInternetRadioStation> {
    val saved = SavedInternetRadioStation.fromStation(station)
    return (listOf(saved) + recentStations.filterNot { it.id == saved.id }).take(limit)
}
