package app.naviamp.presentation

import app.naviamp.domain.Playlist
import app.naviamp.domain.cache.EmptyOfflinePlaylistRepository
import app.naviamp.domain.cache.OfflinePlaylistAvailability
import app.naviamp.domain.cache.OfflinePlaylistRepository
import app.naviamp.domain.cache.OfflinePlaylistSnapshot
import app.naviamp.domain.cache.OfflinePlaylistSummary
import app.naviamp.domain.cache.hasSameOfflineDetails
import app.naviamp.domain.cache.offlinePlaylistAvailability
import app.naviamp.domain.cache.offlinePlaylistAvailableCount

data class NaviampCoreOfflinePlaylistListing(val summary: OfflinePlaylistSummary, val availableCount: Int) {
    val playlist: Playlist get() = summary.offlinePlaylist()
}

data class NaviampCoreOfflinePlaylist(
    val snapshot: OfflinePlaylistSnapshot,
    val availability: OfflinePlaylistAvailability,
) {
    val playlist: Playlist get() = snapshot.offlinePlaylist()
}

/**
 * Saved playlist membership joined with the source's verified local audio. Any library source can
 * supply [playableTrackIds]; a server source reports its downloads whose files are present.
 */
class NaviampCoreOfflinePlaylistSource(
    private val repository: OfflinePlaylistRepository = EmptyOfflinePlaylistRepository,
    private val playableTrackIds: suspend (sourceId: String) -> Set<String>? = { null },
) {
    suspend fun playlists(sourceId: String): List<NaviampCoreOfflinePlaylistListing> {
        val summaries = repository.offlinePlaylistSummaries(sourceId)
        if (summaries.isEmpty()) return emptyList()
        val playable = playable(sourceId)
        return summaries.map { NaviampCoreOfflinePlaylistListing(it, offlinePlaylistAvailableCount(it.trackIds, playable)) }
    }

    suspend fun playlist(sourceId: String, playlistId: String): NaviampCoreOfflinePlaylist? {
        val snapshot = repository.offlinePlaylist(sourceId, playlistId) ?: return null
        return NaviampCoreOfflinePlaylist(snapshot, offlinePlaylistAvailability(snapshot.tracks, playable(sourceId)))
    }

    /** Keeps saved names and artwork current while the source is reachable; membership is untouched. */
    fun refreshDetails(sourceId: String, playlists: List<Playlist>) {
        val saved = repository.offlinePlaylistSummaries(sourceId).associateBy { it.playlist.id }
        playlists.forEach { playlist ->
            val previous = saved[playlist.id]?.playlist ?: return@forEach
            if (!previous.hasSameOfflineDetails(playlist)) repository.updateOfflinePlaylistDetails(sourceId, playlist)
        }
    }

    // Unverifiable storage must not advertise rows as playable.
    private suspend fun playable(sourceId: String): Set<String> = playableTrackIds(sourceId).orEmpty()
}
