package app.naviamp.provider.aurral

import app.naviamp.domain.provider.ProviderDiscoveryStatus
import app.naviamp.domain.source.AurralCompanion
import app.naviamp.domain.source.ConnectionTlsSettings
import kotlinx.coroutines.runBlocking
import java.net.ServerSocket
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class AurralCompanionVerificationTlsTest {
    @Test fun aFailedSkipTlsProbeLeavesTheProcessTlsDefaultsUntouched() = runBlocking {
        val context = SSLContext.getDefault()
        val socketFactory = HttpsURLConnection.getDefaultSSLSocketFactory()
        val hostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
        // A port that was just free: the probe fails to connect without leaving this machine.
        val port = ServerSocket(0).use { it.localPort }
        val settings = AurralCompanion(true, "https://127.0.0.1:$port", "secondary", "wrong",
            tlsSettings = ConnectionTlsSettings(insecureSkipTlsVerification = true))

        assertEquals(ProviderDiscoveryStatus.Unavailable, verifyAurralCompanionSignIn(settings))

        assertSame(context, SSLContext.getDefault())
        assertSame(socketFactory, HttpsURLConnection.getDefaultSSLSocketFactory())
        assertSame(hostnameVerifier, HttpsURLConnection.getDefaultHostnameVerifier())
    }
}
