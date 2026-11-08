package app.naviamp.provider.aurral

import app.naviamp.domain.provider.*
import app.naviamp.provider.navidrome.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** Synthetic released 2.13 responses; never submits a live acquisition. */
class AurralWaitingForReleaseTest {
    @Test fun successfulMonitoredAlbumWithoutJobsRetainsWaitingDateManagerAndCorrelation() = runTest {
        withResponse(waitingBody("\"2027-03-05\"")) {
            assertEquals(ProviderDiscoveryOperationStatus.WaitingForRelease, it.status)
            assertEquals(201, it.httpStatusCode)
            assertEquals(ProviderDiscoveryWaitingForRelease(DiscoveryLibraryManager.Aurral, "2027-03-05"), it.waitingForRelease)
            assertEquals(DiscoveryCanonicalAlbumId("71"), it.identity?.canonicalAlbumId)
            assertEquals(DiscoveryManagedAlbumId(DiscoveryLibraryManager.Aurral, "71"), it.identity?.managedAlbumId)
            assertTrue(it.identity!!.jobIds.isEmpty())
            assertNull(it.identity?.requestGroupId)
        }
        withResponse(waitingBody("null").replace("\"releaseDate\":null", "\"requestGroupId\":\"monitor-1\",\"releaseDate\":null")) {
            assertEquals(DiscoveryRequestGroupId("monitor-1"), it.identity?.requestGroupId)
            assertTrue(it.identity!!.jobIds.isEmpty())
        }
    }

    @Test fun unknownAndPartialDatesDoNotDetermineWaitingOrInventAnExactDate() = runTest {
        for ((date, expected) in listOf("null" to null, "\" \"" to null, "2027" to null, "{}" to null,
            "\"2027\"" to "2027", "\"2027-03\"" to "2027-03", "\"${"x".repeat(65)}\"" to null)) {
            withResponse(waitingBody(date)) {
                assertEquals(ProviderDiscoveryOperationStatus.WaitingForRelease, it.status)
                assertEquals(expected, it.waitingForRelease?.releaseDate)
            }
        }
        withResponse("""{"success":true,"managedBy":"aurral","status":"inLibrary","album":{"releaseDate":"2099-01-01"}}""") {
            assertEquals(ProviderDiscoveryOperationStatus.Saved, it.status)
            assertNull(it.waitingForRelease)
        }
    }

    @Test fun onlyAuthoritativeAurralAlbumStatusEstablishesWaiting() = runTest {
        val body = waitingBody("null")
        for (response in listOf(
            body.replace("\"unreleased\"", "\"missing\""),
            body.replace("\"unreleased\"", "\"partial\""),
            body.replace("\"unreleased\"", "\"future_status\""),
            body.replace("\"aurral\"", "\"lidarr\""),
            body.replace("\"aurral\"", "\"future_manager\""),
            body.replace("\"monitored\":true", "\"monitored\":false"),
            body.replace("\"albumStatus\":{", "\"albumStatus\":{\"ignored\":true,").replace("\"managedBy\":\"aurral\",\"releaseDate\"", "\"managedBy\":\"lidarr\",\"releaseDate\""),
        )) withResponse(response) {
            assertEquals(ProviderDiscoveryOperationStatus.Saved, it.status)
            assertNull(it.waitingForRelease)
        }
        withResponse(body, track = true) {
            assertEquals(ProviderDiscoveryOperationStatus.Saved, it.status)
            assertNull(it.waitingForRelease)
        }
        withResponse(body.replace("\"success\":true", "\"success\":false")) {
            assertEquals(ProviderDiscoveryOperationStatus.Uncertain, it.status)
        }
        withResponse(body, code = 500) {
            assertEquals(ProviderDiscoveryOperationStatus.Uncertain, it.status)
            assertNull(it.identity)
        }
    }

