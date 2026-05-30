package app.naviamp.desktop

import app.naviamp.desktop.playback.PlaylistEngine
import app.naviamp.desktop.settings.DesktopSettingsStore
import app.naviamp.desktop.settings.PlaybackSettings
import app.naviamp.domain.Track
import app.naviamp.domain.isInternetRadioTrack
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.canUseNextButton
import app.naviamp.domain.playback.canUsePreviousButton
import app.naviamp.domain.playback.canReportPlaybackTrack
import app.naviamp.domain.playback.nextRepeatMode
import app.naviamp.domain.playback.planPlaybackSeek
import app.naviamp.domain.playback.shouldRestartInsteadOfPrevious
import app.naviamp.domain.playback.shouldSavePlaybackPosition
import app.naviamp.domain.playback.shouldSubmitPlayReport
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.settings.playbackSessionFromQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DesktopPlaybackController(
    private val scope: CoroutineScope,
    private val settingsStore: DesktopSettingsStore,
    private val playbackEngine: PlaybackEngine,
    private val playlistEngine: PlaylistEngine,
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
    private val submittedPlayReportSessionId: () -> Int?,
    private val setSubmittedPlayReportSessionId: (Int?) -> Unit,
    private val setPendingSeekPositionSeconds: (Double?) -> Unit,
    private val setPendingSeekIssuedAtMillis: (Long?) -> Unit,
    private val setOpenPlayerOnTrackStart: (Boolean) -> Unit,
) {
    fun savePlaybackSession(
        queue: PlaybackQueue,
        positionSeconds: Double? = playbackProgress().positionSeconds,
    ) {
        settingsStore.savePlaybackSession(
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
        val mode = nextRepeatMode(repeatMode())
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
        canUsePreviousButton(
            queue = playbackQueue(),
            previousButtonBehavior = playbackSettings().previousButtonBehavior,
            positionSeconds = playbackProgress().positionSeconds,
            restartThresholdSeconds = PreviousRestartThresholdSeconds,
        )

    fun canUseNextButton(): Boolean =
        canUseNextButton(
            queue = playbackQueue(),
            repeatMode = repeatMode(),
        )

    fun handlePreviousButton() {
        setOpenPlayerOnTrackStart(false)
        val positionSeconds = playbackProgress().positionSeconds ?: 0.0
        if (
            shouldRestartInsteadOfPrevious(
                previousButtonBehavior = playbackSettings().previousButtonBehavior,
                positionSeconds = positionSeconds,
                restartThresholdSeconds = PreviousRestartThresholdSeconds,
            )
        ) {
            performSeek(0.0)
            return
        }
        playlistEngine.previous(scope)
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

    fun maybeReportPlayed(progress: PlaybackProgress) {
        val activeProvider = provider() ?: return
        val track = nowPlayingTrack() ?: return
        val durationSeconds = progress.durationSeconds ?: track.durationSeconds?.toDouble()
        if (
            !shouldSubmitPlayReport(
                supportsPlayReporting = activeProvider.capabilities.supportsPlayReporting,
                isInternetRadioTrack = track.isInternetRadioTrack(),
                activeSessionId = playReportSessionId(),
                submittedSessionId = submittedPlayReportSessionId(),
                positionSeconds = progress.positionSeconds,
                durationSeconds = durationSeconds,
            )
        ) {
            return
        }

        val activeSessionId = playReportSessionId()
        setSubmittedPlayReportSessionId(activeSessionId)
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    activeProvider.reportPlayed(track.id, System.currentTimeMillis())
                }
            }.onFailure {
                if (submittedPlayReportSessionId() == activeSessionId) {
                    setSubmittedPlayReportSessionId(null)
                }
            }
        }
    }
}
