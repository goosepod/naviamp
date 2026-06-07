package app.naviamp.domain.playback

import app.naviamp.domain.Track
import app.naviamp.domain.queue.PlaybackQueue

data class PlaybackQueueUpdate(
    val queue: PlaybackQueue,
    val tracksChanged: Boolean,
    val status: String,
)

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
