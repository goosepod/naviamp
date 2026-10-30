package app.naviamp.provider.aurral

import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistId
import app.naviamp.domain.provider.*
import app.naviamp.provider.navidrome.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class AurralNativeDetailsTest {
    @Test fun legacyNameOnlyFeedbackIsReconciledButAnotherArtistsExplicitIdIsNotRemoved() = runTest {
        val http = NativeDetailsHttp()
        http.feedback += buildJsonObject {
            put("id", "legacy"); put("artistId", JsonNull); put("artistName", "Example artist"); put("action", "less_like_this")
        }
        http.feedback += buildJsonObject {
            put("id", "other"); put("artistId", OtherMbid); put("artistName", "Example artist"); put("action", "less_like_this")
        }
        val result = AurralDiscoveryClient(connection(), http, nowMillis = { 0L })
            .toggleFeedback(artist, ProviderDiscoveryFeedbackAction.MoreLikeThis)
        assertEquals(setOf(ProviderDiscoveryFeedbackAction.MoreLikeThis), result.selected)
        assertTrue(http.feedback.any { it["id"]?.jsonPrimitive?.content == "other" })
        assertFalse(http.feedback.any { it["id"]?.jsonPrimitive?.content == "legacy" })
    }
    private val artist = ProviderDiscoveryArtist(Mbid, "Example artist", detailsAvailable = true)
    private fun connection() = NavidromeConnection.fromPassword(providerId = ProviderIdAurral,
        baseUrl = "https://music.test", username = "listener", password = "secret")

    @Test fun nativeDetailsUseCanonicalAndSubsonicIdentityInsteadOfTheNumericNativeId() = runTest {
        val http = NativeDetailsHttp()
        val provider = NavidromeProvider(connection(), http)
        val result = provider.discovery!!.artistDetails(artist)
        assertEquals(ProviderDiscoveryStatus.Ready, result.status)
        val details = assertNotNull(result.details)
        assertEquals("artist:mbid:$Mbid", details.libraryArtist?.id?.value)
        assertEquals("Biography", details.biography)
        assertEquals("Release", details.releases.single().title)
        assertTrue(details.feedbackAvailable)
        assertTrue(http.calls.any { "availableOnly=true" in it && "artistId=7" in it })
        assertTrue(http.calls.any { "/rest/getArtist.view?" in it })
    }

    @Test fun mismatchedCanonicalOrSubsonicMusicBrainzIdsNeverProduceALibraryLink() = runTest {
        for (canonicalMismatch in listOf(true, false)) {
            val http = NativeDetailsHttp().also {
                if (canonicalMismatch) it.canonicalMbid = OtherMbid else it.subsonicMbid = OtherMbid
            }
            val result = NavidromeProvider(connection(), http).discovery!!.artistDetails(artist)
            assertEquals(ProviderDiscoveryStatus.Ready, result.status)
            assertNull(result.details?.libraryArtist)
        }
    }

    @Test fun unavailableOrUnownedLibraryAndUnsupportedFeedbackKeepNativeDetailsReadable() = runTest {
        val http = NativeDetailsHttp().also { it.libraryAvailable = false; it.feedbackSupported = false }
        val result = NavidromeProvider(connection(), http).discovery!!.artistDetails(artist)
        assertEquals("Biography", result.details?.biography)
        assertNull(result.details?.libraryArtist)
        assertFalse(result.details!!.feedbackAvailable)
    }

    @Test fun moreLessAndBlockToggleThroughTheActualServerFeedbackList() = runTest {
        val http = NativeDetailsHttp()
        val client = AurralDiscoveryClient(connection(), http, nowMillis = { 0L })
        assertEquals(setOf(ProviderDiscoveryFeedbackAction.MoreLikeThis), client.toggleFeedback(artist, ProviderDiscoveryFeedbackAction.MoreLikeThis).selected)
        assertEquals(setOf(ProviderDiscoveryFeedbackAction.LessLikeThis), client.toggleFeedback(artist, ProviderDiscoveryFeedbackAction.LessLikeThis).selected)
        assertTrue(http.calls.indexOfFirst { it.startsWith("DELETE") } < http.calls.indexOfLast { it.startsWith("POST feedback") })
        assertEquals(setOf(ProviderDiscoveryFeedbackAction.LessLikeThis, ProviderDiscoveryFeedbackAction.BlockArtist),
            client.toggleFeedback(artist, ProviderDiscoveryFeedbackAction.BlockArtist).selected)
        assertEquals(setOf(ProviderDiscoveryFeedbackAction.LessLikeThis), client.toggleFeedback(artist, ProviderDiscoveryFeedbackAction.BlockArtist).selected)
        assertTrue(client.toggleFeedback(artist, ProviderDiscoveryFeedbackAction.LessLikeThis).selected.isEmpty())
    }

    @Test fun aFailedPostAfterOppositeRemovalIsNotReportedAsSuccessAndDetailsReconcileTheServerState() = runTest {
        val http = NativeDetailsHttp()
        val client = AurralDiscoveryClient(connection(), http, nowMillis = { 0L })
        client.toggleFeedback(artist, ProviderDiscoveryFeedbackAction.MoreLikeThis)
        http.failPost = true
        assertEquals(ProviderDiscoveryStatus.Unavailable, client.toggleFeedback(artist, ProviderDiscoveryFeedbackAction.LessLikeThis).status)
        assertTrue(client.artistDetails(artist).details!!.feedback.isEmpty())
        http.failPost = false
        http.feedbackFailure = 403
        assertEquals(ProviderDiscoveryStatus.Forbidden, client.toggleFeedback(artist, ProviderDiscoveryFeedbackAction.BlockArtist).status)
    }
}

