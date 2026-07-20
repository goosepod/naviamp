package app.naviamp.desktop

import app.naviamp.desktop.playback.DesktopPlaylistEngine
import app.naviamp.desktop.settings.PlaybackSettings
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.app.NaviampPlaybackSessionController
import app.naviamp.app.NaviampPlaybackSessionSaveRequest
import app.naviamp.app.NaviampPlaybackQueueCoordinator
import app.naviamp.app.NaviampPlaybackQueueCommandController
import app.naviamp.app.NaviampPlaybackQueueMutationExecution
import app.naviamp.app.NaviampPlaybackNavigationCommandController
import app.naviamp.app.NaviampPlaybackNavigationExecution
import app.naviamp.app.NaviampPlaybackRepeatCommandController
import app.naviamp.app.NaviampPlaybackRepeatModeExecution
import app.naviamp.app.NaviampPlaybackCommandController
import app.naviamp.app.NaviampPlaybackExecution
import app.naviamp.app.NaviampPlaybackSeekRequest
import app.naviamp.app.NaviampPlaybackReportingController
import app.naviamp.app.NaviampPlaybackStateReportRequest
import app.naviamp.app.NaviampProviderActionController
import app.naviamp.app.NaviampLivePlaybackController
import app.naviamp.domain.isInternetRadioTrack
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackVolumeCommand
import app.naviamp.domain.playback.canReportPlaybackTrack
import app.naviamp.domain.playback.PlaybackQueueNavigationCommand
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.settings.UpNextSelectionBehavior
import app.naviamp.domain.settings.playbackSessionFromQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun handleDesktopQueueIndexSelected(
    playbackController: DesktopPlaybackController,
    queueIndex: Int,
    upNextSelectionBehavior: UpNextSelectionBehavior,
) {
    playbackController.handleQueueIndexSelected(
        index = queueIndex,
        moveSelectedToCurrent = upNextSelectionBehavior == UpNextSelectionBehavior.MoveSelectedToCurrent,
    )
}

