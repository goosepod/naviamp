package app.naviamp.domain.playback

import app.naviamp.domain.Track
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.settings.PreviousButtonBehavior

fun shouldRestartInsteadOfPrevious(
    previousButtonBehavior: PreviousButtonBehavior,
    positionSeconds: Double?,
    restartThresholdSeconds: Double,
): Boolean =
    PlaybackQueueManager().shouldRestartInsteadOfPrevious(
        previousButtonBehavior = previousButtonBehavior,
        positionSeconds = positionSeconds,
        restartThresholdSeconds = restartThresholdSeconds,
    )

fun nextRepeatMode(mode: RepeatMode): RepeatMode =
    PlaybackQueueManager().cycleRepeatMode(mode)

fun canUsePreviousButton(
    queue: PlaybackQueue,
    previousButtonBehavior: PreviousButtonBehavior,
    positionSeconds: Double?,
    restartThresholdSeconds: Double,
): Boolean =
    PlaybackQueueManager().canUsePreviousButton(
        queue = queue,
        previousButtonBehavior = previousButtonBehavior,
        positionSeconds = positionSeconds,
        restartThresholdSeconds = restartThresholdSeconds,
    )

fun canUseNextButton(
    queue: PlaybackQueue,
    repeatMode: RepeatMode,
): Boolean =
    PlaybackQueueManager().canUseNextButton(
        queue = queue,
        repeatMode = repeatMode,
    )

sealed interface PlaybackAdjacentAction {
    data object None : PlaybackAdjacentAction
    data object RestartCurrent : PlaybackAdjacentAction
    data class PlayTrack(
        val track: Track,
        val queue: List<Track>,
    ) : PlaybackAdjacentAction
}

fun planPlaybackAdjacentAction(
    currentTrack: Track?,
    activeQueue: List<Track>,
    offset: Int,
    repeatMode: RepeatMode,
    previousButtonBehavior: PreviousButtonBehavior,
    positionSeconds: Double?,
    restartThresholdSeconds: Double,
): PlaybackAdjacentAction =
    PlaybackQueueManager().planAdjacentAction(
        currentTrack = currentTrack,
        activeQueue = activeQueue,
        offset = offset,
        repeatMode = repeatMode,
        previousButtonBehavior = previousButtonBehavior,
        positionSeconds = positionSeconds,
        restartThresholdSeconds = restartThresholdSeconds,
    )
