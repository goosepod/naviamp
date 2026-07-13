package app.naviamp.domain.playback

import app.naviamp.domain.Track
import app.naviamp.domain.queue.PlaybackQueue

class PlaybackQueueLifecycleManager {
    fun startQueue(
        tracks: List<Track>,
        index: Int,
    ): PlaybackQueueMutationUpdate {
        val selectedIndex = index.takeIf { it in tracks.indices }
            ?: return PlaybackQueueMutationUpdate(queue = PlaybackQueue(), changed = false)
        return PlaybackQueueMutationUpdate(
            queue = PlaybackQueue(tracks = tracks, currentIndex = selectedIndex),
            changed = true,
            clearPreparedNext = true,
        )
    }

    fun restoreQueue(
        tracks: List<Track>,
        index: Int,
    ): PlaybackQueueMutationUpdate =
        startQueue(tracks, index)

    fun restoreQueue(queue: PlaybackQueue): PlaybackQueueMutationUpdate {
        if (queue.currentIndex !in queue.tracks.indices) {
            return PlaybackQueueMutationUpdate(queue = PlaybackQueue(), changed = false)
        }
        return PlaybackQueueMutationUpdate(
            queue = queue.copy(
                playNextCount = queue.playNextCount.coerceIn(
                    0,
                    queue.tracks.size - queue.currentIndex - 1,
                ),
            ),
            changed = true,
            clearPreparedNext = true,
        )
    }

    fun clearQueue(): PlaybackQueueMutationUpdate =
        PlaybackQueueMutationUpdate(
            queue = PlaybackQueue(),
            changed = true,
            clearPreparedNext = true,
        )
}
