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
        val nextIndex = when {
            hasNext() -> currentIndex + 1
            repeatMode == RepeatMode.Queue && tracks.isNotEmpty() -> 0
            else -> return this
        }
        return copy(currentIndex = nextIndex)
    }

    fun previous(repeatMode: RepeatMode = RepeatMode.Off): PlaybackQueue {
        val previousIndex = when {
            hasPrevious() -> currentIndex - 1
            repeatMode == RepeatMode.Queue && tracks.isNotEmpty() -> tracks.lastIndex
            else -> return this
        }
        return copy(currentIndex = previousIndex)
    }

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

    fun restoreUpcoming(tracks: List<Track>): PlaybackQueue {
        if (currentIndex !in this.tracks.indices) return this
        return copy(
            tracks = this.tracks.take(currentIndex + 1) + tracks,
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
