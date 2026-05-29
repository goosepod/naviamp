package app.naviamp.domain.playback

import app.naviamp.domain.StreamQuality
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track

data class PlaybackTargetPlan(
    val engineStartPositionSeconds: Double?,
    val providerStreamRequest: StreamRequest,
)

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
