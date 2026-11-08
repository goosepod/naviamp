package app.naviamp.provider.aurral

import app.naviamp.domain.provider.*
import app.naviamp.provider.navidrome.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** Released 2.13 validation and album-conflict envelopes; synthetic, never live mutations. */
class AurralRequestOutcomeTest {
    @Test fun releasedValidationAndIdentityFailuresAreRejectedWithoutInventedOwnership() = runTest {
        for (track in listOf(false, true)) {
            withRequest(400, """{"error":"Invalid request","message":"artistName: Invalid type"}""", track) {
                assertEquals(ProviderDiscoveryOperationStatus.Rejected, it.status)
                assertNull(it.identity)
            }
        }
        withRequest(422, """{"error":"Identity requires clarification","code":"ambiguous_identity"}""") {
            assertEquals(ProviderDiscoveryOperationStatus.Rejected, it.status)
            assertNull(it.identity)
        }
    }

    @Test fun albumOwnerConflictIsNeitherQueuedNorOwnedAndCannotLeakCanonicalIdentity() = runTest {
        withRequest(409, """{"error":"Album is managed by lidarr","code":"album_owner_conflict","managedBy":"lidarr","canonicalId":71,"providerId":7,"conflict":{"available":true}}""") {
            assertEquals(ProviderDiscoveryOperationStatus.Conflict, it.status)
            assertNull(it.identity)
        }
    }

    @Test fun errorCodesAreSpecificToTheirReleasedRoutesAndHttpStatuses() = runTest {
        for ((code, body, track) in listOf(
            Triple(409, """{"code":"album_owner_conflict"}""", true),
            Triple(422, """{"code":"ambiguous_identity"}""", true),
            Triple(500, """{"error":"Invalid request"}""", false),
            Triple(400, """{"code":"album_owner_conflict"}""", false),
        )) withRequest(code, body, track) {
            assertEquals(ProviderDiscoveryOperationStatus.Uncertain, it.status)
        }
    }

    @Test fun unknownPartialMalformedAndOversizedErrorsStayUncertainWithoutReplay() = runTest {
        for (code in listOf(400, 401, 409, 422, 500)) {
            for (body in listOf("{}", "<html>proxy error</html>", """{"error":"Partially completed","code":"future_error"}""")) {
                withRequest(code, body) { assertEquals(ProviderDiscoveryOperationStatus.Uncertain, it.status) }
            }
        }
        withRequest(400, """{"error":"Invalid request","padding":"${"x".repeat(8_192)}"}""") {
            assertEquals(ProviderDiscoveryOperationStatus.Uncertain, it.status)
        }
    }

    @Test fun permissionDenialKeepsItsExistingMeaning() = runTest {
        withRequest(403, """{"error":"Forbidden"}""") {
            assertEquals(ProviderDiscoveryOperationStatus.Forbidden, it.status)
        }
    }

    @Test fun cancellationEscapesWithoutAnotherSubmission() = runTest {
        var submissions = 0
        val http = object : NavidromeHttpClient {
            override suspend fun get(url: String) = Bootstrap
            override suspend fun postJson(url: String, body: String, headers: Map<String, String>): String {
                if (url.endsWith("/auth/login")) return Login
                submissions++
                throw CancellationException("cancelled")
            }
        }
        assertFailsWith<CancellationException> { AurralDiscoveryClient(connection(), http, { 0L })
            .requestAlbum(artist, release) }
        assertEquals(1, submissions)
    }

    private suspend fun withRequest(code: Int, body: String, track: Boolean = false,
        verify: (ProviderDiscoveryOperation) -> Unit) {
        var submissions = 0
        val ktor = HttpClient(MockEngine { request ->
            val response = when (request.url.encodedPath) {
                "/api/health/bootstrap" -> 200 to Bootstrap
                "/api/auth/login" -> 200 to Login
                else -> {
                    assertEquals(if (track) "/api/library/downloads/track" else "/api/library/albums/request", request.url.encodedPath)
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals("Bearer session", request.headers[HttpHeaders.Authorization])
                    submissions++
                    code to body
                }
            }
            respond(response.second, HttpStatusCode.fromValue(response.first), headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            val client = AurralDiscoveryClient(connection(), KtorNavidromeHttpClient(ktor), { 0L })
            verify(if (track) client.requestTrack(artist, ProviderDiscoverySample("42", "Track"))
                else client.requestAlbum(artist, release))
            assertEquals(1, submissions, "No rejection or uncertain outcome may automatically replay a mutation")
        } finally { ktor.close() }
    }

    private fun connection() = NavidromeConnection.fromPassword(providerId = ProviderIdAurral,
        baseUrl = "https://music.test", username = "listener", password = "password")
    private val artist = ProviderDiscoveryArtist("a74b1b7f-71a5-4011-9441-d0b5e4122711", "Artist")
    private val release = ProviderDiscoveryRelease("b1392450-e666-3926-a536-22c65f834433", "Release")
}

private const val Bootstrap = """{"api":{"version":1,"features":["discover","trackDownloads"]}}"""
private const val Login = """{"token":"session","expiresAt":100000,"user":{"permissions":{"addAlbum":true}}}"""