    @Test fun ownedQueuedAndMissingSourceKeepTheirDistinctMeanings() = runTest {
        val body = waitingBody("\"2099-01-01\"")
        for ((response, status) in listOf(
            body.replace("\"inLibrary\"", "\"available\"") to ProviderDiscoveryOperationStatus.AlreadyOwned,
            body.replace("\"success\":true", "\"success\":true,\"alreadyOwned\":true") to ProviderDiscoveryOperationStatus.AlreadyOwned,
            body.replace("\"inLibrary\"", "\"queued\"").replace("\"jobIds\":[]", "\"jobIds\":[\"job-1\"]") to ProviderDiscoveryOperationStatus.Queued,
            body.replace("\"queued\":false", "\"queued\":true") to ProviderDiscoveryOperationStatus.Queued,
            body.replace("\"queued\":false", "\"alreadyQueued\":true") to ProviderDiscoveryOperationStatus.AlreadyQueued,
            """{"success":true,"status":"download_source_missing"}""" to ProviderDiscoveryOperationStatus.MissingSource,
            """{"success":true,"queued":false}""" to ProviderDiscoveryOperationStatus.Saved,
        )) withResponse(response) {
            assertEquals(status, it.status)
            assertNull(it.waitingForRelease)
        }
        withResponse(body.replace("\"inLibrary\"", "\"blocked\"")) {
            assertEquals(ProviderDiscoveryOperationStatus.WaitingForRelease, it.status)
        }
    }

    @Test fun laterServerResponsesSupplyQueuedAndAvailableTransitionsRatherThanTheDate() = runTest {
        for ((body, expected) in listOf(
            waitingBody("\"2020-01-01\"") to ProviderDiscoveryOperationStatus.WaitingForRelease,
            """{"success":true,"managedBy":"aurral","status":"queued","jobIds":["job-2"],"albumStatus":{"status":"queued"}}""" to ProviderDiscoveryOperationStatus.Queued,
            """{"success":true,"managedBy":"aurral","status":"available","albumStatus":{"status":"complete"}}""" to ProviderDiscoveryOperationStatus.AlreadyOwned,
        )) withResponse(body) { assertEquals(expected, it.status) }
    }

    private fun waitingBody(date: String) = """{"success":true,"managedBy":"aurral","status":"inLibrary","queued":false,"jobIds":[],"requestGroupId":null,"album":{"id":"71","canonicalId":"71","managedBy":"aurral","monitored":true},"albumStatus":{"status":"unreleased","managedBy":"aurral","releaseDate":$date}}"""

    private suspend fun withResponse(body: String, code: Int = 201, track: Boolean = false,
        verify: (ProviderDiscoveryOperation) -> Unit) {
        var submissions = 0
        val ktor = HttpClient(MockEngine { request ->
            val response = when (request.url.encodedPath) {
                "/api/health/bootstrap" -> 200 to """{"api":{"version":1,"features":["discover","trackDownloads"]}}"""
                "/api/auth/login" -> 200 to """{"token":"fixture","expiresAt":100000,"user":{"permissions":{"addAlbum":true}}}"""
                else -> {
                    assertEquals(if (track) "/api/library/downloads/track" else "/api/library/albums/request", request.url.encodedPath)
                    submissions++
                    code to body
                }
            }
            respond(response.second, HttpStatusCode.fromValue(response.first), headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            val client = AurralDiscoveryClient(NavidromeConnection.fromPassword(providerId = ProviderIdAurral,
                baseUrl = "https://fixture.test", username = "test", password = "fixture"), KtorNavidromeHttpClient(ktor), { 0L })
            val artist = ProviderDiscoveryArtist("11111111-1111-1111-1111-111111111111", "Artist")
            val release = ProviderDiscoveryRelease("33333333-3333-3333-3333-333333333333", "Album")
            verify(if (!track) client.requestAlbum(artist, release) else client.requestReleaseTrack(
                ProviderDiscoveryReleaseContext(artist, release), ProviderDiscoveryReleaseTrack(
                    DiscoveryRecordingMbid("55555555-5555-5555-5555-555555555555"), "Track", 1, 1, null)))
            assertEquals(1, submissions, "Waiting must not schedule or replay acquisition")
        } finally { ktor.close() }
    }
}