class DesktopPlaybackController(
    private val scope: CoroutineScope,
    private val playbackSessions: NaviampPlaybackSessionController,
    private val livePlayback: NaviampLivePlaybackController,
    private val queueCoordinator: NaviampPlaybackQueueCoordinator,
    private val playbackEngine: PlaybackEngine,
    private val playlistEngine: DesktopPlaylistEngine,
    private val provider: () -> MediaProvider?,
    private val sourceId: () -> String?,
    private val providerActions: NaviampProviderActionController,
    private val playbackSettings: () -> PlaybackSettings,
    private val playbackQueue: () -> PlaybackQueue,
    private val playbackProgress: () -> PlaybackProgress,
    private val setPlaybackProgress: (PlaybackProgress) -> Unit,
    private val nowPlayingTrack: () -> Track?,
    private val setRepeatMode: (RepeatMode) -> Unit,
    private val playReportSessionId: () -> Int,
    private val setOpenPlayerOnTrackStart: (Boolean) -> Unit,
    private val reporting: NaviampPlaybackReportingController,
) : NaviampPlaybackExecution {
    private val playbackCommands = NaviampPlaybackCommandController(this, livePlayback)
    private val queueCommands = NaviampPlaybackQueueCommandController(
        queue = queueCoordinator,
        execution = NaviampPlaybackQueueMutationExecution(playlistEngine::applyQueueMutation),
    )
    private val repeatCommands = NaviampPlaybackRepeatCommandController(
        queue = queueCoordinator,
        execution = NaviampPlaybackRepeatModeExecution { mode ->
            setRepeatMode(mode)
            playlistEngine.setRepeatMode(mode)
        },
    )
    private val navigationCommands = NaviampPlaybackNavigationCommandController(
        queue = queueCoordinator,
        execution = NaviampPlaybackNavigationExecution(::executeNavigationCommand),
    )
    fun savePlaybackSession(
        queue: PlaybackQueue,
        positionSeconds: Double? = playbackProgress().positionSeconds,
    ) {
        playbackSessions.save(playbackSessionFromQueue(queue, positionSeconds))
    }

    fun clearShuffleSnapshot() {
        queueCoordinator.clearShuffleSnapshot()
    }

    fun toggleShuffle() {
        playlistEngine.applyShuffleUpdate(queueCoordinator.toggleUpcomingShuffle())
    }

    fun cycleRepeatMode() {
        repeatCommands.cycle()
    }

    fun moveQueueTrackNext(index: Int) {
        queueCommands.moveToNext(index)
    }

    fun removeFromQueue(index: Int) {
        queueCommands.removeAt(index)
    }

    fun emptyQueue() {
        queueCommands.clearUpcoming()
    }

    fun maybeSavePlaybackPosition(progress: PlaybackProgress) {
        val positionSeconds = progress.positionSeconds ?: return
        val queue = playbackQueue()
        playbackSessions.planAndSavePositionIfNeeded(
            request = NaviampPlaybackSessionSaveRequest(
                sourceId = sourceId(),
                station = livePlayback.state.value.currentStation,
                currentTrack = nowPlayingTrack(),
                playbackQueue = queue,
                progressPositionSeconds = positionSeconds,
            ),
            saveThresholdSeconds = PlaybackPositionSaveThresholdSeconds,
        )
    }

    fun handlePlayPauseCommand(startOrRestorePlayback: () -> Boolean): Boolean =
        playbackCommands.playPause(
            hasPlaybackTarget = nowPlayingTrack() != null || livePlayback.state.value.currentStation != null,
            startOrRestore = startOrRestorePlayback,
        )

    override fun pause() {
        playbackEngine.pause()
    }

    override fun resume() {
        playbackEngine.resume()
    }

    override fun startOrRestore(): Boolean = false

    fun performSeek(positionSeconds: Double) {
        val streamQuality = playbackSettings().streamQuality(playbackEngine)
        val playbackSource = playlistEngine.cacheRuntimeStats().playbackSource
        val seekPlan = playbackCommands.seek(
            NaviampPlaybackSeekRequest(
                positionSeconds = positionSeconds,
                streamQuality = streamQuality,
                playbackSource = playbackSource,
                issuedAtMillis = DesktopSystemClock.nowEpochMillis(),
            ),
        ) ?: return
        setPlaybackProgress(seekPlan.progress)
        maybeSavePlaybackPosition(seekPlan.progress)
    }

    override fun seek(positionSeconds: Double) {
        playbackEngine.seek(positionSeconds)
    }

    override fun replayCurrent(positionSeconds: Double) {
        playlistEngine.playCurrent(scope, positionSeconds)
    }

    fun changeVolume(requestedPercent: Int): PlaybackVolumeCommand =
        playbackCommands.changeVolume(
            requestedPercent = requestedPercent,
            supportsSoftwareVolume = playbackEngine.supportsSoftwareVolume,
        )

    override fun setVolume(percent: Int) {
        playbackEngine.setVolume(percent)
    }

    override fun stop() {
        playbackEngine.stop()
    }

    fun canUsePreviousButton(): Boolean =
        queueCoordinator.canUsePreviousButton(playbackSettings().previousButtonBehavior)

    fun canUseNextButton(): Boolean = queueCoordinator.canUseNextButton()

    fun handlePreviousButton() {
        setOpenPlayerOnTrackStart(false)
        navigationCommands.previous(playbackSettings().previousButtonBehavior)
    }

    fun handleNextButton() {
        setOpenPlayerOnTrackStart(false)
        navigationCommands.next()
    }

    fun handleQueueIndexSelected(
        index: Int,
        moveSelectedToCurrent: Boolean,
    ) {
        setOpenPlayerOnTrackStart(false)
        navigationCommands.jumpTo(index, moveSelectedToCurrent)
    }

    private fun executeNavigationCommand(command: PlaybackQueueNavigationCommand) {
        when (command) {
            PlaybackQueueNavigationCommand.None -> Unit
            PlaybackQueueNavigationCommand.RestartCurrent -> performSeek(0.0)
            PlaybackQueueNavigationCommand.Previous -> {
                reportCurrentTrackStopped()
                playlistEngine.previous(scope)
            }
            PlaybackQueueNavigationCommand.Next -> {
                reportCurrentTrackStopped()
                playlistEngine.next(scope)
            }
            is PlaybackQueueNavigationCommand.JumpTo -> {
                reportCurrentTrackStopped()
                playlistEngine.jumpTo(
                    scope = scope,
                    index = command.index,
                    moveSelectedToCurrent = command.moveSelectedToCurrent,
                )
            }
        }
    }

    fun reportNowPlaying(track: Track) {
        val activeProvider = provider() ?: return
        if (
            !canReportPlaybackTrack(
                supportsPlayReporting = activeProvider.capabilities.supportsPlayReporting,
                isInternetRadioTrack = track.isInternetRadioTrack(),
            )
        ) {
            return
        }
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    reportNowPlaying(track.id)
                }
            }
        }
    }

    suspend fun reportNowPlaying(trackId: TrackId) {
        val activeProvider = provider() ?: return
        providerActions.offlineCapable(activeProvider, sourceId()).reportNowPlaying(trackId)
    }

    fun maybeReportPlaybackState(state: PlaybackState, progress: PlaybackProgress = playbackProgress()) {
        val activeProvider = provider() ?: return
        val track = nowPlayingTrack() ?: return
        val report = reporting.stateReport(
            NaviampPlaybackStateReportRequest(
                sessionId = playReportSessionId().toLong(),
                trackId = track.id,
                isInternetRadioTrack = track.isInternetRadioTrack(),
                supportsPlayReporting = activeProvider.capabilities.supportsPlayReporting,
                playbackState = state,
                progress = progress,
                nowEpochMillis = DesktopSystemClock.nowEpochMillis(),
            ),
        ) ?: return
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    activeProvider.reportPlaybackState(
                        trackId = report.trackId,
                        state = report.state,
                        positionSeconds = report.positionSeconds,
                    )
                }
            }
        }
    }

    private fun reportCurrentTrackStopped() {
        maybeReportPlaybackState(PlaybackState.Stopped, playbackProgress())
    }

}
