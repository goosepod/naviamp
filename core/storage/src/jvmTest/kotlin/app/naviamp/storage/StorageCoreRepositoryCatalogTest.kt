package app.naviamp.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.naviamp.domain.cache.ProviderMediaSourceConnection
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.settings.PlaybackSessionSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StorageCoreRepositoryCatalogTest {
    @Test
    fun assemblesPortableRepositoriesAndDelegatesOnlyNativeFileCleanup() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        NaviampStorageDatabase.Schema.create(driver)
        val deletedCacheFiles = mutableListOf<String>()
        val deletedDownloadFiles = mutableListOf<String>()
        try {
            val database = NaviampStorageDatabase(driver)
            val catalog = StorageCoreRepositoryCatalog(
                database = database,
                credentialProtector = PassthroughStorageCredentialProtector,
                nowEpochMillis = { 42L },
                databaseLabel = "test.db",
                databaseBytes = { 123L },
                deleteKnownAudioCacheFile = { path -> deletedCacheFiles += path; true },
                deleteKnownDownloadFile = { path -> deletedDownloadFiles += path; true },
            )
            val source = catalog.mediaSources.upsertProviderMediaSource(
                connection = ProviderMediaSourceConnection(
                    displayName = "Server",
                    baseUrl = "https://music.example.test",
                    username = "listener",
                    token = "token",
                    salt = "salt",
                ),
                cacheNamespace = "cache",
                providerId = "navidrome",
            )
            catalog.playbackSessions.savePlaybackSession(PlaybackSessionSettings(), source.id)
            catalog.playbackHistory.recordPlaybackHistory(
                source.id,
                Track(
                    id = TrackId("history"),
                    title = "History",
                    artistName = "Artist",
                    albumTitle = null,
                    durationSeconds = null,
                    coverArtId = null,
                    audioInfo = null,
                    replayGain = null,
                ),
                42L,
            )
            val queries = database.naviampStorageQueries
            queries.upsertCachedAudio(
                source_id = source.id,
                remote_track_id = "cached",
                quality_key = "transcoded:opus:128",
                file_path = "/known/cache.opus",
                size_bytes = 3L,
                content_type = "audio/ogg",
                created_at_epoch_millis = 42L,
                last_accessed_epoch_millis = 42L,
            )
            queries.insertTestDownload(source.id, "/known/download.opus")

            assertEquals(1L, catalog.maintenance.stats().mediaSourceCount)
            assertEquals(1L, catalog.maintenance.stats().playbackSessionCount)
            assertEquals(123L, catalog.maintenance.stats().databaseBytes)
            assertEquals("history", catalog.playbackHistory.playbackHistory(source.id, 10).single().track.id.value)

            catalog.maintenance.clearAll()

            assertEquals(listOf("/known/cache.opus"), deletedCacheFiles)
            assertEquals(listOf("/known/download.opus"), deletedDownloadFiles)
            assertEquals(0L, catalog.maintenance.stats().mediaSourceCount)
            assertTrue(queries.selectAllCachedAudio().executeAsList().isEmpty())
            assertTrue(queries.selectAllDownloadedAudio().executeAsList().isEmpty())
            assertTrue(catalog.playbackHistory.playbackHistory(source.id, 10).isEmpty())
        } finally {
            driver.close()
        }
    }
}

private fun NaviampStorageQueries.insertTestDownload(sourceId: String, filePath: String) {
    upsertDownloadedAudio(
        source_id = sourceId,
        remote_track_id = "downloaded",
        quality_key = "transcoded:opus:128",
        file_path = filePath,
        size_bytes = 3L,
        content_type = "audio/ogg",
        title = "Track",
        artist_id = null,
        artist_name = "Artist",
        album_id = null,
        album_title = null,
        album_release_year = null,
        duration_seconds = null,
        cover_art_id = null,
        audio_codec = "opus",
        audio_bitrate_kbps = 128L,
        audio_content_type = "audio/ogg",
        audio_bit_depth = null,
        audio_sampling_rate_hz = null,
        favorited_at_iso8601 = null,
        user_rating = null,
        downloaded_at_epoch_millis = 42L,
    )
}
