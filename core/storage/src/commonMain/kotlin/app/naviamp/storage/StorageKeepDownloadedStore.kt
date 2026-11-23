package app.naviamp.storage

import app.naviamp.domain.cache.KeepDownloadedCollectionKind
import app.naviamp.domain.cache.ArtistAlbumScope
import app.naviamp.domain.cache.KeepDownloadedCollectionPolicy
import app.naviamp.domain.cache.KeepDownloadedRepository
import app.naviamp.domain.cache.OfflinePlaylistRetention
import app.naviamp.domain.cache.OfflinePlaylistSnapshot
import app.naviamp.domain.cache.OfflinePlaylistSummary
import app.naviamp.domain.cache.PersistedDownloadJob
import app.naviamp.domain.Playlist
import app.naviamp.domain.settings.SavedTrack
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Portable SQLDelight implementation of keep-downloaded policy persistence. */
class StorageKeepDownloadedStore(
    private val queries: NaviampStorageQueries,
    private val nowEpochMillis: () -> Long,
) : KeepDownloadedRepository {
    private val jobJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override fun savedDownloadJobs(sourceId: String): List<PersistedDownloadJob> =
        queries.selectDownloadJobs(sourceId).executeAsList().mapNotNull { encoded ->
            runCatching { jobJson.decodeFromString<PersistedDownloadJob>(encoded) }.getOrNull()
        }.filter { it.sourceId == sourceId }

    override fun saveDownloadJob(job: PersistedDownloadJob) {
        queries.upsertDownloadJob(job.sourceId, job.job.id, jobJson.encodeToString(job))
    }

    override fun deleteDownloadJob(sourceId: String, jobId: String) {
        queries.deleteDownloadJob(sourceId, jobId)
    }

    override fun keepDownloadedPolicies(sourceId: String): List<KeepDownloadedCollectionPolicy> =
        queries.selectKeepDownloadedPolicies(sourceId).executeAsList().map(::toPolicy)

    override fun keepDownloadedPolicy(
        sourceId: String,
        kind: KeepDownloadedCollectionKind,
        collectionId: String,
    ): KeepDownloadedCollectionPolicy? =
        queries.selectKeepDownloadedPolicy(sourceId, kind.name, collectionId).executeAsOneOrNull()?.let(::toPolicy)

    override fun upsertKeepDownloadedPolicy(policy: KeepDownloadedCollectionPolicy) {
        queries.upsertKeepDownloadedPolicy(
            policy.sourceId,
            policy.kind.name,
            policy.collectionId,
            policy.name,
            if (policy.removeUnneededFiles) 1L else 0L,
            policy.artistAlbumScope.name,
            nowEpochMillis(),
        )
    }

    override fun deleteKeepDownloadedPolicy(
        sourceId: String,
        kind: KeepDownloadedCollectionKind,
        collectionId: String,
    ) {
        queries.deleteKeepDownloadedPolicy(sourceId, kind.name, collectionId)
    }

    override fun keepDownloadedTrackIds(
        sourceId: String,
        kind: KeepDownloadedCollectionKind,
        collectionId: String,
    ): Set<String> = queries.selectKeepDownloadedTrackIds(sourceId, kind.name, collectionId).executeAsList().toSet()

    override fun replaceKeepDownloadedTrackIds(policy: KeepDownloadedCollectionPolicy, trackIds: Set<String>) {
        queries.transaction {
            upsertKeepDownloadedPolicy(policy)
            queries.deleteKeepDownloadedCollectionTracks(policy.sourceId, policy.kind.name, policy.collectionId)
            trackIds.forEach {
                queries.insertKeepDownloadedCollectionTrack(policy.sourceId, policy.kind.name, policy.collectionId, it)
            }
        }
    }

    override fun managedKeepDownloadedTrackIds(sourceId: String): Set<String> =
        queries.selectManagedKeepDownloadedTrackIds(sourceId).executeAsList().toSet()

    override fun markManagedKeepDownloadedTracks(sourceId: String, trackIds: Set<String>) {
        queries.transaction { trackIds.forEach { queries.insertManagedKeepDownloadedTrack(sourceId, it) } }
    }

    override fun unmarkManagedKeepDownloadedTracks(sourceId: String, trackIds: Set<String>) {
        queries.transaction { trackIds.forEach { queries.deleteManagedKeepDownloadedTrack(sourceId, it) } }
    }

    override fun manuallyRetainedTrackIds(sourceId: String): Set<String> =
        queries.selectManualDownloadRetentionTrackIds(sourceId).executeAsList().toSet()

    override fun retainManualTrack(sourceId: String, trackId: String) {
        queries.insertManualDownloadRetention(sourceId, trackId)
    }

    override fun releaseTrackRetention(sourceId: String, trackId: String) {
        queries.deleteDownloadRetentionForTrack(sourceId, trackId)
    }

    override fun offlinePlaylistSummaries(sourceId: String): List<OfflinePlaylistSummary> {
        val trackIds = queries.selectOfflinePlaylistTrackIds(sourceId).executeAsList()
            .groupBy({ it.playlist_id }, { it.remote_track_id })
        return queries.selectOfflinePlaylists(sourceId).executeAsList().map { row ->
            val ids = trackIds[row.playlist_id].orEmpty()
            OfflinePlaylistSummary(sourceId, row.toPlaylist().copy(trackCount = ids.size), ids, row.toRetention())
        }
    }

    override fun offlinePlaylist(sourceId: String, playlistId: String): OfflinePlaylistSnapshot? {
        val row = queries.selectOfflinePlaylist(sourceId, playlistId).executeAsOneOrNull() ?: return null
        // A row that cannot be decoded is unknown content; omitting it would silently renumber the playlist.
        val tracks = queries.selectOfflinePlaylistEntries(sourceId, playlistId).executeAsList().map { payload ->
            jobJson.decodeFromString<SavedTrack>(payload).toTrack()
        }
        return OfflinePlaylistSnapshot(sourceId, row.toPlaylist().copy(trackCount = tracks.size), tracks, row.toRetention(), row.saved_at_epoch_millis)
    }

    override fun saveOfflinePlaylist(snapshot: OfflinePlaylistSnapshot) {
        val playlist = snapshot.playlist
        queries.transaction {
            queries.deleteOfflinePlaylistEntries(snapshot.sourceId, playlist.id)
            queries.upsertOfflinePlaylist(
                snapshot.sourceId,
                playlist.id,
                playlist.name,
                playlist.coverArtId,
                playlist.durationSeconds?.toLong(),
                if (playlist.isSmart) 1L else 0L,
                playlist.comment,
                playlist.owner,
                snapshot.retention.name,
                snapshot.savedAtEpochMillis.takeIf { it > 0L } ?: nowEpochMillis(),
            )
            snapshot.tracks.forEachIndexed { position, track ->
                queries.insertOfflinePlaylistEntry(
                    snapshot.sourceId,
                    playlist.id,
                    position.toLong(),
                    track.id.value,
                    jobJson.encodeToString(SavedTrack.fromTrack(track)),
                )
            }
        }
    }

    override fun updateOfflinePlaylistDetails(sourceId: String, playlist: Playlist) {
        queries.updateOfflinePlaylistDetails(
            playlist.name,
            playlist.coverArtId,
            playlist.durationSeconds?.toLong(),
            if (playlist.isSmart) 1L else 0L,
            playlist.comment,
            playlist.owner,
            sourceId,
            playlist.id,
        )
    }

    override fun deleteOfflinePlaylist(sourceId: String, playlistId: String) {
        queries.transaction {
            queries.deleteOfflinePlaylistEntries(sourceId, playlistId)
            queries.deleteOfflinePlaylist(sourceId, playlistId)
        }
    }

    override fun pruneOrphanedOfflinePlaylists(sourceId: String, removedTrackId: String?) {
        queries.transaction {
            queries.selectOrphanedManualOfflinePlaylistIds(sourceId, removedTrackId).executeAsList().forEach {
                queries.deleteOfflinePlaylistEntries(sourceId, it)
                queries.deleteOfflinePlaylist(sourceId, it)
            }
        }
    }

    private fun Offline_playlist.toPlaylist() = Playlist(
        id = playlist_id,
        name = name,
        trackCount = 0,
        durationSeconds = duration_seconds?.toInt(),
        coverArtId = cover_art_id,
        isSmart = is_smart != 0L,
        comment = comment,
        owner = owner,
        canEdit = false,
        canManage = false,
    )

    private fun Offline_playlist.toRetention() =
        runCatching { OfflinePlaylistRetention.valueOf(retention) }.getOrDefault(OfflinePlaylistRetention.Manual)

    private fun toPolicy(row: Keep_downloaded_collection) = KeepDownloadedCollectionPolicy(
        sourceId = row.source_id,
        kind = KeepDownloadedCollectionKind.valueOf(row.collection_kind),
        collectionId = row.collection_id,
        name = row.name,
        removeUnneededFiles = row.remove_unneeded_files != 0L,
        artistAlbumScope = runCatching { ArtistAlbumScope.valueOf(row.artist_album_scope) }
            .getOrDefault(ArtistAlbumScope.FullCatalog),
    )
}
