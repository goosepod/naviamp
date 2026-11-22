package app.naviamp.provider.aurral

import app.naviamp.domain.provider.*
import app.naviamp.domain.media.AlbumReleaseSection
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.StreamQuality
import app.naviamp.provider.navidrome.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class AurralNativeActionsTest {
    private val artist = ProviderDiscoveryArtist(ArtistId, "Artist", detailsAvailable = true)
    private fun connection() = NavidromeConnection.fromPassword(providerId = ProviderIdAurral,
        baseUrl = "https://music.test/aurral", username = "listener", password = "secret")

    @Test fun releasesKeepPrimaryAndSecondaryTypesAndLoadSquareCoverBytesLazily() = runTest {
        val http = ActionHttp()
        val provider = NavidromeProvider(connection(), http)
        val detail = provider.discovery!!.artistDetails(artist).details!!
        val release = detail.releases.single()
        assertEquals(AlbumReleaseSection.Live, release.section)
        assertEquals(listOf("Live"), release.secondaryTypes)
        assertTrue(detail.canRequestTracks && detail.canRequestAlbums && detail.canChangeMonitoring)
        assertEquals(false, detail.monitored)
        assertEquals(0, http.coverCalls)
        assertContentEquals(byteArrayOf(1, 2), provider.bytesForOwnedUrl(release.imageUrl!!))
        assertEquals(1, http.coverCalls)
        assertTrue(http.binaryHeaders.isEmpty())
    }

    @Test fun aurralThreeSessionCoverRoutesLoadThroughTheCoverListWithoutLeakingTheSession() = runTest {
        val http = ActionHttp().also { it.releaseCover = "\"/api/artists/release-group/$ReleaseId/cover/image\"" }
        val provider = NavidromeProvider(connection(), http)
        val release = provider.discovery!!.artistDetails(artist).details!!.releases.single()
        assertEquals("https://music.test/aurral/api/artists/release-group/$ReleaseId/cover?artistName=Artist&albumTitle=Live+release", release.imageUrl)
        assertContentEquals(byteArrayOf(1, 2), provider.bytesForOwnedUrl(release.imageUrl!!))
        assertEquals(1, http.coverCalls)
        assertTrue(http.binaryHeaders.isEmpty(), "The external image host must never receive the Aurral session")
        http.releaseCover = "\"https://images.test/direct.jpg\""
        assertEquals("https://images.test/direct.jpg",
            provider.discovery!!.artistDetails(artist).details!!.releases.single().imageUrl)
    }

    @Test fun samplesOnlyKeepAvailableNumericIdentitiesAndUseStableMediaWithoutReportingOrDownloads() = runTest {
        val http = ActionHttp()
        val provider = NavidromeProvider(connection(), http)
        val samples = provider.discovery!!.samples(artist)
        assertEquals(ProviderDiscoveryStatus.Ready, samples.status)
        assertEquals(listOf("42"), samples.tracks.map { it.id })
        assertEquals("Album", samples.tracks.single().albumTitle)
        val track = samples.tracks.single().playbackTrack(artist.name)
        assertTrue(track.id.isDiscoveryPreview())
        assertNull(track.albumId)
        assertNull(track.artistId)
        assertNull(track.durationSeconds)
        assertEquals("https://music.test/aurral/api/deezer/preview/42.mp3", provider.streamUrl(StreamRequest(track.id, StreamQuality.Original)))
        val before = http.calls.size
        provider.reportNowPlaying(track.id)
        provider.reportLegacyNowPlaying(track.id)
        provider.submitListen(track.id, 1)
        provider.reportPlaybackState(track.id, PlaybackReportState.Playing, 15.0)
        assertNull(provider.lyrics(track.id))
        assertEquals(before, http.calls.size)
        assertFailsWith<IllegalArgumentException> { provider.downloadUrl(StreamRequest(track.id, StreamQuality.Original)) }
    }

    @Test fun requestsDistinguishQueuedOwnedSavedAndMissingSourcesWithoutAutomaticRetries() = runTest {
        val http = ActionHttp()
        val client = AurralDiscoveryClient(connection(), http)
        val sample = client.samples(artist).tracks.single()
        for ((response, status) in listOf(
            """{"success":true,"queued":true}""" to ProviderDiscoveryOperationStatus.Queued,
            """{"success":true,"queued":false,"status":"searching"}""" to ProviderDiscoveryOperationStatus.Queued,
            """{"success":true,"queued":false,"status":"available"}""" to ProviderDiscoveryOperationStatus.AlreadyOwned,
            """{"success":true,"alreadyQueued":true}""" to ProviderDiscoveryOperationStatus.AlreadyQueued,
            """{"success":true,"alreadyOwned":true}""" to ProviderDiscoveryOperationStatus.AlreadyOwned,
            """{"success":true,"queued":false}""" to ProviderDiscoveryOperationStatus.Saved,
            """{"success":true,"status":"download_source_missing"}""" to ProviderDiscoveryOperationStatus.MissingSource,
        )) {
            http.result = response
            assertEquals(status, client.requestTrack(artist, sample).status)
        }
        http.result = """{"success":true,"queued":true}"""
        assertEquals(ProviderDiscoveryOperationStatus.Queued,
            client.requestAlbum(artist, ProviderDiscoveryRelease(ReleaseId, "Live release")).status)
        assertEquals(ReleaseId, http.lastBody!!["albumMbid"]?.jsonPrimitive?.content)
        assertEquals(ArtistId, http.lastBody!!["artistMbid"]?.jsonPrimitive?.content)
        assertEquals(setOf("artistName", "artistMbid", "albumName", "albumMbid"), http.lastBody!!.keys) // Aurral 3 removed triggerSearch.
        http.failWrites = 500
        val before = http.writes
        assertEquals(ProviderDiscoveryOperationStatus.Uncertain, client.requestTrack(artist, sample).status)
        assertEquals(before + 1, http.writes)
        http.failWrites = 403
        assertEquals(ProviderDiscoveryOperationStatus.Forbidden, client.requestTrack(artist, sample).status)
    }

    @Test fun monitoringUsesServerDefaultsAndVerifiesTheSavedState() = runTest {
        val http = ActionHttp()
        val client = AurralDiscoveryClient(connection(), http)
        assertEquals(ProviderDiscoveryOperationStatus.MonitoringEnabled, client.setMonitoring(artist, true).status)
        val enable = http.lastBody!!["monitoring"]!!.jsonObject
        assertEquals(true, enable["monitored"]!!.jsonPrimitive.boolean)
        assertEquals(true, enable["useDefault"]!!.jsonPrimitive.boolean)
        assertNull(http.lastBody!!["monitorOption"])
        assertEquals(ProviderDiscoveryOperationStatus.MonitoringDisabled, client.setMonitoring(artist, false).status)
        assertEquals(mapOf("monitored" to JsonPrimitive(false)), http.lastBody!!["monitoring"]!!.jsonObject.toMap())
        http.ignoreWrites = true
        assertEquals(ProviderDiscoveryOperationStatus.Uncertain, client.setMonitoring(artist, true).status)
    }

    @Test fun missingPermissionsNeverSendAcquisitionWrites() = runTest {
        val http = ActionHttp().also { it.permitted = false }
        val client = AurralDiscoveryClient(connection(), http)
        val detail = client.artistDetails(artist).details!!
        assertFalse(detail.canRequestAlbums || detail.canRequestTracks || detail.canChangeMonitoring)
        assertEquals(ProviderDiscoveryOperationStatus.Forbidden, client.setMonitoring(artist, true).status)
        assertEquals(ProviderDiscoveryOperationStatus.Forbidden, client.requestTrack(artist, ProviderDiscoverySample("42", "Track")).status)
        assertEquals(0, http.writes)
    }
}
private const val ArtistId = "a74b1b7f-71a5-4011-9441-d0b5e4122711"
private const val ReleaseId = "b1392450-e666-3926-a536-22c65f834433"
private class ActionHttp : NavidromeHttpClient {
    var permitted = true
    var ignoreWrites = false
    var releaseCover = "null"
    var monitored = false
    var result = """{"success":true,"queued":true}"""
    var failWrites: Int? = null
    var writes = 0
    var coverCalls = 0
    var lastBody: JsonObject? = null
    var binaryHeaders = emptyMap<String, String>()
    val calls = mutableListOf<String>()
    override suspend fun get(url: String) = get(url, emptyMap())
    override suspend fun get(url: String, headers: Map<String, String>): String {
        calls += url
        if (url.endsWith("/health/bootstrap")) return """{"appVersion":"3.0.1","api":{"version":2,"features":["libraryStatusFilter"]}}"""
        assertEquals("Bearer session", headers["Authorization"])
        return when {
            "/cover?" in url -> { coverCalls++; """{"images":[{"front":true,"image":"https://images.test/cover.jpg"}]}""" }
            "/preview?" in url -> """{"tracks":[{"id":42,"title":"Track","preview_url":"/api/deezer/preview/42.mp3","album":"Album","artworkUrl":"https://images.test/sample.jpg"},{"id":43,"title":"Unavailable","preview_url":null},{"id":"unsafe/id","title":"Invalid","preview_url":"https://unsafe.test"}]}"""
            url.endsWith("/monitoring") -> """{"added":true,"monitored":$monitored,"useDefault":true,"rules":null,"defaultRules":{"catalog":"all"}}"""
            "/api/artists/" in url -> """{"id":"$ArtistId","name":"Artist","release-groups":[{"id":"$ReleaseId","title":"Live release","primary-type":"Album","secondary-types":["Live"],"coverUrl":$releaseCover}]}"""
            "/lookup/" in url -> """{"exists":false}"""
            url.endsWith("/feedback") -> """{"feedback":[]}"""
            else -> error("Unexpected GET")
        }
    }
    override suspend fun postJson(url: String, body: String, headers: Map<String, String>): String {
        if (url.endsWith("/auth/login")) return """{"token":"session","expiresAt":9223372036854775807,"user":{"permissions":{"addArtist":$permitted,"addAlbum":$permitted,"changeMonitoring":$permitted}}}"""
        assertEquals("Bearer session", headers["Authorization"])
        writes++; failWrites?.let { throw NavidromeHttpException(it) }
        lastBody = Json.parseToJsonElement(body).jsonObject
        return result
    }
    override suspend fun putJson(url: String, body: String, headers: Map<String, String>): String {
        assertEquals("Bearer session", headers["Authorization"])
        writes++; lastBody = Json.parseToJsonElement(body).jsonObject
        if (!ignoreWrites) monitored = lastBody!!["monitoring"]!!.jsonObject["monitored"]!!.jsonPrimitive.boolean
        return """{"artist":{"monitored":$monitored}}"""
    }
    override suspend fun getBytes(url: String, headers: Map<String, String>): ByteArray {
        binaryHeaders = headers
        return byteArrayOf(1, 2)
    }
}
