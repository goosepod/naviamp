package app.naviamp.provider.aurral

import app.naviamp.domain.provider.*
import app.naviamp.provider.navidrome.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class AurralMutationHttpTest {
    @Test fun createdAndAcceptedResponsesKeepTheActualStatusAndAcquisitionIdentifiers() = runTest {
        for (status in listOf(HttpStatusCode.Created, HttpStatusCode.Accepted)) {
            val wire = MutationWire(status, """{"success":true,"queued":true,"jobId":"job-1","requestGroupId":"group-1"}""")
            try {
                val result = wire.request()
                assertEquals(ProviderDiscoveryOperationStatus.Queued, result.status)
                assertEquals(status.value, result.httpStatusCode)
                assertEquals("job-1", result.identity?.jobIds?.single()?.value)
                assertEquals("group-1", result.identity?.requestGroupId?.value)
                assertEquals(1, wire.submissions)
            } finally { wire.client.close() }
        }
    }

    @Test fun conflictIsExplicitAndManagerIdsAreNeverInterpretedAsCanonicalOrPlayableIds() = runTest {
        val wire = MutationWire(HttpStatusCode.Conflict,
            """{"error":"Ownership conflict","managedBy":"lidarr","canonicalId":71,"providerId":7}""")
        try {
            val result = wire.request()
            assertEquals(ProviderDiscoveryOperationStatus.Conflict, result.status)
            assertEquals(409, result.httpStatusCode)
            assertEquals(DiscoveryCanonicalAlbumId("71"), result.identity?.canonicalAlbumId)
            assertEquals(DiscoveryManagedAlbumId(DiscoveryLibraryManager.Lidarr, "7"), result.identity?.managedAlbumId)
            assertEquals(1, wire.submissions)
        } finally { wire.client.close() }
    }

    @Test fun unauthorizedForbiddenAndLostResponsesNeverReplayTheMutation() = runTest {
        for ((status, expected) in listOf(HttpStatusCode.Unauthorized to ProviderDiscoveryOperationStatus.Uncertain,
            HttpStatusCode.Forbidden to ProviderDiscoveryOperationStatus.Forbidden,
            HttpStatusCode.OK to ProviderDiscoveryOperationStatus.Uncertain)) {
            val wire = MutationWire(status, "{}", loseResponse = status == HttpStatusCode.OK)
            try {
                assertEquals(expected, wire.request().status)
                assertEquals(1, wire.submissions)
            } finally { wire.client.close() }
        }
    }

    @Test fun savedWithoutSourceAndUnconfirmedSuccessRemainDifferentOutcomes() = runTest {
        for ((body, expected) in listOf("""{"success":true,"status":"download_source_missing"}""" to ProviderDiscoveryOperationStatus.MissingSource,
            """{"success":true,"queued":false}""" to ProviderDiscoveryOperationStatus.Saved,
            "{}" to ProviderDiscoveryOperationStatus.Uncertain)) {
            val wire = MutationWire(HttpStatusCode.Created, body)
            try { assertEquals(expected, wire.request().status); assertEquals(1, wire.submissions) }
            finally { wire.client.close() }
        }
    }
}

private class MutationWire(status: HttpStatusCode, body: String, loseResponse: Boolean = false) {
    var submissions = 0
    private val engine = MockEngine { request ->
        val responseHeaders = headersOf(HttpHeaders.ContentType, "application/json")
        when (request.url.encodedPath) {
            "/api/health/bootstrap" -> respond("""{"api":{"version":1,"features":["discover","trackDownloads"]}}""", headers = responseHeaders)
            "/api/auth/login" -> respond("""{"token":"fixture","expiresAt":100000,"user":{"permissions":{"addAlbum":true}}}""", headers = responseHeaders)
            "/api/library/albums/request" -> {
                submissions++
                if (loseResponse) error("Response lost after the server received the request")
                respond(body, status, responseHeaders)
            }
            else -> error("Unexpected route")
        }
    }
    val client = HttpClient(engine)
    private val native = AurralDiscoveryClient(NavidromeConnection.fromPassword(providerId = ProviderIdAurral,
        baseUrl = "https://aurral.test", username = "test", password = "fixture"), KtorNavidromeHttpClient(client), nowMillis = { 0L })
    suspend fun request() = native.requestAlbum(ProviderDiscoveryArtist("11111111-1111-1111-1111-111111111111", "Artist"),
        ProviderDiscoveryRelease("33333333-3333-3333-3333-333333333333", "Album"))
}
