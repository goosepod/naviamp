package app.naviamp.domain.playback

import app.naviamp.domain.Track
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.settings.PreviousButtonBehavior

class PlaybackQueueManager(
    private val lifecycle: PlaybackQueueLifecycleManager = PlaybackQueueLifecycleManager(),
    private val mutation: PlaybackQueueMutationManager = PlaybackQueueMutationManager(),
    private val controls: PlaybackQueueControlManager = PlaybackQueueControlManager(),
    private val selection: PlaybackQueueSelectionManager = PlaybackQueueSelectionManager(controls),
) {
    fun startQueue(
        tracks: List<Track>,
        index: Int,
    ): PlaybackQueueMutationUpdate =
        lifecycle.startQueue(tracks, index)

    fun restoreQueue(
        tracks: List<Track>,
        index: Int,
    ): PlaybackQueueMutationUpdate =
        lifecycle.restoreQueue(tracks, index)

    fun restoreQueue(queue: PlaybackQueue): PlaybackQueueMutationUpdate =
        lifecycle.restoreQueue(queue)

    fun clearQueue(): PlaybackQueueMutationUpdate =
        lifecycle.clearQueue()

    fun appendTracks(
        currentQueue: PlaybackQueue,
        tracksToAdd: List<Track>,
        label: String = "tracks",
        existingTracks: List<Track> = currentQueue.tracks,
        deduplicateExisting: Boolean = false,
        maxHistory: Int? = null,
    ): PlaybackQueueUpdate =
        mutation.appendTracks(
            currentQueue = currentQueue,
            tracksToAdd = tracksToAdd,
            label = label,
            existingTracks = existingTracks,
            deduplicateExisting = deduplicateExisting,
            maxHistory = maxHistory,
        )

    fun appendQueueTracks(
        currentQueue: PlaybackQueue,
        tracksToAdd: List<Track>,
        maxHistory: Int? = null,
    ): PlaybackQueueMutationUpdate =
        mutation.appendQueueTracks(currentQueue, tracksToAdd, maxHistory)

    fun playNextTracks(
        currentQueue: PlaybackQueue,
        tracksToAdd: List<Track>,
        label: String = "tracks",
        existingTracks: List<Track> = currentQueue.tracks,
        deduplicateExisting: Boolean = false,
        maxHistory: Int? = null,
    ): PlaybackQueueUpdate =
        mutation.playNextTracks(
            currentQueue = currentQueue,
            tracksToAdd = tracksToAdd,
            label = label,
            existingTracks = existingTracks,
            deduplicateExisting = deduplicateExisting,
            maxHistory = maxHistory,
        )

    fun updateTrack(
        currentQueue: PlaybackQueue,
        updatedTrack: Track,
    ): PlaybackQueueMutationUpdate =
        mutation.updateTrack(currentQueue, updatedTrack)

    fun replaceUpcomingTracks(
        currentQueue: PlaybackQueue,
        currentTrack: Track,
        upcomingTracks: List<Track>,
        maxHistory: Int? = null,
    ): PlaybackQueueMutationUpdate =
        mutation.replaceUpcomingTracks(currentQueue, currentTrack, upcomingTracks, maxHistory)

    fun toggleUpcomingShuffle(
        currentQueue: PlaybackQueue,
        shuffledSnapshot: List<Track>?,
    ): PlaybackShuffleUpdate =
        mutation.toggleUpcomingShuffle(currentQueue, shuffledSnapshot)

    fun cycleRepeatMode(currentMode: RepeatMode): RepeatMode =
        controls.cycleRepeatMode(currentMode)

    fun shouldRestartInsteadOfPrevious(
        previousButtonBehavior: PreviousButtonBehavior,
        positionSeconds: Double?,
        restartThresholdSeconds: Double,
    ): Boolean =
        controls.shouldRestartInsteadOfPrevious(
            previousButtonBehavior = previousButtonBehavior,
            positionSeconds = positionSeconds,
            restartThresholdSeconds = restartThresholdSeconds,
        )

    fun canUsePreviousButton(
        queue: PlaybackQueue,
        previousButtonBehavior: PreviousButtonBehavior,
        positionSeconds: Double?,
        restartThresholdSeconds: Double,
    ): Boolean =
        controls.canUsePreviousButton(
            queue = queue,
            previousButtonBehavior = previousButtonBehavior,
            positionSeconds = positionSeconds,
            restartThresholdSeconds = restartThresholdSeconds,
        )

    fun canUseNextButton(
        queue: PlaybackQueue,
        repeatMode: RepeatMode,
    ): Boolean =
        controls.canUseNextButton(queue, repeatMode)

    fun previousCommand(
        queue: PlaybackQueue,
        previousButtonBehavior: PreviousButtonBehavior,
        positionSeconds: Double?,
        restartThresholdSeconds: Double,
    ): PlaybackQueueNavigationCommand =
        controls.previousCommand(queue, previousButtonBehavior, positionSeconds, restartThresholdSeconds)

    fun nextCommand(
        queue: PlaybackQueue,
        repeatMode: RepeatMode,
    ): PlaybackQueueNavigationCommand =
        controls.nextCommand(queue, repeatMode)

    fun jumpCommand(
        queue: PlaybackQueue,
        index: Int,
        moveSelectedToCurrent: Boolean = true,
    ): PlaybackQueueNavigationCommand =
        controls.jumpCommand(queue, index, moveSelectedToCurrent)

    fun selectCurrent(queue: PlaybackQueue): PlaybackQueueSelectionUpdate =
        selection.selectCurrent(queue)

    fun selectNext(
        queue: PlaybackQueue,
        repeatMode: RepeatMode,
    ): PlaybackQueueSelectionUpdate =
        selection.selectNext(queue, repeatMode)

    fun selectPrevious(
        queue: PlaybackQueue,
        repeatMode: RepeatMode,
    ): PlaybackQueueSelectionUpdate =
        selection.selectPrevious(queue, repeatMode)

    fun selectJump(
        queue: PlaybackQueue,
        index: Int,
        moveSelectedToCurrent: Boolean = true,
    ): PlaybackQueueSelectionUpdate =
        selection.selectJump(queue, index, moveSelectedToCurrent)

    fun finishCurrentTrack(
        queue: PlaybackQueue,
        repeatMode: RepeatMode,
        removePlayedTracksFromQueue: Boolean = false,
    ): PlaybackQueueFinishedUpdate =
        selection.finishCurrentTrack(
            queue = queue,
            repeatMode = repeatMode,
            removePlayedTracksFromQueue = removePlayedTracksFromQueue,
        )

    fun nextPreparedQueueIndex(
        queue: PlaybackQueue,
        repeatMode: RepeatMode,
    ): Int? =
        selection.nextPreparedQueueIndex(queue, repeatMode)

    fun shouldPrepareNextQueueIndex(
        preparedNextIndex: Int?,
        nextQueueIndex: Int,
    ): Boolean =
        selection.shouldPrepareNextQueueIndex(preparedNextIndex, nextQueueIndex)

    fun selectAdjacent(
        queue: PlaybackQueue,
        offset: Int,
        repeatMode: RepeatMode,
        wrapQueue: Boolean = true,
    ): PlaybackQueueSelectionUpdate =
        selection.selectAdjacent(queue, offset, repeatMode, wrapQueue)

    fun planAdjacentAction(
        currentTrack: Track?,
        activeQueue: List<Track>,
        offset: Int,
        repeatMode: RepeatMode,
        previousButtonBehavior: PreviousButtonBehavior,
        positionSeconds: Double?,
        restartThresholdSeconds: Double,
    ): PlaybackAdjacentAction =
        selection.planAdjacentAction(
            currentTrack = currentTrack,
            activeQueue = activeQueue,
            offset = offset,
            repeatMode = repeatMode,
            previousButtonBehavior = previousButtonBehavior,
            positionSeconds = positionSeconds,
            restartThresholdSeconds = restartThresholdSeconds,
        )
}
