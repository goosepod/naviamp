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
    val playNextCount: Int = 0,
    val groups: List<PlaybackQueueGroup> = emptyList(),
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

    fun playNext(): List<Track> =
        upNext().take(effectivePlayNextCount)

    fun contextUpNext(): List<Track> =
        upNext().drop(effectivePlayNextCount)

    private val effectivePlayNextCount: Int
        get() = playNextCount.coerceIn(0, upNext().size)

    fun hasNext(): Boolean =
        currentIndex in tracks.indices && currentIndex + 1 < tracks.size

    fun hasPrevious(): Boolean =
        currentIndex in tracks.indices && currentIndex > 0

    fun next(repeatMode: RepeatMode = RepeatMode.Off): PlaybackQueue {
        val targetIndex = nextIndex(repeatMode = repeatMode, repeatTrack = false) ?: return this
        return jumpTo(targetIndex)
    }

    fun previous(repeatMode: RepeatMode = RepeatMode.Off): PlaybackQueue {
        val targetIndex = previousIndex(repeatMode = repeatMode, repeatTrack = false) ?: return this
        return jumpTo(targetIndex)
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
            playNextCount = effectivePlayNextCount,
            groups = groups.afterDroppingPrefix(
                droppedTrackCount = prunedTrackCount,
                remainingTrackCount = this.tracks.size + tracks.size - prunedTrackCount,
            ),
        )
    }

    fun appendGroupedTracks(
        tracks: List<Track>,
        group: PlaybackQueueGroup,
        maxHistory: Int? = null,
    ): PlaybackQueue {
        if (tracks.isEmpty()) return this
        val next = appendTracks(tracks, maxHistory)
        val start = next.tracks.size - tracks.size
        val appendedGroup = group.copy(
            startIndex = start,
            endIndexExclusive = next.tracks.size,
        )
        return next.copy(groups = (next.groups + appendedGroup).normalized(next.tracks.size))
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
        val priorityTracks = playNext()
        val upcoming = contextUpNext()
        return PlaybackQueue(
            tracks = currentAndHistory + priorityTracks + tracks + upcoming,
            currentIndex = currentIndex - prunedTrackCount,
            playNextCount = priorityTracks.size + tracks.size,
        )
    }

    fun playNextGroupedTracks(
        tracks: List<Track>,
        group: PlaybackQueueGroup,
        maxHistory: Int? = null,
    ): PlaybackQueue {
        if (tracks.isEmpty()) return this
        if (currentIndex !in this.tracks.indices) {
            return PlaybackQueue(
                tracks = tracks,
                currentIndex = 0,
                groups = listOf(group.copy(startIndex = 0, endIndexExclusive = tracks.size)),
            )
        }

        val prunedTrackCount = maxHistory
            ?.let { (currentIndex - it.coerceAtLeast(0)).coerceAtLeast(0) }
            ?: 0
        val insertionIndex = currentIndex + 1 + effectivePlayNextCount - prunedTrackCount
        val next = playNextTracks(tracks, maxHistory)
        val retainedGroups = groups
            .afterDroppingPrefix(prunedTrackCount, this.tracks.size - prunedTrackCount)
            .afterInserting(insertionIndex, tracks.size, next.tracks.size)
        val insertedGroup = group.copy(
            startIndex = insertionIndex,
            endIndexExclusive = insertionIndex + tracks.size,
        )
        return next.copy(groups = (retainedGroups + insertedGroup).normalized(next.tracks.size))
    }

    fun replaceUpcomingTracks(
        currentTrack: Track,
        upcomingTracks: List<Track>,
        maxHistory: Int? = null,
    ): PlaybackQueue {
        val currentQueueIndex = resolveTrackOccurrenceIndex(
            tracks = tracks,
            track = currentTrack,
            preferredIndex = currentIndex,
        )
            ?: currentIndex
        val currentQueueTrack = tracks.getOrNull(currentQueueIndex) ?: currentTrack
        val prunedTrackCount = maxHistory
            ?.let { (currentQueueIndex - it.coerceAtLeast(0)).coerceAtLeast(0) }
            ?: 0
        val history = tracks
            .take(currentQueueIndex)
            .drop(prunedTrackCount)
        val priorityTracks = playNext()
        val dedupedUpcoming = upcomingTracks.filterNot { track ->
            track.id == currentQueueTrack.id ||
                history.any { it.id == track.id } ||
                priorityTracks.any { it.id == track.id }
        }

        return PlaybackQueue(
            tracks = history + currentQueueTrack + priorityTracks + dedupedUpcoming,
            currentIndex = history.size,
            playNextCount = priorityTracks.size,
        )
    }

    fun shuffleUpcoming(): Pair<PlaybackQueue, List<Track>>? {
        val priorityTracks = playNext()
        val originalUpcoming = contextUpNext()
        if (originalUpcoming.size < 2 || currentIndex !in tracks.indices) return null
        return copy(
            tracks = tracks.take(currentIndex + 1) + priorityTracks + originalUpcoming.shuffled(),
            playNextCount = priorityTracks.size,
            groups = emptyList(),
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
        val priorityTracks = playNext()
        return copy(
            tracks = this.tracks.take(currentIndex + 1) + priorityTracks + tracks,
            playNextCount = priorityTracks.size,
            groups = emptyList(),
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
        val priorityRange = (currentIndex + 1) until (currentIndex + 1 + effectivePlayNextCount)
        val nextPlayNextCount = when {
            index in priorityRange -> effectivePlayNextCount - 1
            index == currentIndex && effectivePlayNextCount > 0 -> effectivePlayNextCount - 1
            else -> effectivePlayNextCount
        }
        return PlaybackQueue(
            tracks = nextTracks,
            currentIndex = nextCurrentIndex.takeIf { it in nextTracks.indices } ?: -1,
            playNextCount = nextPlayNextCount.coerceAtLeast(0),
        )
    }

    fun moveToNext(index: Int): PlaybackQueue {
        if (index !in tracks.indices || currentIndex !in tracks.indices) return this
        val priorityRange = (currentIndex + 1) until (currentIndex + 1 + effectivePlayNextCount)
        if (index == currentIndex || index == currentIndex + 1) return this

        val reordered = tracks.toMutableList()
        val selected = reordered.removeAt(index)
        val adjustedCurrentIndex = currentIndex - if (index < currentIndex) 1 else 0
        val selectedWasPriority = index in priorityRange
        val insertionIndex = if (selectedWasPriority) {
            adjustedCurrentIndex + 1
        } else {
            adjustedCurrentIndex + 1 + effectivePlayNextCount
        }
        reordered.add(insertionIndex, selected)
        return PlaybackQueue(
            tracks = reordered,
            currentIndex = adjustedCurrentIndex,
            playNextCount = effectivePlayNextCount + if (selectedWasPriority) 0 else 1,
        )
    }

    fun clearUpcoming(): PlaybackQueue {
        if (currentIndex !in tracks.indices) return PlaybackQueue()
        return copy(
            tracks = tracks.take(currentIndex + 1),
            playNextCount = 0,
            groups = normalizedGroups().mapNotNull { it.normalized(currentIndex + 1) },
        )
    }

    /** Removes every queued occurrence except the track that is currently playing. */
    fun retainCurrentOnly(): PlaybackQueue {
        val currentTrack = current ?: return PlaybackQueue()
        return PlaybackQueue(tracks = listOf(currentTrack), currentIndex = 0)
    }

    fun removePlayedHistory(): PlaybackQueue {
        if (currentIndex !in tracks.indices || currentIndex <= 0) return this
        return PlaybackQueue(
            tracks = tracks.drop(currentIndex),
            currentIndex = 0,
            playNextCount = effectivePlayNextCount,
            groups = groups.afterDroppingPrefix(
                droppedTrackCount = currentIndex,
                remainingTrackCount = tracks.size - currentIndex,
            ),
        )
    }

    fun jumpTo(
        index: Int,
        moveSelectedToCurrent: Boolean = true,
    ): PlaybackQueue {
        if (index !in tracks.indices || index == currentIndex) return this
        if (currentIndex !in tracks.indices) return copy(currentIndex = index, playNextCount = 0)
        if (!moveSelectedToCurrent) return copy(currentIndex = index, playNextCount = 0)

        val priorityRange = (currentIndex + 1) until (currentIndex + 1 + effectivePlayNextCount)
        val historyIndexes = if (index > currentIndex) (0..currentIndex).toList() else (0 until index).toList()
        val remainingPriorityIndexes = priorityRange
            .filter { it != index }
        val excludedIndexes = historyIndexes.toSet() + priorityRange.toSet() + index
        val remainingContextIndexes = tracks.indices.filter { it !in excludedIndexes }
        val selectedIndexes = historyIndexes + index + remainingPriorityIndexes + remainingContextIndexes
        return PlaybackQueue(
            tracks = selectedIndexes.map(tracks::get),
            currentIndex = historyIndexes.size,
            playNextCount = remainingPriorityIndexes.size,
            groups = normalizedGroups().afterReordering(selectedIndexes, tracks.size),
        )
    }
}

fun resolveTrackOccurrenceIndex(
    tracks: List<Track>,
    track: Track,
    preferredIndex: Int? = null,
): Int? =
    preferredIndex
        ?.takeIf { index -> tracks.getOrNull(index)?.id == track.id }
        ?: tracks.indexOfFirst { it.id == track.id }.takeIf { it >= 0 }

data class UpcomingShuffleToggle(
    val queue: PlaybackQueue,
    val shuffledSnapshot: List<Track>?,
)
