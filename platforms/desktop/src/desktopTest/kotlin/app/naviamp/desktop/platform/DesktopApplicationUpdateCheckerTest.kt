package app.naviamp.desktop

import app.naviamp.domain.network.SharedHttpClient
import app.naviamp.domain.network.SharedHttpResponse
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopApplicationUpdateCheckerTest {
    @Test
    fun constructsTheSharedUpdatePolicyWithTheDesktopHttpBoundary() = runTest {
        val checker = desktopApplicationUpdateChecker(
            client = object : SharedHttpClient {
                override suspend fun get(url: String, headers: Map<String, String>): String =
                    """{"tag_name":"v9.0.0","name":"Naviamp 9","html_url":"https://example.test/v9"}"""

                override suspend fun getBytes(url: String, headers: Map<String, String>): ByteArray? = null

                override suspend fun getResponse(
                    url: String,
                    headers: Map<String, String>,
                ): SharedHttpResponse? = null

                override suspend fun download(
                    url: String,
                    headers: Map<String, String>,
                    writeChunk: suspend (bytes: ByteArray, count: Int) -> Unit,
                ): Boolean = false
            },
        )

        val update = checker.latestUpdate("v2.0.0-alpha")

        assertEquals("v9.0.0", update?.version)
        assertEquals("https://example.test/v9", update?.releaseUrl)
    }
}
