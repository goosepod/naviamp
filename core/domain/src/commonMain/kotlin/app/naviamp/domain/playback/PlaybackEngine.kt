package app.naviamp.domain.playback

import app.naviamp.domain.ReplayGain
import kotlinx.coroutines.CoroutineScope

interface PlaybackEngine {
    val name: String
    val supportsPause: Boolean
    val supportsSeek: Boolean
    val supportsGapless: Boolean
    val supportsCrossfade: Boolean
    val supportsReplayGain: Boolean
    val supportsSoftwareVolume: Boolean
    val prefersOriginalStream: Boolean

    fun play(
        scope: CoroutineScope,
        request: PlaybackRequest,
        onStateChanged: (PlaybackState) -> Unit,
        onProgressChanged: (PlaybackProgress) -> Unit,
        onMetadataChanged: (PlaybackStreamMetadata) -> Unit = {},
    )

    fun pause()

    fun resume()

    fun seek(positionSeconds: Double)

    fun setVolume(percent: Int)

    fun stop()
}

interface QueueAwarePlaybackEngine : PlaybackEngine {
    fun setCrossfadeDuration(seconds: Int)

    fun prepareNext(request: PlaybackRequest)
}

data class PlaybackRequest(
    val url: String,
    val mediaId: String? = null,
    val replayGainMode: ReplayGainMode = ReplayGainMode.Off,
    val replayGain: PlaybackReplayGain? = null,
    val startPositionSeconds: Double? = null,
)

data class PlaybackReplayGain(
    val replayGain: ReplayGain,
    val source: ReplayGainSource,
)

enum class ReplayGainSource(
    val displayName: String,
) {
    Provider("Provider metadata"),
    LocalTags("Local file tags"),
}

data class PlaybackStreamMetadata(
    val title: String? = null,
    val properties: Map<String, String> = emptyMap(),
) {
    companion object {
        fun fromProperties(
            properties: Map<String, String>,
            fallbackTitle: String? = null,
        ): PlaybackStreamMetadata =
            PlaybackStreamMetadata(
                title = streamTitle(properties, fallbackTitle),
                properties = properties,
            )
    }
}

fun streamTitle(
    properties: Map<String, String>,
    fallbackTitle: String? = null,
): String? =
    listOfNotNull(
        properties.valueIgnoringCase("icy-title"),
        properties.valueIgnoringCase("StreamTitle"),
        properties.valueIgnoringCase("streamtitle"),
        properties.valueIgnoringCase("title"),
        fallbackTitle,
    ).firstOrNull { !it.isNullOrBlank() }?.trim()

private fun Map<String, String>.valueIgnoringCase(key: String): String? {
    get(key)?.let { return it }
    return entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value
}

enum class ReplayGainMode(
    val displayName: String,
) {
    Off("Off"),
    Track("Track"),
    Album("Album"),
}

data class CrossfadeSettings(
    val enabled: Boolean = false,
    val durationSeconds: Int = 0,
) {
    init {
        require(durationSeconds >= 0) { "Crossfade duration cannot be negative." }
    }

    val isActive: Boolean
        get() = enabled && durationSeconds > 0
}

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
    val previousPosition = previous.positionSeconds
    val currentPosition = positionSeconds
    val previousDuration = previous.durationSeconds
    val currentDuration = durationSeconds

    return copy(
        positionSeconds = when {
            currentPosition == null -> previousPosition
            previousPosition == null -> currentPosition
            currentPosition >= previousPosition -> currentPosition
            previousPosition - currentPosition <= 1.0 -> currentPosition
            else -> previousPosition
        },
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
