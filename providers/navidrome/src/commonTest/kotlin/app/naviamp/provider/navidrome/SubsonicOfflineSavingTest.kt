package app.naviamp.provider.navidrome

import app.naviamp.domain.AudioCodec
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.DownloadReplacementRepository
import app.naviamp.domain.cache.DownloadRepository
import app.naviamp.domain.cache.DownloadService
import app.naviamp.domain.cache.DownloadTracksResult
import app.naviamp.domain.network.SharedHttpClient
import app.naviamp.domain.network.SharedHttpResponse
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.ProviderIdSubsonic
import io.ktor.http.parseQueryString
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** #514 permission and endpoint matrix, exercised through the shared download service. */
class SubsonicOfflineSavingTest {
    @Test
    fun downloadRoleSelectsOnlyWhetherTheDownloadEndpointIsUsed() = runTest {
        val transcoded = StreamQuality.Transcoded(AudioCodec.Aac, 192)
        data class Case(val user: String, val original: String?, val transcoded: String?)
        val cases = listOf(
            Case("{\"downloadRole\":true,\"streamRole\":true}", "download.view", "stream.view"),
            Case("{\"downloadRole\":false,\"streamRole\":true}", "stream.view", "stream.view"),
            Case("{\"downloadRole\":true,\"streamRole\":false}", "download.view", null),
            Case("{\"downloadRole\":false,\"streamRole\":false}", null, null),
            Case("{\"downloadRole\":false}", "stream.view", "stream.view"),
            Case("{}", "download.view", "stream.view"),
        )
        for (case in cases) {
            val provider = connected(Server(user = case.user))
            for ((quality, expected) in listOf(StreamQuality.Original to case.original, transcoded to case.transcoded)) {
                val request = StreamRequest(TrackId("s"), quality)
                if (expected == null) {
                    assertFailsWith<NavidromeException>("${case.user} $quality") { provider.downloadUrl(request) }
                } else {
                    assertEquals(expected, provider.downloadUrl(request).substringBefore('?').substringAfterLast('/'),
                        "${case.user} $quality")
                }
            }
            assertEquals(case.original != null, provider.capabilities.supportsDownloads, case.user)
        }
    }

    @Test
    fun streamSavingRequestsRawOriginalOrTheSelectedCodecAndBitrate() = runTest {
        val provider = connected(Server(user = "{\"downloadRole\":false,\"streamRole\":true}"))

        val original = parseQueryString(provider.downloadUrl(StreamRequest(TrackId("s"), StreamQuality.Original)).substringAfter('?'))
        val transcoded = parseQueryString(provider.downloadUrl(
            StreamRequest(TrackId("s"), StreamQuality.Transcoded(AudioCodec.Opus, 128), startPositionSeconds = 30.0),
        ).substringAfter('?'))

        assertEquals("raw", original["format"])
        assertEquals("opus", transcoded["format"])
        assertEquals("128", transcoded["maxBitRate"])
        assertEquals(null, transcoded["timeOffset"])
    }

    @Test
    fun disabledDownloadsStillSaveOfflineThroughStreamWithoutADownloadRequest() = runTest {
        val server = Server(user = "{\"downloadRole\":false,\"streamRole\":true}")
        val provider = connected(server)
        val saved = MemoryDownloads()

        val result = download(provider, saved, StreamQuality.Original)

        assertEquals(DownloadTracksResult.Completed(1), result)
        assertEquals(listOf("stream.view"), server.transfers)
        assertEquals(listOf("s"), saved.tracks.keys.toList())
    }

    @Test
    fun streamingDenialBlocksStreamSavingWithoutSendingAnyMediaRequest() = runTest {
        val server = Server(user = "{\"downloadRole\":false,\"streamRole\":false}")
        val provider = connected(server)
        val saved = MemoryDownloads()

        val result = download(provider, saved, StreamQuality.Transcoded(AudioCodec.Mp3, 192))

        assertIs<DownloadTracksResult.Failed>(result)
        assertEquals(emptyList(), server.transfers)
        assertTrue(saved.tracks.isEmpty())
    }

    @Test
    fun authorizationFailureIsReportedWithoutRetryingThroughAnotherEndpoint() = runTest {
        // Permissions changed after validation: the server now denies the chosen download endpoint.
        val server = Server(user = "{\"downloadRole\":true,\"streamRole\":true}", deniedTransfers = setOf("download.view"))
        val provider = connected(server)
        val saved = MemoryDownloads()

        val result = download(provider, saved, StreamQuality.Original)

        assertIs<DownloadTracksResult.Failed>(result)
        assertEquals(50, (result.error as? NavidromeException)?.subsonicErrorCode)
        assertEquals(listOf("download.view"), server.transfers)
        assertTrue(saved.tracks.isEmpty())
    }

