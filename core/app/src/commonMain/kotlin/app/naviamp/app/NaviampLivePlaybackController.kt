package app.naviamp.app

import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Track
import app.naviamp.domain.internetRadioTrackId
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    private val observers = mutableListOf<(NaviampLivePlaybackState) -> Unit>()

    val state: StateFlow<NaviampLivePlaybackState> = mutableState.asStateFlow()

    fun replace(state: NaviampLivePlaybackState) {
        publish(state)
    }

    /** Observes canonical state mutations without requiring a host lifecycle coroutine. */
    fun observe(observer: (NaviampLivePlaybackState) -> Unit) {
        observers += observer
    }

    fun updateCurrentTrack(track: Track?) = update { current ->
        current.copy(
            currentTrack = track,
            progress = if (current.currentTrack?.id != track?.id) PlaybackProgress.Unknown else current.progress,
            currentStation = current.currentStation?.takeIf { station ->
                track?.id == internetRadioTrackId(station.id)
            },
        )
    }

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
        publish(transform(mutableState.value))
    }

    private fun publish(updated: NaviampLivePlaybackState) {
        if (updated == mutableState.value) return
        mutableState.value = updated
        observers.toList().forEach { it(updated) }
    }
}
