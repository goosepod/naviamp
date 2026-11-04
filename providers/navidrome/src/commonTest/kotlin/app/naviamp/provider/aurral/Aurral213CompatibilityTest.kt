package app.naviamp.provider.aurral

import app.naviamp.domain.provider.*
import app.naviamp.provider.navidrome.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

/** Synthetic excerpts of 3f01fa586f363f416f2e40c234a95550b87d74f7; not live responses.
 * See docs/verification/502-aurral-213-compatibility.md for source and test boundaries. */
class Aurral213CompatibilityTest {
    @Test fun released213FeaturesDoNotReplaceApiVersionOrRequireAdministratorAccess() = runTest {
        val http = CompatibilityHttp()
        val result = client(http).recommendations(0, 2)
        assertEquals(ProviderDiscoveryStatus.Ready, result.status)
        assertEquals(1, http.logins)
        assertEquals(listOf("login"), http.writes)
        assertEquals(listOf("rock"), result.genres)
        assertEquals(listOf("Seed"), result.seeds.map { it.name })
        val context = result.artists.first().recommendation!!
        assertEquals(1, context.depth)
        assertEquals("2026-11-03T10:00:00.000Z", context.firstDiscoveredAt)
        assertEquals("charts", context.sourceType)
        assertEquals(listOf("rock"), context.matchedTags)
        assertEquals(listOf("Seed"), context.supportingSeeds)
        assertEquals(2, result.nextOffset) // Raw rows advance even when a row cannot be rendered.
        assertEquals(1, result.artists.size)
    }

    @Test fun olderServerMissingMetadataStaysUnknownAndDoesNotRequireNewCapabilities() = runTest {
        val http = CompatibilityHttp().apply {
            appVersion = "2.12.0"
            features = listOf("discover")
            recommendations = """{"recommendations":[{"id":"$Artist","name":"Artist"}],"recommendationCount":1}"""
        }
        val result = client(http).recommendations(0, 2)
        assertEquals(ProviderDiscoveryStatus.Ready, result.status)
        assertNull(result.artists.single().recommendation?.depth)
        assertNull(result.artists.single().recommendation?.firstDiscoveredAt)
        assertNull(result.nextOffset)
    }

    @Test fun unknownApiOrAbsentDiscoveryStopsBeforeLoginRegardlessOfAppVersion() = runTest {
        for (http in listOf(CompatibilityHttp().apply { apiVersion = 2 },
            CompatibilityHttp().apply { features = listOf("trending") })) {
            assertEquals(ProviderDiscoveryStatus.Unsupported, client(http).recommendations(0, 2).status)
            assertEquals(0, http.logins)
            assertTrue(http.writes.isEmpty())
        }
    }

    @Test fun renewalUsesUserSessionAnd401InvalidatesWithoutReplayingTheRead() = runTest {
        var now = 0L
        val http = CompatibilityHttp()
        val client = client(http) { now }
        assertEquals(ProviderDiscoveryStatus.Ready, client.recommendations(0, 2).status)
        now = 70_001 // Renew before the 100000 ms expiry, using the existing 30 s margin.
        assertEquals(ProviderDiscoveryStatus.Ready, client.recommendations(0, 2).status)
        assertEquals(2, http.logins)
        http.readFailure = 401
        val before = http.reads
        assertEquals(ProviderDiscoveryStatus.SignInRequired, client.recommendations(0, 2).status)
        assertEquals(before + 1, http.reads)
        assertEquals(2, http.logins)
        http.readFailure = null
        assertEquals(ProviderDiscoveryStatus.Ready, client.recommendations(0, 2).status)
        assertEquals(3, http.logins)
    }

