package app.naviamp.provider.aurral

import app.naviamp.domain.provider.*
import app.naviamp.domain.source.ConnectionHeaderDefinition
import app.naviamp.provider.navidrome.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class AurralDiscoveryClientTest {
    @Test fun acquisitionAccountScopeSurvivesFolderSelectionButChangesWithTheUser() {
        val original = connection()
        val scope = NavidromeProvider(original, FakeAurralHttp()).discoveryAccountScope
        assertEquals(scope, NavidromeProvider(original.copy(selectedMusicFolderIds = listOf("2")), FakeAurralHttp()).discoveryAccountScope)
        assertNotEquals(scope, NavidromeProvider(original.copy(username = "other"), FakeAurralHttp()).discoveryAccountScope)
    }
    @Test fun acquisitionReadsUseQualifiedManagerRoutesAndNeverInferPlaybackAvailability() = runTest {
        val http = FakeAurralHttp().also { it.bootstrap = """{"api":{"version":1,"features":["discover","activity"]}}""" }
        val client = AurralDiscoveryClient(connection(), http, nowMillis = { 0L })
        val aurral = ProviderDiscoveryRequestIdentity(canonicalAlbumId = DiscoveryCanonicalAlbumId("71"),
            managedAlbumId = DiscoveryManagedAlbumId(DiscoveryLibraryManager.Aurral, "71"))
        http.status = """{"canonicalId":71,"managedBy":"aurral","status":"unreleased","releaseDate":"2027-03"}"""
        val waiting = client.acquisitionStatus(aurral)
        assertEquals(DiscoveryAcquisitionPhase.WaitingForRelease, waiting.phase)
        assertEquals("2027-03", waiting.waitingForRelease?.releaseDate)
        assertTrue(http.urls.last().endsWith("/library/albums/aurral/71/status"))
        http.status = """{"canonicalId":71,"managedBy":"aurral","status":"complete"}"""
        assertEquals(DiscoveryAcquisitionPhase.Complete, client.acquisitionStatus(aurral).phase)
        http.status = """{"71":{"status":"processing"}}"""
        val lidarr = aurral.copy(managedAlbumId = DiscoveryManagedAlbumId(DiscoveryLibraryManager.Lidarr, "71"))
        assertEquals(DiscoveryAcquisitionPhase.Processing, client.acquisitionStatus(lidarr).phase)
        assertTrue(http.urls.last().endsWith("/library/downloads/status?albumIds=71"))
        http.status = """{"71":{"status":"unreleased","releaseDate":"2027"}}"""
        val unrecognizedLidarr = client.acquisitionStatus(lidarr)
        assertEquals(DiscoveryAcquisitionPhase.Unknown, unrecognizedLidarr.phase)
        assertNull(unrecognizedLidarr.waitingForRelease)
        assertTrue(http.authenticatedGets.all { it["Authorization"] == "Bearer session" && it["X-Proxy"] == "value" })
        assertEquals(1, http.logins)
        assertEquals("https://music.test/aurral/requests", client.requestsUrl)
    }
    @Test fun acquisitionReadsRejectWrongOwnerAndUnknownStatusesStayUnknown() = runTest {
        val http = FakeAurralHttp().also { it.bootstrap = """{"api":{"version":1,"features":["discover","activity"]}}""" }
        val client = AurralDiscoveryClient(connection(), http, nowMillis = { 0L })
        val identity = ProviderDiscoveryRequestIdentity(canonicalAlbumId = DiscoveryCanonicalAlbumId("71"),
            managedAlbumId = DiscoveryManagedAlbumId(DiscoveryLibraryManager.Aurral, "71"))
        for (body in listOf("""{"canonicalId":72,"managedBy":"aurral","status":"complete"}""",
            """{"canonicalId":71,"managedBy":"lidarr","status":"complete"}""")) {
            http.status = body
            assertEquals(ProviderDiscoveryStatus.Unavailable, client.acquisitionStatus(identity).status)
        }
        http.status = """{"canonicalId":71,"managedBy":"aurral","status":"future"}"""
        assertEquals(DiscoveryAcquisitionPhase.Unknown, client.acquisitionStatus(identity).phase)
        for ((code, expected) in listOf(403 to ProviderDiscoveryStatus.Forbidden, 404 to ProviderDiscoveryStatus.Unsupported,
            401 to ProviderDiscoveryStatus.SignInRequired)) {
            http.failure = NavidromeHttpException(code)
            assertEquals(expected, client.acquisitionStatus(identity).status)
        }
        assertEquals(1, http.logins)
    }
    @Test fun activityKeepsLidarrNamespacesAndDoesNotTreatDownloadSourcesAsManagers() = runTest {
        val http = FakeAurralHttp().also { it.bootstrap = """{"api":{"version":1,"features":["discover","activity"]}}"""
            it.activity = """[{"id":"one","source":"lidarr","albumId":71,"title":"Album","artistName":"Artist","status":"processing"},
                {"id":"two","source":"deezer","albumId":71,"status":"completed","kind":"album_requested","requestGroupId":"group"}]""" }
        val rows = AurralDiscoveryClient(connection(), http, nowMillis = { 0L }).requestActivity().items
        assertEquals(DiscoveryManagedAlbumId(DiscoveryLibraryManager.Lidarr, "71"), rows[0].identity?.managedAlbumId)
        assertEquals("Album", rows[0].title)
        assertNull(rows[1].identity?.managedAlbumId)
        assertEquals(DiscoveryRequestGroupId("group"), rows[1].identity?.requestGroupId)
    }

    @Test fun missingMusicBrainzIdsRemainDiscoveryCardsWithoutInventingPlaybackOrExternalIds() = runTest {
        val http = FakeAurralHttp().also { it.discovery = """{"recommendations":[{"id":null,"name":"Unmatched artist","image":"javascript:bad"}],"globalTop":[]}""" }
        val feed = AurralDiscoveryClient(connection(), http, nowMillis = { 0L }).load()
        assertEquals("Unmatched artist", feed.recommendations.single().name)
        assertNull(feed.recommendations.single().externalUrl)
        assertNull(feed.recommendations.single().imageUrl)
        assertTrue(feed.recommendations.single().id.startsWith("unresolved:"))
    }
    @Test fun absentFeedImagesResolveLazilyThroughTheProviderWithoutLeakingCredentials() = runTest {
        val http = FakeAurralHttp().also {
            it.expiry = Long.MAX_VALUE
            it.discovery = """{"recommendations":[{"id":"$ArtistMbid","name":"Discovery artist","image":null}],"globalTop":[]}"""
        }
        val provider = NavidromeProvider(connection(), http)
        val artist = provider.discovery!!.load().recommendations.single()
        val url = assertNotNull(artist.imageUrl)
        assertTrue(url.endsWith("/api/artists/$ArtistMbid/cover?artistName=Discovery+artist"))
        assertEquals(0, http.coverRequests)
        assertContentEquals(byteArrayOf(1, 2, 3), provider.bytesForOwnedUrl(url))
        assertEquals(1, http.coverRequests)
        assertEquals("https://images.test/front.jpg", http.binaryUrl)
        assertEquals(emptyMap(), http.binaryHeaders)
        http.cover = """{"images":[]}"""
        assertNull(provider.bytesForOwnedUrl(url))
        http.cover = """{"images":[{"image":"javascript:bad","front":true}]}"""
        assertNull(provider.bytesForOwnedUrl(url))
        http.cover = """{"images":[{"image":"/api/image-proxy/local.jpg","front":true}]}"""
        assertContentEquals(byteArrayOf(1, 2, 3), provider.bytesForOwnedUrl(url))
        assertEquals("Bearer session", http.binaryHeaders["Authorization"])
        assertEquals("value", http.binaryHeaders["X-Proxy"])
        assertNull(provider.bytesForOwnedUrl("https://other.test/api/artists/$ArtistMbid/cover?artistName=Artist"))
    }

    @Test fun alternateFeedImageFieldIsAcceptedWithoutCoverLookup() = runTest {
        val http = FakeAurralHttp().also {
            it.discovery = """{"recommendations":[{"id":"$ArtistMbid","name":"Artist","imageUrl":"https://images.test/direct.jpg"}],"globalTop":[]}"""
        }
        assertEquals("https://images.test/direct.jpg", AurralDiscoveryClient(connection(), http, nowMillis = { 0L })
            .load().recommendations.single().imageUrl)
        assertEquals(0, http.coverRequests)
    }

    private fun connection() = NavidromeConnection.fromPassword(
        providerId = ProviderIdAurral, baseUrl = "https://music.test/aurral/", username = "listener", password = "secret",
        customHeaders = listOf(ConnectionHeaderDefinition("X-Proxy", "value")),
    )

    @Test fun nativeDiscoveryKeepsIdentityNamespacesAndUsesUserSessionOnSubpath() = runTest {
        val http = FakeAurralHttp()
        val client = AurralDiscoveryClient(connection(), http, nowMillis = { 100L })
        val feed = client.load()
        assertEquals(ProviderDiscoveryStatus.Building, feed.status)
        assertEquals("Discovery artist", feed.recommendations.single().name)
        assertEquals("https://music.test/aurral/artist/$ArtistMbid", feed.recommendations.single().externalUrl)
        assertEquals("https://music.test/aurral/api/image-proxy/portrait", feed.recommendations.single().imageUrl)
        assertEquals(listOf("rock"), feed.recommendations.single().genres)
        assertEquals(99, feed.recommendations.single().matchPercent)
        assertEquals(1, http.logins)
        client.load()
        assertEquals(1, http.logins)
        assertTrue(http.authenticatedGets.all { it["Authorization"] == "Bearer session" })
        assertTrue(http.authenticatedGets.all { it["X-Proxy"] == "value" })
    }

    @Test fun expiryAndUnauthorizedDoNotCauseAutomaticLoginLoops() = runTest {
        val http = FakeAurralHttp()
        var now = 0L
        val client = AurralDiscoveryClient(connection(), http, nowMillis = { now })
        client.load()
        now = 100_000L
        client.load()
        assertEquals(2, http.logins)
        http.failure = NavidromeHttpException(401)
        assertEquals(ProviderDiscoveryStatus.SignInRequired, client.load().status)
        assertEquals(2, http.logins)
        http.failure = null
        client.load()
        assertEquals(3, http.logins)
    }

    @Test fun olderOrFutureContractsAndMissingFeatureNeverSignIn() = runTest {
        for (bootstrap in listOf("{}", """{"api":{"version":2,"features":["discover"]}}""",
            """{"api":{"version":1,"features":["flows"]}}""")) {
            val http = FakeAurralHttp().also { it.bootstrap = bootstrap }
            assertEquals(ProviderDiscoveryStatus.Unsupported, AurralDiscoveryClient(connection(), http).load().status)
            assertEquals(0, http.logins)
        }
    }

    @Test fun tokenOnlyConnectionAsksForPasswordWithoutUsingTheInstallationApiKey() = runTest {
        val http = FakeAurralHttp()
        val client = AurralDiscoveryClient(connection().copy(password = null), http)
        assertEquals(ProviderDiscoveryStatus.PasswordRequired, client.load().status)
        assertEquals(0, http.logins)
    }

    @Test fun permissionServerAndMalformedFailuresAreTypedAndCancellationPropagates() = runTest {
        for ((code, expected) in listOf(403 to ProviderDiscoveryStatus.Forbidden, 404 to ProviderDiscoveryStatus.Unsupported,
            500 to ProviderDiscoveryStatus.Unavailable)) {
            val http = FakeAurralHttp().also { it.failure = NavidromeHttpException(code) }
            assertEquals(expected, AurralDiscoveryClient(connection(), http, nowMillis = { 0L }).load().status)
        }
        val malformed = FakeAurralHttp().also { it.bootstrap = "not JSON" }
        assertEquals(ProviderDiscoveryStatus.Unavailable, AurralDiscoveryClient(connection(), malformed).load().status)
        val cancelled = FakeAurralHttp().also { it.failure = CancellationException() }
        assertFailsWith<CancellationException> { AurralDiscoveryClient(connection(), cancelled, nowMillis = { 0L }).load() }
    }

    @Test fun aurralProfileUsesStandardPlaybackAndDoesNotClaimNavidromeOrDownloadTranscoding() {
        val provider = NavidromeProvider(connection(), FakeAurralHttp())
        assertEquals(ProviderIdAurral, provider.id.value)
        assertNotNull(provider.discovery)
        assertNull(NavidromeProvider(connection().copy(providerId = ProviderIdSubsonic), FakeAurralHttp()).discovery)
        assertTrue(provider.capabilities.supportsStreamingTranscode)
        assertFalse(provider.capabilities.supportsDownloadTranscode)
        assertFalse(provider.capabilities.supportsSmartPlaylists)
        assertFalse(provider.capabilities.supportsTrackRatings)
        val profile = subsonicProviderProfile(ProviderIdAurral)
        assertFalse(profile.nativeAuthentication)
        assertFalse(profile.canonicalIdMigration)
        assertTrue(profile.favorites)
        assertTrue(profile.playReporting)
    }
}

