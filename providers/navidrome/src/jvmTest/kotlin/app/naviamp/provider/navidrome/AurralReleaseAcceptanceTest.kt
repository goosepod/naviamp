package app.naviamp.provider.navidrome

import app.naviamp.domain.StreamQuality
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.provider.*
import app.naviamp.provider.aurral.AurralDiscoveryClient
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

/** Opt-in private-server acceptance. Neither credentials nor private addresses enter artifacts. */
class AurralReleaseAcceptanceTest {
    @Test fun prepareVerifiedRecordingCandidateUsingOnlyReadRoutes() = runBlocking {
        val connection = acceptanceConnection() ?: return@runBlocking
        val file = candidateFile()
        require(!Files.exists(attemptFile(file))) { "A submitted candidate cannot be replaced or replayed" }
        val wire = AcceptanceWire(connection)
        val client = AurralDiscoveryClient(connection, wire)
        val artist = ProviderDiscoveryArtist(Radiohead, "Radiohead", detailsAvailable = true)
        assertEquals(true, client.artistDetails(artist).details?.canRequestTracks)
        var candidate: JsonObject? = null
        var previewBytes = 0
        for (query in listOf("OK Computer", "In Rainbows", "Kid A")) {
            val page = client.searchCatalog(query, ProviderCatalogScope.Album, limit = 10)
            assertEquals(ProviderDiscoveryStatus.Ready, page.status)
            for (album in page.albums.filter { it.artistMbid?.value.equals(Radiohead, ignoreCase = true) }
                .distinctBy { it.release.id }.take(2)) {
                val group = album.release.releaseGroupMbid ?: continue
                val details = client.releaseDetails(group)
                if (details.status != ProviderDiscoveryStatus.Ready || details.credits.none { it.mbid?.value.equals(Radiohead, ignoreCase = true) }) continue
                val release = details.release ?: continue
                val ownership = client.releaseOwnership(group)
                if (ownership.status != ProviderDiscoveryStatus.Ready || ownership.availability !in
                    listOf(ProviderDiscoveryReleaseAvailability.NotOwned, ProviderDiscoveryReleaseAvailability.Partial)) continue
                val tracks = client.releaseTracks(group, ProviderDiscoveryReleaseContext(artist, release))
                if (tracks.status != ProviderDiscoveryStatus.Ready) continue
                val missing = tracks.tracks.filter { it.recordingMbid != null && it.recordingMbid !in ownership.ownedRecordings }
                val track = missing.firstOrNull { it.previewId != null } ?: missing.firstOrNull() ?: continue
                candidate = candidateJson(artist, release, track)
                track.previewId?.let { preview ->
                    val provider = NavidromeProvider(connection, wire)
                    val sample = ProviderDiscoverySample(preview.value, track.title, release.title)
                    val url = provider.streamUrl(StreamRequest(sample.playbackTrack(artist.name).id, StreamQuality.Original))
                    previewBytes = assertNotNull(provider.bytesForOwnedUrl(url)).size
                    assertTrue(previewBytes > 1000)
                }
                break
            }
            if (candidate != null) break
        }
        val verified = assertNotNull(candidate, "No verified missing recording found; no acquisition was submitted")
        Files.createDirectories(file.parent)
        Files.writeString(file, Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), verified))
        println("Read-only recording candidate prepared: artist=${verified.text("artistName")}, release=${verified.text("albumName")}, track=${verified.text("trackName")}, previewBytes=$previewBytes")
        assertEquals(0, wire.submissions)
    }

    @Test fun submitExactlyOneExplicitlyApprovedRecordingAndNeverReplayIt() = runBlocking {
        val approvedRecording = System.getenv("NAVIAMP_AURRAL_APPROVED_RECORDING_MBID") ?: return@runBlocking
        val approvedGroup = checkNotNull(System.getenv("NAVIAMP_AURRAL_APPROVED_RELEASE_GROUP_MBID"))
        val connection = checkNotNull(acceptanceConnection())
        val file = candidateFile()
        val candidate = Json.parseToJsonElement(Files.readString(file)).jsonObject
        require(candidate.text("trackMbid") == approvedRecording && candidate.text("albumMbid") == approvedGroup)
        require(!Files.exists(attemptFile(file))) { "This approval already has an attempt; do not replay an uncertain mutation" }
        val wire = AcceptanceWire(connection, candidate, attemptFile(file))
        val client = AurralDiscoveryClient(connection, wire)
        val artist = ProviderDiscoveryArtist(checkNotNull(candidate.text("artistMbid")), checkNotNull(candidate.text("artistName")), detailsAvailable = true)
        assertEquals(true, client.artistDetails(artist).details?.canRequestTracks)
        val group = DiscoveryReleaseGroupMbid(approvedGroup)
        val details = client.releaseDetails(group)
        assertEquals(ProviderDiscoveryStatus.Ready, details.status)
        assertTrue(details.credits.any { it.mbid?.value.equals(artist.id, ignoreCase = true) })
        val release = assertNotNull(details.release)
        val ownership = client.releaseOwnership(group)
        assertEquals(ProviderDiscoveryStatus.Ready, ownership.status)
        assertTrue(DiscoveryRecordingMbid(approvedRecording) !in ownership.ownedRecordings)
        val tracks = client.releaseTracks(group, ProviderDiscoveryReleaseContext(artist, release))
        assertEquals(ProviderDiscoveryStatus.Ready, tracks.status)
        val track = tracks.tracks.single { it.recordingMbid?.value == approvedRecording && it.releaseMbid?.value == candidate.text("releaseMbid") }
        val result = client.requestReleaseTrack(ProviderDiscoveryReleaseContext(artist, release), track)
        val output = buildJsonObject {
            put("status", result.status.name); result.httpStatusCode?.let { put("httpStatus", it) }
            put("jobIds", buildJsonArray { result.identity?.jobIds.orEmpty().forEach { add(it.value) } })
            result.identity?.requestGroupId?.let { put("requestGroupId", it.value) }
            result.identity?.canonicalAlbumId?.let { put("canonicalAlbumId", it.value) }
            result.identity?.managedAlbumId?.let { put("manager", it.manager.name); put("managedAlbumId", it.value) }
        }
        Files.writeString(file.resolveSibling(file.fileName.toString() + ".result.json"), output.toString())
        println("One approved acquisition attempt: status=${result.status}, httpStatus=${result.httpStatusCode}, jobs=${result.identity?.jobIds?.size ?: 0}")
        assertEquals(1, wire.submissions)
        assertTrue(result.status in setOf(ProviderDiscoveryOperationStatus.Queued, ProviderDiscoveryOperationStatus.AlreadyQueued,
            ProviderDiscoveryOperationStatus.AlreadyOwned, ProviderDiscoveryOperationStatus.Saved, ProviderDiscoveryOperationStatus.MissingSource))
    }
}

