package app.naviamp.app

import app.naviamp.domain.Track
import app.naviamp.domain.playback.PlaybackQueueManager
import app.naviamp.domain.playback.PlaybackQueueFinishedUpdate
import app.naviamp.domain.playback.PlaybackQueueNavigationCommand
import app.naviamp.domain.playback.PlaybackQueueMutationUpdate
import app.naviamp.domain.playback.PlaybackQueueSelectionUpdate
import app.naviamp.domain.playback.PlaybackShuffleUpdate
import app.naviamp.domain.playback.PlaybackQueueUpdate
import app.naviamp.domain.playback.DefaultPreviousRestartThresholdSeconds
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.PlaybackQueueGroup
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.radio.generatedRadioTracksToAppend
import app.naviamp.domain.radio.generatedRadioUpcomingTracks
import app.naviamp.domain.radio.generatedRadioUpcomingTracksToAppend
import app.naviamp.domain.settings.PreviousButtonBehavior

/**
 * Shared coordinator for queue lifecycle decisions and bounded user mutations.
 *
 * It owns the user-visible queue snapshot. Platform playback engines still mirror the returned
 * queue and remain responsible for preparing audio and executing play, seek, and stop commands.
 */
class NaviampPlaybackQueueCoordinator(
    private val playback: NaviampLivePlaybackController,
    private val queueManager: PlaybackQueueManager = PlaybackQueueManager(),
) {
    fun startQueue(
        tracks: List<Track>,
        index: Int,
    ): PlaybackQueueMutationUpdate =
        queueManager.startQueue(tracks, index).also(::commit)

    fun replaceQueue(
        queue: PlaybackQueue,
        clearPreparedNext: Boolean = true,
    ): PlaybackQueueMutationUpdate =
        PlaybackQueueMutationUpdate(
            queue = queue,
            changed = queue != currentQueue,
            clearPreparedNext = clearPreparedNext,
        ).also(::commit)

    fun restoreQueue(queue: PlaybackQueue): PlaybackQueueMutationUpdate =
        queueManager.restoreQueue(queue).also(::commit)

    fun clearQueue(): PlaybackQueueMutationUpdate =
        queueManager.clearQueue().also(::commit)

    fun appendTracks(
        tracksToAdd: List<Track>,
        label: String = "tracks",
        existingTracks: List<Track> = currentQueue.tracks,
        deduplicateExisting: Boolean = false,
        maxHistory: Int? = null,
        group: PlaybackQueueGroup? = null,
    ): PlaybackQueueUpdate =
        queueManager.appendTracks(
            currentQueue = currentQueue,
            tracksToAdd = tracksToAdd,
            label = label,
            existingTracks = existingTracks,
            deduplicateExisting = deduplicateExisting,
            maxHistory = maxHistory,
            group = group,
        ).also(::commit)

    fun playNextTracks(
        tracksToAdd: List<Track>,
        label: String = "tracks",
        existingTracks: List<Track> = currentQueue.tracks,
        deduplicateExisting: Boolean = false,
        maxHistory: Int? = null,
        group: PlaybackQueueGroup? = null,
    ): PlaybackQueueUpdate =
        queueManager.playNextTracks(
            currentQueue = currentQueue,
            tracksToAdd = tracksToAdd,
            label = label,
            existingTracks = existingTracks,
            deduplicateExisting = deduplicateExisting,
            maxHistory = maxHistory,
            group = group,
        ).also(::commit)

    fun playNextTrack(
        track: Track,
        maxHistory: Int? = null,
    ): PlaybackQueueUpdate =
        queueManager.playNextTrack(currentQueue, track, maxHistory).also(::commit)

    fun removeAt(index: Int): PlaybackQueueMutationUpdate {
        val queue = currentQueue
        val updatedQueue = queue.removeAt(index)
        return PlaybackQueueMutationUpdate(
            queue = updatedQueue,
            changed = updatedQueue != queue,
            clearPreparedNext = true,
        ).also(::commit)
    }

    fun moveToNext(index: Int): PlaybackQueueMutationUpdate =
        mutateQueue(clearPreparedNext = true) { queue -> queue.moveToNext(index) }

    fun moveToPlayNext(index: Int): PlaybackQueueMutationUpdate =
        mutateQueue(clearPreparedNext = true) { queue -> queue.moveToPlayNext(index) }

    fun clearUpcoming(): PlaybackQueueMutationUpdate =
        mutateQueue(clearPreparedNext = true, transform = PlaybackQueue::clearUpcoming)

    fun retainCurrentOnly(): PlaybackQueueMutationUpdate =
        mutateQueue(clearPreparedNext = true, transform = PlaybackQueue::retainCurrentOnly)

    fun updateTrack(updatedTrack: Track): PlaybackQueueMutationUpdate =
        queueManager.updateTrack(currentQueue, updatedTrack).also(::commit)

    fun replaceUpcomingTracks(
        currentTrack: Track,
        upcomingTracks: List<Track>,
        maxHistory: Int? = null,
    ): PlaybackQueueMutationUpdate =
        queueManager.replaceUpcomingTracks(
            currentQueue = currentQueue,
            currentTrack = currentTrack,
            upcomingTracks = upcomingTracks,
            maxHistory = maxHistory,
        ).also(::commit)

    fun appendGeneratedRadioTracks(
        seedTrack: Track,
        fetchedTracks: List<Track>,
        requestIsCurrent: Boolean,
        maxHistory: Int? = null,
    ): PlaybackQueueUpdate =
        appendTracks(
            tracksToAdd = if (requestIsCurrent) {
                generatedRadioTracksToAppend(seedTrack, fetchedTracks, currentQueue.tracks)
            } else {
                emptyList()
            },
            label = "radio tracks",
            maxHistory = maxHistory,
        )

    fun replaceGeneratedRadioUpcomingTracks(
        currentTrack: Track,
        fetchedTracks: List<Track>,
        requestIsCurrent: Boolean,
        maxHistory: Int? = null,
    ): PlaybackQueueMutationUpdate =
        if (requestIsCurrent) {
            replaceUpcomingTracks(
                currentTrack = currentTrack,
                upcomingTracks = generatedRadioUpcomingTracks(currentTrack, fetchedTracks),
                maxHistory = maxHistory,
            )
        } else {
            unchangedMutation()
        }

    fun appendGeneratedRadioUpcomingTracks(
        currentTrack: Track,
        fetchedTracks: List<Track>,
        requestIsCurrent: Boolean,
        maxHistory: Int? = null,
    ): PlaybackQueueUpdate =
        appendTracks(
            tracksToAdd = if (requestIsCurrent) {
                generatedRadioUpcomingTracksToAppend(currentTrack, fetchedTracks, currentQueue.tracks)
            } else {
                emptyList()
            },
            label = "radio tracks",
            maxHistory = maxHistory,
        )

    fun appendSonicContinuationTracks(tracks: List<Track>): PlaybackQueueUpdate =
        appendTracks(
            tracksToAdd = tracks,
            label = "Sonic Autoplay tracks",
            deduplicateExisting = true,
        )

    fun toggleUpcomingShuffle(): PlaybackShuffleUpdate =
        queueManager.toggleUpcomingShuffle(
            currentQueue,
            playback.state.value.shuffledUpNextSnapshot,
        ).also(::commit)

    fun clearShuffleSnapshot() {
        playback.updateShuffledUpNextSnapshot(null)
    }

    fun cycleRepeatMode(): RepeatMode =
        queueManager.cycleRepeatMode(playback.state.value.repeatMode).also(playback::updateRepeatMode)

    fun previousCommand(
        previousButtonBehavior: PreviousButtonBehavior,
    ): PlaybackQueueNavigationCommand =
        queueManager.previousCommand(
            queue = currentQueue,
            previousButtonBehavior = previousButtonBehavior,
            positionSeconds = playback.state.value.progress.positionSeconds,
            restartThresholdSeconds = DefaultPreviousRestartThresholdSeconds,
        )

    fun canUsePreviousButton(previousButtonBehavior: PreviousButtonBehavior): Boolean =
        queueManager.canUsePreviousButton(
            queue = currentQueue,
            previousButtonBehavior = previousButtonBehavior,
            positionSeconds = playback.state.value.progress.positionSeconds,
            restartThresholdSeconds = DefaultPreviousRestartThresholdSeconds,
        )

    fun canUseNextButton(): Boolean =
        queueManager.canUseNextButton(
            queue = currentQueue,
            repeatMode = playback.state.value.repeatMode,
        )

    fun nextCommand(): PlaybackQueueNavigationCommand =
        queueManager.nextCommand(currentQueue, playback.state.value.repeatMode)

    fun selectNext(): PlaybackQueueSelectionUpdate =
        queueManager.selectNext(currentQueue, playback.state.value.repeatMode).also(::commit)

    fun selectPrevious(): PlaybackQueueSelectionUpdate =
        queueManager.selectPrevious(currentQueue, playback.state.value.repeatMode).also(::commit)

    fun selectAdjacent(
        offset: Int,
        wrapQueue: Boolean = true,
    ): PlaybackQueueSelectionUpdate =
        queueManager.selectAdjacent(
            queue = currentQueue,
            offset = offset,
            repeatMode = playback.state.value.repeatMode,
            wrapQueue = wrapQueue,
        ).also(::commit)

    fun finishCurrentTrack(removePlayedTracksFromQueue: Boolean = false): PlaybackQueueFinishedUpdate =
        queueManager.finishCurrentTrack(
            queue = currentQueue,
            repeatMode = playback.state.value.repeatMode,
            removePlayedTracksFromQueue = removePlayedTracksFromQueue,
        ).also(::commit)

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

    private fun commit(update: PlaybackShuffleUpdate) {
        if (update.changed) {
            playback.replace(
                playback.state.value.copy(
                    queue = update.queue,
                    shuffledUpNextSnapshot = update.shuffledSnapshot,
                ),
            )
        }
    }

    private fun commit(update: PlaybackQueueFinishedUpdate) {
        if (update.queue != currentQueue) playback.updateQueue(update.queue)
    }

    private inline fun mutateQueue(
        clearPreparedNext: Boolean,
        transform: (PlaybackQueue) -> PlaybackQueue,
    ): PlaybackQueueMutationUpdate {
        val queue = currentQueue
        val updatedQueue = transform(queue)
        return PlaybackQueueMutationUpdate(
            queue = updatedQueue,
            changed = updatedQueue != queue,
            clearPreparedNext = clearPreparedNext,
        ).also(::commit)
    }

    private fun unchangedMutation(): PlaybackQueueMutationUpdate =
        PlaybackQueueMutationUpdate(
            queue = currentQueue,
            changed = false,
        )
}
