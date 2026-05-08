package app.naviamp.desktop.playback

import kotlinx.coroutines.CoroutineScope

interface PlaybackEngine {
    val name: String
    val supportsPause: Boolean
    val supportsSeek: Boolean
    val prefersOriginalStream: Boolean

    fun play(
        scope: CoroutineScope,
        request: PlaybackRequest,
        onStateChanged: (PlaybackState) -> Unit,
        onProgressChanged: (PlaybackProgress) -> Unit,
    )

    fun pause()

    fun resume()

    fun seek(positionSeconds: Double)

    fun stop()
}

data class PlaybackRequest(
    val url: String,
)

data class PlaybackProgress(
    val positionSeconds: Double?,
    val durationSeconds: Double?,
) {
    val fraction: Double
        get() {
            val position = positionSeconds ?: return 0.0
            val duration = durationSeconds ?: return 0.0
            if (duration <= 0.0) return 0.0
            return (position / duration).coerceIn(0.0, 1.0)
        }

    companion object {
        val Unknown = PlaybackProgress(
            positionSeconds = null,
            durationSeconds = null,
        )
    }
}

fun PlaybackProgress.mergeWith(previous: PlaybackProgress): PlaybackProgress {
    val previousDuration = previous.durationSeconds
    val currentDuration = durationSeconds

    return copy(
        durationSeconds = when {
            currentDuration == null -> previousDuration
            previousDuration == null -> currentDuration
            currentDuration >= previousDuration -> currentDuration
            else -> previousDuration
        },
    )
}

sealed interface PlaybackState {
    data object Idle : PlaybackState
    data object Loading : PlaybackState
    data object Playing : PlaybackState
    data object Paused : PlaybackState
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
        PlaybackState.Paused -> "Paused"
        PlaybackState.Stopped -> "Stopped"
        PlaybackState.Finished -> "Finished"
        is PlaybackState.Error -> message
    }
