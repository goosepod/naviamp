package app.naviamp.domain.radio

import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.provider.isDiscoveryPreview
import kotlinx.coroutines.CancellationException

const val SomethingNewBatchSize = 50
const val SomethingNewMaxBatches = 4

sealed interface SomethingNewPick {
    data class Found(val track: Track, val fromDownloads: Boolean) : SomethingNewPick
    data class NothingUnplayed(val offline: Boolean) : SomethingNewPick
    data object NothingDownloaded : SomethingNewPick
    data object PlayCountsUnavailable : SomethingNewPick
    data class Failed(val error: Throwable) : SomethingNewPick
    data object Superseded : SomethingNewPick
}

fun Track.isNeverPlayed(): Boolean = (playCount ?: 0) == 0 && lastPlayedAtIso8601 == null

/**
 * Samples bounded random batches (or the downloads when offline) for a track with no server play
 * count and no listen still waiting to reach the server.
 */
suspend fun pickSomethingNew(
    playCountsAvailable: Boolean,
    offline: Boolean,
    randomTracks: suspend (limit: Int) -> List<Track>,
    downloadedTracks: suspend () -> List<Track>,
    inSelectedLibraries: (List<Track>) -> List<Track>,
    pendingListens: suspend () -> Set<TrackId>,
    excluded: Set<TrackId> = emptySet(),
    isCurrent: () -> Boolean = { true },
    batchSize: Int = SomethingNewBatchSize,
    maxBatches: Int = SomethingNewMaxBatches,
    shuffle: (List<Track>) -> List<Track> = { it.shuffled() },
): SomethingNewPick {
    // Without server play counts every track would look new, so the claim would be false.
    if (!playCountsAvailable) return SomethingNewPick.PlayCountsUnavailable
    try {
        val pending = pendingListens()
        fun List<Track>.firstNew() = firstOrNull { track ->
            track.isNeverPlayed() && track.id !in pending && track.id !in excluded && !track.id.isDiscoveryPreview()
        }
        if (offline) {
            val local = inSelectedLibraries(downloadedTracks().distinctBy { it.id })
            if (!isCurrent()) return SomethingNewPick.Superseded
            if (local.isEmpty()) return SomethingNewPick.NothingDownloaded
            return shuffle(local).firstNew()?.let { SomethingNewPick.Found(it, fromDownloads = true) }
                ?: SomethingNewPick.NothingUnplayed(offline = true)
        }
        repeat(maxBatches.coerceAtLeast(1)) {
            val batch = randomTracks(batchSize)
            if (!isCurrent()) return SomethingNewPick.Superseded
            inSelectedLibraries(batch).firstNew()?.let { return SomethingNewPick.Found(it, fromDownloads = false) }
            if (batch.isEmpty()) return SomethingNewPick.NothingUnplayed(offline = false)
        }
        return SomethingNewPick.NothingUnplayed(offline = false)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        return SomethingNewPick.Failed(failure)
    }
}
