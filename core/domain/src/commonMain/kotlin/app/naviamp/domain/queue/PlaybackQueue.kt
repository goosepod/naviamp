package app.naviamp.domain.queue

import app.naviamp.domain.Track

enum class RepeatMode {
    Off,
    Queue,
    Track,
}

data class PlaybackQueue(
    val tracks: List<Track> = emptyList(),
    val currentIndex: Int = -1,
) {
    val current: Track?
        get() = tracks.getOrNull(currentIndex)

    fun backTo(): List<Track> =
        if (currentIndex in tracks.indices) {
            tracks.take(currentIndex).asReversed()
        } else {
            emptyList()
        }

    fun upNext(): List<Track> =
        if (currentIndex in tracks.indices) {
            tracks.drop(currentIndex + 1)
        } else {
            emptyList()
        }

    fun hasNext(): Boolean =
        currentIndex in tracks.indices && currentIndex + 1 < tracks.size

    fun hasPrevious(): Boolean =
        currentIndex in tracks.indices && currentIndex > 0

    fun next(repeatMode: RepeatMode = RepeatMode.Off): PlaybackQueue {
        val targetIndex = nextIndex(repeatMode = repeatMode, repeatTrack = false) ?: return this
        return copy(currentIndex = targetIndex)
    }

    fun previous(repeatMode: RepeatMode = RepeatMode.Off): PlaybackQueue {
        val targetIndex = previousIndex(repeatMode = repeatMode, repeatTrack = false) ?: return this
        return copy(currentIndex = targetIndex)
    }

    fun adjacentIndex(
        offset: Int,
        repeatMode: RepeatMode = RepeatMode.Off,
        repeatTrack: Boolean = true,
        wrapQueue: Boolean = true,
    ): Int? {
        if (offset == 0) return currentIndex.takeIf { it in tracks.indices }
        if (currentIndex !in tracks.indices) return null
        if (repeatTrack && repeatMode == RepeatMode.Track) return currentIndex

        val nextIndex = currentIndex + offset
        if (nextIndex in tracks.indices) return nextIndex
        if (!wrapQueue || repeatMode != RepeatMode.Queue || tracks.isEmpty()) return null

        return when {
            offset > 0 && currentIndex == tracks.lastIndex -> 0
            offset < 0 && currentIndex == 0 -> tracks.lastIndex
            else -> null
        }
    }

    fun nextIndex(
        repeatMode: RepeatMode = RepeatMode.Off,
        repeatTrack: Boolean = true,
        wrapQueue: Boolean = true,
    ): Int? =
        adjacentIndex(
            offset = 1,
            repeatMode = repeatMode,
            repeatTrack = repeatTrack,
            wrapQueue = wrapQueue,
        )

    fun previousIndex(
        repeatMode: RepeatMode = RepeatMode.Off,
        repeatTrack: Boolean = true,
        wrapQueue: Boolean = true,
    ): Int? =
        adjacentIndex(
            offset = -1,
            repeatMode = repeatMode,
            repeatTrack = repeatTrack,
            wrapQueue = wrapQueue,
        )

    fun withTracks(
        tracks: List<Track>,
        currentIndex: Int = 0,
    ): PlaybackQueue =
        if (tracks.isEmpty()) {
            PlaybackQueue()
        } else {
            PlaybackQueue(
                tracks = tracks,
                currentIndex = currentIndex.coerceIn(tracks.indices),
            )
        }

    fun appendTracks(
        tracks: List<Track>,
        maxHistory: Int? = null,
    ): PlaybackQueue {
        if (tracks.isEmpty()) return this

        val prunedTrackCount = maxHistory
            ?.let { (currentIndex - it.coerceAtLeast(0)).coerceAtLeast(0) }
            ?: 0
        return PlaybackQueue(
            tracks = (this.tracks + tracks).drop(prunedTrackCount),
            currentIndex = currentIndex - prunedTrackCount,
        )
    }

    fun playNextTracks(
        tracks: List<Track>,
        maxHistory: Int? = null,
    ): PlaybackQueue {
        if (tracks.isEmpty()) return this
        if (currentIndex !in this.tracks.indices) return appendTracks(tracks, maxHistory)

        val prunedTrackCount = maxHistory
            ?.let { (currentIndex - it.coerceAtLeast(0)).coerceAtLeast(0) }
            ?: 0
        val currentAndHistory = this.tracks
            .take(currentIndex + 1)
            .drop(prunedTrackCount)
        val upcoming = this.tracks.drop(currentIndex + 1)
        return PlaybackQueue(
            tracks = currentAndHistory + tracks + upcoming,
            currentIndex = currentIndex - prunedTrackCount,
        )
    }

    fun replaceUpcomingTracks(
        currentTrack: Track,
        upcomingTracks: List<Track>,
        maxHistory: Int? = null,
    ): PlaybackQueue {
        val currentQueueIndex = tracks.indexOfFirst { it.id == currentTrack.id }
            .takeIf { it >= 0 }
            ?: currentIndex
        val currentQueueTrack = tracks.getOrNull(currentQueueIndex) ?: currentTrack
        val prunedTrackCount = maxHistory
            ?.let { (currentQueueIndex - it.coerceAtLeast(0)).coerceAtLeast(0) }
            ?: 0
        val history = tracks
            .take(currentQueueIndex)
            .drop(prunedTrackCount)
        val dedupedUpcoming = upcomingTracks.filterNot { track ->
            track.id == currentQueueTrack.id || history.any { it.id == track.id }
        }

        return PlaybackQueue(
            tracks = history + currentQueueTrack + dedupedUpcoming,
            currentIndex = history.size,
        )
    }

    fun shuffleUpcoming(): Pair<PlaybackQueue, List<Track>>? {
        val originalUpcoming = upNext()
        if (originalUpcoming.size < 2 || currentIndex !in tracks.indices) return null
        return copy(
            tracks = tracks.take(currentIndex + 1) + originalUpcoming.shuffled(),
        ) to originalUpcoming
    }

    fun toggleUpcomingShuffle(shuffledSnapshot: List<Track>?): UpcomingShuffleToggle? =
        if (shuffledSnapshot == null) {
            val (queue, snapshot) = shuffleUpcoming() ?: return null
            UpcomingShuffleToggle(queue = queue, shuffledSnapshot = snapshot)
        } else {
            UpcomingShuffleToggle(queue = restoreUpcoming(shuffledSnapshot), shuffledSnapshot = null)
        }

    fun restoreUpcoming(tracks: List<Track>): PlaybackQueue {
        if (currentIndex !in this.tracks.indices) return this
        return copy(
            tracks = this.tracks.take(currentIndex + 1) + tracks,
        )
    }

    fun removeAt(index: Int): PlaybackQueue {
        if (index !in tracks.indices) return this
        val nextTracks = tracks.filterIndexed { trackIndex, _ -> trackIndex != index }
        if (nextTracks.isEmpty()) return PlaybackQueue()
        val nextCurrentIndex = when {
            currentIndex !in tracks.indices -> currentIndex
            index < currentIndex -> currentIndex - 1
            index == currentIndex -> currentIndex.coerceAtMost(nextTracks.lastIndex)
            else -> currentIndex
        }
        return PlaybackQueue(
            tracks = nextTracks,
            currentIndex = nextCurrentIndex.takeIf { it in nextTracks.indices } ?: -1,
        )
    }

    fun moveToNext(index: Int): PlaybackQueue {
        if (index !in tracks.indices || currentIndex !in tracks.indices) return this
        if (index == currentIndex || index == currentIndex + 1) return this

        val reordered = tracks.toMutableList()
        val selected = reordered.removeAt(index)
        val adjustedCurrentIndex = currentIndex - if (index < currentIndex) 1 else 0
        reordered.add(adjustedCurrentIndex + 1, selected)
        return PlaybackQueue(tracks = reordered, currentIndex = adjustedCurrentIndex)
    }

    fun clearUpcoming(): PlaybackQueue {
        if (currentIndex !in tracks.indices) return PlaybackQueue()
        return copy(tracks = tracks.take(currentIndex + 1))
    }

    fun removePlayedHistory(): PlaybackQueue {
        if (currentIndex !in tracks.indices || currentIndex <= 0) return this
        return PlaybackQueue(
            tracks = tracks.drop(currentIndex),
            currentIndex = 0,
        )
    }

    fun jumpTo(
        index: Int,
        moveSelectedToCurrent: Boolean = true,
    ): PlaybackQueue {
        if (index !in tracks.indices || index == currentIndex) return this
        if (currentIndex !in tracks.indices) return copy(currentIndex = index)
        if (!moveSelectedToCurrent) return copy(currentIndex = index)

        return if (index > currentIndex) {
            val currentAndHistory = tracks.take(currentIndex + 1)
            val upcoming = tracks.drop(currentIndex + 1)
            val selected = tracks[index]
            PlaybackQueue(
                tracks = currentAndHistory + selected + upcoming.filterIndexed { upcomingIndex, _ ->
                    currentIndex + 1 + upcomingIndex != index
                },
                currentIndex = currentAndHistory.size,
            )
        } else {
            copy(currentIndex = index)
        }
    }
}

data class UpcomingShuffleToggle(
    val queue: PlaybackQueue,
    val shuffledSnapshot: List<Track>?,
)
