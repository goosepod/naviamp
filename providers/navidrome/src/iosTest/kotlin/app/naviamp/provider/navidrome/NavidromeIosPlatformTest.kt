package app.naviamp.provider.navidrome

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NavidromeIosPlatformTest {
    @Test
    fun allTlsModesAreAvailableAndMissingCertificateFilesFailClosed() {
        val capabilities = navidromeTlsCapabilities()

        assertTrue(capabilities.insecureSkipVerification)
        assertTrue(capabilities.customServerCertificates)
        assertTrue(capabilities.clientCertificates)
        createDefaultNavidromeKtorClient(
            NavidromeTlsSettings(insecureSkipTlsVerification = true),
        ).close()
        assertFailsWith<IllegalStateException> {
            createDefaultNavidromeKtorClient(
                NavidromeTlsSettings(customCertificatePath = "/server.pem"),
            )
        }
        assertFailsWith<IllegalStateException> {
            createDefaultNavidromeKtorClient(
                NavidromeTlsSettings(clientCertificateKeyStorePath = "/missing-client.p12"),
            )
        }
    }

    @Test
    fun defaultTlsCreatesDarwinClient() {
        createDefaultNavidromeKtorClient(NavidromeTlsSettings()).close()
    }
}
