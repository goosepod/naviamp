package app.naviamp.domain.cache

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.ProviderId
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.network.SharedHttpClient
import app.naviamp.domain.network.SharedHttpResponse
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.provider.ProviderCapabilities
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AudioByteStoreServiceTest {
    @Test
    fun stableAudioFileNameUsesSourceTrackAndQuality() {
        assertEquals(
            "e87fef48031de2bf17c06896156c8212",
            stableAudioFileName("source", "track", "original"),
        )
    }

    @Test
    fun audioExtensionMapsCommonAudioContentTypes() {
        assertEquals(".mp3", "audio/mpeg".audioExtension())
        assertEquals(".flac", "audio/flac; charset=utf-8".audioExtension())
        assertEquals(".aac", "audio/aacp".audioExtension())
        assertEquals(".m4a", "audio/x-m4a".audioExtension())
        assertEquals(".audio", null.audioExtension())
    }

    @Test
    fun writeProviderAudioStreamsProviderBytesThroughStore() = runTest {
        val store = RecordingAudioByteStore()
        val provider = RecordingMediaProvider(downloaded = true)
        val service = AudioByteStoreService(store, NoopHttpClient)

        val stored = service.writeProviderAudio(
            sourceId = "source",
            trackId = TrackId("track"),
            qualityKey = "original",
            contentType = "audio/flac",
            provider = provider,
            streamUrl = "https://example.test/stream.flac",
            errorMessage = "failed",
        )

        assertEquals("e87fef48031de2bf17c06896156c8212.flac", store.fileName)
        assertEquals("https://example.test/stream.flac", provider.downloadedUrl)
        assertEquals("abcdef", store.bytes.decodeToString())
        assertEquals("/audio/e87fef48031de2bf17c06896156c8212.flac", stored.filePath)
        assertEquals(6, stored.sizeBytes)
    }

    @Test
    fun writeProviderAudioDeletesFailedZeroByteWrites() = runTest {
        val store = RecordingAudioByteStore(sizeBytes = 0)
        val service = AudioByteStoreService(store, NoopHttpClient)

        assertFailsWith<IllegalStateException> {
            service.writeProviderAudio(
                sourceId = "source",
                trackId = TrackId("track"),
                qualityKey = "original",
                contentType = "audio/flac",
                provider = RecordingMediaProvider(downloaded = true),
                streamUrl = "https://example.test/stream.flac",
                errorMessage = "failed",
            )
        }

        assertEquals(listOf("/audio/e87fef48031de2bf17c06896156c8212.flac"), store.deleted)
    }

    @Test
    fun writeProviderAudioPropagatesProviderDownloadFailure() = runTest {
        val service = AudioByteStoreService(RecordingAudioByteStore(), NoopHttpClient)

        assertFailsWith<IllegalStateException> {
            service.writeProviderAudio(
                sourceId = "source",
                trackId = TrackId("track"),
                qualityKey = "original",
                contentType = "audio/flac",
                provider = RecordingMediaProvider(downloaded = false),
                streamUrl = "https://example.test/stream.flac",
                errorMessage = "failed",
            )
        }
    }

    @Test
    fun writeProviderAudioCoalescesConcurrentWritesForSameKey() = runTest {
        val store = RecordingAudioByteStore(writeDelayMillis = 20)
        val provider = RecordingMediaProvider(downloaded = true)
        val service = AudioByteStoreService(store, NoopHttpClient)

        val results = listOf(
            async {
                service.writeProviderAudio(
                    sourceId = "source",
                    trackId = TrackId("track"),
                    qualityKey = "original",
                    contentType = "audio/flac",
                    provider = provider,
                    streamUrl = "https://example.test/stream.flac",
                    errorMessage = "failed",
                )
            },
            async {
                service.writeProviderAudio(
                    sourceId = "source",
                    trackId = TrackId("track"),
                    qualityKey = "original",
                    contentType = "audio/flac",
                    provider = provider,
                    streamUrl = "https://example.test/stream.flac",
                    errorMessage = "failed",
                )
            },
        ).awaitAll()

        assertEquals(results.first(), results.last())
        assertEquals(1, store.writeCalls)
        assertEquals(1, provider.downloadCalls)
    }

    private class RecordingAudioByteStore(
        private val sizeBytes: Long? = null,
        private val writeDelayMillis: Long = 0,
    ) : AudioByteStore {
        var fileName: String? = null
        var bytes: ByteArray = byteArrayOf()
        var writeCalls: Int = 0
        val deleted = mutableListOf<String>()

        override suspend fun writeAudioBytes(
            fileName: String,
            errorMessage: String,
            writeBytes: suspend (AudioByteWriter) -> Boolean,
        ): StoredAudioBytes {
            writeCalls += 1
            if (writeDelayMillis > 0) delay(writeDelayMillis)
            this.fileName = fileName
            val writer = AudioByteWriter { chunk, count ->
                bytes += chunk.copyOf(count)
            }
            if (!writeBytes(writer)) throw IllegalStateException(errorMessage)
            return StoredAudioBytes("/audio/$fileName", sizeBytes ?: bytes.size.toLong())
        }

        override fun deleteAudioBytes(filePath: String) {
            deleted += filePath
        }
    }

    private class RecordingMediaProvider(
        private val downloaded: Boolean,
    ) : MediaProvider {
        var downloadedUrl: String? = null
        var downloadCalls: Int = 0

        override val id: ProviderId = ProviderId("fake")
        override val displayName: String = "Fake"
        override val capabilities: ProviderCapabilities = ProviderCapabilities(
            supportsStreamingTranscode = true,
            supportsDownloadTranscode = true,
            supportsArtistRadio = true,
            supportsAlbumRadio = true,
            supportsTrackRadio = true,
        )

        override suspend fun downloadStream(
            url: String,
            httpClient: SharedHttpClient,
            writeChunk: suspend (bytes: ByteArray, count: Int) -> Unit,
        ): Boolean {
            downloadCalls += 1
            downloadedUrl = url
            if (downloaded) writeChunk("abcdef".encodeToByteArray(), 6)
            return downloaded
        }

        override suspend fun validateConnection(): ConnectionValidation = error("unused")
        override suspend fun recentlyAddedAlbums(limit: Int): List<Album> = error("unused")
        override suspend fun album(albumId: AlbumId): AlbumDetails = error("unused")
        override suspend fun artist(artistId: ArtistId): ArtistDetails = error("unused")
        override suspend fun artists(limit: Int): List<Artist> = error("unused")
        override suspend fun tracks(limit: Int): List<Track> = error("unused")
        override suspend fun search(query: String, limit: Int): MediaSearchResults = error("unused")
        override suspend fun streamUrl(request: StreamRequest): String = error("unused")
        override fun coverArtUrl(coverArtId: String): String = error("unused")
    }

    private object NoopHttpClient : SharedHttpClient {
        override suspend fun get(url: String, headers: Map<String, String>): String? = null
        override suspend fun getBytes(url: String, headers: Map<String, String>): ByteArray? = null
        override suspend fun getResponse(url: String, headers: Map<String, String>): SharedHttpResponse? = null
        override suspend fun download(
            url: String,
            headers: Map<String, String>,
            writeChunk: suspend (bytes: ByteArray, count: Int) -> Unit,
        ): Boolean = false
    }
}
