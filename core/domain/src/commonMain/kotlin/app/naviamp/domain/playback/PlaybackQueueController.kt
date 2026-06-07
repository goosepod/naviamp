package app.naviamp.domain.playback

import app.naviamp.domain.Track
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode

class PlaybackQueueController(
    initialQueue: PlaybackQueue = PlaybackQueue(),
) {
    private val queueManager = PlaybackQueueManager()

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
        val selectedIndex = index.takeIf { it in tracks.indices } ?: return null
        playbackSessionId += 1
        return selectQueueIndex(tracks, selectedIndex, playbackSessionId)
    }

    fun restore(
        tracks: List<Track>,
        index: Int,
    ): Boolean {
        if (tracks.isEmpty() || index !in tracks.indices) return false
        playbackSessionId += 1
        queue = PlaybackQueue(tracks = tracks, currentIndex = index)
        preparedNextIndex = null
        return true
    }

    fun clear() {
        playbackSessionId += 1
        queue = PlaybackQueue()
        preparedNextIndex = null
    }

    fun replaceQueue(
        queue: PlaybackQueue,
        incrementSession: Boolean = false,
        clearPreparedNext: Boolean = true,
    ) {
        if (incrementSession) playbackSessionId += 1
        this.queue = queue
        if (clearPreparedNext) preparedNextIndex = null
    }

    fun updateTrack(updatedTrack: Track): PlaybackQueue? {
        val updatedTracks = queue.tracks.map { track ->
            if (track.id == updatedTrack.id) updatedTrack else track
        }
        if (updatedTracks == queue.tracks) return null
        queue = queue.copy(tracks = updatedTracks)
        return queue
    }

    fun appendTracks(
        tracks: List<Track>,
        maxHistory: Int? = null,
    ): PlaybackQueue? {
        if (tracks.isEmpty()) return null
        queue = queue.appendTracks(tracks, maxHistory)
        return queue
    }

    fun replaceUpcomingTracks(
        currentTrack: Track,
        upcomingTracks: List<Track>,
        maxHistory: Int? = null,
    ): PlaybackQueue {
        queue = queue.replaceUpcomingTracks(currentTrack, upcomingTracks, maxHistory)
        preparedNextIndex = null
        return queue
    }

    fun setRepeatMode(mode: RepeatMode) {
        repeatMode = mode
    }

    fun next(): PlaybackQueueSelection? {
        val update = queueManager.selectNext(queue, repeatMode)
        if (!update.changed) return null
        playbackSessionId += 1
        return selectQueueIndex(update.queue.tracks, update.queue.currentIndex, playbackSessionId)
    }

    fun playCurrent(): PlaybackQueueSelection? {
        val update = queueManager.selectCurrent(queue)
        if (!update.changed) return null
        playbackSessionId += 1
        return selectQueueIndex(update.queue.tracks, update.queue.currentIndex, playbackSessionId)
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
        return selectQueueIndex(update.queue.tracks, update.queue.currentIndex, playbackSessionId)
    }

    fun previous(): PlaybackQueueSelection? {
        val update = queueManager.selectPrevious(queue, repeatMode)
        if (!update.changed) return null
        playbackSessionId += 1
        return selectQueueIndex(update.queue.tracks, update.queue.currentIndex, playbackSessionId)
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
        return selectQueueIndex(update.queue.tracks, update.queue.currentIndex, playbackSessionId)
    }

    fun toggleUpcomingShuffle(shuffledSnapshot: List<Track>?): PlaybackShuffleToggle? {
        val result = queue.toggleUpcomingShuffle(shuffledSnapshot) ?: return null
        queue = result.queue
        preparedNextIndex = null
        return PlaybackShuffleToggle(queue = queue, shuffledSnapshot = result.shuffledSnapshot)
    }

    fun finishedSelection(): PlaybackQueueSelection? {
        val update = queueManager.finishCurrentTrack(
            queue = queue,
            repeatMode = repeatMode,
        )
        if (!update.shouldPlay) return null
        playbackSessionId += 1
        return selectQueueIndex(update.queue.tracks, update.queue.currentIndex, playbackSessionId)
    }

    fun nextGaplessQueueIndex(): Int? =
        queueManager.nextPreparedQueueIndex(queue, repeatMode)

    fun nextGaplessQueueIndexForExternalQueue(
        tracks: List<Track>,
        currentTrack: Track?,
        repeatMode: RepeatMode,
    ): Int? {
        val current = currentTrack ?: return null
        val currentIndex = tracks.indexOfFirst { it.id == current.id }
        if (currentIndex < 0) return null
        replaceQueue(
            queue = PlaybackQueue(tracks = tracks, currentIndex = currentIndex),
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

    private fun selectQueueIndex(
        tracks: List<Track>,
        index: Int,
        sessionId: Int,
    ): PlaybackQueueSelection? {
        if (index !in tracks.indices) return null
        queue = PlaybackQueue(tracks = tracks, currentIndex = index)
        preparedNextIndex = null
        return PlaybackQueueSelection(queue = queue, sessionId = sessionId)
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
