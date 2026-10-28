package app.naviamp.provider.navidrome

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class SubsonicRedirectHeadersTest {
    @Test fun externalMediaRedirectKeepsRangeButNotConnectionHeaders() = runTest {
        var destinationHeaders: Headers? = null
        val client = HttpClient(MockEngine { request ->
            if (request.url.host == "music.test") {
                assertEquals("synthetic-connection-value", request.headers["X-Proxy-Token"])
                respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://audio.test/preview.mp3"))
            } else {
                destinationHeaders = request.headers
                respond(byteArrayOf(9, 8, 7), HttpStatusCode.PartialContent, headersOf(
                    HttpHeaders.ContentType to listOf("audio/mpeg"),
                    HttpHeaders.ContentLength to listOf("3"),
                    HttpHeaders.ContentRange to listOf("bytes 0-2/3")))
            }
        })
        try {
            assertTrue(KtorNavidromeHttpClient(client).stream("https://music.test/preview/42", mapOf(
                "X-Proxy-Token" to "synthetic-connection-value", HttpHeaders.Authorization to "Bearer synthetic",
                HttpHeaders.Range to "bytes=0-2"), false, {}, { _, _ -> }))
            val sent = assertNotNull(destinationHeaders)
            assertNull(sent["X-Proxy-Token"])
            assertNull(sent[HttpHeaders.Authorization])
            assertEquals("bytes=0-2", sent[HttpHeaders.Range])
            assertEquals("identity", sent[HttpHeaders.AcceptEncoding])
        } finally { client.close() }
    }
    @Test fun sameOriginRedirectRetainsConnectionHeaders() = runTest {
        val seen = mutableListOf<String?>()
        val client = HttpClient(MockEngine { request ->
            seen += request.headers["X-Proxy-Token"]
            if (request.url.encodedPath == "/first") respond("", HttpStatusCode.Found,
                headersOf(HttpHeaders.Location, "/second"))
            else respond("ok", HttpStatusCode.OK)
        })
        try {
            assertEquals("ok", KtorNavidromeHttpClient(client).get("https://music.test/first",
                mapOf("X-Proxy-Token" to "synthetic")))
            assertEquals(listOf<String?>("synthetic", "synthetic"), seen)
        } finally { client.close() }
    }

    @Test fun equivalentHostCaseAndDefaultPortRemainTheSameOrigin() = runTest {
        var destination: String? = null
        val client = HttpClient(MockEngine { request ->
            if (request.url.encodedPath == "/first") respond("", HttpStatusCode.Found,
                headersOf(HttpHeaders.Location, "https://MUSIC.TEST:443/second"))
            else { destination = request.headers["X-Proxy-Token"]; respond("ok", HttpStatusCode.OK) }
        })
        try {
            KtorNavidromeHttpClient(client).get("https://music.test/first", mapOf("X-Proxy-Token" to "synthetic"))
            assertEquals("synthetic", destination)
        } finally { client.close() }
    }

    @Test fun aConnectionHeaderNamedLikeTransportStillStaysAtItsOrigin() = runTest {
        var destination: Headers? = null
        val client = HttpClient(MockEngine { request ->
            if (request.url.host == "music.test") {
                assertEquals("bytes=100-200", request.headers[HttpHeaders.Range])
                respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://audio.test/preview.mp3"))
            } else { destination = request.headers; respond(byteArrayOf(9, 8, 7), HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "audio/mpeg")) }
        })
        try {
            val provider = NavidromeProvider(NavidromeConnection.fromPassword(baseUrl = "https://music.test",
                username = "synthetic", password = "synthetic", customHeaders = listOf(
                    app.naviamp.domain.source.ConnectionHeaderDefinition(HttpHeaders.Range, "bytes=100-200", true))),
                KtorNavidromeHttpClient(client))
            assertTrue(provider.streamTrackBytes(app.naviamp.domain.StreamRequest(app.naviamp.domain.TrackId("track"),
                app.naviamp.domain.StreamQuality.Original), null, false, {}, { _, _ -> }))
            assertNull(assertNotNull(destination)[HttpHeaders.Range])
        } finally { client.close() }
    }

    @Test fun leavingAndReturningDoesNotRestoreHeadersOrMutateTheNextRequest() = runTest {
        val seen = mutableListOf<String?>()
        val client = HttpClient(MockEngine { request ->
            seen += request.headers["X-Proxy-Token"]
            when (request.url.encodedPath) {
                "/first" -> respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://audio.test/second"))
                "/second" -> respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://music.test/third"))
                else -> respond("ok", HttpStatusCode.OK)
            }
        })
        try {
            val http = KtorNavidromeHttpClient(client)
            repeat(2) { http.get("https://music.test/first", mapOf("X-Proxy-Token" to "synthetic")) }
            assertEquals(listOf("synthetic", null, null, "synthetic", null, null), seen)
        } finally { client.close() }
    }

    @Test fun anotherPortIsAnotherOrigin() = runTest {
        var destination: Headers? = null
        val client = HttpClient(MockEngine { request ->
            if (request.url.encodedPath == "/first") respond("", HttpStatusCode.Found,
                headersOf(HttpHeaders.Location, "https://music.test:8443/second"))
            else { destination = request.headers; respond("ok", HttpStatusCode.OK) }
        })
        try {
            KtorNavidromeHttpClient(client).get("https://music.test/first", mapOf("X-Proxy-Token" to "synthetic"))
            assertNull(assertNotNull(destination)["X-Proxy-Token"])
        } finally { client.close() }
    }

}
