package app.naviamp.domain.playback

import app.naviamp.domain.StreamQuality

data class PlaybackSeekPlan(
    val progress: PlaybackProgress,
    val shouldReplayCurrent: Boolean,
    val pendingSeekPositionSeconds: Double,
    val shouldClearRestoredStartPosition: Boolean = true,
)

fun planPlaybackSeek(
    isInternetRadioTrack: Boolean,
    positionSeconds: Double,
    currentProgress: PlaybackProgress,
    trackDurationSeconds: Int?,
    streamQuality: StreamQuality,
    shouldReplayTranscodedStream: Boolean,
): PlaybackSeekPlan? {
    if (isInternetRadioTrack) return null
    return PlaybackSeekPlan(
        progress = PlaybackProgress(
            positionSeconds = positionSeconds.coerceAtLeast(0.0),
            durationSeconds = currentProgress.durationSeconds ?: trackDurationSeconds?.toDouble(),
        ),
        shouldReplayCurrent = shouldReplayTranscodedSeek(
            streamQuality = streamQuality,
            shouldReplayTranscodedStream = shouldReplayTranscodedStream,
        ),
        pendingSeekPositionSeconds = positionSeconds,
    )
}

fun shouldReplayTranscodedSeek(
    streamQuality: StreamQuality,
    shouldReplayTranscodedStream: Boolean,
): Boolean =
    shouldReplayTranscodedStream && streamQuality is StreamQuality.Transcoded
