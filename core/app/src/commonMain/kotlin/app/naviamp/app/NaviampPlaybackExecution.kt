package app.naviamp.app

import app.naviamp.domain.StreamQuality
import app.naviamp.domain.isInternetRadioTrack
import app.naviamp.domain.playback.PlaybackSource
import app.naviamp.domain.playback.PlaybackSeekPlan
import app.naviamp.domain.playback.planPlaybackSeek
import app.naviamp.domain.playback.shouldReplayCurrentForSeek

data class NaviampPlaybackSeekRequest(
    val positionSeconds: Double,
    val streamQuality: StreamQuality,
    val playbackSource: PlaybackSource,
    val issuedAtMillis: Long,
)

/**
 * Narrow host boundary for audio commands that cannot be executed in shared code.
 *
 * Implementations retain ownership of BASS, Android foreground-service coordination, prepared
 * audio, and any platform lifecycle requirements.
 */
interface NaviampPlaybackExecution {
    fun seek(positionSeconds: Double)

    fun replayCurrent(positionSeconds: Double)
}

/** Shared command owner that translates product decisions into host audio operations. */
class NaviampPlaybackCommandController(
    private val execution: NaviampPlaybackExecution,
    private val playback: NaviampLivePlaybackController,
) {
    fun seek(request: NaviampPlaybackSeekRequest): PlaybackSeekPlan? {
        val state = playback.state.value
        val currentTrack = state.currentTrack
        val plan = planPlaybackSeek(
            isInternetRadioTrack = currentTrack?.isInternetRadioTrack() == true,
            positionSeconds = request.positionSeconds,
            currentProgress = state.progress,
            trackDurationSeconds = currentTrack?.durationSeconds,
            streamQuality = request.streamQuality,
            shouldReplayTranscodedStream = shouldReplayCurrentForSeek(request.playbackSource),
        ) ?: return null
        playback.applySeekPlan(
            progress = plan.progress,
            pendingPositionSeconds = plan.pendingSeekPositionSeconds,
            issuedAtMillis = request.issuedAtMillis,
        )
        executeSeek(plan)
        return plan
    }

    fun executeSeek(plan: PlaybackSeekPlan) {
        if (plan.shouldReplayCurrent) {
            execution.replayCurrent(plan.pendingSeekPositionSeconds)
        } else {
            execution.seek(plan.pendingSeekPositionSeconds)
        }
    }
}
