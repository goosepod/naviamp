package app.naviamp.domain.playback

import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.settings.PreviousButtonBehavior

fun shouldRestartInsteadOfPrevious(
    previousButtonBehavior: PreviousButtonBehavior,
    positionSeconds: Double?,
    restartThresholdSeconds: Double,
): Boolean =
    previousButtonBehavior == PreviousButtonBehavior.RestartThenPrevious &&
        (positionSeconds ?: 0.0) > restartThresholdSeconds

fun nextRepeatMode(mode: RepeatMode): RepeatMode =
    when (mode) {
        RepeatMode.Off -> RepeatMode.Queue
        RepeatMode.Queue -> RepeatMode.Track
        RepeatMode.Track -> RepeatMode.Off
    }
