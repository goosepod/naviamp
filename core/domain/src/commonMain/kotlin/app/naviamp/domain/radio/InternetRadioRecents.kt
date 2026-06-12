package app.naviamp.domain.radio

import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.settings.SavedInternetRadioStation

const val MaxRecentInternetRadioStations = 12

data class InternetRadioRecentStationPlan(
    val recentStations: List<InternetRadioStation>,
    val recentSavedStations: List<SavedInternetRadioStation>,
)

data class InternetRadioRecentStationApplier(
    val saveRecentStations: (List<SavedInternetRadioStation>) -> Unit = {},
    val setRecentStations: (List<InternetRadioStation>) -> Unit = {},
)

fun planRememberInternetRadioStation(
    station: InternetRadioStation,
    recentStations: List<InternetRadioStation>,
    recentSavedStations: List<SavedInternetRadioStation>,
): InternetRadioRecentStationPlan =
    InternetRadioRecentStationPlan(
        recentStations = recentInternetRadioStationsWith(recentStations, station),
        recentSavedStations = recentSavedInternetRadioStationsWith(recentSavedStations, station),
    )

fun applyRememberInternetRadioStation(
    plan: InternetRadioRecentStationPlan,
    applier: InternetRadioRecentStationApplier,
) {
    applier.saveRecentStations(plan.recentSavedStations)
    applier.setRecentStations(plan.recentStations)
}

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
