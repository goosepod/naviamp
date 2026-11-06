package app.naviamp.provider.navidrome

import app.naviamp.domain.provider.*
import app.naviamp.provider.aurral.AurralDiscoveryClient
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import kotlin.test.*

/** Explicit one-shot account mutation; ordinary live suites never enable this test. */
class AurralGenreAcceptanceTest {
    @Test fun temporarilyBlockOneApprovedGenreAndRestoreTheOriginalBlocklist() = runBlocking {
        val approved = System.getenv("NAVIAMP_AURRAL_APPROVED_GENRE") ?: return@runBlocking
        require(approved == "rock")
        val marker = Path.of(checkNotNull(System.getenv("NAVIAMP_AURRAL_GENRE_ATTEMPT_FILE")))
        require(!Files.exists(marker)) { "Approval already has an attempt; never replay an ambiguous mutation" }
        val connection = NavidromeConnection.fromPassword(providerId = ProviderIdAurral,
            baseUrl = checkNotNull(System.getenv("NAVIAMP_AURRAL_TEST_URL")),
            username = checkNotNull(System.getenv("NAVIAMP_AURRAL_TEST_USER")),
            password = checkNotNull(System.getenv("NAVIAMP_AURRAL_TEST_PASSWORD")),
            tlsSettings = NavidromeTlsSettings(insecureSkipTlsVerification = System.getenv("NAVIAMP_AURRAL_TEST_INSECURE") == "true"))
        val transport = createDefaultNavidromeHttpClient(connection.tlsSettings)
        val base = connection.normalizedBaseUrl
        var posts = 0; var deletes = 0
        val wire = object : NavidromeHttpClient by transport {
            override suspend fun postJson(url: String, body: String, headers: Map<String, String>): String {
                if (url != "$base/api/auth/login") {
                    require(url == "$base/api/discover/blocked-tags" && posts == 0)
                    require(Json.parseToJsonElement(body).jsonObject["tag"]?.jsonPrimitive?.content == approved)
                    Files.createFile(marker)
                    posts++
                }
                return transport.postJson(url, body, headers)
            }
            override suspend fun postJsonResponse(url: String, body: String, headers: Map<String, String>) =
                NavidromeHttpResponse(postJson(url, body, headers))
            override suspend fun deleteJson(url: String, headers: Map<String, String>): String {
                require(posts == 1 && deletes == 0 && url == "$base/api/discover/blocked-tags?tag=$approved")
                deletes++
                return transport.deleteJson(url, headers)
            }
            override suspend fun putJson(url: String, body: String, headers: Map<String, String>): String = error("Acceptance cannot change configuration")
        }
        val client = AurralDiscoveryClient(connection, wire)
        val before = client.blockedGenres()
        assertEquals(ProviderDiscoveryStatus.Ready, before.status)
        require(before.tags.none { discoveryGenreKey(it) == discoveryGenreKey(approved) }) { "The original blocklist changed; preserve it without mutating" }
        val recommendationBefore = client.recommendations(0, 100)
        assertEquals(ProviderDiscoveryStatus.Ready, recommendationBefore.status)
        assertTrue(recommendationBefore.genres.any { discoveryGenreKey(it) == discoveryGenreKey(approved) })
        try {
            val blocked = client.setGenreBlocked(approved, true)
            assertEquals(ProviderDiscoveryOperationStatus.Saved, blocked.status)
            assertTrue(blocked.tags.orEmpty().any { discoveryGenreKey(it) == discoveryGenreKey(approved) })
            val confirmed = client.blockedGenres()
            assertEquals(ProviderDiscoveryStatus.Ready, confirmed.status)
            assertTrue(confirmed.tags.any { discoveryGenreKey(it) == discoveryGenreKey(approved) })
            assertEquals(ProviderDiscoveryStatus.Ready, client.recommendations(0, 100).status)
        } finally {
            withContext(NonCancellable) {
                val state = client.blockedGenres()
                assertEquals(ProviderDiscoveryStatus.Ready, state.status)
                if (posts == 1 && state.tags.any { discoveryGenreKey(it) == discoveryGenreKey(approved) }) {
                    val restored = client.setGenreBlocked(approved, false)
                    assertEquals(ProviderDiscoveryOperationStatus.Saved, restored.status)
                }
                val after = client.blockedGenres()
                assertEquals(ProviderDiscoveryStatus.Ready, after.status)
                assertEquals(before.tags.toSet(), after.tags.toSet())
                assertEquals(ProviderDiscoveryStatus.Ready, client.recommendations(0, 100).status)
            }
        }
        assertEquals(1, posts); assertEquals(1, deletes)
        println("Approved genre acceptance: one block, one unblock, original blocklist restored; no acquisition or configuration mutation.")
    }
}
