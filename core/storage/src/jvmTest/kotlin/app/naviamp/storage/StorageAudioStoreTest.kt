package app.naviamp.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.naviamp.domain.*
import app.naviamp.domain.provider.*
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
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
    fun replacementQuotaAndMidStreamFailuresPreserveExistingBytesAndDatabaseRow() = runBlocking {
        val path = "/downloads/" + app.naviamp.domain.cache.stableAudioFileName("source", "track", "original") + ".flac"
        val original = ByteArray(24) { 7 }
        val files = mutableMapOf(path to original)
        val bytes = object : AudioByteStore {
            override suspend fun writeAudioBytes(fileName: String, errorMessage: String,
                writeBytes: suspend (AudioByteWriter) -> Boolean): StoredAudioBytes {
                var pending = byteArrayOf()
                check(writeBytes(AudioByteWriter { chunk, count -> pending += chunk.copyOf(count) }))
                files["/downloads/$fileName"] = pending
                return StoredAudioBytes("/downloads/$fileName", pending.size.toLong())
            }
            override fun deleteAudioBytes(filePath: String) { files.remove(filePath) }
        }
        fixture(fileExists = { it in files }, byteStore = bytes).use { fixture ->
            fixture.insertDownload(path)
            val before = fixture.store.downloadedTracks("source").single()
            for (failure in listOf("quota", "partial", "empty", "html", "xml", "json")) {
                assertFailsWith<IllegalStateException> {
                    fixture.store.replaceDownloadedAudioTrack("source", AudioTestProvider(failure), before.track,
                        StreamQuality.Original, maxDownloadBytes = if (failure == "quota") 32 else Long.MAX_VALUE)
                }
                assertContentEquals(original, files[path])
                assertEquals(before, fixture.store.downloadedTracks("source").single())
            }
        }
    }

    @Test
    fun configuredDownloadLimitRejectsNewBytesWithoutAllocatingTheReportedUsage() = runBlocking {
        val limit = app.naviamp.domain.settings.CacheSettings().maxDownloadBytes
        fixture(fileExists = { true }, byteStore = object : AudioByteStore {
            override suspend fun writeAudioBytes(fileName: String, errorMessage: String,
                writeBytes: suspend (AudioByteWriter) -> Boolean): StoredAudioBytes {
                check(writeBytes(AudioByteWriter { _, _ -> error("Over-budget bytes reached the writer") }))
                error("Over-budget download committed")
            }
            override fun deleteAudioBytes(filePath: String) = Unit
        }).use { fixture ->
            // Only database accounting is large; no filler files are allocated.
            fixture.insertDownload("/downloads/existing.flac", sizeBytes = limit)
            val target = fixture.store.downloadedTracks("source").single().track.copy(id = TrackId("new"))
            val failure = assertFailsWith<IllegalStateException> {
                fixture.store.downloadAudioTrack("source", AudioTestProvider(), target,
                    StreamQuality.Original, maxDownloadBytes = limit)
            }
            assertEquals("Download storage limit exceeded.", failure.message)
            assertEquals(limit, fixture.queries.downloadedAudioSize().executeAsOne())
            assertEquals(1, fixture.store.downloadedTracks("source").size)
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
            assertEquals(1999, stored.track.originalReleaseYear)
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
    byteStore: AudioByteStore = UnusedAudioByteStore,
): StorageAudioStoreFixture {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    NaviampStorageDatabase.Schema.create(driver)
    val queries = NaviampStorageDatabase(driver).naviampStorageQueries
    val byteService = AudioByteStoreService(byteStore, UnusedSharedHttpClient)
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

    fun insertDownload(path: String, sizeBytes: Long = 24L) {
        queries.upsertDownloadedAudio(
            source_id = "source",
            remote_track_id = "track",
            quality_key = "original",
            file_path = path,
            size_bytes = sizeBytes,
            content_type = "audio/flac",
            title = "Downloaded track",
            artist_id = null,
            artist_name = "Artist",
            album_id = null,
            album_title = "Album",
            album_release_year = 2026L,
            original_release_year = 1999L,
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

private class AudioTestProvider(private val failure: String = "quota") : MediaProvider {
    override val id = ProviderId("test")
    override val displayName = "Test"
    override val capabilities = ProviderCapabilities(false, false, false, false, false)
    override suspend fun validateConnection() = ConnectionValidation(null, null)
    override suspend fun recentlyAddedAlbums(limit: Int) = emptyList<Album>()
    override suspend fun album(albumId: AlbumId): AlbumDetails = error("unused")
    override suspend fun artist(artistId: ArtistId): ArtistDetails = error("unused")
    override suspend fun artists(limit: Int) = emptyList<Artist>()
    override suspend fun tracks(limit: Int) = emptyList<Track>()
    override suspend fun search(query: String, limit: Int) = MediaSearchResults()
    override suspend fun streamUrl(request: StreamRequest) = "https://example.test/audio"
    override suspend fun downloadUrl(request: StreamRequest) = streamUrl(request)
    override fun coverArtUrl(coverArtId: String) = ""
    override suspend fun downloadStream(url: String, httpClient: SharedHttpClient,
        writeChunk: suspend (ByteArray, Int) -> Unit): Boolean {
        if (failure == "empty") return true
        val errorBody = when (failure) {
            "html" -> "  <!DOCTYPE html><html>Server error</html>"
            "xml" -> "<?xml version=\"1.0\"?><error/>"
            "json" -> "{\"error\":\"unavailable\"}"
            else -> null
        }
        if (errorBody != null) {
            // Split the signature across chunks to exercise incremental validation.
            for (byte in errorBody.encodeToByteArray()) writeChunk(byteArrayOf(byte), 1)
            return true
        }
        val count = if (failure == "partial") 16 else 64
        writeChunk(ByteArray(count) { 9 }, count)
        if (failure == "partial") error("Connection lost after partial audio")
        return true
    }
}
