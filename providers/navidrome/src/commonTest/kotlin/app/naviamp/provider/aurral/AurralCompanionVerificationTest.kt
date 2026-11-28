package app.naviamp.provider.aurral

import app.naviamp.domain.provider.ProviderDiscoveryStatus
import app.naviamp.domain.source.AurralCompanion
import app.naviamp.provider.navidrome.NavidromeHttpClient
import app.naviamp.provider.navidrome.NavidromeHttpException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
            verifyAurralCompanionSignIn(settings.copy(baseUrl = "aurral.example"), createClient = { error("Invalid address must not reach HTTP") }))
        assertEquals(ProviderDiscoveryStatus.Unavailable,
            verifyAurralCompanionSignIn(settings, Http { """{"error":"no token"}""" }))
    }

    @Test fun theProbeClosesTheClientItCreatesWhetherSignInSucceedsOrFails() = runTest {
        for (loginStatus in listOf(HttpStatusCode.OK, HttpStatusCode.Unauthorized)) {
            val created = mutableListOf<HttpClient>()
            val status = verifyAurralCompanionSignIn(settings, createClient = {
                HttpClient(MockEngine { request ->
                    val json = headersOf("Content-Type", "application/json")
                    when {
                        request.url.encodedPath.endsWith("/api/health/bootstrap") -> respond("""{"api":{"version":2}}""", headers = json)
                        loginStatus == HttpStatusCode.OK -> respond("""{"token":"session","expiresAt":9999999999999}""", headers = json)
                        else -> respond("""{"error":"Invalid username or password"}""", loginStatus, json)
                    }
                }).also { created += it }
            })
            assertEquals(if (loginStatus == HttpStatusCode.OK) ProviderDiscoveryStatus.Ready else ProviderDiscoveryStatus.SignInRequired, status)
            val client = created.single()
            // close() starts a graceful shutdown; wait for it in real time, bounded so an unclosed
            // client (whose job never completes) still fails.
            val closed = withContext(Dispatchers.Default) {
                withTimeoutOrNull(5_000) { client.coroutineContext[Job]!!.join() }
            } != null
            assertTrue(closed, "HTTP $loginStatus left the probe's client open")
        }
    }
}
