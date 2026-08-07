package app.naviamp.domain.settings

import app.naviamp.domain.cache.ProviderMediaSourceConnection
import app.naviamp.domain.cache.ProviderMediaSourceRepository
import app.naviamp.domain.provider.ProviderIdNavidrome
import app.naviamp.domain.provider.ProviderIdSubsonic
import app.naviamp.domain.playback.ReplayGainMode
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackRequest
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.radio.RadioDjPreset
import app.naviamp.domain.radio.RadioDjPresetRepository
import app.naviamp.domain.source.MediaSourceIdentity
import app.naviamp.domain.source.ConnectionHeaderDefinition
import app.naviamp.domain.source.ConnectionSecondaryUrl
import app.naviamp.domain.source.ConnectionTlsSettings
import app.naviamp.domain.source.SavedMediaSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope

class SettingsSyncMappingTest {
    @Test
    fun legacySyncedProfileWithoutProviderIdDefaultsToNavidrome() {
        val document = SettingsSyncJson.decode(
            """
            {
              "serverProfiles": [
                {
                  "id": "legacy",
                  "displayName": "Legacy server",
                  "username": "demo",
                  "primaryUrl": "https://legacy.example"
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(ProviderIdNavidrome, document.serverProfiles.single().providerId)
        assertEquals(
            ProviderIdNavidrome,
            document.serverProfiles.single().toConnectionFormState().providerId,
        )
    }

    @Test
    fun buildsPortableDocumentFromLocalSnapshotWithoutSecretsOrDeviceLocalPlaybackSettings() {
        val document = buildSettingsSyncDocument(
            snapshot = SettingsSyncLocalSnapshot(
                serverProfiles = listOf(savedSource()),
                playback = PlaybackSettings(
                    replayGainMode = ReplayGainMode.Track,
                    outputDevice = AudioOutputDevicePreference(
                        mode = AudioOutputDeviceMode.Pinned,
                        deviceId = "built-in-output",
                        deviceName = "Built-in Output",
                    ),
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
        assertEquals(AudioOutputDevicePreference(), document.preferences.playback.outputDevice)
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
        assertEquals(ProviderIdNavidrome, form.providerId)
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
    fun importedProfileCarriesItsProviderIdentityIntoTheConnectionForm() {
        val form = savedSource()
            .toSettingsSyncServerProfile()
            .copy(providerId = ProviderIdSubsonic)
            .toConnectionFormState()

        assertEquals(ProviderIdSubsonic, form.providerId)
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

    @Test
    fun appliesPortablePreferencesAndRepositoryMutationsAsOnePlan() {
        val mediaSources = RecordingProviderMediaSourceRepository()
        val radioDjs = RecordingRadioDjPresetRepository()
        val document = SettingsSyncDocument(
            serverProfiles = listOf(savedSource().toSettingsSyncServerProfile()),
            preferences = SettingsSyncPreferences(
                playback = PlaybackSettings(
                    crossfadeDurationSeconds = 7,
                    radioDjs = listOf(RadioDjPreset(id = "wide", name = "Wide")),
                ),
                visualizer = VisualizerSettings(selectedVisualizer = "AudioBars"),
            ),
        )

        val applied = applySettingsSyncDocument(
            document = document,
            playbackEngine = FakePlaybackEngine(supportsCrossfade = false),
            mediaSourceRepository = mediaSources,
            radioDjPresetRepository = radioDjs,
        )

        assertEquals(0, applied.playbackSettings.crossfadeDurationSeconds)
        assertEquals("wide", applied.playbackSettings.radioDjs.single().id)
        assertEquals("AudioBars", applied.visualizer.selectedVisualizer)
        assertEquals(1, applied.importedServerProfiles.importedCount)
        assertEquals("wide", radioDjs.radioDjPresets().single().id)
        assertEquals(1, mediaSources.connections.size)
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
            preferredSourceId: String?,
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

    private class RecordingRadioDjPresetRepository : RadioDjPresetRepository {
        private val presets = mutableListOf<RadioDjPreset>()

        override fun radioDjPresets(): List<RadioDjPreset> = presets.toList()

        override fun replaceRadioDjPresets(presets: List<RadioDjPreset>) {
            this.presets.clear()
            this.presets += presets
        }

        override fun upsertRadioDjPreset(preset: RadioDjPreset) {
            deleteRadioDjPreset(preset.id)
            presets += preset
        }

        override fun deleteRadioDjPreset(id: String) {
            presets.removeAll { it.id == id }
        }
    }

    private class FakePlaybackEngine(
        override val supportsCrossfade: Boolean,
    ) : PlaybackEngine {
        override val name = "Fake"
        override val supportsPause = true
        override val supportsSeek = true
        override val supportsGapless = true
        override val supportsReplayGain = true
        override val supportsSoftwareVolume = true
        override val prefersOriginalStream = false

        override fun play(
            scope: CoroutineScope,
            request: PlaybackRequest,
            onStateChanged: (PlaybackState) -> Unit,
            onProgressChanged: (PlaybackProgress) -> Unit,
            onMetadataChanged: (PlaybackStreamMetadata) -> Unit,
        ) = Unit

        override fun pause() = Unit
        override fun resume() = Unit
        override fun seek(positionSeconds: Double) = Unit
        override fun setVolume(percent: Int) = Unit
        override fun stop() = Unit
    }
}
