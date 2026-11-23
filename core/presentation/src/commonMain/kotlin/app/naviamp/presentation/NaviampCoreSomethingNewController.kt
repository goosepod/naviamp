package app.naviamp.presentation

import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.home.HomeStationSomethingNew
import app.naviamp.domain.radio.SomethingNewPick
import app.naviamp.domain.radio.pickSomethingNew
import app.naviamp.ui.NaviampSomethingNewNotice

/** Owns Play something new for Home, Now Playing, and every host that issues the shared commands. */
class NaviampCoreSomethingNewController(
    private val stateStore: NaviampCoreStateStore,
    private val providerSource: NaviampCoreMediaProviderSource,
    private val downloadedTracks: suspend (sourceId: String) -> List<Track>,
    private val pendingListens: (sourceId: String) -> Set<TrackId>,
    private val currentTrack: () -> Track?,
    private val radio: NaviampCoreTrackRadioTransactions,
    private val playTrack: (Track) -> Unit,
    /** Issues a resolved command through the full (Connect-aware) handler; local when absent. */
    private val issueResolvedCommand: (suspend (NaviampCoreCommand) -> Unit)? = null,
) : NaviampCoreCommandController {
    private var pickGeneration = 0L

    override fun dispatch(command: NaviampCoreCommand): NaviampCoreImmediateCommandResult = when (command) {
        NaviampCoreCommand.Home.PlaySomethingNew,
        is NaviampCoreCommand.Home.StartSomethingNewRadio,
        -> NaviampCoreImmediateCommandResult.Deferred
        is NaviampCoreCommand.Home.SelectStation ->
            if (command.station.id == HomeStationSomethingNew) NaviampCoreImmediateCommandResult.Deferred
            else NaviampCoreImmediateCommandResult.Unhandled
        NaviampCoreCommand.Home.DismissSomethingNewNotice -> {
            publish(null)
            NaviampCoreImmediateCommandResult.Handled()
        }
        else -> NaviampCoreImmediateCommandResult.Unhandled
    }

    override suspend fun execute(command: NaviampCoreCommand): NaviampCoreCommandResult? {
        when (command) {
            NaviampCoreCommand.Home.PlaySomethingNew, is NaviampCoreCommand.Home.SelectStation -> playSomethingNew()
            is NaviampCoreCommand.Home.StartSomethingNewRadio ->
                if (command.startRadio) radio.startTrackRadio(command.seed) else playTrack(command.seed)
            else -> return null
        }
        return NaviampCoreCommandResult.Completed
    }

    private suspend fun playSomethingNew() {
        val generation = ++pickGeneration
        publish(null)
        val started = stateStore.state.value
        val sourceId = started.shell.connectionSettings.currentSourceId
        val provider = providerSource.current()
        if (sourceId == null || provider == null) return publish(NaviampSomethingNewNotice.Failed)
        // Starting anything else, switching source, or asking again abandons this pick.
        fun isCurrent() = generation == pickGeneration && providerSource.isCurrent(provider) &&
            stateStore.state.value.let {
                it.shell.connectionSettings.currentSourceId == sourceId &&
                    it.sourceGeneration == started.sourceGeneration &&
                    it.playbackIntentGeneration == started.playbackIntentGeneration &&
                    it.playbackSelectionGeneration == started.playbackSelectionGeneration
            }
        val pick = pickSomethingNew(
            playCountsAvailable = provider.capabilities.supportsPlayReporting,
            offline = started.shell.connectionSettings.connection.offline,
            randomTracks = { limit -> provider.randomSongs(limit = limit) },
            downloadedTracks = { downloadedTracks(sourceId) },
            inSelectedLibraries = provider::tracksInSelectedLibraries,
            pendingListens = { pendingListens(sourceId) },
            excluded = setOfNotNull(currentTrack()?.id),
            isCurrent = ::isCurrent,
        )
        if (pick == SomethingNewPick.Superseded || !isCurrent()) return
        when (pick) {
            is SomethingNewPick.Found -> {
                val start = NaviampCoreCommand.Home.StartSomethingNewRadio(pick.track, startRadio = !pick.fromDownloads)
                val issue = issueResolvedCommand
                if (issue != null) issue(start) else execute(start)
                if (pick.fromDownloads) publish(NaviampSomethingNewNotice.PlayingFromDownloads)
            }
            is SomethingNewPick.NothingUnplayed -> publish(
                if (pick.offline) NaviampSomethingNewNotice.NothingUnplayedOffline else NaviampSomethingNewNotice.NothingUnplayed,
            )
            SomethingNewPick.NothingDownloaded -> publish(NaviampSomethingNewNotice.NothingDownloaded)
            SomethingNewPick.PlayCountsUnavailable -> publish(NaviampSomethingNewNotice.PlayCountsUnavailable)
            is SomethingNewPick.Failed -> publish(NaviampSomethingNewNotice.Failed)
            SomethingNewPick.Superseded -> Unit
        }
    }

    private fun publish(notice: NaviampSomethingNewNotice?) {
        stateStore.update { it.copy(overlays = it.overlays.copy(somethingNewNotice = notice)) }
    }
}
