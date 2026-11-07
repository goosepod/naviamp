package app.naviamp.provider.aurral

import app.naviamp.domain.provider.*
import app.naviamp.provider.navidrome.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class AurralGenreBlockContractTest {
    @Test fun safeReadProbesSupportAndOlderServersNeverReceiveMutationProbes() = runTest {
        val http = GenreHttp().apply { supported = false }
        val client = genreClient(http)
        assertEquals(ProviderDiscoveryStatus.Unsupported, client.blockedGenres().status)
        assertEquals(ProviderDiscoveryOperationStatus.Unsupported, client.setGenreBlocked("rock", true).status)
        assertEquals(0, http.mutations)
    }
    @Test fun blockAndUnblockUseServerTagsNormalizeComparisonsAndEscapeUnicode() = runTest {
        val http = GenreHttp(); val client = genreClient(http)
        assertEquals(ProviderDiscoveryOperationStatus.Rejected, client.setGenreBlocked(" ", true).status)
        assertEquals(ProviderDiscoveryOperationStatus.Rejected, client.setGenreBlocked("x".repeat(101), true).status)
        assertEquals(ProviderDiscoveryOperationStatus.Rejected, client.setGenreBlocked("unknown", true).status)
        assertEquals(0, http.mutations)
        assertEquals(listOf("rock"), client.setGenreBlocked(" ROCK ", true).tags)
        assertEquals(listOf("rock"), client.setGenreBlocked("rock", true).tags)
        assertEquals(1, http.mutations)
        http.tags = listOf("日本 ポップ & Rock")
        assertEquals(emptyList(), client.setGenreBlocked("日本 ポップ & Rock", false).tags)
        assertTrue(http.deleted!!.endsWith("tag=%E6%97%A5%E6%9C%AC+%E3%83%9D%E3%83%83%E3%83%97+%26+Rock"))
        assertEquals("postrock", discoveryGenreKey(" POST   ROCK "))
        http.tags = listOf("post-rock")
        val before = http.mutations
        assertEquals(listOf("post-rock"), client.setGenreBlocked("Post_Rock", true).tags)
        assertEquals(before, http.mutations)
    }
    @Test fun uncertainMutationIsNotRetriedAndAuthoritativeReadCanReconcileIt() = runTest {
        val http = GenreHttp().apply { loseResponse = true }; val client = genreClient(http)
        assertEquals(ProviderDiscoveryOperationStatus.Uncertain, client.setGenreBlocked("rock", true).status)
        assertEquals(1, http.mutations)
        assertEquals(listOf("rock"), client.blockedGenres().tags)
        assertEquals(1, http.mutations)
    }
    @Test fun permissionDenialLeavesReadStateIntact() = runTest {
        val http = GenreHttp().apply { denied = true }; val client = genreClient(http)
        assertEquals(ProviderDiscoveryOperationStatus.Forbidden, client.setGenreBlocked("rock", true).status)
        assertEquals(emptyList(), client.blockedGenres().tags)
    }
}
private fun genreClient(http: GenreHttp) = AurralDiscoveryClient(NavidromeConnection.fromPassword(
    providerId = ProviderIdAurral, baseUrl = "https://music.test/proxy", username = "listener", password = "synthetic"), http, nowMillis = { 0L })
private class GenreHttp : NavidromeHttpClient {
    var supported = true; var mutations = 0; var loseResponse = false; var denied = false
    var tags = emptyList<String>(); var deleted: String? = null
    private fun response() = kotlinx.serialization.json.buildJsonObject { put("tags", kotlinx.serialization.json.JsonArray(tags.map { kotlinx.serialization.json.JsonPrimitive(it) })) }.toString()
    override suspend fun get(url: String) = get(url, emptyMap())
    override suspend fun get(url: String, headers: Map<String, String>): String = when {
        url.endsWith("/bootstrap") -> """{"api":{"version":1,"features":["discover"]}}"""
        url.endsWith("/blocked-tags") -> { if (!supported) throw NavidromeHttpException(404); assertEquals("Bearer session", headers["Authorization"]); response() }
        "/discover?" in url -> """{"topGenres":["rock","post rock"]}"""
        else -> error("Unexpected fixture read")
    }
    override suspend fun postJson(url: String, body: String, headers: Map<String, String>): String = when {
        url.endsWith("/login") -> """{"token":"session","expiresAt":100000,"user":{"permissions":{}}}"""
        url.endsWith("/blocked-tags") -> { mutations++; if (denied) throw NavidromeHttpException(403); tags = listOf("rock"); if (loseResponse) error("Lost response"); response() }
        else -> error("Unexpected fixture write")
    }
    override suspend fun deleteJson(url: String, headers: Map<String, String>): String {
        mutations++; deleted = url; assertEquals("Bearer session", headers["Authorization"]); tags = emptyList(); return response()
    }
}
