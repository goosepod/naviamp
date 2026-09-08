package app.naviamp.provider.navidrome

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class SubsonicDownloadTransportTest {
    @Test
    fun successfulHttpErrorDocumentNeverReachesAudioStorage() = runTest {
        val engine = MockEngine {
            respond("""<?xml version="1.0"?><subsonic-response status="failed"><error code="50" message="Denied"/></subsonic-response>""",
                HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/octet-stream"))
        }
        val client = HttpClient(engine)
        try {
            var writes = 0
            val failure = assertFailsWith<NavidromeException> {
                KtorNavidromeHttpClient(client).download("https://server/rest/download.view", emptyMap()) { _, _ -> writes++ }
            }
            assertEquals(50, failure.subsonicErrorCode)
            assertEquals(0, writes)
        } finally { client.close() }
    }

    @Test
    fun truncatedDeclaredBodyDoesNotReportSuccess() = runTest {
        val client = HttpClient(MockEngine {
            respond(ByteArray(32) { 9 }, HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentLength, "4096"))
        })
        try {
            assertFalse(KtorNavidromeHttpClient(client).download("https://server/rest/download.view", emptyMap()) { _, _ -> })
        } finally { client.close() }
    }

    @Test
    fun prefixInspectionPreservesEveryAudioByte() = runTest {
        val audio = ByteArray(100_000) { (it % 251).toByte() }
        val client = HttpClient(MockEngine {
            respond(audio, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "audio/flac"))
        })
        try {
            val chunks = mutableListOf<ByteArray>()
            assertTrue(KtorNavidromeHttpClient(client).download("https://server/rest/download.view", emptyMap()) { bytes, count ->
                chunks += bytes.copyOf(count)
            })
            assertContentEquals(audio, chunks.flatMap { it.toList() }.toByteArray())
        } finally { client.close() }
    }
}
