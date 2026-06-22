package app.naviamp.domain.settings

import app.naviamp.domain.cache.ProviderMediaSourceConnection
import app.naviamp.domain.cache.ProviderMediaSourceRepository
import app.naviamp.domain.playback.ReplayGainMode
import app.naviamp.domain.source.MediaSourceIdentity
import app.naviamp.domain.source.ConnectionHeaderDefinition
import app.naviamp.domain.source.ConnectionSecondaryUrl
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
        assertEquals("https://navidrome.tailnet", profile.secondaryUrls.single().url)
        assertEquals("Tailscale", profile.secondaryUrls.single().label)
        val nonSecretHeader = profile.customHeaders.first { it.name == "X-Proxy-User" }
        assertEquals("ursasmar", nonSecretHeader.value)
        assertEquals(null, profile.customHeaders.first { it.name == "X-Secret" }.value)
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
        assertFalse(SettingsSyncJson.encode(document).contains("do-not-sync"))
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
        assertEquals("https://navidrome.tailnet", form.secondaryUrls.single().url)
        assertEquals("X-Proxy-User", form.customHeaders.first().name)
    }

    @Test
    fun importsAllSyncedServerProfilesWithoutSecrets() {
        val repository = RecordingProviderMediaSourceRepository()

        val result = importSettingsSyncServerProfiles(
            serverProfiles = listOf(
                savedSource().toSettingsSyncServerProfile(),
                savedSource(
                    id = "source_2",
                    displayName = "Away Music",
                    baseUrl = "https://away.example",
                    username = "demo2",
                ).toSettingsSyncServerProfile(),
            ),
            repository = repository,
        )

        assertEquals(2, result.importedCount)
        assertEquals("Goosepod", result.firstConnectionForm?.displayName)
        assertEquals(
            listOf(
                "navidrome:https://navidrome.lan:ursasmar",
                "navidrome:https://away.example:demo2",
            ),
            repository.cacheNamespaces,
        )
        assertTrue(repository.connections.all { it.token.isEmpty() })
        assertTrue(repository.connections.all { it.salt.isEmpty() })
        assertTrue(repository.connections.all { it.nativeToken == null })
        assertTrue(repository.connections.all { it.tlsSettings.clientCertificateKeyStorePassword == null })
    }

    private fun savedSource(): SavedMediaSource =
        savedSource(
            id = "source_1",
            displayName = "Goosepod",
            baseUrl = "https://navidrome.lan/",
            username = "ursasmar",
        )

    private fun savedSource(
        id: String,
        displayName: String,
        baseUrl: String,
        username: String,
    ): SavedMediaSource =
        SavedMediaSource(
            id = id,
            providerId = ProviderIdNavidrome,
            cacheNamespace = "navidrome:${baseUrl.trimEnd('/')}:$username",
            displayName = displayName,
            baseUrl = baseUrl,
            username = username,
            token = "token",
            salt = "salt",
            nativeToken = "native-token",
            secondaryUrls = listOf(
                ConnectionSecondaryUrl(
                    url = "https://navidrome.tailnet",
                    label = "Tailscale",
                ),
            ),
            customHeaders = listOf(
                ConnectionHeaderDefinition(
                    name = "X-Proxy-User",
                    value = "ursasmar",
                ),
                ConnectionHeaderDefinition(
                    name = "X-Secret",
                    value = "do-not-sync",
                    valueIsSecret = true,
                ),
            ),
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

    private class RecordingProviderMediaSourceRepository : ProviderMediaSourceRepository {
        val connections = mutableListOf<ProviderMediaSourceConnection>()
        val cacheNamespaces = mutableListOf<String>()

        override fun upsertProviderMediaSource(
            connection: ProviderMediaSourceConnection,
            cacheNamespace: String,
            providerId: String,
        ): MediaSourceIdentity {
            connections += connection
            cacheNamespaces += cacheNamespace
            return MediaSourceIdentity(
                id = "source_${connections.size}",
                cacheNamespace = cacheNamespace,
                displayName = connection.displayName,
            )
        }
    }
}
