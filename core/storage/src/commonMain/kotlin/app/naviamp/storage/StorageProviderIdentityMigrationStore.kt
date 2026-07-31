package app.naviamp.storage

import app.naviamp.domain.cache.ProviderIdentityMigrationResult
import app.naviamp.domain.settings.PlaybackSessionSettings
import app.naviamp.domain.settings.SavedArtistCredit
import app.naviamp.domain.settings.SavedTrack
import kotlinx.serialization.json.Json

/** Executes a provider-declared remote-ID format transition as one shared storage transaction. */
internal class StorageProviderIdentityMigrationStore(
    private val queries: NaviampStorageQueries,
    private val json: Json,
) {
    fun migrate(
        sourceId: String,
        providerId: String,
        targetVersion: Long,
        transform: (String) -> String,
    ): ProviderIdentityMigrationResult {
        val installedVersion = queries.selectProviderIdentityVersion(sourceId).executeAsOneOrNull() ?: return unchanged()
        if (installedVersion >= targetVersion) return unchanged()

        var transformed = 0
        fun migrate(value: String): String = transform(value).also { if (it != value) transformed++ }
        fun migrateNullable(value: String?): String? = value?.let(::migrate)

        queries.transaction {
            // The index is reproducible provider data. Rebuilding it avoids stale duplicates and
            // makes no assumptions about future additions to its denormalized rows.
            queries.clearArtistPopularTracksForSource(sourceId)
            queries.clearLibraryForSource(sourceId)
            queries.clearLibraryAlbumsForSource(sourceId)
            queries.clearLibraryArtistsForSource(sourceId)
            queries.resetMediaSourceLibraryScan(sourceId)

            queries.selectCachedAudioTrackIdsForIdentityMigration(sourceId).executeAsList().forEach { row ->
                queries.updateCachedAudioTrackId(migrate(row.remote_track_id), sourceId, row.remote_track_id, row.quality_key)
            }

            queries.selectDownloadedAudio(sourceId).executeAsList().forEach { row ->
                queries.updateDownloadedAudioIdentity(
                    remote_track_id = migrate(row.remote_track_id),
                    artist_id = migrateNullable(row.artist_id),
                    album_id = migrateNullable(row.album_id),
                    cover_art_id = migrateNullable(row.cover_art_id),
                    source_id = sourceId,
                    remote_track_id_ = row.remote_track_id,
                    quality_key = row.quality_key,
                )
            }

            migrateKeepDownloaded(sourceId, ::migrate)

            queries.selectManagedKeepDownloadedTrackIdsForIdentityMigration(sourceId).executeAsList().forEach { oldId ->
                queries.updateManagedKeepDownloadedTrackId(migrate(oldId), sourceId, oldId)
            }
            queries.selectWaveformTrackIdsForIdentityMigration(sourceId).executeAsList().forEach { row ->
                queries.updateWaveformTrackId(migrate(row.remote_track_id), sourceId, row.remote_track_id, row.quality_key)
            }
            queries.selectLyricsTrackIdsForIdentityMigration(sourceId).executeAsList().forEach { oldId ->
                queries.updateLyricsTrackId(migrate(oldId), sourceId, oldId)
            }
            queries.selectLrclibLyricsTrackIdsForIdentityMigration(sourceId).executeAsList().forEach { oldId ->
                queries.updateLrclibLyricsTrackId(migrate(oldId), sourceId, oldId)
            }
            queries.selectLyricsOffsetTrackIdsForIdentityMigration(sourceId).executeAsList().forEach { oldId ->
                queries.updateLyricsOffsetTrackId(migrate(oldId), sourceId, oldId)
            }
            queries.selectSidecarStatusTrackIdsForIdentityMigration(sourceId).executeAsList().forEach { row ->
                queries.updateSidecarStatusTrackId(
                    migrate(row.remote_track_id),
                    sourceId,
                    row.remote_track_id,
                    row.quality_key,
                    row.sidecar_type,
                )
            }

            migratePlaybackSession(sourceId, ::migrate)

            queries.selectPlaybackHistoryForIdentityMigration(sourceId).executeAsList().forEach { row ->
                queries.updatePlaybackHistoryIdentity(
                    remote_track_id = migrate(row.remote_track_id),
                    artist_id = migrateNullable(row.artist_id),
                    album_id = migrateNullable(row.album_id),
                    cover_art_id = migrateNullable(row.cover_art_id),
                    source_id = sourceId,
                    remote_track_id_ = row.remote_track_id,
                    played_at_epoch_millis = row.played_at_epoch_millis,
                )
            }
            queries.selectPendingProviderActionsForIdentityMigration(sourceId).executeAsList().forEach { row ->
                queries.updatePendingProviderActionEntityId(migrate(row.entity_id), row.id)
            }

            // Response bodies and image URLs can contain IDs in provider-defined structures. They
            // are disposable caches, so invalidation is safer than incomplete JSON rewriting.
            queries.deleteResponsesForProviderIdentityMigration(providerId)
            queries.clearImages()
            queries.markProviderIdentityVersion(targetVersion, sourceId)
        }

        return ProviderIdentityMigrationResult(
            migrated = true,
            transformedReferences = transformed,
            libraryIndexInvalidated = true,
            providerResponsesInvalidated = true,
            artworkInvalidated = true,
        )
    }

    private fun migrateKeepDownloaded(sourceId: String, migrate: (String) -> String) {
        data class PolicySnapshot(
            val kind: String,
            val id: String,
            val name: String,
            val removeUnneededFiles: Long,
            val updatedAt: Long,
            val trackIds: List<String>,
        )

        val policies = queries.selectKeepDownloadedPolicies(sourceId).executeAsList().map { row ->
            PolicySnapshot(
                kind = row.collection_kind,
                id = row.collection_id,
                name = row.name,
                removeUnneededFiles = row.remove_unneeded_files,
                updatedAt = row.updated_at_epoch_millis,
                trackIds = queries.selectKeepDownloadedTrackIds(sourceId, row.collection_kind, row.collection_id).executeAsList(),
            )
        }
        policies.forEach { row -> queries.deleteKeepDownloadedPolicy(sourceId, row.kind, row.id) }
        policies.forEach { row ->
            val migratedId = migrate(row.id)
            queries.upsertKeepDownloadedPolicy(
                source_id = sourceId,
                collection_kind = row.kind,
                collection_id = migratedId,
                name = row.name,
                remove_unneeded_files = row.removeUnneededFiles,
                updated_at_epoch_millis = row.updatedAt,
            )
            row.trackIds.forEach { trackId ->
                queries.insertKeepDownloadedCollectionTrack(sourceId, row.kind, migratedId, migrate(trackId))
            }
        }
    }

    private fun migratePlaybackSession(sourceId: String, migrate: (String) -> String) {
        queries.selectPlaybackSessionQueueForIdentityMigration(sourceId).executeAsList().forEach { row ->
            val track = runCatching { json.decodeFromString<SavedTrack>(row.payload) }.getOrNull() ?: return@forEach
            val migrated = track.migrateIds(migrate)
            queries.updatePlaybackSessionQueueIdentity(
                remote_track_id = migrated.id,
                payload = json.encodeToString(migrated),
                source_id = sourceId,
                queue_index = row.queue_index,
            )
        }
        val legacy = queries.selectPlaybackSession(sourceId).executeAsOneOrNull() ?: return
        val session = runCatching { json.decodeFromString<PlaybackSessionSettings>(legacy) }.getOrNull() ?: return
        queries.updateLegacyPlaybackSessionPayload(
            json.encodeToString(session.copy(tracks = session.tracks.map { it.migrateIds(migrate) })),
            sourceId,
        )
    }

    private fun SavedTrack.migrateIds(migrate: (String) -> String): SavedTrack = copy(
        id = migrate(id),
        artistId = artistId?.let(migrate),
        albumId = albumId?.let(migrate),
        coverArtId = coverArtId?.let(migrate),
        artistCredits = artistCredits.map { credit ->
            SavedArtistCredit(id = credit.id?.let(migrate), name = credit.name)
        },
    )

    private fun unchanged() = ProviderIdentityMigrationResult(migrated = false)
}
