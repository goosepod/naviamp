package app.naviamp.domain.playback

import app.naviamp.domain.Track
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.settings.PreviousButtonBehavior

data class PlaybackQueueUpdate(
    val queue: PlaybackQueue,
    val tracksChanged: Boolean,
    val status: String,
)

data class PlaybackShuffleUpdate(
    val queue: PlaybackQueue,
    val shuffledSnapshot: List<Track>?,
    val changed: Boolean,
)

sealed interface PlaybackQueueNavigationCommand {
    data object None : PlaybackQueueNavigationCommand
    data object RestartCurrent : PlaybackQueueNavigationCommand
    data object Previous : PlaybackQueueNavigationCommand
    data object Next : PlaybackQueueNavigationCommand
    data class JumpTo(
        val index: Int,
        val moveSelectedToCurrent: Boolean,
    ) : PlaybackQueueNavigationCommand
}

sealed interface PlaybackQueueFinishedCommand {
    data object None : PlaybackQueueFinishedCommand
    data object ReplayCurrent : PlaybackQueueFinishedCommand
    data object PlayNext : PlaybackQueueFinishedCommand
}

data class PlaybackQueueFinishedUpdate(
    val queue: PlaybackQueue,
    val command: PlaybackQueueFinishedCommand,
) {
    val shouldPlay: Boolean
        get() = command != PlaybackQueueFinishedCommand.None
}

class PlaybackQueueManager {
    fun appendTracks(
        currentQueue: PlaybackQueue,
        tracksToAdd: List<Track>,
        label: String = "tracks",
        existingTracks: List<Track> = currentQueue.tracks,
        deduplicateExisting: Boolean = false,
        maxHistory: Int? = null,
    ): PlaybackQueueUpdate {
        val tracks = appendableTracks(
            tracksToAdd = tracksToAdd,
            existingTracks = existingTracks,
            deduplicateExisting = deduplicateExisting,
        )
        val nextQueue = when {
            tracks.isEmpty() -> currentQueue
            currentQueue.isInactiveEmpty -> PlaybackQueue(tracks = tracks, currentIndex = 0)
            else -> currentQueue.appendTracks(tracks, maxHistory = maxHistory)
        }
        return PlaybackQueueUpdate(
            queue = nextQueue,
            tracksChanged = tracks.isNotEmpty(),
            status = queueAppendStatus(
                originalTracks = tracksToAdd,
                tracksToAdd = tracks,
                label = label,
                deduplicateExisting = deduplicateExisting,
            ),
        )
    }

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

    fun finishCurrentTrack(
        queue: PlaybackQueue,
        repeatMode: RepeatMode,
    ): PlaybackQueueFinishedUpdate {
        val nextIndex = queue.nextIndex(repeatMode = repeatMode)
            ?: return PlaybackQueueFinishedUpdate(
                queue = queue,
                command = PlaybackQueueFinishedCommand.None,
            )
        val nextQueue = queue.copy(currentIndex = nextIndex)
        return PlaybackQueueFinishedUpdate(
            queue = nextQueue,
            command = if (nextIndex == queue.currentIndex) {
                PlaybackQueueFinishedCommand.ReplayCurrent
            } else {
                PlaybackQueueFinishedCommand.PlayNext
            },
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

    fun toggleUpcomingShuffle(
        currentQueue: PlaybackQueue,
        shuffledSnapshot: List<Track>?,
    ): PlaybackShuffleUpdate {
        val toggle = currentQueue.toggleUpcomingShuffle(shuffledSnapshot)
            ?: return PlaybackShuffleUpdate(
                queue = currentQueue,
                shuffledSnapshot = shuffledSnapshot,
                changed = false,
            )
        return PlaybackShuffleUpdate(
            queue = toggle.queue,
            shuffledSnapshot = toggle.shuffledSnapshot,
            changed = true,
        )
    }

    private val PlaybackQueue.isInactiveEmpty: Boolean
        get() = currentIndex < 0 && tracks.isEmpty()
}

fun appendableTracks(
    tracksToAdd: List<Track>,
    existingTracks: List<Track> = emptyList(),
    deduplicateExisting: Boolean = false,
): List<Track> {
    if (!deduplicateExisting) return tracksToAdd
    val existingIds = existingTracks.map { it.id }.toSet()
    return tracksToAdd.filterNot { track -> track.id in existingIds }
}

fun queueAppendStatus(
    originalTracks: List<Track>,
    tracksToAdd: List<Track>,
    label: String = "tracks",
    deduplicateExisting: Boolean = false,
): String =
    if (tracksToAdd.isEmpty()) {
        if (deduplicateExisting && originalTracks.isNotEmpty()) {
            "${label.replaceFirstChar { it.uppercase() }} are already in the queue."
        } else {
            "No tracks found."
        }
    } else {
        val displayLabel = if (tracksToAdd.size == 1 && label == "tracks") "track" else label
        "Added ${tracksToAdd.size} $displayLabel to queue."
    }
