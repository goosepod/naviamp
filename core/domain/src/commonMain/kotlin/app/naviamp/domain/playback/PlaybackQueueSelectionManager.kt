package app.naviamp.domain.playback

import app.naviamp.domain.Track
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.settings.PreviousButtonBehavior

class PlaybackQueueSelectionManager(
    private val controls: PlaybackQueueControlManager = PlaybackQueueControlManager(),
) {
    fun selectCurrent(queue: PlaybackQueue): PlaybackQueueSelectionUpdate =
        if (queue.currentIndex in queue.tracks.indices) {
            PlaybackQueueSelectionUpdate(queue = queue, changed = true)
        } else {
            PlaybackQueueSelectionUpdate(queue = queue, changed = false)
        }

    fun selectNext(
        queue: PlaybackQueue,
        repeatMode: RepeatMode,
    ): PlaybackQueueSelectionUpdate =
        selectAdjacent(
            queue = queue,
            offset = 1,
            repeatMode = repeatMode,
            wrapQueue = true,
        )

    fun selectPrevious(
        queue: PlaybackQueue,
        repeatMode: RepeatMode,
    ): PlaybackQueueSelectionUpdate =
        selectAdjacent(
            queue = queue,
            offset = -1,
            repeatMode = repeatMode,
            wrapQueue = false,
        )

    fun selectJump(
        queue: PlaybackQueue,
        index: Int,
        moveSelectedToCurrent: Boolean = true,
    ): PlaybackQueueSelectionUpdate =
        if (index in queue.tracks.indices && index != queue.currentIndex) {
            PlaybackQueueSelectionUpdate(
                queue = queue.jumpTo(index, moveSelectedToCurrent),
                changed = true,
            )
        } else {
            PlaybackQueueSelectionUpdate(queue = queue, changed = false)
        }

    fun finishCurrentTrack(
        queue: PlaybackQueue,
        repeatMode: RepeatMode,
        removePlayedTracksFromQueue: Boolean = false,
    ): PlaybackQueueFinishedUpdate {
        val nextIndex = queue.nextIndex(repeatMode = repeatMode)
            ?: return PlaybackQueueFinishedUpdate(
                queue = if (removePlayedTracksFromQueue && repeatMode == RepeatMode.Off) {
                    PlaybackQueue()
                } else {
                    queue
                },
                command = PlaybackQueueFinishedCommand.None,
            )
        val command = if (nextIndex == queue.currentIndex) {
            PlaybackQueueFinishedCommand.ReplayCurrent
        } else {
            PlaybackQueueFinishedCommand.PlayNext
        }
        val nextQueue = queue.copy(currentIndex = nextIndex)
            .let { updatedQueue ->
                if (
                    removePlayedTracksFromQueue &&
                    repeatMode == RepeatMode.Off &&
                    command == PlaybackQueueFinishedCommand.PlayNext
                ) {
                    updatedQueue.removePlayedHistory()
                } else {
                    updatedQueue
                }
            }
        return PlaybackQueueFinishedUpdate(
            queue = nextQueue,
            command = command,
        )
    }

    fun nextPreparedQueueIndex(
        queue: PlaybackQueue,
        repeatMode: RepeatMode,
    ): Int? =
        queue.nextIndex(repeatMode = repeatMode)

    fun shouldPrepareNextQueueIndex(
        preparedNextIndex: Int?,
        nextQueueIndex: Int,
    ): Boolean =
        preparedNextIndex != nextQueueIndex

    fun selectAdjacent(
        queue: PlaybackQueue,
        offset: Int,
        repeatMode: RepeatMode,
        wrapQueue: Boolean = true,
    ): PlaybackQueueSelectionUpdate {
        val nextIndex = queue.adjacentIndex(
            offset = offset,
            repeatMode = repeatMode,
            repeatTrack = false,
            wrapQueue = wrapQueue,
        ) ?: return PlaybackQueueSelectionUpdate(queue = queue, changed = false)
        return PlaybackQueueSelectionUpdate(
            queue = queue.copy(currentIndex = nextIndex),
            changed = nextIndex != queue.currentIndex,
        )
    }

    fun planAdjacentAction(
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
            controls.shouldRestartInsteadOfPrevious(
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
}
