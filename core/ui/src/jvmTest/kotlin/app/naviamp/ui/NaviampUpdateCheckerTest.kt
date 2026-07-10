package app.naviamp.ui

import app.naviamp.domain.network.SharedHttpClient
import app.naviamp.domain.network.SharedHttpResponse
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NaviampUpdateCheckerTest {
    @Test
    fun parsesNewerLatestRelease() = kotlinx.coroutines.test.runTest {
        val update = checkForNaviampUpdate(
            currentVersion = "v0.17.0",
            client = FakeUpdateHttpClient(
                """{"tag_name":"v0.18.0","name":"Naviamp 0.18.0","html_url":"https://github.com/goosepod/naviamp/releases/tag/v0.18.0"}""",
            ),
        )

        assertNotNull(update)
        assertTrue(update.version == "v0.18.0")
        assertTrue(update.releaseUrl.endsWith("/v0.18.0"))
    }

    @Test
    fun comparesReleaseVersionsNumerically() {
        assertTrue(isNewerNaviampVersion("v0.18.0", "v0.17.9"))
        assertTrue(isNewerNaviampVersion("v1.0.0", "0.99.0"))
        assertFalse(isNewerNaviampVersion("v0.17.0", "v0.17.0"))
        assertFalse(isNewerNaviampVersion("v0.16.9", "v0.17.0"))
    }

    @Test
    fun ignoresInvalidVersions() {
        assertFalse(isNewerNaviampVersion("latest", "v0.17.0"))
        assertFalse(isNewerNaviampVersion("v0.18.0", "development"))
    }
}

private class FakeUpdateHttpClient(private val response: String) : SharedHttpClient {
    override suspend fun get(url: String, headers: Map<String, String>): String = response
    override suspend fun getBytes(url: String, headers: Map<String, String>): ByteArray = response.encodeToByteArray()
    override suspend fun getResponse(url: String, headers: Map<String, String>): SharedHttpResponse? = null
    override suspend fun download(
        url: String,
        headers: Map<String, String>,
        writeChunk: suspend (bytes: ByteArray, count: Int) -> Unit,
    ): Boolean = false
}
