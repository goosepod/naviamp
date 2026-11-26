package app.naviamp.domain.playback

import app.naviamp.domain.Track
import app.naviamp.domain.queue.PlaybackQueue

data class PlaybackQueueUpdate(
    val queue: PlaybackQueue,
    val tracksChanged: Boolean,
    val status: String,
    /** What an add to the queue did, for the user-facing notice; null for other queue changes. */
    val outcome: PlaybackQueueAddOutcome? = null,
)

enum class PlaybackQueueAddOutcome {
    AddedToQueue,
    AddedToPlayNext,
    /** Every requested track was already queued and deduplication skipped it. */
    AlreadyQueued,
    NothingFound,
}

internal fun playbackQueueAddOutcome(
    originalTracks: List<Track>,
    tracksToAdd: List<Track>,
    deduplicateExisting: Boolean,
    playNext: Boolean,
): PlaybackQueueAddOutcome = when {
    tracksToAdd.isNotEmpty() ->
        if (playNext) PlaybackQueueAddOutcome.AddedToPlayNext else PlaybackQueueAddOutcome.AddedToQueue
    deduplicateExisting && originalTracks.isNotEmpty() -> PlaybackQueueAddOutcome.AlreadyQueued
    else -> PlaybackQueueAddOutcome.NothingFound
}

fun applyPlaybackQueueUpdate(
    update: PlaybackQueueUpdate,
    setStatus: (String) -> Unit,
    replaceQueue: (PlaybackQueue) -> Unit,
): Boolean {
    setStatus(update.status)
    if (!update.tracksChanged) return false
    replaceQueue(update.queue)
    return true
}

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
    /** The finished track was the last one a sleep timer allowed; the queue did not advance. */
    val endedBySleepTimer: Boolean = false,
) {
    val shouldPlay: Boolean
        get() = command != PlaybackQueueFinishedCommand.None
}

data class PlaybackQueueSelectionUpdate(
    val queue: PlaybackQueue,
    val changed: Boolean,
)

data class PlaybackQueueMutationUpdate(
    val queue: PlaybackQueue,
    val changed: Boolean,
    val clearPreparedNext: Boolean = false,
)