private const val Radiohead = "a74b1b7f-71a5-4011-9441-d0b5e4122711"
private fun candidateFile(): Path = Path.of(checkNotNull(System.getenv("NAVIAMP_AURRAL_CANDIDATE_FILE"))).toAbsolutePath().normalize()
private fun attemptFile(file: Path): Path = file.resolveSibling(file.fileName.toString() + ".attempted")
private fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull
private fun acceptanceConnection(): NavidromeConnection? = System.getenv("NAVIAMP_AURRAL_TEST_URL")?.let { url ->
    NavidromeConnection.fromPassword(providerId = ProviderIdAurral, baseUrl = url,
        username = checkNotNull(System.getenv("NAVIAMP_AURRAL_TEST_USER")), password = checkNotNull(System.getenv("NAVIAMP_AURRAL_TEST_PASSWORD")),
        tlsSettings = NavidromeTlsSettings(insecureSkipTlsVerification = System.getenv("NAVIAMP_AURRAL_TEST_INSECURE") == "true"))
}
private fun candidateJson(artist: ProviderDiscoveryArtist, release: ProviderDiscoveryRelease, track: ProviderDiscoveryReleaseTrack) = buildJsonObject {
    put("artistName", artist.name); put("artistMbid", artist.id)
    put("albumName", release.title); put("albumMbid", release.id)
    put("trackName", track.title); put("trackMbid", checkNotNull(track.recordingMbid).value)
    track.releaseMbid?.let { put("releaseMbid", it.value) }
    track.discNumber?.let { put("discNumber", it) }; track.trackNumber?.let { put("trackNumber", it) }
    track.durationMillis?.let { put("durationMs", it) }
}

/** GET and the released lookup POST are reads. A mutation needs exact reviewed IDs and an atomic attempt marker. */
private class AcceptanceWire(
    connection: NavidromeConnection,
    private val approved: JsonObject? = null,
    private val marker: Path? = null,
    private val transport: NavidromeHttpClient = createDefaultNavidromeHttpClient(connection.tlsSettings),
) : NavidromeHttpClient by transport {
    private val base = connection.normalizedBaseUrl
    var submissions = 0
        private set
    override suspend fun postJson(url: String, body: String, headers: Map<String, String>) = postJsonResponse(url, body, headers).body
    override suspend fun postJsonResponse(url: String, body: String, headers: Map<String, String>): NavidromeHttpResponse {
        if (url != "$base/api/auth/login" && url != "$base/api/library/albums/lookup/batch") {
            val expected = checkNotNull(approved) { "Read-only acceptance cannot acquire music" }
            require(url == "$base/api/library/downloads/track" && submissions == 0)
            val actual = Json.parseToJsonElement(body).jsonObject
            require(listOf("artistMbid", "albumMbid", "trackMbid", "releaseMbid").all { actual.text(it) == expected.text(it) })
            Files.createFile(checkNotNull(marker)) // Atomic, before sending: lost replies must never be retried automatically.
            submissions++
        }
        return transport.postJsonResponse(url, body, headers)
    }
    override suspend fun putJson(url: String, body: String, headers: Map<String, String>): String = error("Acceptance cannot change monitoring or settings")
    override suspend fun putJsonResponse(url: String, body: String, headers: Map<String, String>): NavidromeHttpResponse = error("Acceptance cannot change monitoring or settings")
    override suspend fun deleteJson(url: String, headers: Map<String, String>): String = error("Acceptance cannot delete data")
}
