package app.naviamp.provider.navidrome

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NavidromeIosPlatformTest {
    @Test
    fun insecureTlsIsAvailableWhileCertificateFilesRemainUnavailable() {
        val capabilities = navidromeTlsCapabilities()

        assertTrue(capabilities.insecureSkipVerification)
        assertFalse(capabilities.customServerCertificates)
        assertFalse(capabilities.clientCertificates)
        createDefaultNavidromeKtorClient(
            NavidromeTlsSettings(insecureSkipTlsVerification = true),
        ).close()
        assertFailsWith<NavidromeException> {
            createDefaultNavidromeKtorClient(
                NavidromeTlsSettings(customCertificatePath = "/server.pem"),
            )
        }
    }

    @Test
    fun defaultTlsCreatesDarwinClient() {
        createDefaultNavidromeKtorClient(NavidromeTlsSettings()).close()
    }
}
