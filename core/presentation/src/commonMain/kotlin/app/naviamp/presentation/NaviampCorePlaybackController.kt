package app.naviamp.presentation

import app.naviamp.app.NaviampLivePlaybackController
import app.naviamp.app.NaviampPlaybackCommandController
import app.naviamp.app.NaviampNowPlayingReportRequest
import app.naviamp.app.NaviampPlaybackReportingController
import app.naviamp.app.NaviampPlaybackStateReportRequest
import app.naviamp.app.NaviampPlaybackSessionController
import app.naviamp.app.NaviampPlaybackSessionSaveRequest
import app.naviamp.app.NaviampPlaybackQueueCommandController
import app.naviamp.app.NaviampPlaybackQueueCoordinator
import app.naviamp.app.NaviampPlaybackRepeatCommandController
import app.naviamp.app.NaviampPlaybackSeekRequest
import app.naviamp.domain.playback.PlaybackQueueNavigationCommand
import app.naviamp.domain.playback.SleepTimerRequest
import app.naviamp.domain.playback.sleepTimerSelection
import app.naviamp.domain.playback.PlaybackQueueSelectionUpdate
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.PlaybackVisualizerFrame
import app.naviamp.domain.isInternetRadioTrack
import app.naviamp.domain.settings.streamQualityForNetwork
import app.naviamp.domain.settings.PlaybackSessionRestorePlan
import app.naviamp.domain.settings.PlaybackSessionSavePlan
import app.naviamp.domain.radio.internetRadioTrack
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.ui.NowPlayingPlaybackAction
import app.naviamp.ui.NowPlayingPlaybackActionRequest
import app.naviamp.ui.NowPlayingQueueAction
import app.naviamp.ui.NowPlayingQueueActionRequest
import app.naviamp.ui.NowPlayingSleepTimerAction
import app.naviamp.ui.NowPlayingSleepTimerActionRequest
import app.naviamp.ui.NaviampVisualizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Owns Now Playing transport, queue commands, volume, repeat/shuffle, and sleep-timer policy. */
class NaviampCorePlaybackController(
    private val scope: CoroutineScope,
    private val stateStore: NaviampCoreStateStore,
    private val providerSource: NaviampCoreMediaProviderSource,
    private val playback: NaviampLivePlaybackController,
    private val queue: NaviampPlaybackQueueCoordinator,
    private val effects: NaviampCorePlaybackEffectPort,
    private val settings: NaviampCorePlaybackSettingsPort,
    private val sidecars: NaviampCoreNowPlayingSidecarPort,
    private val sessions: NaviampPlaybackSessionController,
    private val presenter: NaviampCoreNowPlayingPresenter,
    private val nowEpochMillis: () -> Long,
) : NaviampCoreCommandController {
    private var display = NaviampCoreNowPlayingDisplayState()
    private val commands = NaviampPlaybackCommandController(effects, playback)
    private val mutations = NaviampPlaybackQueueCommandController(queue) { update ->
        effects.applyQueue(update.queue, update.clearPreparedNext)
    }
    private val repeat = NaviampPlaybackRepeatCommandController(queue, effects::applyRepeatMode)
    private val reporting = NaviampPlaybackReportingController()
    private var reportingSessionId = 0L
    private var reportingTrackId: app.naviamp.domain.TrackId? = null
    private var reportedNowPlayingSessionId = -1L
    private var sidecarTrackId: app.naviamp.domain.TrackId? = null
    private var persistedQueue = PlaybackQueue()
    private var persistedStationId: String? = null

    override fun dispatch(command: NaviampCoreCommand): NaviampCoreImmediateCommandResult = when (command) {
        is NaviampCoreCommand.NowPlaying.Playback,
        is NaviampCoreCommand.NowPlaying.Queue,
        is NaviampCoreCommand.NowPlaying.SleepTimer,
        -> NaviampCoreImmediateCommandResult.Deferred
        else -> NaviampCoreImmediateCommandResult.Unhandled
    }

    override suspend fun execute(command: NaviampCoreCommand): NaviampCoreCommandResult? {
        when (command) {
            is NaviampCoreCommand.NowPlaying.Playback -> playback(command.request)
            is NaviampCoreCommand.NowPlaying.Queue -> queue(command.request)
            is NaviampCoreCommand.NowPlaying.SleepTimer -> sleepTimer(command.request)
            else -> return null
        }
        presenter.publish(display)
        return NaviampCoreCommandResult.Completed
    }

    fun updateLiveState(
        transform: (app.naviamp.app.NaviampLivePlaybackState) -> app.naviamp.app.NaviampLivePlaybackState,
    ) {
        playback.replace(transform(playback.state.value))
        presenter.publish(display)
    }

    fun updateDisplay(transform: (NaviampCoreNowPlayingDisplayState) -> NaviampCoreNowPlayingDisplayState) {
        display = transform(display)
        presenter.publish(display)
    }

    fun currentDisplay(): NaviampCoreNowPlayingDisplayState = display

    fun diagnostics(): List<Pair<String, String>> = effects.diagnostics()

    fun attachNativePlayback() {
        playback.observe { persistSession(force = false) }
        effects.attach(object : NaviampCorePlaybackObserver {
            override fun onStateChanged(state: PlaybackState) {
                val repeatedFinished = state == PlaybackState.Finished &&
                    playback.state.value.playbackState == PlaybackState.Finished
                playback.updatePlaybackState(state)
                reportPlayback(state, playback.state.value.progress)
                persistSession(force = state == PlaybackState.Paused || state == PlaybackState.Stopped)
                if (state == PlaybackState.Playing) loadCurrentTrackSidecars()
                if (state == PlaybackState.Finished && !repeatedFinished) {
                    navigate(queue.nextCommand(), automatic = true)
                }
                presenter.publish(display)
            }

            override fun onProgressChanged(progress: PlaybackProgress) {
                playback.updateProgress(progress)
                reportPlayback(playback.state.value.playbackState, progress)
                persistSession(force = false)
                presenter.publish(display)
            }

            override fun onMetadataChanged(metadata: PlaybackStreamMetadata) {
                presenter.updateStreamMetadata(metadata)
                presenter.publish(display)
                playback.state.value.currentStation?.let { station ->
                    scope.launch {
                        sidecars.loadInternetRadioArtwork(station, metadata)
                        presenter.publish(display)
                    }
                }
            }

            override fun onVisualizerFrameChanged(frame: PlaybackVisualizerFrame?) {
                presenter.updateVisualizerFrame(frame)
                presenter.publish(display)
            }
        })
    }

    fun resetAfterDatabaseClear() {
        val sourceId = stateStore.state.value.shell.connectionSettings.currentSourceId
        effects.stop()
        queue.clearQueue()
        playback.replace(
            app.naviamp.app.NaviampLivePlaybackState(
                playbackState = PlaybackState.Stopped,
            ),
        )
        sessions.clear(sourceId)
        persistedQueue = PlaybackQueue()
        persistedStationId = null
        display = NaviampCoreNowPlayingDisplayState()
        presenter.publish(display)
    }

    suspend fun restoreSession(sourceId: String): Boolean {
        when (val restored = sessions.restorePlan(sourceId)) {
            PlaybackSessionRestorePlan.None -> return false
            is PlaybackSessionRestorePlan.TrackSession -> {
                queue.restoreQueue(restored.playbackQueue)
                playback.replace(
                    playback.state.value.copy(
                        currentTrack = restored.currentTrack,
                        currentStation = null,
                        queue = restored.playbackQueue,
                        progress = restored.playbackProgress,
                        playbackState = PlaybackState.Idle,
                    ),
                )
                effects.restoreQueue(restored.playbackQueue, restored.restoredStartPositionSeconds)
                persistedQueue = restored.playbackQueue
                persistedStationId = null
                loadTrackSidecars(restored.currentTrack)
                if (stateStore.state.value.shell.general.interfaceSettings.startPlayingOnLaunch) {
                    effects.startOrRestore()
                }
                publishStatus(restored.status)
            }
            is PlaybackSessionRestorePlan.InternetRadio -> {
                val track = restored.currentTrack ?: internetRadioTrack(restored.station)
                val restoredQueue = PlaybackQueue(listOf(track), 0)
                queue.restoreQueue(restoredQueue)
                playback.replace(
                    playback.state.value.copy(
                        currentTrack = track,
                        currentStation = restored.station,
                        queue = restoredQueue,
                        progress = restored.playbackProgress,
                        playbackState = PlaybackState.Idle,
                    ),
                )
                effects.restoreInternetRadio(restored.station)
                persistedQueue = restoredQueue
                persistedStationId = restored.station.id
                if (stateStore.state.value.shell.general.interfaceSettings.startPlayingOnLaunch) {
                    effects.startOrRestore()
                }
                publishStatus(restored.status)
            }
        }
        presenter.publish(display)
        return true
    }

    private fun persistSession(force: Boolean) {
        val live = playback.state.value
        val structuralChange = live.queue != persistedQueue || live.currentStation?.id != persistedStationId
        val plan = runCatching {
            sessions.planAndSaveThrottled(
                request = NaviampPlaybackSessionSaveRequest(
                    sourceId = stateStore.state.value.shell.connectionSettings.currentSourceId,
                    station = live.currentStation,
                    currentTrack = live.currentTrack,
                    playbackQueue = live.queue,
                    progressPositionSeconds = live.progress.positionSeconds,
                ),
                force = force || structuralChange,
                nowMillis = nowEpochMillis(),
                saveIntervalMillis = PlaybackSessionSaveIntervalMillis,
            )
        }.getOrNull()
        if (plan is PlaybackSessionSavePlan.Save) {
            persistedQueue = live.queue
            persistedStationId = live.currentStation?.id
        }
    }

    private fun loadCurrentTrackSidecars() {
        val track = playback.state.value.currentTrack ?: return
        if (track.id == sidecarTrackId) return
        sidecarTrackId = track.id
        scope.launch {
            loadTrackSidecars(track)
            if (playback.state.value.currentTrack?.id == track.id) presenter.publish(display)
        }
    }

    private suspend fun loadTrackSidecars(track: app.naviamp.domain.Track) {
        sidecars.loadForTrack(track)
        val lyricsNeeded = display.lyricsVisible ||
            stateStore.state.value.shell.shellChrome.selectedVisualizer == NaviampVisualizer.LyricMirrorTunnel
        if (lyricsNeeded && playback.state.value.currentTrack?.id == track.id) {
            sidecars.loadLyrics(track)
        }
    }

    private fun reportPlayback(state: PlaybackState, progress: PlaybackProgress) {
        val track = playback.state.value.currentTrack ?: return
        val provider = providerSource.current() ?: return
        if (track.id != reportingTrackId) {
            reportingTrackId = track.id
            reportingSessionId += 1
        }
        if (reportedNowPlayingSessionId != reportingSessionId) {
            reporting.nowPlayingReport(
                NaviampNowPlayingReportRequest(
                    trackId = track.id,
                    isInternetRadioTrack = track.isInternetRadioTrack(),
                    supportsPlayReporting = provider.capabilities.supportsPlayReporting,
                ),
            )?.let { report ->
                reportedNowPlayingSessionId = reportingSessionId
                scope.launch { runCatching { provider.reportNowPlaying(report.trackId) } }
            }
        }
        reporting.stateReport(
            NaviampPlaybackStateReportRequest(
                sessionId = reportingSessionId,
                trackId = track.id,
                isInternetRadioTrack = track.isInternetRadioTrack(),
                supportsPlayReporting = provider.capabilities.supportsPlayReporting,
                playbackState = state,
                progress = progress,
                nowEpochMillis = nowEpochMillis(),
            ),
        )?.let { report ->
            scope.launch {
                runCatching {
                    provider.reportPlaybackState(report.trackId, report.state, report.positionSeconds)
                }
            }
        }
    }

    private fun playback(request: NowPlayingPlaybackActionRequest) {
        val playbackSettings = stateStore.state.value.shell.playback.settings
        when (request.action) {
            NowPlayingPlaybackAction.Stop -> commands.stop()
            NowPlayingPlaybackAction.Pause,
            NowPlayingPlaybackAction.Resume,
            NowPlayingPlaybackAction.PlayCurrent,
            -> if (!commands.playPause()) publishStatus("Nothing is available to play.")
            NowPlayingPlaybackAction.Seek -> request.seekSeconds?.let { seconds ->
                commands.seek(
                    NaviampPlaybackSeekRequest(
                        positionSeconds = seconds,
                        streamQuality = playbackSettings.streamQualityForNetwork(false),
                        playbackSource = effects.playbackSource,
                        issuedAtMillis = nowEpochMillis(),
                    ),
                ) ?: publishStatus("This stream cannot be seeked.")
            } ?: publishStatus("Seek position is missing.")
            NowPlayingPlaybackAction.Previous ->
                navigate(queue.previousCommand(playbackSettings.previousButtonBehavior))
                    .publishIfUnavailable("No previous track is available.")
            NowPlayingPlaybackAction.Next ->
                navigate(queue.nextCommand()).publishIfUnavailable("No next track is available.")
            NowPlayingPlaybackAction.ToggleShuffle -> {
                val update = queue.toggleUpcomingShuffle()
                if (update.changed) effects.applyQueue(update.queue, clearPreparedNext = true)
                else publishStatus("There are not enough upcoming tracks to shuffle.")
            }
            NowPlayingPlaybackAction.CycleRepeatMode -> repeat.cycle()
            NowPlayingPlaybackAction.ChangeVolume -> {
                val requested = request.volumePercent
                if (requested == null) {
                    publishStatus("Volume value is missing.")
                } else {
                    val softwareVolumeEnabled = effects.capabilities.supportsSoftwareVolume &&
                        stateStore.state.value.shell.capabilities.softwareVolumeControl
                    val command = commands.changeVolume(requested, softwareVolumeEnabled)
                    val updated = playbackSettings.copy(volumePercent = command.volumePercent)
                    settings.apply(updated, redownload = false)
                    stateStore.updateShell { shell -> shell.copy(playback = shell.playback.copy(settings = updated)) }
                }
            }
        }
    }

    private suspend fun queue(request: NowPlayingQueueActionRequest) {
        when (request.action) {
            NowPlayingQueueAction.SaveQueueAsPlaylist -> saveQueue(request.playlistName)
            NowPlayingQueueAction.MoveToNext -> request.queueIndex?.let(mutations::moveToNext)
                ?: publishStatus("Queue position is missing.")
            NowPlayingQueueAction.RemoveFromQueue -> request.queueIndex?.let(mutations::removeAt)
                ?: publishStatus("Queue position is missing.")
            NowPlayingQueueAction.EmptyQueue -> {
                val update = queue.retainCurrentOnly()
                if (update.changed) {
                    effects.applyQueue(update.queue, update.clearPreparedNext)
                }
            }
        }
    }

    private suspend fun saveQueue(requestedName: String?) {
        val provider = providerSource.current()
        if (provider == null) {
            publishPlaylistStatus("Connect to Navidrome to save the queue.")
            return
        }
        val name = requestedName?.trim().orEmpty()
        val tracks = playback.state.value.queue.tracks
        when {
            name.isEmpty() -> publishPlaylistStatus("Playlist name cannot be blank.")
            tracks.isEmpty() -> publishPlaylistStatus("The queue is empty.")
            else -> runCatching { provider.createPlaylist(name, tracks.map { it.id }) }
                .onSuccess { publishPlaylistStatus("Saved $name.") }
                .onFailure { publishPlaylistStatus(it.message ?: "Could not save queue.") }
        }
    }

    private fun sleepTimer(request: NowPlayingSleepTimerActionRequest) {
        when (request.action) {
            NowPlayingSleepTimerAction.Select -> {
                val selectionRequest = request.request
                if (selectionRequest == null) {
                    publishStatus("Sleep timer selection is missing.")
                    return
                }
                display = display.withSleepTimer(selectionRequest)
                publishStatus(display.sleepTimer?.let { app.naviamp.domain.playback.sleepTimerDisplayLabel(it, display.sleepTimerNowEpochMillis) }
                    ?: "Sleep timer")
            }
            NowPlayingSleepTimerAction.Cancel -> {
                display = display.copy(sleepTimer = null, sleepTimerNowEpochMillis = nowEpochMillis())
                publishStatus("Sleep timer canceled.")
            }
        }
    }

    fun expireSleepTimer() {
        commands.stop()
        playback.updatePlaybackState(PlaybackState.Stopped)
        display = display.copy(sleepTimer = null, sleepTimerNowEpochMillis = nowEpochMillis())
        publishStatus("Sleep timer stopped playback.")
        presenter.publish(display)
    }

    fun tickSleepTimer(nowMillis: Long) {
        display = display.copy(sleepTimerNowEpochMillis = nowMillis)
        presenter.publish(display)
    }

    private fun NaviampCoreNowPlayingDisplayState.withSleepTimer(
        request: SleepTimerRequest,
    ): NaviampCoreNowPlayingDisplayState {
        val live = playback.state.value
        val selection = sleepTimerSelection(
            request = request,
            nowEpochMillis = nowEpochMillis(),
            nowPlaying = live.currentTrack ?: live.queue.current,
            playbackQueue = live.queue,
            playbackProgress = live.progress,
            playbackState = live.playbackState,
        )
        return copy(sleepTimer = selection.timer, sleepTimerNowEpochMillis = selection.nowEpochMillis)
    }

    private fun PlaybackQueueNavigationCommand.publishIfUnavailable(message: String) {
        if (this == PlaybackQueueNavigationCommand.None) publishStatus(message)
    }

    private fun navigate(
        command: PlaybackQueueNavigationCommand,
        automatic: Boolean = false,
    ): PlaybackQueueNavigationCommand {
        val selection = when (command) {
            PlaybackQueueNavigationCommand.Previous -> queue.selectPrevious()
            PlaybackQueueNavigationCommand.Next -> queue.selectNext()
            is PlaybackQueueNavigationCommand.JumpTo ->
                queue.selectIndex(command.index, command.moveSelectedToCurrent)
            PlaybackQueueNavigationCommand.RestartCurrent,
            PlaybackQueueNavigationCommand.None,
            -> null
        }
        selection?.applyToLivePlayback()
        if (command != PlaybackQueueNavigationCommand.None) {
            if (automatic) effects.applyAutomaticNavigation(command) else effects.applyNavigation(command)
        }
        return command
    }

    private fun PlaybackQueueSelectionUpdate.applyToLivePlayback() {
        if (!changed) return
        playback.updateCurrentTrack(queue.current)
    }

    private fun publishPlaylistStatus(message: String) {
        display = display.copy(playlistActionStatus = message)
        publishStatus(message)
    }

    private fun publishStatus(message: String) {
        stateStore.update { state -> state.copy(overlays = state.overlays.copy(status = message)) }
    }
}

private const val PlaybackSessionSaveIntervalMillis = 5_000L
