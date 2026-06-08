package app.naviamp.domain.radio

import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.provider.MediaProvider

class InternetRadioStationManager(
    private val providerResponseService: ProviderResponseService,
) {
    suspend fun refreshStations(provider: MediaProvider): List<InternetRadioStation> =
        providerResponseService.internetRadioStations(provider)

    suspend fun saveStation(
        provider: MediaProvider,
        station: InternetRadioStation,
    ): List<InternetRadioStation> {
        if (station.isUnsavedInternetRadioStation()) {
            provider.createInternetRadioStation(
                name = station.name,
                streamUrl = station.streamUrl,
                homePageUrl = station.homePageUrl,
            )
        } else {
            provider.updateInternetRadioStation(station)
        }
        return reloadStationsAfterMutation(provider)
    }

    suspend fun deleteStation(
        provider: MediaProvider,
        station: InternetRadioStation,
    ): List<InternetRadioStation> {
        provider.deleteInternetRadioStation(station.id)
        return reloadStationsAfterMutation(provider)
    }

    private suspend fun reloadStationsAfterMutation(provider: MediaProvider): List<InternetRadioStation> {
        providerResponseService.invalidateInternetRadioStations(provider)
        return providerResponseService.internetRadioStations(provider)
    }
}

fun InternetRadioStation.isUnsavedInternetRadioStation(): Boolean =
    id == streamUrl

fun internetRadioRefreshLoadingStatus(): String =
    "Loading internet radio..."

fun internetRadioRefreshErrorStatus(): String =
    "Could not load internet radio stations."

fun internetRadioSaveLoadingStatus(station: InternetRadioStation): String =
    "Saving ${station.name}..."

fun internetRadioSaveErrorStatus(): String =
    "Could not save station."

fun internetRadioDeleteLoadingStatus(station: InternetRadioStation): String =
    "Deleting ${station.name}..."

fun internetRadioDeleteErrorStatus(): String =
    "Could not delete station."