private const val ArtistMbid = "6925db17-f35e-42f3-a4eb-84ee6bf5d4b0"
private class FakeAurralHttp : NavidromeHttpClient {
    var bootstrap = """{"api":{"version":1,"features":["discover","future"]}}"""
    var logins = 0
    var expiry: Long? = null
    var discovery = """{"recommendations":[{"id":"$ArtistMbid","name":"Discovery artist","image":"/api/image-proxy/portrait","tags":["rock"],"matchPercent":99}],"globalTop":[],"unknown":true}"""
    var coverRequests = 0
    var cover = """{"images":[{"image":"https://images.test/back.jpg"},{"image":"https://images.test/front.jpg","front":true}]}"""
    var binaryUrl: String? = null
    var binaryHeaders = emptyMap<String, String>()
    override suspend fun getBytes(url: String, headers: Map<String, String>): ByteArray {
        binaryUrl = url
        binaryHeaders = headers
        return byteArrayOf(1, 2, 3)
    }
    var status = "{}"
    var activity = "[]"
    val urls = mutableListOf<String>()
    var failure: Throwable? = null
    val authenticatedGets = mutableListOf<Map<String, String>>()
    override suspend fun get(url: String) = get(url, emptyMap())
    override suspend fun get(url: String, headers: Map<String, String>): String {
        assertTrue(url.startsWith("https://music.test/aurral/api/"))
        if (url.endsWith("/health/bootstrap")) return bootstrap
        authenticatedGets += headers
        urls += url
        failure?.let { throw it }
        if (url.endsWith("/requests")) return activity
        if (url.contains("/library/albums/aurral/") || url.contains("/library/downloads/status?")) return status
        if (url.contains("/cover?artistName=")) { coverRequests++; return cover }
        return if (url.endsWith("/discover/status")) """{"isUpdating":true,"error":null,"lastUpdated":"yesterday"}"""
        else discovery
    }
    override suspend fun postJson(url: String, body: String, headers: Map<String, String>): String {
        assertEquals("https://music.test/aurral/api/auth/login", url)
        assertEquals("listener", Json.parseToJsonElement(body).jsonObject["username"]?.jsonPrimitive?.content)
        assertEquals("secret", Json.parseToJsonElement(body).jsonObject["password"]?.jsonPrimitive?.content)
        assertEquals("value", headers["X-Proxy"])
        assertFalse(headers.containsKey("X-Api-Key"))
        logins++
        return """{"token":"session","expiresAt":${expiry ?: logins * 100_000},"user":{"permissions":{"accessFlow":false}}}"""
    }
}
