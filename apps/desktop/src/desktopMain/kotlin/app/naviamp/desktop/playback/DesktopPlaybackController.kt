package app.naviamp.desktop

import app.naviamp.desktop.playback.DesktopPlaylistEngine
import app.naviamp.desktop.settings.PlaybackSettings
import app.naviamp.domain.Track
import app.naviamp.domain.cache.PlaybackSessionRepository
import app.naviamp.domain.isInternetRadioTrack
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.playback.PlaybackPlayPauseCommand
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.canReportPlaybackTrack
import app.naviamp.domain.playback.PlaybackQueueManager
import app.naviamp.domain.playback.PlaybackQueueNavigationCommand
import app.naviamp.domain.playback.planPlaybackSeek
import app.naviamp.domain.playback.playbackPlayPauseCommand
import app.naviamp.domain.playback.shouldReplayCurrentForSeek
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

internal fun handleDesktopPlayPauseCommand(
    playbackState: PlaybackState,
    hasPlaybackTarget: Boolean,
    playbackEngine: PlaybackEngine,
    startOrRestorePlayback: () -> Unit,
) {
    when (
        playbackPlayPauseCommand(
            playbackState = playbackState,
            hasPlaybackTarget = hasPlaybackTarget,
        )
    ) {
        PlaybackPlayPauseCommand.Pause -> playbackEngine.pause()
        PlaybackPlayPauseCommand.Resume -> playbackEngine.resume()
        PlaybackPlayPauseCommand.StartOrRestore -> startOrRestorePlayback()
        PlaybackPlayPauseCommand.None -> Unit
    }
}

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
    private val playbackSessionRepository: PlaybackSessionRepository,
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
    private val setPendingSeekPositionSeconds: (Double?) -> Unit,
    private val setPendingSeekIssuedAtMillis: (Long?) -> Unit,
    private val setOpenPlayerOnTrackStart: (Boolean) -> Unit,
) {
    private val queueManager = PlaybackQueueManager()
    private var lastPlaybackStateReportSessionId: Int? = null
    private var lastPlaybackStateReportState: PlaybackReportState? = null
    private var lastPlaybackStateReportAtMillis: Long = 0L

    fun savePlaybackSession(
        queue: PlaybackQueue,
        positionSeconds: Double? = playbackProgress().positionSeconds,
    ) {
        playbackSessionRepository.savePlaybackSession(
            playbackSessionFromQueue(queue, positionSeconds),
        )
    }

    fun clearShuffleSnapshot() {
        setShuffledUpNextSnapshot(null)
    }

    fun toggleShuffle() {
        setShuffledUpNextSnapshot(playlistEngine.toggleUpcomingShuffle(shuffledUpNextSnapshot()))
    }

    fun cycleRepeatMode() {
        val mode = queueManager.cycleRepeatMode(repeatMode())
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

    fun performSeek(positionSeconds: Double) {
        val streamQuality = playbackSettings().streamQuality(playbackEngine)
        val playbackSource = playlistEngine.cacheRuntimeStats().playbackSource
        val track = nowPlayingTrack()
        val seekPlan = planPlaybackSeek(
            isInternetRadioTrack = track?.isInternetRadioTrack() == true,
            positionSeconds = positionSeconds,
            currentProgress = playbackProgress(),
            trackDurationSeconds = track?.durationSeconds,
            streamQuality = streamQuality,
            shouldReplayTranscodedStream = shouldReplayCurrentForSeek(playbackSource),
        ) ?: return
        setPendingSeekPositionSeconds(seekPlan.pendingSeekPositionSeconds)
        setPendingSeekIssuedAtMillis(System.currentTimeMillis())
        setPlaybackProgress(seekPlan.progress)
        maybeSavePlaybackPosition(seekPlan.progress)
        if (seekPlan.shouldReplayCurrent) {
            playlistEngine.playCurrent(scope, seekPlan.pendingSeekPositionSeconds)
            return
        }
        playbackEngine.seek(seekPlan.pendingSeekPositionSeconds)
    }

    fun canUsePreviousButton(): Boolean =
        queueManager.canUsePreviousButton(
            queue = playbackQueue(),
            previousButtonBehavior = playbackSettings().previousButtonBehavior,
            positionSeconds = playbackProgress().positionSeconds,
            restartThresholdSeconds = PreviousRestartThresholdSeconds,
        )

    fun canUseNextButton(): Boolean =
        queueManager.canUseNextButton(
            queue = playbackQueue(),
            repeatMode = repeatMode(),
        )

    fun handlePreviousButton() {
        setOpenPlayerOnTrackStart(false)
        when (
            queueManager.previousCommand(
                queue = playbackQueue(),
                previousButtonBehavior = playbackSettings().previousButtonBehavior,
                positionSeconds = playbackProgress().positionSeconds,
                restartThresholdSeconds = PreviousRestartThresholdSeconds,
            )
        ) {
            PlaybackQueueNavigationCommand.None -> Unit
            PlaybackQueueNavigationCommand.RestartCurrent -> performSeek(0.0)
            PlaybackQueueNavigationCommand.Previous -> playlistEngine.previous(scope)
            PlaybackQueueNavigationCommand.Next -> playlistEngine.next(scope)
            is PlaybackQueueNavigationCommand.JumpTo -> Unit
        }
    }

    fun handleNextButton() {
        setOpenPlayerOnTrackStart(false)
        when (queueManager.nextCommand(playbackQueue(), repeatMode())) {
            PlaybackQueueNavigationCommand.Next -> playlistEngine.next(scope)
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
            is PlaybackQueueNavigationCommand.JumpTo -> playlistEngine.jumpTo(
                scope = scope,
                index = command.index,
                moveSelectedToCurrent = command.moveSelectedToCurrent,
            )
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
