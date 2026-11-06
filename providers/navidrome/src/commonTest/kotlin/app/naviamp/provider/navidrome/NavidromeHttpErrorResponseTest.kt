package app.naviamp.provider.navidrome

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class NavidromeHttpErrorResponseTest {
    @Test fun responseEvidenceIsBoundedAndExcludedFromExceptionAndHistory() = runTest {
        for (size in listOf(0, 8_192, 8_193, 16_384)) {
            val marker = "private-marker".padEnd(size.coerceAtLeast(14), 'x').take(size)
            val client = HttpClient(MockEngine { respond(marker, HttpStatusCode.BadRequest) })
            try {
                val error = assertFailsWith<NavidromeHttpException> {
                    KtorNavidromeHttpClient(client).postJson("https://music.test/error-evidence", "{}")
                }
                assertEquals(400, error.statusCode)
                assertEquals(if (size <= 8_192) marker else null, error.responseBody)
                assertEquals("Navidrome returned HTTP 400.", error.message)
                val history = NavidromeApiCallHistory.recent().first { it.endpoint.contains("error-evidence") }
                assertFalse(history.errorMessage.orEmpty().contains("private-marker"))
            } finally { client.close() }
        }
    }
}