    @Test fun validationDenialsAndUnknownActivityDoNotInventOwnershipOrReplayWrites() = runTest {
        val http = CompatibilityHttp()
        val client = client(http)
        for (code in listOf(400, 403, 409, 422, 500)) {
            http.writeFailure = code
            val before = http.writes.size
            val result = client.requestAlbum(ProviderDiscoveryArtist(Artist, "Artist"), ProviderDiscoveryRelease(Release, "Release"))
            assertEquals(if (code == 403) ProviderDiscoveryOperationStatus.Forbidden else ProviderDiscoveryOperationStatus.Uncertain, result.status)
            assertNull(result.identity)
            // First operation also logs in; every later operation performs exactly one POST.
            assertEquals(if (before == 0) 2 else before + 1, http.writes.size)
        }
        val activity = client.requestActivity()
        assertEquals(ProviderDiscoveryStatus.Ready, activity.status)
        assertEquals("future_status", activity.items.single().status)
        assertNull(activity.items.single().identity?.managedAlbumId)
        assertEquals("71", activity.items.single().identity?.canonicalAlbumId?.value)
    }

    @Test fun nullableAndMalformedOptionalRowsCannotDiscardHealthyRecommendations() = runTest {
        val http = CompatibilityHttp().apply {
            recommendations = """{"recommendations":[null,[],{"name":"Artist","id":"$Artist","discoveryDepth":null,"firstDiscoveredAt":null,"matchedTags":[null,{},"rock"],"supportingSeeds":[null,{}, {"artistName":"Seed"}]}],"basedOn":[null,{}, {"name":"Seed"}],"topTags":null}"""
        }
        val result = client(http).recommendations(0, 3)
        assertEquals(ProviderDiscoveryStatus.Ready, result.status)
        assertEquals(3, result.nextOffset)
        assertEquals(3, result.artists.single().recommendation?.rank)
        assertEquals(listOf("rock"), result.artists.single().recommendation?.matchedTags)
        assertNull(result.artists.single().recommendation?.depth)
        assertNull(result.artists.single().recommendation?.firstDiscoveredAt)
    }

    @Test fun createdAndAcceptedResponsesReachTheNativeClientThroughRealHttpTransport() = runTest {
        for ((code, response, expected) in listOf(
            Triple(201, """{"success":true,"status":"queued","requestGroupId":"group-1","jobIds":["job-1"],"managedBy":"aurral","album":{"id":7,"canonicalId":71}}""", ProviderDiscoveryOperationStatus.Queued),
            Triple(202, """{"success":true,"queued":true,"jobId":"job-1"}""", ProviderDiscoveryOperationStatus.Queued),
            Triple(202, """{"success":true,"alreadyQueued":true,"jobId":null}""", ProviderDiscoveryOperationStatus.AlreadyQueued),
        )) {
            var submissions = 0
            val ktor = HttpClient(MockEngine { request ->
                val (status, body) = when (request.url.encodedPath) {
                    "/proxy/aurral/api/health/bootstrap" -> 200 to """{"appVersion":"2.13.0","api":{"version":1,"features":["discover","trackDownloads"]}}"""
                    "/proxy/aurral/api/auth/login" -> 200 to """{"token":"session","expiresAt":100000,"user":{"permissions":{"addAlbum":true}}}"""
                    else -> {
                        assertEquals(HttpMethod.Post, request.method)
                        assertEquals("Bearer session", request.headers[HttpHeaders.Authorization])
                        assertTrue(request.url.encodedPath.endsWith(if (code == 201) "/albums/request" else "/downloads/track"))
                        submissions++
                        code to response
                    }
                }
                respond(body, HttpStatusCode.fromValue(status), headersOf(HttpHeaders.ContentType, "application/json"))
            })
            try {
                val connection = NavidromeConnection.fromPassword(providerId = ProviderIdAurral,
                    baseUrl = "https://music.test/proxy/aurral", username = "listener", password = "password")
                val client = AurralDiscoveryClient(connection, KtorNavidromeHttpClient(ktor), nowMillis = { 0L })
                val artist = ProviderDiscoveryArtist(Artist, "Artist")
                val result = if (code == 201) client.requestAlbum(artist, ProviderDiscoveryRelease(Release, "Release"))
                    else client.requestTrack(artist, ProviderDiscoverySample("42", "Track"))
                assertEquals(expected, result.status)
                assertEquals(1, submissions)
                if (code == 201) assertEquals("71", result.identity?.canonicalAlbumId?.value)
                else assertNull(result.identity?.canonicalAlbumId)
            } finally { ktor.close() }
        }
    }

