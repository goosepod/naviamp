package app.naviamp.ui

import app.naviamp.domain.network.SharedHttpClient
import app.naviamp.domain.network.SharedHttpResponse
import app.naviamp.domain.settings.ApplicationUpdateChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NaviampUpdateCheckerTest {
    @Test
    fun parsesNewerLatestRelease() = kotlinx.coroutines.test.runTest {
        val update = checkForNaviampUpdate(
            currentVersion = "v0.17.0",
            channel = ApplicationUpdateChannel.Stable,
            client = FakeUpdateHttpClient(
                """[{"tag_name":"v0.18.0","name":"Naviamp 0.18.0","html_url":"https://github.com/goosepod/naviamp/releases/tag/v0.18.0","draft":false,"prerelease":false}]""",
            ),
        )

        assertNotNull(update)
        assertTrue(update.version == "v0.18.0")
        assertTrue(update.releaseUrl.endsWith("/v0.18.0"))
    }

    @Test
    fun injectedHttpCheckerUsesHostClient() = kotlinx.coroutines.test.runTest {
        val checker = HttpNaviampApplicationUpdateChecker(
            FakeUpdateHttpClient(
                """[{"tag_name":"v0.18.0","name":"Naviamp 0.18.0","html_url":"https://example.test/v0.18.0","draft":false,"prerelease":false}]""",
            ),
        )

        val update = checker.latestUpdate("v0.17.0", ApplicationUpdateChannel.Stable)

        assertNotNull(update)
        assertTrue(update.releaseUrl == "https://example.test/v0.18.0")
    }

    @Test
    fun comparesReleaseVersionsNumerically() {
        assertTrue(isNewerNaviampVersion("v0.18.0", "v0.17.9"))
        assertTrue(isNewerNaviampVersion("v1.0.0", "0.99.0"))
        assertFalse(isNewerNaviampVersion("v0.17.0", "v0.17.0"))
        assertFalse(isNewerNaviampVersion("v0.16.9", "v0.17.0"))
        assertTrue(isNewerNaviampVersion("v2.0.0-beta.3", "v2.0.0-beta.2"))
        assertTrue(isNewerNaviampVersion("v2.0.0", "v2.0.0-beta.3"))
        assertFalse(isNewerNaviampVersion("v2.0.0-beta.2", "v2.0.0-beta.3"))
    }

    @Test
    fun ignoresInvalidVersions() {
        assertFalse(isNewerNaviampVersion("latest", "v0.17.0"))
        assertFalse(isNewerNaviampVersion("v0.18.0", "development"))
    }

    @Test
    fun stableChannelIgnoresPrereleases() = kotlinx.coroutines.test.runTest {
        val update = checkForNaviampUpdate(
            currentVersion = "v2.0.0-beta.1",
            channel = ApplicationUpdateChannel.Stable,
            client = FakeUpdateHttpClient(
                """[
                    {"tag_name":"v2.0.0-beta.3","name":"Beta 3","html_url":"https://example.test/beta3","draft":false,"prerelease":true},
                    {"tag_name":"v1.9.0","name":"Stable","html_url":"https://example.test/stable","draft":false,"prerelease":false}
                ]""",
            ),
        )

        assertEquals(null, update)
    }

    @Test
    fun betaChannelSelectsNewestEligibleRelease() = kotlinx.coroutines.test.runTest {
        val update = checkForNaviampUpdate(
            currentVersion = "v2.0.0-beta.1",
            channel = ApplicationUpdateChannel.Beta,
            client = FakeUpdateHttpClient(
                """[
                    {"tag_name":"v2.0.0-beta.2","name":"Beta 2","html_url":"https://example.test/beta2","draft":false,"prerelease":true},
                    {"tag_name":"v2.0.0-beta.3","name":"Beta 3","html_url":"https://example.test/beta3","draft":false,"prerelease":true},
                    {"tag_name":"v2.0.0-beta.4","name":"Draft","html_url":"https://example.test/draft","draft":true,"prerelease":true}
                ]""",
            ),
        )

        assertEquals("v2.0.0-beta.3", update?.version)
    }

    @Test
    fun defaultsPrereleaseBuildsToBetaAndFinishedBuildsToStable() {
        assertEquals(ApplicationUpdateChannel.Beta, defaultApplicationUpdateChannel("2.0.0-beta.3"))
        assertEquals(ApplicationUpdateChannel.Stable, defaultApplicationUpdateChannel("2.0.0"))
        assertEquals(ApplicationUpdateChannel.Stable, defaultApplicationUpdateChannel("development"))
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
