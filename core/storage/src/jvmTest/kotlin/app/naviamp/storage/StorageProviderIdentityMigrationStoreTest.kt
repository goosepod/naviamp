package app.naviamp.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.naviamp.domain.ArtistCredit
import app.naviamp.domain.ArtistId
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.ProviderMediaSourceConnection
import app.naviamp.domain.cache.ProviderIdentityProbeState
import app.naviamp.domain.settings.PlaybackSessionSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StorageProviderIdentityMigrationStoreTest {
    @Test
    fun atomicallyRelinksDurableMediaWithoutDeletingFilesAndInvalidatesReproducibleCaches() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        NaviampStorageDatabase.Schema.create(driver)
        val database = NaviampStorageDatabase(driver)
        val queries = database.naviampStorageQueries
        var deletionAttempted = false
        val catalog = StorageCoreRepositoryCatalog(
            database = database,
            credentialProtector = PassthroughStorageCredentialProtector,
            nowEpochMillis = { 50L },
            databaseLabel = "test.db",
            deleteKnownAudioCacheFile = { deletionAttempted = true; false },
            deleteKnownDownloadFile = { deletionAttempted = true; false },
        )
        try {
            val source = catalog.mediaSources.upsertProviderMediaSource(
                ProviderMediaSourceConnection("Server", "https://example.test", "user", "token", "salt", "native"),
                cacheNamespace = "navidrome:test",
                providerId = "navidrome",
            )
            val oldTrack = "old-track"
            val oldArtist = "old-artist"
            val oldAlbum = "old-album"
            val oldCover = "old-cover"
            val oldPlaylist = "old-playlist"
            val migrated = mapOf(
                oldTrack to "new-track",
                oldArtist to "new-artist",
                oldAlbum to "new-album",
                oldCover to "new-cover",
                oldPlaylist to "new-playlist",
            )
            val transform: (String) -> String = { migrated[it] ?: it }

            queries.upsertLibraryArtist(source.id, oldArtist, "Artist", "artist", 1L)
            queries.upsertLibraryAlbum(source.id, oldAlbum, oldArtist, "Album", "Artist", "album", "artist", oldCover, null, 1L)
            queries.upsertCachedAudio(source.id, oldTrack, "original", "/owned/cache.flac", 10L, "audio/flac", 1L, 1L)
            queries.upsertDownloadedAudio(
                source.id, oldTrack, "original", "/owned/download.flac", 20L, "audio/flac", "Track",
                oldArtist, "Artist", oldAlbum, "Album", null, 120L, oldCover, "flac", null, "audio/flac",
                null, null, null, null, 1L,
            )
            queries.upsertKeepDownloadedPolicy(source.id, "playlist", oldPlaylist, "Playlist", 0L, 1L)
            queries.insertKeepDownloadedCollectionTrack(source.id, "playlist", oldPlaylist, oldTrack)
            queries.insertManagedKeepDownloadedTrack(source.id, oldTrack)
            queries.upsertCachedAudioWaveform(source.id, oldTrack, "original", "/owned/cache.flac", 1L, "[0.5]", 3L, 1L, 1L)
            queries.upsertCachedLyrics(source.id, oldTrack, "provider", 0L, "[]", null, null, null, 0L, 2L, 1L, 1L)
            queries.upsertCachedLrclibLyrics(source.id, oldTrack, 0L, "[]", null, null, null, 0L, 2L, 1L, 1L)
            queries.upsertTrackLyricsOffset(source.id, oldTrack, 50L, 1L)
            queries.upsertCachedSidecarStatus(source.id, oldTrack, "original", "lyrics", "ready", 1L, null, 1L)
            val track = Track(
                id = TrackId(oldTrack), title = "Track", artistId = ArtistId(oldArtist), artistName = "Artist",
                albumId = app.naviamp.domain.AlbumId(oldAlbum), albumTitle = "Album", durationSeconds = 120,
                coverArtId = oldCover, audioInfo = null, replayGain = null,
                artistCredits = listOf(ArtistCredit(ArtistId(oldArtist), "Artist")),
            )
            catalog.playbackSessions.savePlaybackSession(PlaybackSessionSettings.fromTracks(listOf(track), 0), source.id)
            queries.upsertPlaybackHistory(
                source.id, oldTrack, "Track", oldArtist, "Artist", oldAlbum, "Album", null, 120L, oldCover,
                "flac", null, "audio/flac", null, null, null, null, 1L,
            )
            queries.insertPendingProviderAction(source.id, "favorite-track", oldTrack, 1L, null, 1L)
            queries.upsertResponse("key", "navidrome", "track", oldTrack, "{}", 1L, 1L)
            queries.upsertImage("https://example.test/art/$oldCover", byteArrayOf(1), 1L, 1L, 1L)

            assertTrue(catalog.mediaSources.providerIdentitySamples(source.id).contains(oldTrack))
            val probeState = ProviderIdentityProbeState(1L, "0.63.2 (old-build)")
            catalog.mediaSources.recordProviderIdentityProbeState(source.id, probeState)
            assertEquals(probeState, catalog.mediaSources.providerIdentityProbeState(source.id))

            val result = catalog.mediaSources.migrateProviderIdentities(source.id, "navidrome", 1L, transform)

            assertTrue(result.migrated)
            assertTrue(result.transformedReferences > 0)
            assertFalse(deletionAttempted, "identity migration must never invoke native file deletion")
            assertTrue(queries.selectLibraryArtists(source.id, 10L, 0L).executeAsList().isEmpty())
            assertEquals("/owned/cache.flac", queries.selectAnyCachedAudio(source.id, "new-track").executeAsOne().file_path)
            val download = queries.selectDownloadedAudioFileForTrack(source.id, "new-track").executeAsOne()
            assertEquals("/owned/download.flac", download.file_path)
            assertEquals("new-artist", download.artist_id)
            assertEquals("new-album", download.album_id)
            assertEquals("new-cover", download.cover_art_id)
            assertEquals(listOf("new-track"), queries.selectKeepDownloadedTrackIds(source.id, "playlist", "new-playlist").executeAsList())
            assertEquals(listOf("new-track"), queries.selectManagedKeepDownloadedTrackIds(source.id).executeAsList())
            assertEquals("new-track", catalog.playbackSessions.loadPlaybackSession(source.id)?.tracks?.single()?.id)
            assertEquals("new-artist", catalog.playbackSessions.loadPlaybackSession(source.id)?.tracks?.single()?.artistId)
            assertEquals("new-track", queries.selectPlaybackHistory(source.id, 1L).executeAsOne().remote_track_id)
            assertEquals("new-track", queries.selectPendingProviderActions(source.id, 1L).executeAsOne().entity_id)
            assertEquals(0L, queries.responseCacheCount().executeAsOne())
            assertEquals(0L, queries.imageCacheCount().executeAsOne())
            assertNull(catalog.mediaSources.mediaSource(source.id)?.lastLibraryScanSignature)
            assertNull(catalog.mediaSources.providerIdentityProbeState(source.id))

            assertFalse(catalog.mediaSources.migrateProviderIdentities(source.id, "navidrome", 1L, transform).migrated)
        } finally {
            driver.close()
        }
    }

    @Test
    fun migrationCollapsesAnAlreadyRedownloadedCanonicalDuplicateWithoutDeletingEitherFile() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        NaviampStorageDatabase.Schema.create(driver)
        val database = NaviampStorageDatabase(driver)
        var deletionAttempted = false
        val catalog = StorageCoreRepositoryCatalog(
            database = database,
            credentialProtector = PassthroughStorageCredentialProtector,
            nowEpochMillis = { 50L },
            databaseLabel = "test.db",
            deleteKnownAudioCacheFile = { deletionAttempted = true; false },
            deleteKnownDownloadFile = { deletionAttempted = true; false },
        )
        try {
            val source = catalog.mediaSources.upsertProviderMediaSource(
                ProviderMediaSourceConnection("Server", "https://example.test", "user", "token", "salt", null),
                cacheNamespace = "navidrome:test",
                providerId = "navidrome",
            )
            val queries = database.naviampStorageQueries
            fun download(id: String, path: String, cover: String, downloadedAt: Long) {
                queries.upsertDownloadedAudio(
                    source.id, id, "transcoded:opus:128", path, 20L, "audio/ogg", "Track",
                    null, "Artist", null, "Album", null, 120L, cover, "opus", 128L, "audio/ogg",
                    null, null, null, null, downloadedAt,
                )
            }
            download("old-track", "/owned/old.ogg", "old-cover", 1L)
            download("new-track", "/owned/redownloaded.ogg", "new-cover", 2L)

            catalog.mediaSources.migrateProviderIdentities(source.id, "navidrome", 1L) { value ->
                when (value) {
                    "old-track" -> "new-track"
                    "old-cover" -> "new-cover"
                    else -> value
                }
            }

            val downloads = queries.selectDownloadedAudio(source.id).executeAsList()
            assertEquals(1, downloads.size)
            assertEquals("new-track", downloads.single().remote_track_id)
            assertEquals("new-cover", downloads.single().cover_art_id)
            assertEquals("/owned/redownloaded.ogg", downloads.single().file_path)
            assertEquals(2L, downloads.single().downloaded_at_epoch_millis)
            assertFalse(deletionAttempted, "collision repair must retain physical files when migration cannot delete safely")
        } finally {
            driver.close()
        }
    }
}
