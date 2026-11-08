package app.naviamp.provider.aurral

import app.naviamp.domain.provider.*
import app.naviamp.provider.navidrome.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.serialization.json.*
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AurralReleaseContractTest {
    @Test fun mutationsRemainSerializedWhileIndependentReadsCanFinish() = runTest {
        val gate = CompletableDeferred<Unit>()
        val transport = ReleaseHttp()
        var submissions = 0
        val http = object : NavidromeHttpClient by transport {
            override suspend fun postJson(url: String, body: String, headers: Map<String, String>): String {
                if (url.endsWith("/api/library/downloads/track")) {
                    submissions++
                    if (submissions == 1) gate.await()
                }
                return transport.postJson(url, body, headers)
            }
            override suspend fun postJsonResponse(url: String, body: String, headers: Map<String, String>) =
                NavidromeHttpResponse(postJson(url, body, headers))
        }
        val client = AurralDiscoveryClient(NavidromeConnection.fromPassword(providerId = ProviderIdAurral,
            baseUrl = "https://aurral.test", username = "test", password = "fixture"), http, nowMillis = { 0L })
        val context = ProviderDiscoveryReleaseContext(ProviderDiscoveryArtist(Artist, "Artist"), ProviderDiscoveryRelease(Group, "Album"))
        val track = ProviderDiscoveryReleaseTrack(DiscoveryRecordingMbid(Recording), "Track", 1, 1, null)
        val first = async { client.requestReleaseTrack(context, track) }
        runCurrent()
        val second = async { client.requestReleaseTrack(context, track.copy(recordingMbid = DiscoveryRecordingMbid("77777777-7777-7777-7777-777777777777"))) }
        runCurrent()
        assertEquals(1, submissions)
        assertFalse(second.isCompleted)
        assertEquals(ProviderDiscoveryStatus.Ready, client.releaseTracks(DiscoveryReleaseGroupMbid(Group)).status)
        gate.complete(Unit)
        assertEquals(ProviderDiscoveryOperationStatus.Queued, first.await().status)
        assertEquals(ProviderDiscoveryOperationStatus.Queued, second.await().status)
        assertEquals(2, submissions)
        assertEquals(1, transport.logins)
    }

    @Test fun independentRoutesShareOneSessionWithoutSerializingSlowMetadata() = runTest {
        val gate = CompletableDeferred<Unit>()
        val transport = ReleaseHttp()
        val http = object : NavidromeHttpClient by transport {
            override suspend fun get(url: String, headers: Map<String, String>): String {
                if ("/release-group/$Group" in url && "/tracks" !in url) gate.await()
                return transport.get(url, headers)
            }
        }
        val client = AurralDiscoveryClient(NavidromeConnection.fromPassword(providerId = ProviderIdAurral,
            baseUrl = "https://aurral.test", username = "test", password = "fixture"), http, nowMillis = { 0L })
        val slow = async { client.releaseDetails(DiscoveryReleaseGroupMbid(Group)) }
        runCurrent()
        val fast = async { client.releaseTracks(DiscoveryReleaseGroupMbid(Group)) }
        runCurrent()
        assertTrue(fast.isCompleted)
        assertFalse(slow.isCompleted)
        assertEquals(ProviderDiscoveryStatus.Ready, fast.await().status)
        gate.complete(Unit)
        assertEquals(ProviderDiscoveryStatus.Ready, slow.await().status)
        assertEquals(1, transport.logins)
    }

    @Test fun lateUnauthorizedResponseCannotClearANewerSessionOrReplayTheRequest() = runTest {
        val gate = CompletableDeferred<Unit>()
        val transport = ReleaseHttp()
        var failNextTracks = true
        val http = object : NavidromeHttpClient by transport {
            override suspend fun get(url: String, headers: Map<String, String>): String {
                if ("/release-group/$Group" in url && "/tracks" !in url) {
                    gate.await()
                    throw NavidromeHttpException(401)
                }
                if ("/tracks" in url && failNextTracks) {
                    failNextTracks = false
                    throw NavidromeHttpException(401)
                }
                return transport.get(url, headers)
            }
        }
        val client = AurralDiscoveryClient(NavidromeConnection.fromPassword(providerId = ProviderIdAurral,
            baseUrl = "https://aurral.test", username = "test", password = "fixture"), http, nowMillis = { 0L })
        val old = async { client.releaseDetails(DiscoveryReleaseGroupMbid(Group)) }
        runCurrent()
        assertEquals(ProviderDiscoveryStatus.SignInRequired, client.releaseTracks(DiscoveryReleaseGroupMbid(Group)).status)
        assertEquals(ProviderDiscoveryStatus.Ready, client.releaseTracks(DiscoveryReleaseGroupMbid(Group)).status)
        assertEquals(2, transport.logins)
        gate.complete(Unit)
        assertEquals(ProviderDiscoveryStatus.SignInRequired, old.await().status)
        assertEquals(ProviderDiscoveryStatus.Ready, client.releaseTracks(DiscoveryReleaseGroupMbid(Group)).status)
        assertEquals(2, transport.logins)
    }
    @Test fun editionsAndTrackEditionIdsRemainSeparateFromReleaseGroupsAndRecordings() = runTest {
        val http = ReleaseHttp()
        val client = releaseClient(http)
        val detail = client.releaseDetails(DiscoveryReleaseGroupMbid(Group))
        assertEquals(listOf(Edition), detail.editions.map { it.mbid.value })
        assertEquals("Official", detail.editions.single().status)
        assertEquals(listOf(Artist, OtherArtist), detail.credits.map { it.mbid?.value })
        val context = ProviderDiscoveryReleaseContext(ProviderDiscoveryArtist(Artist, "Björk & guest"), detail.release!!)
        val tracks = client.releaseTracks(DiscoveryReleaseGroupMbid(Group), context).tracks
        assertEquals(listOf(2, 1), tracks.map { it.discNumber }) // Presentation owns ordering; transport preserves rows.
        assertEquals(Edition, tracks.first().releaseMbid?.value)
        assertEquals(Recording, tracks.first().recordingMbid?.value)
        assertEquals("42", tracks.first().previewId?.value)
        assertNull(tracks.last().previewId)
        assertNull(tracks.last().recordingMbid)
        assertTrue(http.urls.any { "artistName=Bj%C3%B6rk+%26+guest" in it && "albumTitle=Two+discs" in it })
    }

    @Test fun recordingRequestsCarryExactContextAndNeverUseThePreviewIdentity() = runTest {
        val http = ReleaseHttp()
        val context = ProviderDiscoveryReleaseContext(ProviderDiscoveryArtist(Artist, "Artist"), ProviderDiscoveryRelease(Group, "Album"))
        val track = ProviderDiscoveryReleaseTrack(DiscoveryRecordingMbid(Recording), "Track", 2, 3, 123456,
            DiscoveryDeezerPreviewId("42"), DiscoveryReleaseMbid(Edition))
        val result = releaseClient(http).requestReleaseTrack(context, track)
        assertEquals(ProviderDiscoveryOperationStatus.Queued, result.status)
        assertEquals("track-job", result.identity?.jobIds?.single()?.value)
        val body = Json.parseToJsonElement(http.submission!!).jsonObject
        assertEquals(Recording, body.getValue("trackMbid").jsonPrimitive.content)
        assertEquals(Edition, body.getValue("releaseMbid").jsonPrimitive.content)
        assertEquals(Group, body.getValue("albumMbid").jsonPrimitive.content)
        assertEquals(Artist, body.getValue("artistMbid").jsonPrimitive.content)
        assertEquals(2, body.getValue("discNumber").jsonPrimitive.int)
        assertEquals(123456, body.getValue("durationMs").jsonPrimitive.int)
        assertFalse(body.values.any { it.jsonPrimitive.content == "42" })
    }

    @Test fun mismatchedContextAndMissingRecordingNeverSubmit() = runTest {
        val http = ReleaseHttp()
        val client = releaseClient(http)
        val wrong = ProviderDiscoveryReleaseContext(ProviderDiscoveryArtist(Artist, "Artist"), ProviderDiscoveryRelease(Edition, "Album"))
        assertEquals(ProviderDiscoveryStatus.Unavailable, client.releaseTracks(DiscoveryReleaseGroupMbid(Group), wrong).status)
        val context = wrong.copy(release = ProviderDiscoveryRelease(Group, "Album"))
        assertEquals(ProviderDiscoveryOperationStatus.Unavailable, client.requestReleaseTrack(context,
            ProviderDiscoveryReleaseTrack(null, "No recording", 1, 1, null, DiscoveryDeezerPreviewId("42"))).status)
        assertNull(http.submission)
    }
}

