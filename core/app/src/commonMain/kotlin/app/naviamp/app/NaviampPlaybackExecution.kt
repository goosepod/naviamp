package app.naviamp.app

import app.naviamp.domain.playback.PlaybackSeekPlan

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
) {
    fun executeSeek(plan: PlaybackSeekPlan) {
        if (plan.shouldReplayCurrent) {
            execution.replayCurrent(plan.pendingSeekPositionSeconds)
        } else {
            execution.seek(plan.pendingSeekPositionSeconds)
        }
    }
}
