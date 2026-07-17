package app.naviamp.desktop

import app.naviamp.desktop.playback.DesktopPlaylistEngine
import app.naviamp.desktop.settings.PlaybackSettings
import app.naviamp.domain.Track
import app.naviamp.app.NaviampPlaybackSessionController
import app.naviamp.app.NaviampPlaybackQueueCoordinator
import app.naviamp.app.NaviampPlaybackCommandController
import app.naviamp.app.NaviampPlaybackExecution
import app.naviamp.app.NaviampPlaybackSeekRequest
import app.naviamp.app.NaviampLivePlaybackController
import app.naviamp.domain.isInternetRadioTrack
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.canReportPlaybackTrack
import app.naviamp.domain.playback.PlaybackQueueManager
import app.naviamp.domain.playback.PlaybackQueueNavigationCommand
import app.naviamp.domain.playback.shouldSavePlaybackPosition
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.PlaybackReportState
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
    private val playbackSettings: () -> PlaybackSettings,
    private val playbackQueue: () -> PlaybackQueue,
    private val playbackProgress: () -> PlaybackProgress,
    private val setPlaybackProgress: (PlaybackProgress) -> Unit,
    private val nowPlayingTrack: () -> Track?,
    private val repeatMode: () -> RepeatMode,
    private val setRepeatMode: (RepeatMode) -> Unit,
    private val shuffledUpNextSnapshot: () -> List<Track>?,
    private val setShuffledUpNextSnapshot: (List<Track>?) -> Unit,
    private val lastSavedPlaybackPositionSeconds: () -> Double?,
    private val setLastSavedPlaybackPositionSeconds: (Double?) -> Unit,
    private val playReportSessionId: () -> Int,
    private val setOpenPlayerOnTrackStart: (Boolean) -> Unit,
) : NaviampPlaybackExecution {
    private val playbackCommands = NaviampPlaybackCommandController(this, livePlayback)
    private val queueManager = PlaybackQueueManager()
    private var lastPlaybackStateReportSessionId: Int? = null
    private var lastPlaybackStateReportState: PlaybackReportState? = null
    private var lastPlaybackStateReportAtMillis: Long = 0L

    fun savePlaybackSession(
        queue: PlaybackQueue,
        positionSeconds: Double? = playbackProgress().positionSeconds,
    ) {
        playbackSessions.save(playbackSessionFromQueue(queue, positionSeconds))
    }

    fun clearShuffleSnapshot() {
        setShuffledUpNextSnapshot(null)
    }

    fun toggleShuffle() {
        setShuffledUpNextSnapshot(playlistEngine.toggleUpcomingShuffle(shuffledUpNextSnapshot()))
    }

    fun cycleRepeatMode() {
        val mode = queueCoordinator.cycleRepeatMode()
        setRepeatMode(mode)
        playlistEngine.setRepeatMode(mode)
    }

    fun maybeSavePlaybackPosition(progress: PlaybackProgress) {
        val positionSeconds = progress.positionSeconds ?: return
        if (
            !shouldSavePlaybackPosition(
                queue = playbackQueue(),
                positionSeconds = positionSeconds,
                lastSavedPositionSeconds = lastSavedPlaybackPositionSeconds(),
                saveThresholdSeconds = PlaybackPositionSaveThresholdSeconds,
            )
        ) {
            return
        }
        setLastSavedPlaybackPositionSeconds(positionSeconds)
        savePlaybackSession(playbackQueue(), positionSeconds)
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
                issuedAtMillis = System.currentTimeMillis(),
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

    override fun stop() {
        playbackEngine.stop()
    }

    fun canUsePreviousButton(): Boolean =
        queueCoordinator.canUsePreviousButton(playbackSettings().previousButtonBehavior)

    fun canUseNextButton(): Boolean =
        queueManager.canUseNextButton(
            queue = playbackQueue(),
            repeatMode = repeatMode(),
        )

    fun handlePreviousButton() {
        setOpenPlayerOnTrackStart(false)
        when (
            queueCoordinator.previousCommand(
                previousButtonBehavior = playbackSettings().previousButtonBehavior,
            )
        ) {
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
            is PlaybackQueueNavigationCommand.JumpTo -> Unit
        }
    }

    fun handleNextButton() {
        setOpenPlayerOnTrackStart(false)
        when (queueCoordinator.nextCommand()) {
            PlaybackQueueNavigationCommand.Next -> {
                reportCurrentTrackStopped()
                playlistEngine.next(scope)
            }
            PlaybackQueueNavigationCommand.None,
            PlaybackQueueNavigationCommand.Previous,
            PlaybackQueueNavigationCommand.RestartCurrent,
            is PlaybackQueueNavigationCommand.JumpTo,
            -> Unit
        }
    }

    fun handleQueueIndexSelected(
        index: Int,
        moveSelectedToCurrent: Boolean,
    ) {
        setOpenPlayerOnTrackStart(false)
        when (val command = queueManager.jumpCommand(playbackQueue(), index, moveSelectedToCurrent)) {
            is PlaybackQueueNavigationCommand.JumpTo -> {
                if (!queueCoordinator.selectIndex(index, moveSelectedToCurrent).changed) return
                reportCurrentTrackStopped()
                playlistEngine.jumpTo(
                    scope = scope,
                    index = command.index,
                    moveSelectedToCurrent = command.moveSelectedToCurrent,
                )
            }
            PlaybackQueueNavigationCommand.None,
            PlaybackQueueNavigationCommand.Previous,
            PlaybackQueueNavigationCommand.Next,
            PlaybackQueueNavigationCommand.RestartCurrent,
            -> Unit
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
                    activeProvider.reportNowPlaying(track.id)
                }
            }
        }
    }

    fun maybeReportPlaybackState(state: PlaybackState, progress: PlaybackProgress = playbackProgress()) {
        val reportState = state.toPlaybackReportState() ?: return
        val activeProvider = provider() ?: return
        val track = nowPlayingTrack() ?: return
        if (
            !canReportPlaybackTrack(
                supportsPlayReporting = activeProvider.capabilities.supportsPlayReporting,
                isInternetRadioTrack = track.isInternetRadioTrack(),
            )
        ) {
            return
        }
        val activeSessionId = playReportSessionId()
        val nowMillis = System.currentTimeMillis()
        val sameSession = lastPlaybackStateReportSessionId == activeSessionId
        val sameState = lastPlaybackStateReportState == reportState
        val shouldReport = !sameSession ||
            !sameState ||
            (reportState == PlaybackReportState.Playing &&
                nowMillis - lastPlaybackStateReportAtMillis >= PlaybackStateReportIntervalMillis)
        if (!shouldReport) return

        lastPlaybackStateReportSessionId = activeSessionId
        lastPlaybackStateReportState = reportState
        lastPlaybackStateReportAtMillis = nowMillis
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    activeProvider.reportPlaybackState(
                        trackId = track.id,
                        state = reportState,
                        positionSeconds = progress.positionSeconds,
                    )
                }
            }
        }
    }

    private fun reportCurrentTrackStopped() {
        maybeReportPlaybackState(PlaybackState.Stopped, playbackProgress())
    }

}

private fun PlaybackState.toPlaybackReportState(): PlaybackReportState? =
    when (this) {
        PlaybackState.Loading -> PlaybackReportState.Starting
        PlaybackState.Playing -> PlaybackReportState.Playing
        PlaybackState.Paused -> PlaybackReportState.Paused
        PlaybackState.Stopped,
        PlaybackState.Finished,
        is PlaybackState.Error,
        -> PlaybackReportState.Stopped
        PlaybackState.Idle -> null
    }

private const val PlaybackStateReportIntervalMillis = 15_000L
