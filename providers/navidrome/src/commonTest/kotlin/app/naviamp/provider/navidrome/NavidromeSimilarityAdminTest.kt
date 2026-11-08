package app.naviamp.provider.navidrome

import app.naviamp.domain.radio.*
import app.naviamp.domain.provider.ProviderIdSubsonic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class NavidromeSimilarityAdminTest {
    private fun connection(token: String? = "native-token") = NavidromeConnection(
        baseUrl = "https://music.example.test", username = "demo", token = "subsonic-token", salt = "salt", nativeToken = token,
    )

    @Test
    fun adminReadsWhitelistedPluginMetadataAndRetainsRefreshedNativeToken() = runTest {
        val client = object : NavidromeHttpClient {
            override suspend fun get(url: String) = error("Headers required")
            override suspend fun getResponse(url: String, headers: Map<String, String>): NavidromeHttpResponse {
                assertTrue(url.endsWith("/api/config/") || url.endsWith("/api/plugin/"))
                if (url.endsWith("config/")) {
                    assertEquals("Bearer native-token", headers[NavidromeNativeAuthorizationHeader])
                    return NavidromeHttpResponse("""{"config":{"Plugins":{"Enabled":true},"EnableExternalServices":false,"Agents":"audiomuseai,lastfm","PasswordEncryptionKey":"SECRET"}}""",
                        mapOf(NavidromeNativeAuthorizationHeader to "Bearer refreshed-token"))
                }
                assertEquals("Bearer refreshed-token", headers[NavidromeNativeAuthorizationHeader])
                return NavidromeHttpResponse("""[{"id":"audiomuseai","manifest":"{\"name\":\"AudioMuse\",\"version\":\"10.0.0\"}","enabled":true,"config":"SECRET","lastError":"SECRET","path":"SECRET"}]""")
            }
        }
        val provider = NavidromeProvider(connection(), client)
        val details = provider.similarityAdminDiagnostics()
        assertEquals(SimilarityAdminStatus.Available, details.status)
        assertEquals(true, details.pluginsEnabled)
        assertEquals(false, details.externalServicesEnabled)
        assertEquals(listOf("audiomuseai", "lastfm"), details.agents)
        assertEquals(listOf(SimilarityPluginInfo("audiomuseai", "AudioMuse", "10.0.0", true, true)), details.plugins)
        assertFalse(details.toString().contains("SECRET"))
        assertFalse(details.toString().contains("refreshed-token"))
        assertEquals("refreshed-token", provider.connectionWithCurrentNativeToken().nativeToken)
    }

    @Test
    fun disabledPluginSystemDoesNotClaimAnEmptyInventoryOrCallPluginRoute() = runTest {
        var requests = 0
        val client = object : NavidromeHttpClient {
            override suspend fun get(url: String): String {
                requests++
                assertTrue(url.endsWith("config/"))
                return """{"config":{"Plugins":{"Enabled":false},"EnableExternalServices":true,"Agents":["lastfm"]}}"""
            }
        }
        val details = NavidromeProvider(connection(), client).similarityAdminDiagnostics()
        assertEquals(SimilarityAdminStatus.Available, details.status)
        assertEquals(false, details.pluginsEnabled)
        assertNull(details.plugins)
        assertEquals(listOf("lastfm"), details.agents)
        assertEquals(1, requests)
    }

    @Test
    fun nativeLoginAndNonNavidromeProvidersDoNotMakeNativeRequests() = runTest {
        val client = object : NavidromeHttpClient { override suspend fun get(url: String): String = error("Must not request") }
        assertEquals(SimilarityAdminStatus.NativeLoginRequired, NavidromeProvider(connection(null), client).similarityAdminDiagnostics().status)
        assertEquals(SimilarityAdminStatus.Unavailable,
            NavidromeProvider(connection().copy(providerId = ProviderIdSubsonic), client).similarityAdminDiagnostics().status)
    }

    @Test
    fun permissionAndSessionFailuresDoNotClaimPluginAbsence() = runTest {
        for ((code, status) in listOf(401 to SimilarityAdminStatus.NativeLoginRequired, 403 to SimilarityAdminStatus.NotAdmin,
            404 to SimilarityAdminStatus.Unavailable, 500 to SimilarityAdminStatus.Failed)) {
            val client = object : NavidromeHttpClient {
                override suspend fun get(url: String): String = throw NavidromeHttpException(code)
            }
            val provider = NavidromeProvider(connection(), client)
            val details = provider.similarityAdminDiagnostics()
            assertEquals(status, details.status)
            assertEquals(code, details.httpStatus)
            assertNull(details.plugins)
            if (code == 401) assertNull(provider.connectionWithCurrentNativeToken().nativeToken)
        }
    }

    @Test
    fun pluginApiFailureKeepsKnownConfigButNotAnEmptyList() = runTest {
        val client = object : NavidromeHttpClient {
            override suspend fun get(url: String): String = if (url.endsWith("config/"))
                """{"config":{"Plugins":{"Enabled":true}}}""" else throw NavidromeHttpException(404)
        }
        val details = NavidromeProvider(connection(), client).similarityAdminDiagnostics()
        assertEquals(SimilarityAdminStatus.Unavailable, details.status)
        assertEquals(true, details.pluginsEnabled)
        assertNull(details.plugins)
    }

    @Test
    fun olderConfigApiCanStillReturnDisabledPluginAndWrappedInventory() = runTest {
        val client = object : NavidromeHttpClient {
            override suspend fun get(url: String): String = if (url.endsWith("config/")) throw NavidromeHttpException(404)
            else """{"data":[{"id":"audiomuseai","manifest":{"name":"AudioMuse","version":"8"},"enabled":false}]}"""
        }
        val details = NavidromeProvider(connection(), client).similarityAdminDiagnostics()
        assertEquals(SimilarityAdminStatus.Available, details.status)
        assertNull(details.pluginsEnabled)
        assertEquals(false, details.plugins?.single()?.enabled)
    }

    @Test
    fun successfulEmptyInventoryIsDistinctFromMalformedInventory() = runTest {
        for ((body, status) in listOf("[]" to SimilarityAdminStatus.Available, "{}" to SimilarityAdminStatus.Failed,
            "[{\"enabled\":true}]" to SimilarityAdminStatus.Failed, "[{\"id\":\"\"}]" to SimilarityAdminStatus.Failed)) {
            val client = object : NavidromeHttpClient {
                override suspend fun get(url: String): String = if (url.endsWith("config/")) """{"config":{}}""" else body
            }
            val details = NavidromeProvider(connection(), client).similarityAdminDiagnostics()
            assertEquals(status, details.status)
            if (status == SimilarityAdminStatus.Available) assertEquals(emptyList(), details.plugins) else assertNull(details.plugins)
        }
    }

    @Test
    fun adminProbePreservesCancellation() = runTest {
        val client = object : NavidromeHttpClient {
            override suspend fun get(url: String): String = throw CancellationException("cancel")
        }
        assertFailsWith<CancellationException> { NavidromeProvider(connection(), client).similarityAdminDiagnostics() }
    }
}