private const val Artist = "11111111-1111-1111-1111-111111111111"
private const val OtherArtist = "22222222-2222-2222-2222-222222222222"
private const val Group = "33333333-3333-3333-3333-333333333333"
private const val Edition = "44444444-4444-4444-4444-444444444444"
private const val Recording = "55555555-5555-5555-5555-555555555555"
private fun releaseClient(http: ReleaseHttp) = AurralDiscoveryClient(NavidromeConnection.fromPassword(
    providerId = ProviderIdAurral, baseUrl = "https://aurral.test", username = "test", password = "fixture"), http, nowMillis = { 0L })

private class ReleaseHttp : NavidromeHttpClient {
    var logins = 0
    val urls = mutableListOf<String>()
    var submission: String? = null
    override suspend fun get(url: String) = get(url, emptyMap())
    override suspend fun get(url: String, headers: Map<String, String>): String {
        urls += url
        if (url.endsWith("/bootstrap")) return """{"api":{"version":1,"features":["discover","trackDownloads"]}}"""
        assertTrue(headers.getValue("Authorization").startsWith("Bearer "))
        if ("/tracks" in url) return """[{"mbid":"$Recording","title":"Second disc","discNumber":2,"trackNumber":1,"releaseMbid":"$Edition","length":123456,"preview_url":"https://preview.test/42","previewProvider":"deezer","previewTrackId":"42"},{"title":"First disc","discNumber":1,"trackNumber":1,"previewProvider":"unknown","previewTrackId":"42","preview_url":"https://preview.test/42"}]"""
        return """{"id":"$Group","title":"Two discs","artist-credit":[{"name":"One","artist":{"id":"$Artist"}},{"name":"Two","artist":{"id":"$OtherArtist"}}],"releases":[{"id":"$Edition","title":"Edition","date":"2026-01-01","status":"Official"},{"id":"invalid"}]}"""
    }
    override suspend fun postJson(url: String, body: String, headers: Map<String, String>): String {
        if (url.endsWith("/login")) {
            logins++
            return """{"token":"fixture","expiresAt":100000,"user":{"permissions":{"addAlbum":true}}}"""
        }
        assertTrue(url.endsWith("/api/library/downloads/track"))
        submission = body
        return """{"queued":true,"jobId":"track-job"}"""
    }
}
