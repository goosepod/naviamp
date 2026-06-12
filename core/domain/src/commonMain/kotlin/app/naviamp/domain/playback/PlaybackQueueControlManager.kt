package app.naviamp.domain.playback

import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.settings.PreviousButtonBehavior

class PlaybackQueueControlManager {
    fun cycleRepeatMode(currentMode: RepeatMode): RepeatMode =
        when (currentMode) {
            RepeatMode.Off -> RepeatMode.Queue
            RepeatMode.Queue -> RepeatMode.Track
            RepeatMode.Track -> RepeatMode.Off
        }

    fun shouldRestartInsteadOfPrevious(
        previousButtonBehavior: PreviousButtonBehavior,
        positionSeconds: Double?,
        restartThresholdSeconds: Double,
    ): Boolean =
        previousButtonBehavior == PreviousButtonBehavior.RestartThenPrevious &&
            (positionSeconds ?: 0.0) > restartThresholdSeconds

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

    fun previousCommand(
        queue: PlaybackQueue,
        previousButtonBehavior: PreviousButtonBehavior,
        positionSeconds: Double?,
        restartThresholdSeconds: Double,
    ): PlaybackQueueNavigationCommand =
        when {
            shouldRestartInsteadOfPrevious(
                previousButtonBehavior = previousButtonBehavior,
                positionSeconds = positionSeconds,
                restartThresholdSeconds = restartThresholdSeconds,
            ) -> PlaybackQueueNavigationCommand.RestartCurrent
            queue.hasPrevious() -> PlaybackQueueNavigationCommand.Previous
            else -> PlaybackQueueNavigationCommand.None
        }

    fun nextCommand(
        queue: PlaybackQueue,
        repeatMode: RepeatMode,
    ): PlaybackQueueNavigationCommand =
        if (canUseNextButton(queue, repeatMode)) {
            PlaybackQueueNavigationCommand.Next
        } else {
            PlaybackQueueNavigationCommand.None
        }

    fun jumpCommand(
        queue: PlaybackQueue,
        index: Int,
        moveSelectedToCurrent: Boolean = true,
    ): PlaybackQueueNavigationCommand =
        if (index in queue.tracks.indices && index != queue.currentIndex) {
            PlaybackQueueNavigationCommand.JumpTo(
                index = index,
                moveSelectedToCurrent = moveSelectedToCurrent,
            )
        } else {
            PlaybackQueueNavigationCommand.None
        }
}
