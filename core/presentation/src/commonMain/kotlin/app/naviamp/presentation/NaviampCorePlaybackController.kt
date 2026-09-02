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
import app.naviamp.domain.playback.PlaybackTransitionMode
import app.naviamp.domain.playback.resolveAgainst
import app.naviamp.domain.playback.PlaybackQueueFinishedCommand
import app.naviamp.domain.playback.PlaybackSource
import app.naviamp.domain.playback.SleepTimerRequest
import app.naviamp.domain.playback.sleepTimerSelection
import app.naviamp.domain.playback.PlaybackQueueSelectionUpdate
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.PlaybackVisualizerFrame
import app.naviamp.domain.isInternetRadioTrack
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.settings.streamQualityForNetwork
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.settings.PlaybackSessionRestorePlan
import app.naviamp.domain.settings.PlaybackSessionSavePlan
import app.naviamp.domain.radio.internetRadioTrack
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.queue.groupAt
import app.naviamp.domain.queue.groupForTransition
import app.naviamp.domain.sonicautoplay.SonicAutoplayService
import app.naviamp.ui.NowPlayingPlaybackAction
import app.naviamp.ui.NowPlayingPlaybackActionRequest
import app.naviamp.ui.NowPlayingQueueAction
import app.naviamp.ui.NowPlayingQueueActionRequest
import app.naviamp.ui.NowPlayingSleepTimerAction
import app.naviamp.ui.NowPlayingSleepTimerActionRequest
import app.naviamp.ui.NaviampVisualizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
    private var sidecarLoadJob: Job? = null
    private var sonicAutoplayJob: Job? = null
    private val sonicAutoplay = SonicAutoplayService(providerSource::current)
    private var persistedQueue = PlaybackQueue()
    private var persistedStationId: String? = null
    private var sourceTransitionTargetId: String? = null
    private var connectHandoffAwaitingStart = false

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
        effects.setVisualizerFramesEnabled(display.visualizerVisible)
        presenter.publish(display)
    }

    fun currentDisplay(): NaviampCoreNowPlayingDisplayState = display

    /** Executes the remote-control subset against the same shared owners used by local UI. */
    internal fun executeConnectPlayback(command: app.naviamp.domain.connect.NaviampConnectCommand): Boolean =
        when (command) {
            app.naviamp.domain.connect.NaviampConnectPlay -> when {
                connectHandoffAwaitingStart -> startConnectHandoff()
                else -> when (playback.state.value.playbackState) {
                PlaybackState.Playing -> true
                PlaybackState.Paused -> commands.executePlayPause(
                    app.naviamp.domain.playback.PlaybackPlayPauseCommand.Resume,
                )
                else -> commands.playPause()
                }
            }
            app.naviamp.domain.connect.NaviampConnectPause -> when {
                connectHandoffAwaitingStart -> true
                else -> when (playback.state.value.playbackState) {
                PlaybackState.Paused -> true
                PlaybackState.Playing -> commands.executePlayPause(
                    app.naviamp.domain.playback.PlaybackPlayPauseCommand.Pause,
                )
                else -> false
                }
            }
            app.naviamp.domain.connect.NaviampConnectTogglePlayPause ->
                if (connectHandoffAwaitingStart) startConnectHandoff() else commands.playPause()
            app.naviamp.domain.connect.NaviampConnectPrevious ->
                navigate(queue.previousCommand(stateStore.state.value.shell.playback.settings.previousButtonBehavior)) !=
                    PlaybackQueueNavigationCommand.None
            app.naviamp.domain.connect.NaviampConnectNext ->
                navigate(queue.nextCommand()) != PlaybackQueueNavigationCommand.None
            app.naviamp.domain.connect.NaviampConnectStop -> {
                commands.stop()
                true
            }
            is app.naviamp.domain.connect.NaviampConnectSeek -> commands.seek(
                NaviampPlaybackSeekRequest(
                    positionSeconds = command.positionMillis / 1_000.0,
                    streamQuality = effects.playbackQuality
                        ?: stateStore.state.value.shell.playback.settings.streamQualityForNetwork(false),
                    playbackSource = effects.playbackSource,
                    issuedAtMillis = nowEpochMillis(),
                ),
            ) != null
            is app.naviamp.domain.connect.NaviampConnectSetRepeat -> {
                val requested = when (command.mode) {
                    app.naviamp.domain.connect.NaviampConnectRepeatMode.Off -> RepeatMode.Off
                    app.naviamp.domain.connect.NaviampConnectRepeatMode.All -> RepeatMode.Queue
                    app.naviamp.domain.connect.NaviampConnectRepeatMode.One -> RepeatMode.Track
                }
                if (playback.state.value.repeatMode != requested) {
                    playback.updateRepeatMode(requested)
                    effects.applyRepeatMode(requested)
                }
                true
            }
            is app.naviamp.domain.connect.NaviampConnectSetShuffle -> {
                val enabled = playback.state.value.shuffledUpNextSnapshot != null
                if (enabled != command.enabled) {
                    val update = queue.toggleUpcomingShuffle()
                    if (!update.changed) return false
                    effects.applyQueue(update.queue, clearPreparedNext = true)
                }
                true
            }
            else -> false
        }.also { presenter.publish(display) }

    internal fun selectConnectQueueIndex(index: Int): Boolean {
        if (index !in playback.state.value.queue.tracks.indices) return false
        navigate(PlaybackQueueNavigationCommand.JumpTo(index, moveSelectedToCurrent = true))
        presenter.publish(display)
        return true
    }

    internal fun moveConnectQueueIndex(fromIndex: Int, beforeIndex: Int?): Boolean {
        val current = playback.state.value.queue
        val upcoming = (current.currentIndex + 1)..current.tracks.lastIndex
        if (fromIndex !in upcoming || (beforeIndex != null && beforeIndex !in upcoming)) return false
        val destination = when {
            beforeIndex == null -> current.tracks.lastIndex
            fromIndex < beforeIndex -> beforeIndex - 1
            else -> beforeIndex
        }
        val update = mutations.moveUpcoming(fromIndex, destination)
        presenter.publish(display)
        return update.changed || fromIndex == destination
    }

    internal fun removeConnectQueueIndex(index: Int): Boolean {
        val current = playback.state.value.queue
        if (index <= current.currentIndex || index !in current.tracks.indices) return false
        val update = mutations.removeAt(index)
        presenter.publish(display)
        return update.changed
    }

    internal fun clearConnectUpNext(): Boolean {
        val update = queue.retainCurrentOnly()
        if (update.changed) effects.applyQueue(update.queue, update.clearPreparedNext)
        presenter.publish(display)
        return update.changed
    }

    internal suspend fun handoffConnectQueue(
        command: app.naviamp.domain.connect.NaviampConnectHandoffQueue,
    ): Boolean {
        if (command.queue.occurrences.isEmpty() || command.queue.currentIndex !in command.queue.occurrences.indices) {
            return false
        }
        providerSource.current() ?: return false
        val locallyKnownTracks = playback.state.value.queue.tracks.associateBy { it.id.value }
        val tracks = command.queue.occurrences.map { occurrence ->
            locallyKnownTracks[occurrence.mediaId] ?: app.naviamp.domain.Track(
                id = app.naviamp.domain.TrackId(occurrence.mediaId),
                title = occurrence.title,
                artistName = occurrence.artistName,
                albumTitle = occurrence.albumTitle,
                durationSeconds = occurrence.durationMillis?.div(1_000L)?.toInt(),
                coverArtId = occurrence.artworkId,
                audioInfo = null,
                replayGain = null,
                favoritedAtIso8601 = ConnectHandoffFavoriteMarker.takeIf { occurrence.favorite },
            )
        }
        val handedOffQueue = app.naviamp.domain.queue.PlaybackQueue(
            tracks = tracks,
            currentIndex = command.queue.currentIndex,
            playNextCount = command.queue.playNextCount,
            groups = command.queue.groups.map { group ->
                app.naviamp.domain.queue.PlaybackQueueGroup(
                    id = group.groupId,
                    target = app.naviamp.domain.playback.PlaybackProfileTarget(
                        type = group.targetType,
                        id = group.targetId,
                    ),
                    label = group.label.orEmpty(),
                    startIndex = group.startIndex,
                    endIndexExclusive = group.endIndexExclusive,
                    profile = group.playbackProfile,
                )
            },
        )
        val repeatMode = when (command.repeatMode) {
            app.naviamp.domain.connect.NaviampConnectRepeatMode.Off -> RepeatMode.Off
            app.naviamp.domain.connect.NaviampConnectRepeatMode.All -> RepeatMode.Queue
            app.naviamp.domain.connect.NaviampConnectRepeatMode.One -> RepeatMode.Track
        }
        val positionSeconds = command.positionMillis / 1_000.0
        val previous = playback.state.value
        val previousHandoffAwaitingStart = connectHandoffAwaitingStart
        // A handoff replaces the native playback session as well as Core's queue. Pausing the
        // previous stream leaves that stream alive, and some native engines then report the new
        // handoff as logically playing without ever activating its audio output.
        effects.stop()
        playback.replace(
            playback.state.value.copy(
                currentTrack = handedOffQueue.current,
                currentStation = null,
                queue = handedOffQueue,
                progress = app.naviamp.domain.playback.PlaybackProgress(
                    positionSeconds,
                    handedOffQueue.current?.durationSeconds?.toDouble(),
                ),
                playbackState = if (command.playing) PlaybackState.Playing else PlaybackState.Paused,
                repeatMode = repeatMode,
                shuffledUpNextSnapshot = handedOffQueue.upNext().takeIf { command.shuffled },
            ),
        )
        effects.restoreQueue(handedOffQueue, positionSeconds)
        effects.applyRepeatMode(repeatMode)
        connectHandoffAwaitingStart = true
        if (command.playing) {
            if (!startConnectHandoff()) {
                restorePlaybackAfterRejectedConnectHandoff(previous, previousHandoffAwaitingStart)
                presenter.publish(display)
                return false
            }
        }
        presenter.publish(display)
        return true
    }

    private fun restorePlaybackAfterRejectedConnectHandoff(
        previous: app.naviamp.app.NaviampLivePlaybackState,
        previousHandoffAwaitingStart: Boolean,
    ) {
        effects.stop()
        playback.replace(previous)
        effects.restoreQueue(previous.queue, previous.progress.positionSeconds)
        effects.applyRepeatMode(previous.repeatMode)
        connectHandoffAwaitingStart = previousHandoffAwaitingStart
        if (previous.playbackState == PlaybackState.Playing ||
            previous.playbackState == PlaybackState.Loading
        ) {
            effects.startOrRestore()
        }
    }

    private fun startConnectHandoff(): Boolean {
        if (!connectHandoffAwaitingStart) return false
        val started = effects.startOrRestore()
        if (started) connectHandoffAwaitingStart = false
        return started
    }

    internal fun connectLiveState(): app.naviamp.app.NaviampLivePlaybackState = playback.state.value

    fun diagnostics(): List<Pair<String, String>> =
        effects.diagnostics() + sessions.performanceDiagnostics()

    fun playbackProfileDiagnostics(): List<Pair<String, String>> {
        val liveQueue = playback.state.value.queue
        val global = stateStore.state.value.shell.playback.settings
        return playbackProfileDiagnosticRows(liveQueue, global)
    }

    fun attachNativePlayback() {
        effects.setVisualizerFramesEnabled(display.visualizerVisible)
        playback.observe { persistSession(force = false) }
        effects.attach(object : NaviampCorePlaybackObserver {
            override fun onStateChanged(state: PlaybackState) {
                if (state == PlaybackState.Playing) connectHandoffAwaitingStart = false
                val repeatedFinished = state == PlaybackState.Finished &&
                    playback.state.value.playbackState == PlaybackState.Finished
                if (state != PlaybackState.Finished) {
                    sonicAutoplayJob?.cancel()
                    sonicAutoplayJob = null
                }
                playback.updatePlaybackState(state)
                reportPlayback(state, playback.state.value.progress)
                persistSession(force = state == PlaybackState.Paused || state == PlaybackState.Stopped)
                if (state == PlaybackState.Playing) loadCurrentTrackSidecars()
                if (state == PlaybackState.Finished && !repeatedFinished) {
                    val finished = queue.finishCurrentTrack()
                    when (finished.command) {
                        PlaybackQueueFinishedCommand.ReplayCurrent -> effects.replayCurrent(0.0)
                        PlaybackQueueFinishedCommand.PlayNext -> {
                            playback.updateCurrentTrack(finished.queue.current)
                            // A gapless engine may remain Playing without publishing another state
                            // callback, so advance sidecars from the authoritative queue transition.
                            loadCurrentTrackSidecars()
                            effects.applyAutomaticNavigation(PlaybackQueueNavigationCommand.Next)
                        }
                        PlaybackQueueFinishedCommand.None -> startSonicAutoplayContinuation()
                    }
                }
                presenter.publish(display)
            }

            override fun onProgressChanged(progress: PlaybackProgress) {
                playback.updateProgress(progress)
                reportPlayback(playback.state.value.playbackState, progress)
                persistSession(force = false)
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

            override fun onSourceChanged(source: PlaybackSource, quality: StreamQuality?) {
                presenter.publish(display)
            }

            override fun onVisualizerFrameChanged(frame: PlaybackVisualizerFrame?) {
                presenter.updateVisualizerFrame(frame)
                presenter.publish(display)
            }
        })
    }

    fun resetAfterDatabaseClear() {
        sonicAutoplayJob?.cancel()
        sonicAutoplayJob = null
        sidecarLoadJob?.cancel()
        sidecarLoadJob = null
        sidecarTrackId = null
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

    /** Enforces single-source playback until deliberate multi-server playback is implemented. */
    fun resetForSourceChange(previousSourceId: String?, newSourceId: String) {
        sonicAutoplayJob?.cancel()
        sonicAutoplayJob = null
        sourceTransitionTargetId = newSourceId
        sidecarLoadJob?.cancel()
        sidecarLoadJob = null
        sidecarTrackId = null
        effects.stop()
        queue.clearQueue()
        playback.replace(
            app.naviamp.app.NaviampLivePlaybackState(
                playbackState = PlaybackState.Stopped,
            ),
        )
        previousSourceId?.let(sessions::clear)
        persistedQueue = PlaybackQueue()
        persistedStationId = null
        display = NaviampCoreNowPlayingDisplayState()
        presenter.publish(display)
    }

    suspend fun restoreSession(sourceId: String): Boolean {
        when (val restored = sessions.restorePlan(sourceId)) {
            PlaybackSessionRestorePlan.None -> {
                if (sourceTransitionTargetId == sourceId) sourceTransitionTargetId = null
                presenter.publish(display)
                return false
            }
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
                // Restored metadata and transport state must be usable immediately. Waveform,
                // lyrics, and tag loading can be expensive for long tracks, so resume that work
                // in the controller's cancellable background sidecar job.
                loadCurrentTrackSidecars()
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
        if (sourceTransitionTargetId == sourceId) sourceTransitionTargetId = null
        presenter.publish(display)
        return true
    }

    private fun persistSession(force: Boolean) {
        if (sourceTransitionTargetId != null) return
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
        sidecarLoadJob?.cancel()
        sidecarLoadJob = scope.launch {
            loadTrackSidecars(track)
            if (playback.state.value.currentTrack?.id == track.id) presenter.publish(display)
        }
    }

    private fun startSonicAutoplayContinuation() {
        val expectedState = playback.state.value
        val expectedQueue = expectedState.queue
        val expectedProvider = providerSource.current()
        val expectedSourceId = stateStore.state.value.shell.connectionSettings.currentSourceId
        if (!stateStore.state.value.shell.playback.settings.sonicAutoplayEnabled) return
        if (expectedProvider?.capabilities?.supportsSonicSimilarity != true) return

        sonicAutoplayJob?.cancel()
        sonicAutoplayJob = scope.launch {
            val tracks = sonicAutoplay.continuationTracks(expectedQueue)
            val currentState = playback.state.value
            val currentShell = stateStore.state.value.shell
            val requestIsCurrent =
                currentState.playbackState == PlaybackState.Finished &&
                    currentState.queue == expectedQueue &&
                    currentShell.connectionSettings.currentSourceId == expectedSourceId &&
                    currentShell.playback.settings.sonicAutoplayEnabled
            if (!requestIsCurrent) return@launch

            val update = queue.appendSonicContinuationTracks(tracks)
            if (!update.tracksChanged) return@launch
            effects.applyQueue(update.queue, clearPreparedNext = true)
            navigate(queue.nextCommand(), automatic = true)
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
                        streamQuality = effects.playbackQuality
                            ?: playbackSettings.streamQualityForNetwork(false),
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
            NowPlayingQueueAction.MoveQueueItem -> {
                val fromIndex = request.queueIndex
                val toIndex = request.destinationQueueIndex
                if (fromIndex == null || toIndex == null) publishStatus("Queue positions are missing.")
                else mutations.moveUpcoming(fromIndex, toIndex)
            }
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

internal fun playbackProfileDiagnosticRows(
    queue: PlaybackQueue,
    global: PlaybackSettings,
): List<Pair<String, String>> {
    val group = queue.groupAt()
    val resolvedTrack = group?.profile?.resolveAgainst(global) ?: global
    val nextIndex = queue.nextIndex(repeatTrack = false)
    val transitionGroup = nextIndex?.let { queue.groupForTransition(toIndex = it) }
    val resolvedTransition = transitionGroup?.profile?.resolveAgainst(global) ?: global
    val transitionSource = when {
        nextIndex == null -> "No next track"
        transitionGroup != null && !transitionGroup.profile.isInherited -> "Custom group profile"
        transitionGroup != null -> "Group inherits global"
        group != null -> "Global at group boundary"
        else -> "Global player settings"
    }
    return listOf(
        "Custom profile active" to (group?.profile?.isInherited == false).toString(),
        "Queue group" to (group?.label?.ifBlank { group.target.type.name } ?: "None"),
        "Profile target" to group?.let { "${it.target.type.name}: ${it.target.id}" }.orEmpty().ifBlank { "None" },
        "Transition override" to (group?.profile?.transitionMode?.name ?: "None"),
        "ReplayGain override" to (group?.profile?.replayGainMode?.name ?: "None"),
        "Resolved ReplayGain" to resolvedTrack.replayGainMode.displayName,
        "Next transition source" to transitionSource,
        "Next transition" to when {
            nextIndex == null -> "None"
            resolvedTransition.crossfadeDurationSeconds > 0 -> "Crossfade ${resolvedTransition.crossfadeDurationSeconds}s"
            resolvedTransition.gaplessEnabled -> PlaybackTransitionMode.Gapless.name
            else -> "Track break"
        },
    )
}

private const val PlaybackSessionSaveIntervalMillis = 5_000L
private const val ConnectHandoffFavoriteMarker = "1970-01-01T00:00:00Z"
