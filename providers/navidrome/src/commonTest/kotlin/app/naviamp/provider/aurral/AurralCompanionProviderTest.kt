package app.naviamp.provider.aurral

import app.naviamp.domain.*
import app.naviamp.domain.provider.*
import app.naviamp.domain.source.*
import app.naviamp.provider.navidrome.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class AurralCompanionProviderTest {
    @Test fun invalidImportedCompanionDetailsNeverProbeAnUnintendedEndpoint() = runTest {
        val primary = NavidromeProvider(NavidromeConnection(baseUrl = "https://primary.example", username = "primary", token = "token", salt = "salt"))
        var createdClients = 0
        val provider = companionProvider(primary, AurralCompanion(enabled = true), createHttp = {
            createdClients++; error("Invalid details must not reach HTTP client creation")
        })
        assertEquals(ProviderDiscoveryStatus.Unavailable, provider.discovery!!.load().status)
        assertEquals(0, createdClients)
        assertEquals(primary.cacheNamespace, provider.cacheNamespace)
    }

    @Test fun primaryLibraryFolderFiltersCannotCreateAnotherCompanionRequestAccount() {
        val http = object : NavidromeHttpClient { override suspend fun get(url: String): String = error("No network expected") }
        val connection = NavidromeConnection(baseUrl = "https://primary.example", username = "primary", token = "token", salt = "salt")
        val first = NavidromeProvider(connection.copy(selectedMusicFolderIds = listOf("one")), http)
        val second = NavidromeProvider(connection.copy(selectedMusicFolderIds = listOf("two")), http)
        val settings = AurralCompanion(true, "https://aurral.example", "secondary", "secret")
        assertNotEquals(first.cacheNamespace, second.cacheNamespace)
        assertEquals(companionProvider(first, settings, http).discoveryAccountScope,
            companionProvider(second, settings, http).discoveryAccountScope)
    }

    @Test fun companionClientInitializationFailureIsAnUnavailableHubAndCannotDisableThePrimary() = runTest {
        val primary = NavidromeProvider(NavidromeConnection(baseUrl = "https://primary.example", username = "primary", token = "token", salt = "salt"))
        val companion = companionProvider(primary, AurralCompanion(true, "https://aurral.example", "secondary"),
            createHttp = { error("Companion certificate could not be loaded") })
        assertEquals(ProviderDiscoveryStatus.Unavailable, companion.discovery!!.load().status)
        assertEquals(ProviderId(ProviderIdAurral), companion.effectiveDiscoveryProviderId)
        val request = StreamRequest(TrackId("primary-track"), StreamQuality.Original)
        assertEquals(primary.streamUrl(request), companion.streamUrl(request))
    }

    @Test fun companionFailureAndTlsPolicyCannotChangePrimaryPlaybackOrLibraryIdentity() = runTest {
        val primary = NavidromeProvider(NavidromeConnection(baseUrl = "https://primary.example", username = "primary",
            token = "primary-token", salt = "primary-salt"), object : NavidromeHttpClient {
                override suspend fun get(url: String): String = error("Primary library must not be used for companion discovery")
            })
        val reads = mutableListOf<String>()
        val companion = companionProvider(primary, AurralCompanion(true, "https://aurral.example", "secondary", "secret",
            ConnectionTlsSettings(insecureSkipTlsVerification = true)), object : NavidromeHttpClient {
                override suspend fun get(url: String): String { reads += url; throw NavidromeHttpException(503) }
            })
        assertEquals(primary.id, companion.id)
        assertEquals(primary.cacheNamespace, companion.cacheNamespace)
        assertEquals(primary.capabilities, companion.capabilities)
        assertEquals(primary.requiresAuthenticatedPlaybackTransport, companion.requiresAuthenticatedPlaybackTransport)
        assertTrue(companion.discoveryPreviewProvider!!.requiresAuthenticatedPlaybackTransport)
        assertEquals(ProviderDiscoveryStatus.Unavailable, companion.discovery!!.load().status)
        assertTrue(reads.all { it.startsWith("https://aurral.example/") })
        val request = StreamRequest(TrackId("primary-track"), StreamQuality.Original)
        assertEquals(primary.streamUrl(request), companion.streamUrl(request))
        assertFalse(companion.streamUrl(request).contains("aurral.example"))
        assertNotEquals(primary.discoveryAccountScope, companion.discoveryAccountScope)
        assertEquals("https://aurral.example/api/deezer/preview/123.mp3",
            companion.discoveryPreviewProvider!!.streamUrl(StreamRequest(TrackId(DiscoveryPreviewTrackPrefix + "123"), StreamQuality.Original)))
    }
}
