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
    previousButtonBehavior == PreviousButtonBehavior.RestartThenPrevious &&
        (positionSeconds ?: 0.0) > restartThresholdSeconds

fun nextRepeatMode(mode: RepeatMode): RepeatMode =
    PlaybackQueueManager().cycleRepeatMode(mode)

fun canUsePreviousButton(
    queue: PlaybackQueue,
    previousButtonBehavior: PreviousButtonBehavior,
    positionSeconds: Double?,
    restartThresholdSeconds: Double,
): Boolean =
    queue.hasPrevious() ||
        shouldRestartInsteadOfPrevious(
            previousButtonBehavior = previousButtonBehavior,
            positionSeconds = positionSeconds,
            restartThresholdSeconds = restartThresholdSeconds,
        )

fun canUseNextButton(
    queue: PlaybackQueue,
    repeatMode: RepeatMode,
): Boolean =
    queue.hasNext() ||
        queue.nextIndex(repeatMode = repeatMode, repeatTrack = false) != null

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
): PlaybackAdjacentAction {
    val track = currentTrack ?: return PlaybackAdjacentAction.None
    if (
        offset < 0 &&
        shouldRestartInsteadOfPrevious(
            previousButtonBehavior = previousButtonBehavior,
            positionSeconds = positionSeconds,
            restartThresholdSeconds = restartThresholdSeconds,
        )
    ) {
        return PlaybackAdjacentAction.RestartCurrent
    }
    val currentIndex = activeQueue.indexOfFirst { it.id == track.id }
    if (currentIndex < 0) return PlaybackAdjacentAction.None
    val nextIndex = PlaybackQueue(tracks = activeQueue, currentIndex = currentIndex)
        .adjacentIndex(offset = offset, repeatMode = repeatMode)
        ?: return PlaybackAdjacentAction.None
    val nextTrack = activeQueue.getOrNull(nextIndex) ?: return PlaybackAdjacentAction.None
    return PlaybackAdjacentAction.PlayTrack(nextTrack, activeQueue)
}
