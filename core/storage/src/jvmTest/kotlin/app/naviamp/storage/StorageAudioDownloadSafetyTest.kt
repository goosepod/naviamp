package app.naviamp.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.AudioCodec
import app.naviamp.domain.AudioInfo
import app.naviamp.domain.ProviderId
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.AudioByteStoreService
import app.naviamp.domain.network.SharedHttpClient
import app.naviamp.domain.network.SharedHttpResponse
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.provider.ProviderCapabilities
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** End-to-end download safety through the shared store, byte service, and SQL schema with a fake transport. */
class StorageAudioDownloadSafetyTest {
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also(NaviampStorageDatabase.Schema::create)
    private val queries = NaviampStorageDatabase(driver).naviampStorageQueries
    private val files = MemoryAudioByteStore("/downloads")

    @AfterTest
    fun close() = driver.close()

    @Test
    fun errorDocumentsWithSuccessStatusAndEmptyBodiesNeverComplete() = runBlocking {
        for (body in listOf("", "<!DOCTYPE html><title>Login</title>", "<?xml version=\"1.0\"?><subsonic-response status=\"failed\"/>",
            "{\"subsonic-response\":{\"status\":\"failed\",\"error\":{\"code\":50}}}")) {
            assertFails { store().downloadAudioTrack("source", FakeTransport(body.encodeToByteArray()), track(), StreamQuality.Original, Long.MAX_VALUE) }
            assertNoTraces()
        }
    }

    @Test
    fun truncatedTransfersFailWhetherTheTransportOrTheContainerDetectsIt() = runBlocking {
        // The transport reports a declared-length mismatch by returning false.
        assertFails { store().downloadAudioTrack("source", FakeTransport(flac(), complete = false), track(), StreamQuality.Original, Long.MAX_VALUE) }
        assertNoTraces()
        assertFails { store().downloadAudioTrack("source", FakeTransport(mp4().copyOf(150)), track(), StreamQuality.Original, Long.MAX_VALUE) }
        assertNoTraces()
    }

    @Test
    fun savedMetadataAndExtensionReflectTheReturnedFormatNotTheRequest() = runBlocking {
        val requestedAac = StreamQuality.Transcoded(AudioCodec.Aac, 256)

        val saved = store().downloadAudioTrack("source", FakeTransport(flac()), track(), requestedAac, Long.MAX_VALUE)

        assertEquals("audio/flac", saved.contentType)
        assertTrue(saved.filePath.endsWith(".flac"))
        assertEquals(requestedAac, saved.streamQuality)
        assertEquals("audio/flac", queries.selectAllDownloadedAudio().executeAsOne().content_type)
        assertEquals(setOf(saved.filePath), files.files.keys)
    }

    @Test
    fun genericBinaryAudioWithoutLengthEvidenceRemainsSupported() = runBlocking {
        val body = ByteArray(300) { 9 }

        val saved = store().downloadAudioTrack("source", FakeTransport(body),
            track(contentType = "audio/x-ms-wma"), StreamQuality.Original, Long.MAX_VALUE)

        assertEquals("audio/x-ms-wma", saved.contentType)
        assertTrue(saved.filePath.endsWith(".audio"))
        assertContentEquals(body, files.files.getValue(saved.filePath))
    }

    @Test
    fun fullDiskLeavesNoCompletedRecordAndKeepsTheExistingCopy() = runBlocking {
        val old = store().downloadAudioTrack("source", FakeTransport(flac()), track(), StreamQuality.Original, Long.MAX_VALUE)
        files.failWritesAfterBytes = 20

        assertFails { store().replaceDownloadedAudioTrack("source", FakeTransport(mp4()), track(), StreamQuality.Original, Long.MAX_VALUE) }

        assertEquals(old.filePath, store().downloadedAudioFile("source", TrackId("track"))?.filePath)
        assertEquals(setOf(old.filePath), files.files.keys)
        assertEquals(0, queries.selectPendingAudioFiles().executeAsList().size)
    }

