package app.naviamp.provider.aurral

import app.naviamp.domain.provider.*
import app.naviamp.domain.source.ConnectionHeaderDefinition
import app.naviamp.provider.navidrome.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class AurralDiscoveryClientTest {
    @Test fun missingMusicBrainzIdsRemainDiscoveryCardsWithoutInventingPlaybackOrExternalIds() = runTest {
        val http = FakeAurralHttp().also { it.discovery = """{"recommendations":[{"id":null,"name":"Unmatched artist","image":"javascript:bad"}],"globalTop":[]}""" }
        val feed = AurralDiscoveryClient(connection(), http, nowMillis = { 0L }).load()
        assertEquals("Unmatched artist", feed.recommendations.single().name)
        assertNull(feed.recommendations.single().externalUrl)
        assertNull(feed.recommendations.single().imageUrl)
        assertTrue(feed.recommendations.single().id.startsWith("unresolved:"))
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
    var discovery = """{"recommendations":[{"id":"$ArtistMbid","name":"Discovery artist","image":"/api/image-proxy/portrait","tags":["rock"],"matchPercent":99}],"globalTop":[],"unknown":true}"""
    var failure: Throwable? = null
    val authenticatedGets = mutableListOf<Map<String, String>>()
    override suspend fun get(url: String) = get(url, emptyMap())
    override suspend fun get(url: String, headers: Map<String, String>): String {
        assertTrue(url.startsWith("https://music.test/aurral/api/"))
        if (url.endsWith("/health/bootstrap")) return bootstrap
        authenticatedGets += headers
        failure?.let { throw it }
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
        return """{"token":"session","expiresAt":${logins * 100_000},"user":{"permissions":{"accessFlow":false}}}"""
    }
}
