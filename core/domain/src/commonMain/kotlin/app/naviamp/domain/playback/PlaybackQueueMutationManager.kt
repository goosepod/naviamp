package app.naviamp.domain.playback

import app.naviamp.domain.Track
import app.naviamp.domain.queue.PlaybackQueue

class PlaybackQueueMutationManager {
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

    fun appendQueueTracks(
        currentQueue: PlaybackQueue,
        tracksToAdd: List<Track>,
        maxHistory: Int? = null,
    ): PlaybackQueueMutationUpdate {
        val update = appendTracks(
            currentQueue = currentQueue,
            tracksToAdd = tracksToAdd,
            maxHistory = maxHistory,
        )
        return PlaybackQueueMutationUpdate(
            queue = update.queue,
            changed = update.tracksChanged,
        )
    }

    fun playNextTracks(
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
            else -> currentQueue.playNextTracks(tracks, maxHistory = maxHistory)
        }
        return PlaybackQueueUpdate(
            queue = nextQueue,
            tracksChanged = tracks.isNotEmpty(),
            status = queuePlayNextStatus(
                originalTracks = tracksToAdd,
                tracksToAdd = tracks,
                label = label,
                deduplicateExisting = deduplicateExisting,
            ),
        )
    }

    fun updateTrack(
        currentQueue: PlaybackQueue,
        updatedTrack: Track,
    ): PlaybackQueueMutationUpdate {
        val updatedTracks = currentQueue.tracks.map { track ->
            if (track.id == updatedTrack.id) updatedTrack else track
        }
        if (updatedTracks == currentQueue.tracks) {
            return PlaybackQueueMutationUpdate(queue = currentQueue, changed = false)
        }
        return PlaybackQueueMutationUpdate(
            queue = currentQueue.copy(tracks = updatedTracks),
            changed = true,
        )
    }

    fun replaceUpcomingTracks(
        currentQueue: PlaybackQueue,
        currentTrack: Track,
        upcomingTracks: List<Track>,
        maxHistory: Int? = null,
    ): PlaybackQueueMutationUpdate {
        val nextQueue = currentQueue.replaceUpcomingTracks(currentTrack, upcomingTracks, maxHistory)
        return PlaybackQueueMutationUpdate(
            queue = nextQueue,
            changed = nextQueue != currentQueue,
            clearPreparedNext = true,
        )
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