    private fun client(http: CompatibilityHttp, now: () -> Long = { 0L }) = AurralDiscoveryClient(
        NavidromeConnection.fromPassword(providerId = ProviderIdAurral, baseUrl = "https://music.test/proxy/aurral",
            username = "listener", password = "password"), http, nowMillis = now)
}

private const val Artist = "a74b1b7f-71a5-4011-9441-d0b5e4122711"
private const val Release = "b1392450-e666-3926-a536-22c65f834433"
private class CompatibilityHttp : NavidromeHttpClient {
    var appVersion = "2.13.0"
    var apiVersion = 1
    var features = listOf("discover", "flows", "playlistCoverTemplates", "activity", "review",
        "trackDownloads", "linkSearch", "downloadCancel", "shareImports", "trending", "flowAutoDownload", "futureFeature")
    var recommendations = """{"recommendations":[{"id":"$Artist","name":"Artist","image":null,"matchedTags":["rock"],"sourceArtists":[],"sourceType":"charts","supportingSeeds":[{"artistName":"Seed"}],"discoveryTier":null,"discoveryDepth":1,"firstDiscoveredAt":"2026-11-03T10:00:00.000Z","futureField":{}},null],"recommendationCount":5,"basedOn":[{"name":"Seed"}],"topGenres":["rock"],"topTags":[],"lastUpdated":null}"""
    var readFailure: Int? = null
    var writeFailure: Int? = null
    var logins = 0
    var reads = 0
    val writes = mutableListOf<String>()
    override suspend fun get(url: String) = get(url, emptyMap())
    override suspend fun get(url: String, headers: Map<String, String>): String {
        assertTrue(url.startsWith("https://music.test/proxy/aurral/api/"))
        if (url.endsWith("/health/bootstrap")) return buildJsonObject {
            put("appVersion", appVersion)
            put("api", buildJsonObject { put("version", apiVersion); put("features", JsonArray(features.map(::JsonPrimitive))) })
        }.toString()
        assertEquals("Bearer session-$logins", headers["Authorization"])
        reads++
        readFailure?.let { throw NavidromeHttpException(it) }
        return when {
            "/discover?" in url -> { assertTrue(Regex("limit=[1-3]&offset=0$").containsMatchIn(url)); recommendations }
            url.endsWith("/requests") -> """[null,{}, {"id":"activity-1","status":"future_status","canonicalAlbumId":71,"albumId":7,"source":"tidal"}]"""
            else -> error("Unexpected read")
        }
    }
    override suspend fun postJson(url: String, body: String, headers: Map<String, String>): String {
        val payload = Json.parseToJsonElement(body).jsonObject
        if (url.endsWith("/auth/login")) {
            assertEquals(setOf("username", "password"), payload.keys)
            logins++; writes += "login"
            return """{"token":"session-$logins","expiresAt":${logins * 100000},"user":{"isAdmin":false,"permissions":{"addAlbum":true}},"futureField":null}"""
        }
        assertTrue(url.endsWith("/library/albums/request"))
        assertEquals(Artist, payload["artistMbid"]?.jsonPrimitive?.content)
        assertEquals(Release, payload["albumMbid"]?.jsonPrimitive?.content)
        assertEquals("Bearer session-$logins", headers["Authorization"])
        writes += "album"
        writeFailure?.let { throw NavidromeHttpException(it) }
        error("Test must explicitly choose a request outcome")
    }
}
