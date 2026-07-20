package app.naviamp.app

import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Track
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class NaviampLivePlaybackState(
    val currentTrack: Track? = null,
    val currentStation: InternetRadioStation? = null,
    val queue: PlaybackQueue = PlaybackQueue(),
    val progress: PlaybackProgress = PlaybackProgress.Unknown,
    val pendingSeekPositionSeconds: Double? = null,
    val pendingSeekIssuedAtMillis: Long? = null,
    val playbackState: PlaybackState = PlaybackState.Idle,
    val repeatMode: RepeatMode = RepeatMode.Off,
    val shuffledUpNextSnapshot: List<Track>? = null,
)

/** Shared owner of live, user-visible playback state; it does not execute audio commands. */
class NaviampLivePlaybackController(
    initialState: NaviampLivePlaybackState = NaviampLivePlaybackState(),
) {
    private val mutableState = MutableStateFlow(initialState)

    val state: StateFlow<NaviampLivePlaybackState> = mutableState.asStateFlow()

    fun replace(state: NaviampLivePlaybackState) {
        mutableState.value = state
    }

    fun updateCurrentTrack(track: Track?) = update { current -> current.copy(currentTrack = track) }

    fun updateCurrentStation(station: InternetRadioStation?) =
        update { current -> current.copy(currentStation = station) }

    fun updateQueue(queue: PlaybackQueue) = update { current -> current.copy(queue = queue) }

    fun applyQueueChange(
        queue: PlaybackQueue,
        positionSeconds: Double?,
        persist: (PlaybackQueue, Double?) -> Unit,
    ) {
        updateQueue(queue)
        persist(queue, positionSeconds)
    }

    fun updateProgress(progress: PlaybackProgress) = update { current -> current.copy(progress = progress) }

    fun updatePendingSeek(
        positionSeconds: Double?,
        issuedAtMillis: Long?,
    ) = update { current ->
        current.copy(
            pendingSeekPositionSeconds = positionSeconds,
            pendingSeekIssuedAtMillis = issuedAtMillis,
        )
    }

    fun applySeekPlan(
        progress: PlaybackProgress,
        pendingPositionSeconds: Double,
        issuedAtMillis: Long,
    ) = update { current ->
        current.copy(
            progress = progress,
            pendingSeekPositionSeconds = pendingPositionSeconds,
            pendingSeekIssuedAtMillis = issuedAtMillis,
        )
    }

    fun updatePlaybackState(playbackState: PlaybackState) =
        update { current -> current.copy(playbackState = playbackState) }

    fun applyPlaybackStateChange(
        playbackState: PlaybackState,
        progress: PlaybackProgress = state.value.progress,
        report: (PlaybackState, PlaybackProgress) -> Unit,
    ) {
        updatePlaybackState(playbackState)
        report(playbackState, progress)
    }

    fun updateRepeatMode(repeatMode: RepeatMode) =
        update { current -> current.copy(repeatMode = repeatMode) }

    fun updateShuffledUpNextSnapshot(snapshot: List<Track>?) =
        update { current -> current.copy(shuffledUpNextSnapshot = snapshot) }

    private inline fun update(transform: (NaviampLivePlaybackState) -> NaviampLivePlaybackState) {
        mutableState.update(transform)
    }
}
