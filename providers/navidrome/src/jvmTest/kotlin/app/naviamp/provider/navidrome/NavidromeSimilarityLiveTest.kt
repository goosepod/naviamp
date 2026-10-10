package app.naviamp.provider.navidrome

import app.naviamp.domain.radio.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Opt-in test against a disposable server. Credentials stay in environment variables. */
class NavidromeSimilarityLiveTest {
    @Test
    fun probeUsesRealServerAndRadioRecoversFromUnavailableSonicBackend() = runBlocking {
        val url = System.getenv("NAVIAMP_SIMILARITY_TEST_URL") ?: return@runBlocking
        val user = checkNotNull(System.getenv("NAVIAMP_SIMILARITY_TEST_USER"))
        val password = checkNotNull(System.getenv("NAVIAMP_SIMILARITY_TEST_PASSWORD"))
        val provider = NavidromeProvider(NavidromeConnection.fromPassword(baseUrl = url, username = user, password = password))
        provider.validateConnection()
        val seed = provider.tracks(limit = 1).first()
        val report = testSimilarity(provider, seed)
        val expected = System.getenv("NAVIAMP_SIMILARITY_TEST_SUPPORT") ?: "Missing"
        assertEquals(SimilaritySupport.valueOf(expected), report.support)
        assertTrue(report.sonic.kind != SimilarityResultKind.Matches)
        println("Similarity live check: support=${report.support}, sonic=${report.sonic}, regular=${report.regular}")
        val reasons = mutableListOf<SonicRadioFallbackReason>()
        val tracks = RadioService(provider, onSonicFallback = reasons::add).trackRadio(seed, preferSonicSimilarity = true)
        assertTrue(tracks.isNotEmpty(), "Regular Radio must still return tracks from the test library")
        if (report.support == SimilaritySupport.Advertised) assertTrue(reasons.isNotEmpty())
        assertTrue(tracks.none { it.id == seed.id })
        println("Similarity live check: Radio added ${tracks.size} tracks, fallback=$reasons")
    }
}
