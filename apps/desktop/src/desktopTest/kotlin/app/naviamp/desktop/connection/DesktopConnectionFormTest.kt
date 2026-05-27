package app.naviamp.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopConnectionFormTest {
    @Test
    fun tlsSettingsTrimPathsAndIgnoreCustomCertificateWhenSkippingTls() {
        val settings = desktopConnectionTlsSettings(
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
        val settings = desktopConnectionTlsSettings(
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

    @Test
    fun displayNameFallsBackToNormalizedServerUrl() {
        assertEquals(
            "My Server",
            desktopConnectionDisplayName(" My Server ", " https://music.example.test/ "),
        )
        assertEquals(
            "https://music.example.test",
            desktopConnectionDisplayName(" ", " https://music.example.test/ "),
        )
    }
}
