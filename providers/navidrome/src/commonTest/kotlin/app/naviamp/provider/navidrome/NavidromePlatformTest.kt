package app.naviamp.provider.navidrome

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NavidromePlatformTest {
    @Test
    fun md5MatchesStandardVectors() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", navidromeMd5(""))
        assertEquals("900150983cd24fb0d6963f7d28e17f72", navidromeMd5("abc"))
        assertEquals("5d41402abc4b2a76b9719d911017c592", navidromeMd5("hello"))
    }

    @Test
    fun urlEncodingUsesFormSemantics() {
        assertEquals("Beyonc%C3%A9+%26+Jay-Z", "Beyoncé & Jay-Z".urlEncode())
    }

    @Test
    fun unsupportedTlsSettingsFailClosed() {
        val unavailable = NavidromeTlsCapabilities(
            insecureSkipVerification = false,
            customServerCertificates = false,
            clientCertificates = false,
        )

        assertFailsWith<NavidromeException> {
            NavidromeTlsSettings(insecureSkipTlsVerification = true).requireSupportedBy(unavailable)
        }
        assertFailsWith<NavidromeException> {
            NavidromeTlsSettings(customCertificatePath = "/server.pem").requireSupportedBy(unavailable)
        }
        assertFailsWith<NavidromeException> {
            NavidromeTlsSettings(clientCertificateKeyStorePath = "/client.p12").requireSupportedBy(unavailable)
        }
    }
}
