package app.naviamp.domain.cache

import app.naviamp.domain.Playlist
import app.naviamp.domain.Track

/**
 * Why a saved playlist exists. A one-time download keeps the membership seen at download time;
 * a keep-downloaded subscription refreshes it whenever its source can be read again.
 */
enum class OfflinePlaylistRetention { Manual, KeepDownloaded }

/**
 * A playlist's ordered membership saved for browsing without its source. Rows are positions, so
 * repeated tracks stay repeated. The snapshot is scoped by source, not by provider, so any library
 * source with locally playable tracks can use it.
 */
data class OfflinePlaylistSnapshot(
    val sourceId: String,
    val playlist: Playlist,
    val tracks: List<Track>,
    val retention: OfflinePlaylistRetention = OfflinePlaylistRetention.Manual,
    val savedAtEpochMillis: Long = 0L,
) {
    fun offlinePlaylist(): Playlist = playlist.offlineReadOnly(tracks.size)

    fun needsRefresh(current: Playlist, currentTracks: List<Track>): Boolean =
        tracks.map(Track::id) != currentTracks.map(Track::id) ||
            !playlist.hasSameOfflineDetails(current)
}

/** Lightweight list projection; track identities only, in saved order. */
data class OfflinePlaylistSummary(
    val sourceId: String,
    val playlist: Playlist,
    val trackIds: List<String>,
    val retention: OfflinePlaylistRetention = OfflinePlaylistRetention.Manual,
) {
    fun offlinePlaylist(): Playlist = playlist.offlineReadOnly(trackIds.size)
}

interface OfflinePlaylistRepository {
    fun offlinePlaylistSummaries(sourceId: String): List<OfflinePlaylistSummary>

    fun offlinePlaylist(sourceId: String, playlistId: String): OfflinePlaylistSnapshot?

    /** Replaces the saved details and complete membership atomically. */
    fun saveOfflinePlaylist(snapshot: OfflinePlaylistSnapshot)

    /** Refreshes name and artwork without touching membership; a missing snapshot stays missing. */
    fun updateOfflinePlaylistDetails(sourceId: String, playlist: Playlist)

    fun deleteOfflinePlaylist(sourceId: String, playlistId: String)

    /**
     * Removes one-time snapshots none of whose tracks remain downloaded, limited to snapshots that
     * contain [removedTrackId] when given. Subscriptions are removed only with their policy.
     */
    fun pruneOrphanedOfflinePlaylists(sourceId: String, removedTrackId: String? = null)
}

object EmptyOfflinePlaylistRepository : OfflinePlaylistRepository {
    override fun offlinePlaylistSummaries(sourceId: String) = emptyList<OfflinePlaylistSummary>()
    override fun offlinePlaylist(sourceId: String, playlistId: String): OfflinePlaylistSnapshot? = null
    override fun saveOfflinePlaylist(snapshot: OfflinePlaylistSnapshot) = Unit
    override fun updateOfflinePlaylistDetails(sourceId: String, playlist: Playlist) = Unit
    override fun deleteOfflinePlaylist(sourceId: String, playlistId: String) = Unit
    override fun pruneOrphanedOfflinePlaylists(sourceId: String, removedTrackId: String?) = Unit
}

data class OfflinePlaylistEntry(val position: Int, val track: Track, val available: Boolean)

data class OfflinePlaylistAvailability(val entries: List<OfflinePlaylistEntry>) {
    val totalCount: Int get() = entries.size
    val availableCount: Int get() = entries.count(OfflinePlaylistEntry::available)
    val playableTracks: List<Track> get() = entries.filter(OfflinePlaylistEntry::available).map(OfflinePlaylistEntry::track)
    val canPlay: Boolean get() = entries.any(OfflinePlaylistEntry::available)
}

/** [playableTrackIds] is the source-scoped set of tracks whose local audio is verified present. */
fun offlinePlaylistAvailability(tracks: List<Track>, playableTrackIds: Set<String>) =
    OfflinePlaylistAvailability(
        tracks.mapIndexed { index, track -> OfflinePlaylistEntry(index, track, track.id.value in playableTrackIds) },
    )

fun offlinePlaylistAvailableCount(trackIds: List<String>, playableTrackIds: Set<String>): Int =
    trackIds.count { it in playableTrackIds }

private fun Playlist.offlineReadOnly(savedTrackCount: Int) =
    copy(trackCount = savedTrackCount, canEdit = false, canManage = false)

/** The saved fields shown offline; capabilities and counts are derived, not saved. */
fun Playlist.hasSameOfflineDetails(other: Playlist): Boolean =
    name == other.name && coverArtId == other.coverArtId && isSmart == other.isSmart &&
        comment == other.comment && owner == other.owner && durationSeconds == other.durationSeconds
