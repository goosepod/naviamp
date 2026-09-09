package app.naviamp.provider.jellyfin

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class JellyfinDownloadTransportTest {
    @Test
    fun declaredBodyMustBeCompleteBeforeDownloadReportsSuccess() = runTest {
        for (declared in listOf(32, 4096)) {
            val client = HttpClient(MockEngine {
                respond(ByteArray(32) { 9 }, HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentLength, declared.toString()))
            })
            try {
                assertEquals(declared == 32, KtorJellyfinHttpClient(client)
                    .download("https://server/audio", emptyMap()) { _, _ -> })
            } finally { client.close() }
        }
    }
}
