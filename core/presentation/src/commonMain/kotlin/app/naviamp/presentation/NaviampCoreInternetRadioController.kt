package app.naviamp.presentation

import app.naviamp.domain.InternetRadioStation
import app.naviamp.ui.NaviampInternetRadioStationEditUi
import app.naviamp.ui.StationRowAction
import app.naviamp.ui.toInternetRadioStation
import app.naviamp.ui.toInternetRadioStationUi
import app.naviamp.ui.toSharedMediaItemUi

fun interface NaviampCoreInternetRadioPlaybackPort {
    suspend fun play(station: InternetRadioStation)
}

interface NaviampCoreInternetRadioRecentsPort {
    fun current(): List<InternetRadioStation>
    suspend fun record(station: InternetRadioStation): List<InternetRadioStation>
}

/** Owns Internet Radio browsing, mutations, selection, recents, and shared result state. */
class NaviampCoreInternetRadioController(
    private val stateStore: NaviampCoreStateStore,
    private val providerSource: NaviampCoreMediaProviderSource,
    private val playback: NaviampCoreInternetRadioPlaybackPort,
    private val recents: NaviampCoreInternetRadioRecentsPort,
    private val onPlaybackStarted: (InternetRadioStation) -> Unit = {},
) : NaviampCoreCommandController {
    private var generation = 0L
    private var stationsById = emptyMap<String, InternetRadioStation>()

    override fun dispatch(command: NaviampCoreCommand): NaviampCoreImmediateCommandResult = when (command) {
        NaviampCoreCommand.Radio.Refresh,
        is NaviampCoreCommand.Radio.StationAction,
        is NaviampCoreCommand.Radio.SaveStation,
        is NaviampCoreCommand.Home.SelectRecentRadio,
        is NaviampCoreCommand.Home.SelectInternetRadio,
        -> NaviampCoreImmediateCommandResult.Deferred
        else -> NaviampCoreImmediateCommandResult.Unhandled
    }

    override suspend fun execute(command: NaviampCoreCommand): NaviampCoreCommandResult? {
        when (command) {
            NaviampCoreCommand.Radio.Refresh -> refresh()
            is NaviampCoreCommand.Radio.SaveStation -> save(command.station)
            is NaviampCoreCommand.Radio.StationAction -> when (command.request.action) {
                StationRowAction.Select -> playResolved {
                    resolve(command.request.station.id, command.request.station.title)
                }
                StationRowAction.Delete -> deleteResolved {
                    resolve(command.request.station.id, command.request.station.title)
                }
                StationRowAction.Edit -> return null
            }
            is NaviampCoreCommand.Home.SelectRecentRadio ->
                playResolved { resolveRecent(command.item.id, command.item.title) }
            is NaviampCoreCommand.Home.SelectInternetRadio ->
                playResolved { resolve(command.item.id, command.item.title) }
            else -> return null
        }
        return NaviampCoreCommandResult.Completed
    }

    private suspend fun refresh(finalStatus: String? = null) {
        val requestGeneration = ++generation
        stateStore.updateShell { shell ->
            shell.copy(radio = shell.radio.copy(refreshing = true, status = "Loading internet radio..."))
        }
        val provider = providerSource.current()
        if (provider == null) {
            publishFailure("Connect to Navidrome to load internet radio.")
            return
        }
        runCatching { provider.internetRadioStations() }
            .onSuccess { stations ->
                if (requestGeneration != generation) return@onSuccess
                stationsById = stations.associateBy(InternetRadioStation::id)
                stateStore.updateShell { shell ->
                    shell.copy(
                        radio = shell.radio.copy(
                            stations = stations.map(InternetRadioStation::toInternetRadioStationUi),
                            refreshing = false,
                            status = finalStatus,
                        ),
                    )
                }
            }
            .onFailure { cause ->
                if (requestGeneration == generation) {
                    publishFailure(cause.message ?: "Could not load internet radio stations.")
                }
            }
    }

    suspend fun refreshAfterConnection() = refresh()

    private suspend fun save(edit: NaviampInternetRadioStationEditUi) {
        val provider = providerOrPublish() ?: return
        val station = runCatching { validated(edit.toInternetRadioStation()) }.getOrElse { cause ->
            publishStatus(cause.message ?: "Station details are invalid.")
            return
        }
        publishStatus("Saving ${station.name}...")
        runCatching {
            if (edit.id == null) {
                provider.createInternetRadioStation(station.name, station.streamUrl, station.homePageUrl)
            } else {
                provider.updateInternetRadioStation(station)
            }
        }.onSuccess {
            refresh("Saved ${station.name}.")
        }.onFailure { cause ->
            publishStatus(cause.message ?: "Could not save station.")
        }
    }

    private suspend fun delete(station: InternetRadioStation) {
        val provider = providerOrPublish() ?: return
        publishStatus("Deleting ${station.name}...")
        runCatching { provider.deleteInternetRadioStation(station.id) }
            .onSuccess { refresh("Deleted ${station.name}.") }
            .onFailure { cause -> publishStatus(cause.message ?: "Could not delete station.") }
    }

    private suspend fun deleteResolved(resolve: () -> InternetRadioStation) {
        val station = runCatching(resolve).getOrElse { cause ->
            publishStatus(cause.message ?: "Station not found.")
            return
        }
        delete(station)
    }

    private suspend fun play(station: InternetRadioStation) {
        publishStatus("Starting ${station.name}...")
        runCatching {
            onPlaybackStarted(station)
            playback.play(station)
            recents.record(station)
        }.onSuccess { updatedRecents ->
            stateStore.updateShell { shell ->
                shell.copy(
                    radio = shell.radio.copy(status = null),
                    home = shell.home.copy(
                        content = shell.home.content.copy(
                            recentRadioStreams = updatedRecents.map(InternetRadioStation::toSharedMediaItemUi),
                        ),
                    ),
                )
            }
        }.onFailure { cause -> publishStatus(cause.message ?: "Could not play station.") }
    }

    private suspend fun playResolved(resolve: () -> InternetRadioStation) {
        val station = runCatching(resolve).getOrElse { cause ->
            publishStatus(cause.message ?: "Station not found.")
            return
        }
        play(station)
    }

    private fun resolve(id: String, title: String): InternetRadioStation =
        stationsById[id] ?: throw IllegalStateException("Station $title is no longer available.")

    private fun resolveRecent(id: String, title: String): InternetRadioStation =
        recents.current().firstOrNull { it.id == id }
            ?: stationsById[id]
            ?: throw IllegalStateException("Recent station $title is no longer available.")

    private fun validated(station: InternetRadioStation): InternetRadioStation {
        require(station.name.isNotBlank()) { "Station name cannot be blank." }
        require(station.streamUrl.isNotBlank()) { "Station URL cannot be blank." }
        return station
    }

    private fun providerOrPublish() = providerSource.current().also { provider ->
        if (provider == null) publishStatus("Connect to Navidrome to manage internet radio.")
    }

    private fun publishFailure(status: String) {
        stateStore.updateShell { shell ->
            shell.copy(radio = shell.radio.copy(refreshing = false, status = status))
        }
    }

    private fun publishStatus(status: String?) {
        stateStore.updateShell { shell -> shell.copy(radio = shell.radio.copy(status = status)) }
    }
}