private const val Mbid = "a74b1b7f-71a5-4011-9441-d0b5e4122711"
private const val OtherMbid = "19516266-e5d9-4774-b749-812bb76a6559"
private class NativeDetailsHttp : NavidromeHttpClient {
    val calls = mutableListOf<String>()
    var canonicalMbid = Mbid
    var subsonicMbid = Mbid
    var libraryAvailable = true
    var feedbackSupported = true
    var failPost = false
    var feedbackFailure: Int? = null
    private var sequence = 0
    val feedback = mutableListOf<JsonObject>()
    private fun feedbackResponse() = buildJsonObject { put("feedback", JsonArray(feedback.toList())) }.toString()
    override suspend fun get(url: String) = get(url, emptyMap())
    override suspend fun get(url: String, headers: Map<String, String>): String {
        calls += url
        return when {
            "/rest/" in url -> {
                assertNull(headers["Authorization"])
                """{"subsonic-response":{"status":"ok","artist":{"id":"artist:mbid:$Mbid","name":"Example artist","musicBrainzId":"$subsonicMbid","album":[]}}}"""
            }
            url.endsWith("/health/bootstrap") -> """{"api":{"version":1,"features":["discover"]}}"""
            else -> {
                assertEquals("Bearer session", headers["Authorization"])
                when {
                    "/api/artists/" in url -> """{"id":"$Mbid","name":"Example artist","bio":"Biography","genres":["rock"],"release-groups":[{"id":"release","title":"Release","first-release-date":"2020-01-01","primary-type":"Album"}]}"""
                    "/lookup/" in url -> if (libraryAvailable) """{"exists":true,"libraryArtistId":"7"}""" else """{"exists":false,"libraryArtistId":null}"""
                    "/canonical?" in url -> """{"items":[{"id":7,"identityKey":"mbid:$Mbid","mbid":"$canonicalMbid"}]}"""
                    url.endsWith("/feedback") -> {
                        if (!feedbackSupported) throw NavidromeHttpException(404)
                        feedbackFailure?.let { throw NavidromeHttpException(it) }
                        feedbackResponse()
                    }
                    else -> error("Unexpected endpoint")
                }
            }
        }
    }
    override suspend fun postJson(url: String, body: String, headers: Map<String, String>): String {
        if (url.endsWith("/auth/login")) return """{"token":"session","expiresAt":9999999999999}"""
        assertTrue(url.endsWith("/feedback"))
        assertEquals("Bearer session", headers["Authorization"])
        calls += "POST feedback"
        if (failPost) return """{"success":false}"""
        val payload = Json.parseToJsonElement(body).jsonObject
        assertEquals(Mbid, payload["artistId"]?.jsonPrimitive?.content)
        feedback += JsonObject(payload + ("id" to JsonPrimitive("entry-${++sequence}")))
        return """{"success":true}"""
    }
    override suspend fun deleteJson(url: String, headers: Map<String, String>): String {
        assertEquals("Bearer session", headers["Authorization"])
        calls += "DELETE $url"
        feedback.removeAll { it["id"]?.jsonPrimitive?.content == url.substringAfterLast('/') }
        return """{"success":true}"""
    }
}
