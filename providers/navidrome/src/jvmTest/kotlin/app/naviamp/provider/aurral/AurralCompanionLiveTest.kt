package app.naviamp.provider.aurral

import app.naviamp.domain.*
import app.naviamp.domain.provider.*
import app.naviamp.domain.source.AurralCompanion
import app.naviamp.provider.navidrome.*
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import kotlin.test.*

/** Opt-in read-only acceptance. Session login is the only POST; no monitoring or acquisition. */
class AurralCompanionLiveTest {
    @Test fun realDiscoveryAccountRemainsSeparateFromThePrimaryLibraryAndPlayback() = runBlocking {
        val endpoint = System.getenv("NAVIAMP_COMPANION_LIVE_URL")
        assumeTrue("Dedicated companion acceptance environment is not configured", endpoint != null)
        val settings = AurralCompanion(true, endpoint!!,
            checkNotNull(System.getenv("NAVIAMP_COMPANION_LIVE_USER")),
            checkNotNull(System.getenv("NAVIAMP_COMPANION_LIVE_PASSWORD")))
        val primary = NavidromeProvider(NavidromeConnection(baseUrl = "https://primary.invalid", username = "primary",
            token = "synthetic-token", salt = "synthetic-salt"), object : NavidromeHttpClient {
            override suspend fun get(url: String): String = error("Discovery must not read the primary library")
        })
        val provider = companionProvider(primary, settings)
        val discovery = assertNotNull(provider.discovery)
        val feed = discovery.load()
        assertTrue(feed.status in setOf(ProviderDiscoveryStatus.Ready, ProviderDiscoveryStatus.Building), "Companion feed: ${feed.status}")
        val query = (feed.recommendations + feed.trending).firstOrNull()?.name ?: "Massive Attack"
        val results = discovery.searchCatalog(query, ProviderCatalogScope.Artist)
        assertEquals(ProviderDiscoveryStatus.Ready, results.status)
        assertTrue(results.artists.isNotEmpty(), "Acceptance search must exercise a real catalog result")
        val artist = results.artists.first { it.artistMbid != null }
        val details = discovery.artistDetails(artist)
        assertEquals(ProviderDiscoveryStatus.Ready, details.status)
        assertNull(details.details?.libraryArtist, "Companion IDs cannot become primary Library IDs")
        val request = StreamRequest(TrackId("primary-track"), StreamQuality.Original)
        assertEquals(primary.streamUrl(request), provider.streamUrl(request))
        assertEquals(primary.cacheNamespace, provider.cacheNamespace)
        assertEquals(ProviderId(ProviderIdAurral), provider.effectiveDiscoveryProviderId)
        val needsPassword = companionProvider(primary, settings.copy(password = ""))
        assertEquals(ProviderDiscoveryStatus.PasswordRequired, needsPassword.discovery!!.load().status)
        assertEquals(primary.streamUrl(request), needsPassword.streamUrl(request))
    }
}
