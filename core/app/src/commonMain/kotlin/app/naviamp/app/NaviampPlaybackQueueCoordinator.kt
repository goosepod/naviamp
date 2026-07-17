package app.naviamp.app

import app.naviamp.domain.Track
import app.naviamp.domain.playback.PlaybackQueueManager
import app.naviamp.domain.playback.PlaybackQueueMutationUpdate
import app.naviamp.domain.playback.PlaybackQueueSelectionUpdate
import app.naviamp.domain.playback.PlaybackQueueUpdate

/**
 * Shared coordinator for bounded, user-initiated queue mutations.
 *
 * It owns the user-visible queue snapshot. Platform playback engines still mirror the returned
 * queue and remain responsible for preparing audio and executing play, seek, and stop commands.
 */
class NaviampPlaybackQueueCoordinator(
    private val playback: NaviampLivePlaybackController,
    private val queueManager: PlaybackQueueManager = PlaybackQueueManager(),
) {
    fun appendTracks(
        tracksToAdd: List<Track>,
        label: String = "tracks",
        existingTracks: List<Track> = currentQueue.tracks,
        deduplicateExisting: Boolean = false,
        maxHistory: Int? = null,
    ): PlaybackQueueUpdate =
        queueManager.appendTracks(
            currentQueue = currentQueue,
            tracksToAdd = tracksToAdd,
            label = label,
            existingTracks = existingTracks,
            deduplicateExisting = deduplicateExisting,
            maxHistory = maxHistory,
        ).also(::commit)

    fun playNextTracks(
        tracksToAdd: List<Track>,
        label: String = "tracks",
        existingTracks: List<Track> = currentQueue.tracks,
        deduplicateExisting: Boolean = false,
        maxHistory: Int? = null,
    ): PlaybackQueueUpdate =
        queueManager.playNextTracks(
            currentQueue = currentQueue,
            tracksToAdd = tracksToAdd,
            label = label,
            existingTracks = existingTracks,
            deduplicateExisting = deduplicateExisting,
            maxHistory = maxHistory,
        ).also(::commit)

    fun removeAt(index: Int): PlaybackQueueMutationUpdate {
        val queue = currentQueue
        val updatedQueue = queue.removeAt(index)
        return PlaybackQueueMutationUpdate(
            queue = updatedQueue,
            changed = updatedQueue != queue,
            clearPreparedNext = true,
        ).also(::commit)
    }

    fun selectIndex(
        index: Int,
        moveSelectedToCurrent: Boolean = true,
    ): PlaybackQueueSelectionUpdate =
        queueManager.selectJump(
            queue = currentQueue,
            index = index,
            moveSelectedToCurrent = moveSelectedToCurrent,
        ).also(::commit)

    private val currentQueue
        get() = playback.state.value.queue

    private fun commit(update: PlaybackQueueUpdate) {
        if (update.tracksChanged) playback.updateQueue(update.queue)
    }

    private fun commit(update: PlaybackQueueMutationUpdate) {
        if (update.changed) playback.updateQueue(update.queue)
    }

    private fun commit(update: PlaybackQueueSelectionUpdate) {
        if (update.changed) playback.updateQueue(update.queue)
    }
}
