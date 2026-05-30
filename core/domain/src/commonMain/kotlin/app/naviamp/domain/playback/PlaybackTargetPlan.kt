package app.naviamp.domain.playback

import app.naviamp.domain.StreamQuality
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track

data class PlaybackTargetPlan(
    val engineStartPositionSeconds: Double?,
    val providerStreamRequest: StreamRequest,
)

data class PlaybackStartPlan(
    val queue: List<Track>,
    val queueIndex: Int,
    val target: PlaybackTargetPlan,
    val restoredStartPositionSeconds: Double?,
    val initialProgress: PlaybackProgress?,
) {
    val shouldResetProgress: Boolean = restoredStartPositionSeconds == null
}

fun planPlaybackStart(
    track: Track,
    requestedQueue: List<Track>?,
    activeQueue: List<Track>,
    quality: StreamQuality,
    startPositionSeconds: Double?,
    hasLocalAudio: Boolean,
): PlaybackStartPlan {
    val queue = requestedQueue
        ?.takeIf { tracks -> tracks.any { it.id == track.id } }
        ?: activeQueue.takeIf { tracks -> tracks.any { it.id == track.id } }
        ?: listOf(track)
    val target = playbackTargetPlan(
        track = track,
        quality = quality,
        startPositionSeconds = startPositionSeconds,
        hasLocalAudio = hasLocalAudio,
    )
    val restoredStartPosition = target.engineStartPositionSeconds?.takeIf { it > 0.0 }
    return PlaybackStartPlan(
        queue = queue,
        queueIndex = queue.indexOfFirst { it.id == track.id }.takeIf { it >= 0 } ?: 0,
        target = target,
        restoredStartPositionSeconds = restoredStartPosition,
        initialProgress = restoredStartPosition?.let { positionSeconds ->
            PlaybackProgress(
                positionSeconds = positionSeconds,
                durationSeconds = track.durationSeconds?.toDouble(),
            )
        },
    )
}

fun playbackTargetPlan(
    track: Track,
    quality: StreamQuality,
    startPositionSeconds: Double?,
    hasLocalAudio: Boolean,
): PlaybackTargetPlan {
    val engineStartPositionSeconds = startPositionSeconds
        ?.takeIf { hasLocalAudio || quality !is StreamQuality.Transcoded }
    val providerStartPositionSeconds = startPositionSeconds
        ?.takeIf { !hasLocalAudio && quality is StreamQuality.Transcoded }
    return PlaybackTargetPlan(
        engineStartPositionSeconds = engineStartPositionSeconds,
        providerStreamRequest = StreamRequest(
            trackId = track.id,
            quality = quality,
            startPositionSeconds = providerStartPositionSeconds,
        ),
    )
}