    @Test
    fun successfulReplacementCommitsTheNewCopyBeforeRemovingTheOldOne() = runBlocking {
        val old = store().downloadAudioTrack("source", FakeTransport(flac()), track(), StreamQuality.Original, Long.MAX_VALUE)

        val replaced = store().replaceDownloadedAudioTrack("source", FakeTransport(flac(extra = 9)), track(), StreamQuality.Original, Long.MAX_VALUE)

        assertTrue(replaced.filePath != old.filePath)
        assertEquals(listOf(replaced.filePath), queries.selectAllDownloadedAudio().executeAsList().map { it.file_path })
        assertEquals(setOf(replaced.filePath), files.files.keys)
        assertEquals(0, queries.selectPendingAudioFiles().executeAsList().size)
    }

    @Test
    fun cancellationRemovesPartialBytesAndLeavesNoRecord() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val job = launch {
            store().downloadAudioTrack("source", FakeTransport(flac(), stall = { started.complete(Unit); awaitCancellation() }),
                track(), StreamQuality.Original, Long.MAX_VALUE)
        }
        started.await()
        job.cancel()
        job.join()

        assertNoTraces()
    }

    @Test
    fun processDeathAtEachBoundaryRecoversWithoutAFalseCompletedEntry() {
        for (boundary in Boundary.entries) {
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also(NaviampStorageDatabase.Schema::create)
            val queries = NaviampStorageDatabase(driver).naviampStorageQueries
            val files = MemoryAudioByteStore("/downloads")
            var deleteFails = false
            fun store() = StorageAudioStore(
                queries = queries,
                audioCacheByteStoreService = AudioByteStoreService(files, NoHttp),
                downloadAudioByteStoreService = AudioByteStoreService(files, NoHttp),
                nowEpochMillis = { 1L },
                cachedAudioFileExists = files::exists,
                downloadedAudioFileExists = files::exists,
                deleteKnownAudioCacheFile = files::delete,
                deleteKnownDownloadFile = { path -> !deleteFails && files.delete(path) },
                maxAudioCacheBytes = Long.MAX_VALUE,
            )
            val old = runBlocking { store().downloadAudioTrack("source", FakeTransport(flac()), track(), StreamQuality.Original, Long.MAX_VALUE) }
            val oldBytes = files.files.getValue(old.filePath)
            val never: suspend () -> Unit = { awaitCancellation() }
            var stall: suspend () -> Unit = {}
            when (boundary) {
                Boundary.TemporaryWrite -> stall = never
                Boundary.Validated -> files.beforeRename = never
                Boundary.FinalPlacement -> files.afterRename = never
                Boundary.DatabaseCommit -> deleteFails = true
            }
            // A coroutine that never resumes models a killed process: no cleanup or catch block runs.
            CoroutineScope(Dispatchers.Unconfined).launch {
                store().replaceDownloadedAudioTrack("source", FakeTransport(mp4(), stall = stall), track(),
                    StreamQuality.Original, Long.MAX_VALUE)
            }
            files.beforeRename = {}
            files.afterRename = {}
            deleteFails = false

            val restarted = store()
            runBlocking { restarted.reconcileInterruptedAudioWrites() }

            val rows = queries.selectAllDownloadedAudio().executeAsList()
            assertEquals(1, rows.size, "$boundary")
            val expectedPath = if (boundary == Boundary.DatabaseCommit) rows.single().file_path.also {
                assertTrue(it.endsWith(".m4a"), "$boundary")
            } else old.filePath.also { assertContentEquals(oldBytes, files.files[it], "$boundary") }
            assertEquals(expectedPath, rows.single().file_path, "$boundary")
            assertEquals(setOf(expectedPath), files.files.keys, "$boundary")
            assertEquals(0, queries.selectPendingAudioFiles().executeAsList().size, "$boundary")
            driver.close()
        }
    }

    @Test
    fun cachedAudioUsesTheSameValidatedCommit() = runBlocking {
        assertFails { store().cacheAudioTrack("source", FakeTransport("<html>".encodeToByteArray()), track(), StreamQuality.Original) }
        assertNull(store().cachedAudioFile("source", TrackId("track")))
        assertEquals(emptySet(), files.files.keys)

        val cached = store().cacheAudioTrack("source", FakeTransport(flac()), track(), StreamQuality.Original)
        assertEquals("audio/flac", cached.contentType)
        assertEquals(setOf(cached.filePath), files.files.keys)
    }

    private enum class Boundary { TemporaryWrite, Validated, FinalPlacement, DatabaseCommit }

    private fun store() = StorageAudioStore(
        queries = queries,
        audioCacheByteStoreService = AudioByteStoreService(files, NoHttp),
        downloadAudioByteStoreService = AudioByteStoreService(files, NoHttp),
        nowEpochMillis = { 1L },
        cachedAudioFileExists = files::exists,
        downloadedAudioFileExists = files::exists,
        deleteKnownAudioCacheFile = files::delete,
        deleteKnownDownloadFile = files::delete,
        maxAudioCacheBytes = Long.MAX_VALUE,
    )

    private fun assertNoTraces() {
        assertEquals(0, queries.selectAllDownloadedAudio().executeAsList().size)
        assertEquals(emptySet(), files.files.keys)
        assertEquals(0, queries.selectPendingAudioFiles().executeAsList().size)
    }
}

