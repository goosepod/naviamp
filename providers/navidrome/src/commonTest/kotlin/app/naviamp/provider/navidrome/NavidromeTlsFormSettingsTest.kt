package app.naviamp.provider.navidrome

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NavidromeTlsFormSettingsTest {
    @Test
    fun tlsSettingsTrimPathsAndIgnoreCustomCertificateWhenSkippingTls() {
        val settings = navidromeTlsSettingsFromForm(
            insecureSkipTlsVerification = true,
            customCertificatePath = " /cert.pem ",
            clientCertificateKeyStorePath = " /client.p12 ",
            clientCertificateKeyStorePassword = "secret",
        )

        assertEquals(true, settings.insecureSkipTlsVerification)
        assertNull(settings.customCertificatePath)
        assertEquals("/client.p12", settings.clientCertificateKeyStorePath)
        assertEquals("secret", settings.clientCertificateKeyStorePassword)
    }

    @Test
    fun tlsSettingsOnlyKeepsClientPasswordWhenClientPathExists() {
        val settings = navidromeTlsSettingsFromForm(
            insecureSkipTlsVerification = false,
            customCertificatePath = " /cert.pem ",
            clientCertificateKeyStorePath = " ",
            clientCertificateKeyStorePassword = "secret",
        )

        assertEquals(false, settings.insecureSkipTlsVerification)
        assertEquals("/cert.pem", settings.customCertificatePath)
        assertNull(settings.clientCertificateKeyStorePath)
        assertNull(settings.clientCertificateKeyStorePassword)
    }
}
