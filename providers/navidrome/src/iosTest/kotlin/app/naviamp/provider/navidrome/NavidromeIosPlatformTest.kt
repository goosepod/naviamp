package app.naviamp.provider.navidrome

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

class NavidromeIosPlatformTest {
    @Test
    fun advancedTlsOptionsAreUnavailableAndRejected() {
        val capabilities = navidromeTlsCapabilities()

        assertFalse(capabilities.insecureSkipVerification)
        assertFalse(capabilities.customServerCertificates)
        assertFalse(capabilities.clientCertificates)
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
