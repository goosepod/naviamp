package app.naviamp.domain.playback

import app.naviamp.domain.Track
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.queue.resolveTrackOccurrenceIndex

class PlaybackQueueController(
    initialQueue: PlaybackQueue = PlaybackQueue(),
) {
    private val queueManager = PlaybackQueueManager()
    private var preparedNextInvalidationHandler: (() -> Unit)? = null

    var queue: PlaybackQueue = initialQueue
        private set

    var repeatMode: RepeatMode = RepeatMode.Off
        private set

    var playbackSessionId: Int = 0
        private set

    var preparedNextIndex: Int? = null
        private set

    fun start(
        tracks: List<Track>,
        index: Int,
    ): PlaybackQueueSelection? {
        val update = queueManager.startQueue(tracks, index)
        if (!update.changed) return null
        playbackSessionId += 1
        applyMutation(update)
        return PlaybackQueueSelection(queue = queue, sessionId = playbackSessionId)
    }

    fun restore(
        tracks: List<Track>,
        index: Int,
    ): Boolean {
        val update = queueManager.restoreQueue(tracks, index)
        if (!update.changed) return false
        playbackSessionId += 1
        applyMutation(update)
        return true
    }

    fun restore(restoredQueue: PlaybackQueue): Boolean {
        val update = queueManager.restoreQueue(restoredQueue)
        if (!update.changed) return false
        playbackSessionId += 1
        applyMutation(update)
        return true
    }

    fun restoreOrClear(restoredQueue: PlaybackQueue): Boolean {
        if (restore(restoredQueue)) return true
        clear()
        return false
    }

    fun clear() {
        val update = queueManager.clearQueue()
        playbackSessionId += 1
        applyMutation(update)
    }

    fun replaceQueue(
        queue: PlaybackQueue,
        incrementSession: Boolean = false,
        clearPreparedNext: Boolean = true,
    ) {
        if (incrementSession) playbackSessionId += 1
        this.queue = queue
        if (clearPreparedNext) invalidatePreparedNext()
    }

    fun updateTrack(updatedTrack: Track): PlaybackQueue? {
        val update = queueManager.updateTrack(queue, updatedTrack)
        if (!update.changed) return null
        applyMutation(update)
        return update.queue
    }

    fun appendTracks(
        tracks: List<Track>,
        maxHistory: Int? = null,
    ): PlaybackQueue? {
        val update = queueManager.appendQueueTracks(
            currentQueue = queue,
            tracksToAdd = tracks,
            maxHistory = maxHistory,
        )
        if (!update.changed) return null
        applyMutation(update)
        return update.queue
    }

    fun replaceUpcomingTracks(
        currentTrack: Track,
        upcomingTracks: List<Track>,
        maxHistory: Int? = null,
    ): PlaybackQueue {
        val update = queueManager.replaceUpcomingTracks(
            currentQueue = queue,
            currentTrack = currentTrack,
            upcomingTracks = upcomingTracks,
            maxHistory = maxHistory,
        )
        applyMutation(update)
        return update.queue
    }

    fun setRepeatMode(mode: RepeatMode) {
        repeatMode = mode
    }

    fun next(): PlaybackQueueSelection? {
        val update = queueManager.selectNext(queue, repeatMode)
        if (!update.changed) return null
        playbackSessionId += 1
        return selectQueue(update.queue, playbackSessionId)
    }

    fun playCurrent(): PlaybackQueueSelection? {
        val update = queueManager.selectCurrent(queue)
        if (!update.changed) return null
        playbackSessionId += 1
        return selectQueue(update.queue, playbackSessionId)
    }

    fun jumpTo(
        index: Int,
        moveSelectedToCurrent: Boolean = true,
    ): PlaybackQueueSelection? {
        val update = queueManager.selectJump(
            queue = queue,
            index = index,
            moveSelectedToCurrent = moveSelectedToCurrent,
        )
        if (!update.changed) return null
        playbackSessionId += 1
        return selectQueue(update.queue, playbackSessionId)
    }

    fun previous(): PlaybackQueueSelection? {
        val update = queueManager.selectPrevious(queue, repeatMode)
        if (!update.changed) return null
        playbackSessionId += 1
        return selectQueue(update.queue, playbackSessionId)
    }

    fun adjacent(
        offset: Int,
        wrapQueue: Boolean = true,
    ): PlaybackQueueSelection? {
        val update = queueManager.selectAdjacent(
            queue = queue,
            offset = offset,
            repeatMode = repeatMode,
            wrapQueue = wrapQueue,
        )
        if (!update.changed) return null
        playbackSessionId += 1
        return selectQueue(update.queue, playbackSessionId)
    }

    fun toggleUpcomingShuffle(shuffledSnapshot: List<Track>?): PlaybackShuffleToggle? {
        val update = queueManager.toggleUpcomingShuffle(queue, shuffledSnapshot)
        if (!update.changed) return null
        applyMutation(
            PlaybackQueueMutationUpdate(
                queue = update.queue,
                changed = true,
                clearPreparedNext = true,
            ),
        )
        return PlaybackShuffleToggle(queue = queue, shuffledSnapshot = update.shuffledSnapshot)
    }

    fun finishedSelection(removePlayedTracksFromQueue: Boolean = false): PlaybackQueueSelection? {
        val update = queueManager.finishCurrentTrack(
            queue = queue,
            repeatMode = repeatMode,
            removePlayedTracksFromQueue = removePlayedTracksFromQueue,
        )
        if (!update.shouldPlay) {
            if (update.queue != queue) applyMutation(
                PlaybackQueueMutationUpdate(
                    queue = update.queue,
                    changed = true,
                    clearPreparedNext = true,
                ),
            )
            return null
        }
        playbackSessionId += 1
        return selectQueue(update.queue, playbackSessionId)
    }

    fun nextGaplessQueueIndex(): Int? =
        queueManager.nextPreparedQueueIndex(queue, repeatMode)

    fun nextGaplessQueueIndexForExternalQueue(
        tracks: List<Track>,
        currentTrack: Track?,
        currentIndex: Int? = null,
        repeatMode: RepeatMode,
        playNextCount: Int = 0,
    ): Int? {
        val current = currentTrack ?: return null
        val resolvedCurrentIndex = resolveTrackOccurrenceIndex(
            tracks = tracks,
            track = current,
            preferredIndex = currentIndex,
        ) ?: return null
        replaceQueue(
            queue = PlaybackQueue(
                tracks = tracks,
                currentIndex = resolvedCurrentIndex,
                playNextCount = playNextCount.coerceIn(0, tracks.size - resolvedCurrentIndex - 1),
            ),
            clearPreparedNext = false,
        )
        setRepeatMode(repeatMode)
        return nextGaplessQueueIndex()
    }

    fun shouldPrepareNext(index: Int): Boolean =
        queueManager.shouldPrepareNextQueueIndex(
            preparedNextIndex = preparedNextIndex,
            nextQueueIndex = index,
        )

    fun markPreparedNext(index: Int) {
        preparedNextIndex = index
    }

    fun clearPreparedNext() {
        preparedNextIndex = null
    }

    fun setPreparedNextInvalidationHandler(handler: (() -> Unit)?) {
        preparedNextInvalidationHandler = handler
    }

    private fun selectQueue(
        selectedQueue: PlaybackQueue,
        sessionId: Int,
    ): PlaybackQueueSelection? {
        if (selectedQueue.currentIndex !in selectedQueue.tracks.indices) return null
        queue = selectedQueue
        preparedNextIndex = null
        return PlaybackQueueSelection(queue = queue, sessionId = sessionId)
    }

    private fun applyMutation(update: PlaybackQueueMutationUpdate) {
        queue = update.queue
        if (update.clearPreparedNext) invalidatePreparedNext()
    }

    private fun invalidatePreparedNext() {
        if (preparedNextIndex == null) return
        preparedNextIndex = null
        preparedNextInvalidationHandler?.invoke()
    }
}

data class PlaybackQueueSelection(
    val queue: PlaybackQueue,
    val sessionId: Int,
) {
    val track: Track?
        get() = queue.current
}

data class PlaybackShuffleToggle(
    val queue: PlaybackQueue,
    val shuffledSnapshot: List<Track>?,
)
