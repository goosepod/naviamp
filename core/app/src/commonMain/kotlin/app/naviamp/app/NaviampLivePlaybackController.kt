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
    val playbackState: PlaybackState = PlaybackState.Idle,
    val repeatMode: RepeatMode = RepeatMode.Off,
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

    fun updateProgress(progress: PlaybackProgress) = update { current -> current.copy(progress = progress) }

    fun updatePlaybackState(playbackState: PlaybackState) =
        update { current -> current.copy(playbackState = playbackState) }

    fun updateRepeatMode(repeatMode: RepeatMode) =
        update { current -> current.copy(repeatMode = repeatMode) }

    private inline fun update(transform: (NaviampLivePlaybackState) -> NaviampLivePlaybackState) {
        mutableState.update(transform)
    }
}
