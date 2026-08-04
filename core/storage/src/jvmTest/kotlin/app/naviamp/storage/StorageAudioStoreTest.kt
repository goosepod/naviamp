package app.naviamp.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.AudioByteStore
import app.naviamp.domain.cache.AudioByteStoreService
import app.naviamp.domain.cache.AudioByteWriter
import app.naviamp.domain.cache.StoredAudioBytes
import app.naviamp.domain.network.SharedHttpClient
import app.naviamp.domain.network.SharedHttpResponse
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class StorageAudioStoreTest {
    @Test
    fun missingCacheFileRepairsTheOwnedDatabaseRow() = runBlocking {
        fixture(fileExists = { false }).use { fixture ->
            fixture.insertCached("/cache/missing.mp3")

            assertNull(fixture.store.cachedAudioFile("source", TrackId("track"), StreamQuality.Original))
            assertEquals(0L, fixture.queries.audioCacheCount().executeAsOne())
        }
    }

    @Test
    fun cacheEvictionKeepsOwnershipWhenNativeDeletionFails() {
        fixture(fileExists = { true }, deleteCache = { false }).use { fixture ->
            fixture.insertCached("/cache/owned.mp3")

            fixture.store.updateAudioCacheLimit(0L)

            assertEquals(1L, fixture.queries.audioCacheCount().executeAsOne())
        }
    }

    @Test
    fun cacheEvictionPreservesTracksProtectedByTheSharedQueuePolicy() {
        fixture(fileExists = { true }, protectedTrackIds = { setOf("track") }).use { fixture ->
            fixture.insertCached("/cache/protected.mp3")

            fixture.store.updateAudioCacheLimit(0L)

            assertEquals(1L, fixture.queries.audioCacheCount().executeAsOne())
        }
    }

    @Test
    fun downloadRemovalDeletesOnlyAfterTheKnownFileIsRemoved() {
        var allowDeletion = false
        fixture(fileExists = { true }, deleteDownload = { allowDeletion }).use { fixture ->
            fixture.insertDownload("/downloads/owned.mp3")

            fixture.store.removeDownloadedAudio("source", TrackId("track"))
            assertEquals(1, fixture.store.downloadedTracks("source").size)

            allowDeletion = true
            fixture.store.removeDownloadedAudio("source", TrackId("track"))
            assertEquals(0, fixture.store.downloadedTracks("source").size)
        }
    }

    @Test
    fun storedDownloadReconstructsPortableTrackMetadata() {
        fixture(fileExists = { true }).use { fixture ->
            fixture.insertDownload("/downloads/owned.flac")

            val stored = assertNotNull(fixture.store.downloadedTracks("source").singleOrNull())
            assertEquals("track", stored.track.id.value)
            assertEquals("Downloaded track", stored.track.title)
            assertEquals("Artist", stored.track.artistName)
            assertEquals("original", stored.qualityKey)
            assertEquals("/downloads/owned.flac", stored.filePath)
        }
    }
}

private fun fixture(
    fileExists: (String) -> Boolean,
    deleteCache: (String) -> Boolean = { true },
    deleteDownload: (String) -> Boolean = { true },
    protectedTrackIds: () -> Set<String> = { emptySet() },
): StorageAudioStoreFixture {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    NaviampStorageDatabase.Schema.create(driver)
    val queries = NaviampStorageDatabase(driver).naviampStorageQueries
    val byteService = AudioByteStoreService(UnusedAudioByteStore, UnusedSharedHttpClient)
    return StorageAudioStoreFixture(
        driver = driver,
        queries = queries,
        store = StorageAudioStore(
            queries = queries,
            audioCacheByteStoreService = byteService,
            downloadAudioByteStoreService = byteService,
            nowEpochMillis = { 100L },
            cachedAudioFileExists = fileExists,
            downloadedAudioFileExists = fileExists,
            deleteKnownAudioCacheFile = deleteCache,
            deleteKnownDownloadFile = deleteDownload,
            maxAudioCacheBytes = 1024L,
            protectedTrackIds = protectedTrackIds,
        ),
    )
}

private class StorageAudioStoreFixture(
    val driver: JdbcSqliteDriver,
    val queries: NaviampStorageQueries,
    val store: StorageAudioStore,
) : AutoCloseable {
    fun insertCached(path: String) {
        queries.upsertCachedAudio(
            source_id = "source",
            remote_track_id = "track",
            quality_key = "original",
            file_path = path,
            size_bytes = 12L,
            content_type = "audio/mpeg",
            created_at_epoch_millis = 1L,
            last_accessed_epoch_millis = 1L,
        )
    }

    fun insertDownload(path: String) {
        queries.upsertDownloadedAudio(
            source_id = "source",
            remote_track_id = "track",
            quality_key = "original",
            file_path = path,
            size_bytes = 24L,
            content_type = "audio/flac",
            title = "Downloaded track",
            artist_id = null,
            artist_name = "Artist",
            album_id = null,
            album_title = "Album",
            album_release_year = 2026L,
            duration_seconds = 120L,
            cover_art_id = null,
            audio_codec = "flac",
            audio_bitrate_kbps = null,
            audio_content_type = "audio/flac",
            audio_bit_depth = 16L,
            audio_sampling_rate_hz = 44_100L,
            favorited_at_iso8601 = null,
            user_rating = null,
            downloaded_at_epoch_millis = 2L,
        )
    }

    override fun close() = driver.close()
}

private object UnusedAudioByteStore : AudioByteStore {
    override suspend fun writeAudioBytes(
        fileName: String,
        errorMessage: String,
        writeBytes: suspend (AudioByteWriter) -> Boolean,
    ): StoredAudioBytes = error("Not used by repository metadata tests.")

    override fun deleteAudioBytes(filePath: String) = Unit
}

private object UnusedSharedHttpClient : SharedHttpClient {
    override suspend fun get(url: String, headers: Map<String, String>): String? = null
    override suspend fun getBytes(url: String, headers: Map<String, String>): ByteArray? = null
    override suspend fun getResponse(url: String, headers: Map<String, String>): SharedHttpResponse? = null
    override suspend fun download(
        url: String,
        headers: Map<String, String>,
        writeChunk: suspend (ByteArray, Int) -> Unit,
    ): Boolean = false
}
