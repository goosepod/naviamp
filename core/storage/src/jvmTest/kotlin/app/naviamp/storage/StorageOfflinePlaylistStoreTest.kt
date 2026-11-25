package app.naviamp.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.OfflinePlaylistRetention
import app.naviamp.domain.cache.OfflinePlaylistSnapshot
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class StorageOfflinePlaylistStoreTest {
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { driver ->
        NaviampStorageDatabase.Schema.create(driver)
        driver.execute(null, "PRAGMA foreign_keys=ON", 0)
        listOf("source", "other").forEach { id ->
            driver.execute(null, """
                INSERT INTO media_source(id, provider_id, cache_namespace, display_name, base_url,
                    username, token, salt, created_at_epoch_millis)
                VALUES ('$id', 'navidrome', '$id', 'Server', 'https://example.test', 'user', '', '', 1)
            """.trimIndent(), 0)
        }
    }
    private val queries = NaviampStorageDatabase(driver).naviampStorageQueries
    private val store = StorageKeepDownloadedStore(queries, nowEpochMillis = { 42L })
    private val playlist = Playlist("road", "Road Trip", trackCount = 4, coverArtId = "cover", owner = "me")

    @AfterTest fun close() = driver.close()

    private fun track(id: String) = Track(TrackId(id), "Track $id", artistName = "Artist", albumTitle = "Album",
        durationSeconds = 120, coverArtId = "art-$id", audioInfo = null, replayGain = null)

    private fun download(sourceId: String, trackId: String) = driver.execute(null, """
        INSERT INTO downloaded_audio(source_id, remote_track_id, quality_key, file_path, size_bytes,
            title, artist_name, downloaded_at_epoch_millis)
        VALUES ('$sourceId', '$trackId', 'original', '/fixture/$sourceId-$trackId', 100, 'T', 'A', 1)
    """.trimIndent(), 0)

    private fun save(id: String = playlist.id, tracks: List<String>, retention: OfflinePlaylistRetention = OfflinePlaylistRetention.Manual,
        sourceId: String = "source") =
        store.saveOfflinePlaylist(OfflinePlaylistSnapshot(sourceId, playlist.copy(id = id, name = "Playlist $id"),
            tracks.map(::track), retention))

    @Test fun snapshotSurvivesStoreRecreationWithOrderRepeatsAndSourceScope() {
        store.saveOfflinePlaylist(OfflinePlaylistSnapshot("source", playlist,
            listOf(track("b"), track("a"), track("b"), track("c")), OfflinePlaylistRetention.KeepDownloaded))
        save(tracks = listOf("x"), sourceId = "other")

        val restarted = StorageKeepDownloadedStore(queries, nowEpochMillis = { 99L })
        val snapshot = checkNotNull(restarted.offlinePlaylist("source", "road"))

        assertEquals(listOf("b", "a", "b", "c"), snapshot.tracks.map { it.id.value })
        assertEquals("art-a", snapshot.tracks[1].coverArtId)
        assertEquals("Road Trip", snapshot.playlist.name)
        assertEquals(4, snapshot.playlist.trackCount)
        assertFalse(snapshot.playlist.canEdit)
        assertEquals(OfflinePlaylistRetention.KeepDownloaded, snapshot.retention)
        assertEquals(42L, snapshot.savedAtEpochMillis)
        val summary = restarted.offlinePlaylistSummaries("source").single()
        assertEquals(listOf("b", "a", "b", "c"), summary.trackIds)
        assertEquals(listOf("x"), restarted.offlinePlaylistSummaries("other").single().trackIds)
    }

    @Test fun savingAgainReplacesMembershipAndDetailsUpdateKeepsIt() {
        save(tracks = listOf("a", "b", "c"))
        save(tracks = listOf("c", "a"))
        store.updateOfflinePlaylistDetails("source", playlist.copy(name = "Renamed", coverArtId = "new"))
        store.updateOfflinePlaylistDetails("source", playlist.copy(id = "missing", name = "Ignored"))

        val snapshot = checkNotNull(store.offlinePlaylist("source", "road"))
        assertEquals(listOf("c", "a"), snapshot.tracks.map { it.id.value })
        assertEquals("Renamed", snapshot.playlist.name)
        assertEquals("new", snapshot.playlist.coverArtId)
        assertNull(store.offlinePlaylist("source", "missing"))
    }

    @Test fun deletingSnapshotOrSourceRemovesEveryRow() {
        save(tracks = listOf("a", "a"))
        save(id = "second", tracks = listOf("b"))
        store.deleteOfflinePlaylist("source", "road")
        assertNull(store.offlinePlaylist("source", "road"))
        assertEquals(listOf("second"), store.offlinePlaylistSummaries("source").map { it.playlist.id })

        queries.deleteMediaSource("source")
        assertEquals(0L, count("SELECT COUNT(*) FROM offline_playlist"))
        assertEquals(0L, count("SELECT COUNT(*) FROM offline_playlist_entry"))
    }

    @Test fun orphanPruningRemovesOnlyAffectedOneTimeSnapshotsWithNoDownloadedTracks() {
        download("source", "shared")
        save(id = "manual-shared", tracks = listOf("shared", "gone"))
        save(id = "manual-gone", tracks = listOf("gone"))
        save(id = "manual-other", tracks = listOf("never"))
        save(id = "kept", tracks = listOf("gone"), retention = OfflinePlaylistRetention.KeepDownloaded)

        store.pruneOrphanedOfflinePlaylists("source", removedTrackId = "gone")
        assertEquals(listOf("kept", "manual-other", "manual-shared"), ids())

        store.pruneOrphanedOfflinePlaylists("source")
        assertEquals(listOf("kept", "manual-shared"), ids())
    }

    @Test fun clearingDownloadDataKeepsSubscriptionsAndRemovesOrphanedOneTimeSnapshots() {
        download("source", "a")
        save(id = "manual", tracks = listOf("a"))
        save(id = "kept", tracks = listOf("a"), retention = OfflinePlaylistRetention.KeepDownloaded)

        StorageMaintenanceStore(queries).clearDownloadData { true }

        assertEquals(listOf("kept"), ids())
    }

    @Test fun providerIdentityMigrationRewritesSnapshotPlaylistAndTrackIds() {
        driver.execute(null, "UPDATE media_source SET provider_identity_version = 1 WHERE id = 'source'", 0)
        save(tracks = listOf("a", "b", "a"))

        StorageProviderIdentityMigrationStore(queries, Json).migrate("source", "navidrome", 2L) { "v2-$it" }

        assertNull(store.offlinePlaylist("source", "road"))
        val migrated = checkNotNull(store.offlinePlaylist("source", "v2-road"))
        assertEquals(listOf("v2-a", "v2-b", "v2-a"), migrated.tracks.map { it.id.value })
        assertEquals("v2-cover", migrated.playlist.coverArtId)
        assertEquals("v2-art-a", migrated.tracks.first().coverArtId)
    }

    private fun ids() = store.offlinePlaylistSummaries("source").map { it.playlist.id }.sorted()

    private fun count(sql: String): Long = driver.executeQuery(null, sql, { cursor ->
        cursor.next()
        app.cash.sqldelight.db.QueryResult.Value(cursor.getLong(0) ?: 0L)
    }, 0).value
}
