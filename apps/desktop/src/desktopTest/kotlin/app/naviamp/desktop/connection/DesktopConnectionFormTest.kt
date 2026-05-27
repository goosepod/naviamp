package app.naviamp.desktop

import app.naviamp.domain.source.ConnectionTlsSettings
import app.naviamp.domain.source.SavedMediaSource
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

    @Test
    fun newFormStateClearsConnectionFields() {
        assertEquals(DesktopConnectionFormState(), newDesktopConnectionFormState())
    }

    @Test
    fun savedFormStateLoadsConnectionFieldsAndHidesDefaultDisplayName() {
        val formState = savedDesktopConnectionFormState(
            savedSource(
                displayName = "https://music.example.test",
                tlsSettings = ConnectionTlsSettings(
                    insecureSkipTlsVerification = true,
                    customCertificatePath = "/cert.pem",
                    clientCertificateKeyStorePath = "/client.p12",
                    clientCertificateKeyStorePassword = "secret",
                ),
            ),
        )

        assertEquals("https://music.example.test", formState.serverUrl)
        assertEquals("", formState.connectionName)
        assertEquals("demo", formState.username)
        assertEquals("", formState.password)
        assertEquals(true, formState.insecureSkipTlsVerification)
        assertEquals("/cert.pem", formState.customCertificatePath)
        assertEquals("/client.p12", formState.clientCertificateKeyStorePath)
        assertEquals("secret", formState.clientCertificateKeyStorePassword)
        assertEquals("https://music.example.test", formState.savedConnectionForLogin?.baseUrl)
    }

    @Test
    fun savedFormStateKeepsCustomDisplayName() {
        val formState = savedDesktopConnectionFormState(
            savedSource(displayName = "Home Music"),
        )

        assertEquals("Home Music", formState.connectionName)
    }

    private fun savedSource(
        displayName: String,
        tlsSettings: ConnectionTlsSettings = ConnectionTlsSettings(),
    ): SavedMediaSource =
        SavedMediaSource(
            id = "source",
            providerId = "navidrome",
            cacheNamespace = "navidrome:https://music.example.test:demo",
            displayName = displayName,
            baseUrl = "https://music.example.test",
            username = "demo",
            token = "token",
            salt = "salt",
            nativeToken = "native",
            tlsSettings = tlsSettings,
            createdAtEpochMillis = 1L,
            lastConnectedAtEpochMillis = 2L,
            lastSyncStartedAtEpochMillis = 3L,
            lastSyncCompletedAtEpochMillis = 4L,
        )
}
