package app.naviamp.provider.aurral

import app.naviamp.domain.provider.ProviderDiscoveryStatus
import app.naviamp.domain.source.AurralCompanion
import app.naviamp.provider.navidrome.NavidromeHttpClient
import app.naviamp.provider.navidrome.NavidromeHttpException
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class AurralCompanionVerificationTest {
    private val settings = AurralCompanion(true, "https://aurral.example", "secondary", "secret")

    private class Http(val version: Int = 2, val login: () -> String) : NavidromeHttpClient {
        val paths = mutableListOf<String>()
        override suspend fun get(url: String): String = get(url, emptyMap())
        override suspend fun get(url: String, headers: Map<String, String>): String {
            paths += url.substringAfter("aurral.example")
            check(url.endsWith("/api/health/bootstrap")) { "Verification must not read discovery routes: $url" }
            return """{"api":{"version":$version}}"""
        }
        override suspend fun postJson(url: String, body: String, headers: Map<String, String>): String {
            paths += url.substringAfter("aurral.example")
            return login()
        }
    }

    @Test fun acceptedCredentialsSignInOnceWithoutReadingDiscovery() = runTest {
        val http = Http { """{"token":"session","expiresAt":9999999999999}""" }
        assertEquals(ProviderDiscoveryStatus.Ready, verifyAurralCompanionSignIn(settings, http))
        assertEquals(listOf("/api/health/bootstrap", "/api/auth/login"), http.paths)
    }

    @Test fun rejectedCredentialsAreASignInFailure() = runTest {
        for (code in listOf(400, 401)) {
            assertEquals(ProviderDiscoveryStatus.SignInRequired,
                verifyAurralCompanionSignIn(settings, Http { throw NavidromeHttpException(code) }), "HTTP $code")
        }
        assertEquals(ProviderDiscoveryStatus.Forbidden, verifyAurralCompanionSignIn(settings, Http { throw NavidromeHttpException(403) }))
    }

    @Test fun missingPasswordUnsupportedServerAndBadAddressAreDistinguished() = runTest {
        assertEquals(ProviderDiscoveryStatus.PasswordRequired,
            verifyAurralCompanionSignIn(settings.copy(password = ""), Http { error("No login without a password") }))
        assertEquals(ProviderDiscoveryStatus.Unsupported,
            verifyAurralCompanionSignIn(settings, Http(version = 1) { error("No login on an unsupported server") }))
        assertEquals(ProviderDiscoveryStatus.Unavailable,
            verifyAurralCompanionSignIn(settings, Http { throw NavidromeHttpException(502) }))
        assertEquals(ProviderDiscoveryStatus.Unavailable,
            verifyAurralCompanionSignIn(settings.copy(baseUrl = "aurral.example"), createHttp = { error("Invalid address must not reach HTTP") }))
        assertEquals(ProviderDiscoveryStatus.Unavailable,
            verifyAurralCompanionSignIn(settings, Http { """{"error":"no token"}""" }))
    }
}
