package app.naviamp.domain.settings

import app.naviamp.domain.playback.ReplayGainMode
import app.naviamp.domain.source.ConnectionTlsSettings
import app.naviamp.domain.source.SavedMediaSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsSyncMappingTest {
    @Test
    fun buildsPortableDocumentFromLocalSnapshotWithoutSecretsOrDeviceLocalPlaybackSettings() {
        val document = buildSettingsSyncDocument(
            snapshot = SettingsSyncLocalSnapshot(
                serverProfiles = listOf(savedSource()),
                playback = PlaybackSettings(
                    replayGainMode = ReplayGainMode.Track,
                    crossfadeDurationSeconds = 5,
                    volumePercent = 42,
                    debugLoggingEnabled = true,
                    allowMobileDownloads = true,
                ),
                visualizer = VisualizerSettings(selectedVisualizer = "AudioBars"),
            ),
            nowEpochMillis = 123L,
            deviceId = " desktop ",
        )

        val profile = document.serverProfiles.single()

        assertEquals(123L, document.updatedAtEpochMillis)
        assertEquals("desktop", document.lastWriterDeviceId)
        assertEquals("source_1", profile.id)
        assertEquals(ProviderIdNavidrome, profile.providerId)
        assertEquals("Goosepod", profile.displayName)
        assertEquals("ursasmar", profile.username)
        assertEquals("https://navidrome.lan", profile.primaryUrl)
        assertTrue(profile.tls.insecureSkipTlsVerification)
        assertEquals("/certs/navidrome.pem", profile.tls.customCertificatePath)
        assertEquals("/certs/client.p12", profile.tls.clientCertificateKeyStorePath)
        assertEquals(ReplayGainMode.Track, document.preferences.playback.replayGainMode)
        assertEquals(5, document.preferences.playback.crossfadeDurationSeconds)
        assertEquals(100, document.preferences.playback.volumePercent)
        assertFalse(document.preferences.playback.debugLoggingEnabled)
        assertFalse(document.preferences.playback.allowMobileDownloads)
        assertEquals("AudioBars", document.preferences.visualizer.selectedVisualizer)
        assertFalse(SettingsSyncJson.encode(document).contains("native-token"))
        assertFalse(SettingsSyncJson.encode(document).contains("secret-password"))
    }

    @Test
    fun importedProfileBuildsConnectionFormThatStillRequiresLocalPassword() {
        val form = savedSource()
            .toSettingsSyncServerProfile()
            .toConnectionFormState(password = "new-password")

        assertEquals("Goosepod", form.displayName)
        assertEquals("https://navidrome.lan", form.serverUrl)
        assertEquals("ursasmar", form.username)
        assertEquals("new-password", form.password)
        assertTrue(form.skipTlsVerification)
        assertEquals("/certs/navidrome.pem", form.customCertificatePath)
        assertEquals("/certs/client.p12", form.clientCertificatePath)
        assertEquals("", form.clientCertificatePassword)
    }

    private fun savedSource(): SavedMediaSource =
        SavedMediaSource(
            id = "source_1",
            providerId = ProviderIdNavidrome,
            cacheNamespace = "navidrome:https://navidrome.lan:ursasmar",
            displayName = "Goosepod",
            baseUrl = "https://navidrome.lan/",
            username = "ursasmar",
            token = "token",
            salt = "salt",
            nativeToken = "native-token",
            tlsSettings = ConnectionTlsSettings(
                insecureSkipTlsVerification = true,
                customCertificatePath = "/certs/navidrome.pem",
                clientCertificateKeyStorePath = "/certs/client.p12",
                clientCertificateKeyStorePassword = "secret-password",
            ),
            createdAtEpochMillis = 1L,
            lastConnectedAtEpochMillis = 2L,
            lastSyncStartedAtEpochMillis = 3L,
            lastSyncCompletedAtEpochMillis = 4L,
        )
}