    @Test
    fun unavailableAccountMetadataDoesNotPreventConnectionOrRestrictSaving() = runTest {
        val provider = connected(Server(user = null))

        assertTrue(provider.capabilities.supportsDownloads)
        assertFalse(provider.downloadUrl(StreamRequest(TrackId("s"), StreamQuality.Original)).contains("stream.view"))
    }

    private suspend fun connected(server: Server) = NavidromeProvider(
        NavidromeConnection(providerId = ProviderIdSubsonic, baseUrl = "https://test.invalid",
            username = "me", token = "token", salt = "salt"),
        server,
    ).also { it.validateConnection() }

    private suspend fun download(provider: MediaProvider, saved: MemoryDownloads, quality: StreamQuality) =
        DownloadService(saved, saved).downloadTracksWithStatus(
            sourceId = "source",
            provider = provider,
            tracks = listOf(Track(id = TrackId("s"), title = "Song", artistName = "Artist", albumTitle = null,
                durationSeconds = 1, coverArtId = null, audioInfo = null, replayGain = null)),
            quality = quality,
            maxDownloadBytes = Long.MAX_VALUE,
            label = "song",
            setStatus = {},
        )

    private class Server(
        private val user: String?,
        private val deniedTransfers: Set<String> = emptySet(),
    ) : NavidromeHttpClient {
        val transfers = mutableListOf<String>()

        override suspend fun get(url: String): String = when (url.endpoint()) {
            "getUser.view" -> user?.let { envelope("\"user\":$it") } ?: throw NavidromeHttpException(404)
            else -> envelope("")
        }

        override suspend fun download(
            url: String,
            headers: Map<String, String>,
            writeChunk: suspend (bytes: ByteArray, count: Int) -> Unit,
        ): Boolean {
            val endpoint = url.endpoint()
            transfers += endpoint
            if (endpoint in deniedTransfers) throw NavidromeException("User is not authorized for the given operation.", 50)
            val bytes = "fLaC".encodeToByteArray() + ByteArray(32)
            writeChunk(bytes, bytes.size)
            return true
        }

        private fun String.endpoint() = substringBefore('?').substringAfterLast('/')
        private fun envelope(body: String) =
            "{\"subsonic-response\":{\"status\":\"ok\"${if (body.isEmpty()) "" else ",$body"}}}"
    }

    /** Mirrors the storage repository's route: resolve the provider download URL, then transfer it. */
    private class MemoryDownloads : DownloadRepository<String, Track>, DownloadReplacementRepository<String> {
        val tracks = linkedMapOf<String, ByteArray>()

        override suspend fun downloadAudioTrack(
            sourceId: String,
            provider: MediaProvider,
            track: Track,
            quality: StreamQuality,
            maxDownloadBytes: Long,
        ): String {
            var bytes = byteArrayOf()
            val complete = provider.downloadStream(provider.downloadUrl(StreamRequest(track.id, quality)), NoHttp) { chunk, count ->
                bytes += chunk.copyOf(count)
            }
            check(complete)
            tracks[track.id.value] = bytes
            return track.id.value
        }

        override suspend fun replaceDownloadedAudioTrack(
            sourceId: String,
            provider: MediaProvider,
            track: Track,
            quality: StreamQuality,
            maxDownloadBytes: Long,
        ): String = downloadAudioTrack(sourceId, provider, track, quality, maxDownloadBytes)

        override suspend fun downloadedAudioFile(sourceId: String, trackId: TrackId, quality: StreamQuality) =
            trackId.value.takeIf { it in tracks }
        override suspend fun downloadedAudioFile(sourceId: String, trackId: TrackId) = trackId.value.takeIf { it in tracks }
        override fun downloadedTracks(sourceId: String): List<Track> = emptyList()
        override fun removeDownloadedAudio(sourceId: String, trackId: TrackId, quality: StreamQuality) = Unit
        override fun removeDownloadedAudio(sourceId: String, trackId: TrackId) = Unit
    }

    private object NoHttp : SharedHttpClient {
        override suspend fun get(url: String, headers: Map<String, String>): String? = null
        override suspend fun getBytes(url: String, headers: Map<String, String>): ByteArray? = null
        override suspend fun getResponse(url: String, headers: Map<String, String>): SharedHttpResponse? = null
        override suspend fun download(url: String, headers: Map<String, String>,
            writeChunk: suspend (ByteArray, Int) -> Unit): Boolean = error("Provider-owned URLs use the provider transport")
    }
}
