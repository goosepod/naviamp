package app.naviamp.desktop.playback

import kotlinx.coroutines.CoroutineScope

interface PlaybackEngine {
    fun play(
        scope: CoroutineScope,
        request: PlaybackRequest,
        onStateChanged: (PlaybackState) -> Unit,
    )

    fun stop()
}

data class PlaybackRequest(
    val url: String,
)

sealed interface PlaybackState {
    data object Idle : PlaybackState
    data object Loading : PlaybackState
    data object Playing : PlaybackState
    data object Stopped : PlaybackState
    data object Finished : PlaybackState

    data class Error(
        val message: String,
    ) : PlaybackState
}

fun PlaybackState.label(): String =
    when (this) {
        PlaybackState.Idle -> "Nothing Playing"
        PlaybackState.Loading -> "Loading"
        PlaybackState.Playing -> "Playing"
        PlaybackState.Stopped -> "Stopped"
        PlaybackState.Finished -> "Finished"
        is PlaybackState.Error -> message
    }

