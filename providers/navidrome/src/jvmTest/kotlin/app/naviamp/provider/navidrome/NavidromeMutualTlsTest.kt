package app.naviamp.provider.navidrome

import app.naviamp.domain.StreamQuality
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.TrackId
import app.naviamp.testkit.NaviampMutualTlsFixture
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.AfterClass
import org.junit.BeforeClass
import java.io.ByteArrayOutputStream
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

/** Real CIO/client-certificate handshakes, included in ordinary jvmTest (no opt-in flags). */
class NavidromeMutualTlsTest {
    private val originalContext = SSLContext.getDefault()
    private val originalSocketFactory = HttpsURLConnection.getDefaultSSLSocketFactory()
    private val originalHostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier()

    @AfterTest fun restoreJvmTlsDefaults() {
        SSLContext.setDefault(originalContext)
        HttpsURLConnection.setDefaultSSLSocketFactory(originalSocketFactory)
        HttpsURLConnection.setDefaultHostnameVerifier(originalHostnameVerifier)
    }

    @Test fun authenticatesBrowsingArtworkAndStreamBytesWithTheClientIdentity() = runBlocking {
        fixture.startServer().use { server ->
            val ktor = createDefaultNavidromeKtorClient(settings())
            try {
                val http = KtorNavidromeHttpClient(ktor)
                val provider = NavidromeProvider(connection(server.baseUrl), http)
                assertEquals("fixture", provider.validateConnection().serverVersion)
                assertEquals("TLS Artist", provider.artists(10).single().name)
                assertEquals("TLS Tone", provider.track(TrackId("track"))?.title)
                val artworkUrl = provider.coverArtUrl("cover")
                assertContentEquals(fixture.artwork, provider.bytesForOwnedUrl(artworkUrl))
                val request = StreamRequest(TrackId("track"), StreamQuality.Original)
                val downloaded = ByteArrayOutputStream()
                assertTrue(provider.download(provider.streamUrl(request)) { bytes, count -> downloaded.write(bytes, 0, count) })
                assertContentEquals(fixture.audio, downloaded.toByteArray())
                val streamed = ByteArrayOutputStream()
                assertTrue(provider.streamTrackBytes(request, null, false, { response ->
                    assertEquals(200, response.statusCode)
                }) { bytes, count -> streamed.write(bytes, 0, count) })
                assertContentEquals(fixture.audio, streamed.toByteArray())
                assertTrue(server.requestedPaths.containsAll(listOf("/rest/ping.view", "/rest/getArtists.view",
                    "/rest/getSong.view", "/rest/getCoverArt.view", "/rest/stream.view")))
                assertTrue(server.peerSubjects.isNotEmpty())
                assertTrue(server.peerSubjects.all { it == "CN=Naviamp Test Client" })
            } finally { ktor.close() }
        }
    }

    @Test fun rejectsMissingClientCertificateBeforeAnyHttpRequest() = runBlocking {
        assertHandshakeRejected(settings().copy(clientCertificateKeyStorePath = null, clientCertificateKeyStorePassword = null))
    }

    @Test fun rejectsClientCertificateNotIssuedByTheServersAuthority() = runBlocking {
        assertHandshakeRejected(settings().copy(clientCertificateKeyStorePath = fixture.untrustedClientKeyStore.toString()))
    }

    @Test fun rejectsPrivateServerAuthorityWhenItIsNotConfiguredAsTrusted() = runBlocking {
        assertHandshakeRejected(settings().copy(customCertificatePath = null))
    }

    @Test fun wrongPkcs12PasswordFailsBeforeAnyRequestAndDoesNotPoisonTheNextConnection() = runBlocking {
        fixture.startServer().use { server ->
            val failure = assertFails {
                createDefaultNavidromeKtorClient(settings().copy(clientCertificateKeyStorePassword = "wrong-password")).close()
            }
            assertTrue(generateSequence(failure) { it.cause }.any {
                it.message.orEmpty().contains("password", ignoreCase = true)
            }, "The certificate password failure should remain diagnosable")
            assertTrue(server.requestedPaths.isEmpty())
            val ktor = createDefaultNavidromeKtorClient(settings())
            try { assertTrue(KtorNavidromeHttpClient(ktor).get("${server.baseUrl}/rest/ping.view").contains("\"ok\"")) }
            finally { ktor.close() }
        }
    }

    @Test fun resettingJvmDefaultsDoesNotRemoveAnExistingClientsIdentity() = runBlocking {
        fixture.startServer().use { server ->
            val authenticated = createDefaultNavidromeKtorClient(settings())
            val default = createDefaultNavidromeKtorClient(NavidromeTlsSettings())
            try {
                assertTrue(KtorNavidromeHttpClient(authenticated).get("${server.baseUrl}/rest/ping.view").contains("\"ok\""))
                assertTrue(server.peerSubjects.all { it == "CN=Naviamp Test Client" })
            } finally { authenticated.close(); default.close() }
        }
    }

    private suspend fun assertHandshakeRejected(tls: NavidromeTlsSettings) {
        fixture.startServer().use { server ->
            val ktor = createDefaultNavidromeKtorClient(tls)
            try {
                val failure = assertFails {
                    withTimeout(15_000) { KtorNavidromeHttpClient(ktor).get("${server.baseUrl}/rest/ping.view") }
                }
                val details = generateSequence(failure) { it.cause }.joinToString { "${it::class.simpleName}: ${it.message}" }
                // CIO reports a server-aborted client-auth handshake as EOF; JDK trust failures
                // can surface directly as CertPathBuilderException rather than SSLException.
                assertTrue(listOf("tls", "ssl", "cert", "pkix", "EOFException").any { details.contains(it, ignoreCase = true) },
                    "Expected a TLS authentication failure, got $details")
                assertTrue(server.requestedPaths.isEmpty(), "Rejected TLS must never reach an authenticated HTTP handler")
            } finally { ktor.close() }
            // Positive control on this exact server rules out a dead listener or broken fixture.
            val authenticated = createDefaultNavidromeKtorClient(settings())
            try {
                assertTrue(KtorNavidromeHttpClient(authenticated).get("${server.baseUrl}/rest/ping.view").contains("\"ok\""))
                assertEquals(listOf("CN=Naviamp Test Client"), server.peerSubjects.toList())
            } finally { authenticated.close() }
        }
    }

    private fun settings() = NavidromeTlsSettings(
        customCertificatePath = fixture.serverCa.toString(),
        clientCertificateKeyStorePath = fixture.clientKeyStore.toString(),
        clientCertificateKeyStorePassword = fixture.password,
    )

    private fun connection(baseUrl: String) = NavidromeConnection.fromPassword(
        baseUrl = baseUrl, username = "fixture", password = "fixture", salt = "fixture-salt", tlsSettings = settings(),
    )

    companion object {
        private lateinit var fixture: NaviampMutualTlsFixture
        @BeforeClass @JvmStatic fun createCertificates() { fixture = NaviampMutualTlsFixture() }
        @AfterClass @JvmStatic fun deleteCertificates() { if (::fixture.isInitialized) fixture.close() }
    }
}