private fun track(contentType: String? = "audio/flac") = Track(
    id = TrackId("track"), title = "Track", artistName = "Artist", albumTitle = "Album", durationSeconds = 60,
    coverArtId = null, audioInfo = AudioInfo(codec = null, bitrateKbps = null, contentType = contentType), replayGain = null,
)

private fun flac(extra: Int = 0) = "fLaC".encodeToByteArray() + ByteArray(200 + extra) { 1 }

private fun mp4(): ByteArray {
    fun box(type: String, size: Int) = byteArrayOf(0, 0, (size shr 8).toByte(), size.toByte()) +
        type.encodeToByteArray() + ByteArray(size - 8) { 4 }
    return box("ftyp", 16) + box("moov", 40) + box("mdat", 200)
}

private class FakeTransport(
    private val body: ByteArray,
    private val complete: Boolean = true,
    private val stall: suspend () -> Unit = {},
) : MediaProvider {
    override val id = ProviderId("fake")
    override val displayName = "Fake"
    override val capabilities = ProviderCapabilities(true, true, false, false, false)
    override suspend fun downloadStream(url: String, httpClient: SharedHttpClient,
        writeChunk: suspend (ByteArray, Int) -> Unit): Boolean {
        val half = body.size / 2
        if (half > 0) writeChunk(body.copyOfRange(0, half), half)
        stall()
        if (body.size > half) writeChunk(body.copyOfRange(half, body.size), body.size - half)
        return complete
    }
    override suspend fun validateConnection() = ConnectionValidation(null, null)
    override suspend fun recentlyAddedAlbums(limit: Int) = emptyList<Album>()
    override suspend fun album(albumId: AlbumId): AlbumDetails = error("unused")
    override suspend fun artist(artistId: ArtistId): ArtistDetails = error("unused")
    override suspend fun artists(limit: Int) = emptyList<Artist>()
    override suspend fun tracks(limit: Int) = emptyList<Track>()
    override suspend fun search(query: String, limit: Int) = MediaSearchResults()
    override suspend fun streamUrl(request: StreamRequest) = "https://example.test/stream"
    override fun coverArtUrl(coverArtId: String) = ""
}

private object NoHttp : SharedHttpClient {
    override suspend fun get(url: String, headers: Map<String, String>): String? = null
    override suspend fun getBytes(url: String, headers: Map<String, String>): ByteArray? = null
    override suspend fun getResponse(url: String, headers: Map<String, String>): SharedHttpResponse? = null
    override suspend fun download(url: String, headers: Map<String, String>,
        writeChunk: suspend (ByteArray, Int) -> Unit): Boolean = false
}
