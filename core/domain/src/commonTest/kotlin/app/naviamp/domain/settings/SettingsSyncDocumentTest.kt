package app.naviamp.domain.settings

import app.naviamp.domain.playback.ReplayGainMode
import app.naviamp.domain.radio.RadioDjPreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsSyncDocumentTest {
    @Test
    fun roundTripsPortableSettingsSyncDocument() {
        val document = SettingsSyncDocument(
            updatedAtEpochMillis = 123L,
            lastWriterDeviceId = " desktop ",
            serverProfiles = listOf(
                SettingsSyncServerProfile(
                    id = " goosepod ",
                    displayName = " Goosepod Navidrome ",
                    username = " ursasmar ",
                    primaryUrl = " https://navidrome.lan/ ",
                    secondaryUrls = listOf(
                        SettingsSyncServerEndpoint(
                            url = " https://navidrome.tailnet.example/ ",
                            label = " Tailscale ",
                            priority = 2,
                        ),
                    ),
                    tls = SettingsSyncTlsSettings(
                        insecureSkipTlsVerification = true,
                        customCertificatePath = " /certs/navidrome.pem ",
                    ),
                    customHeaders = listOf(
                        SettingsSyncHeaderDefinition(
                            name = " X-Proxy-User ",
                            value = " ursasmar ",
                        ),
                    ),
                ),
            ),
            preferences = SettingsSyncPreferences(
                interfaceSettings = InterfaceSettings(
                    startPlayingOnLaunch = true,
                    nowPlaying = NowPlayingDisplaySettings(
                        showAlbumYear = false,
                        showAudioInfo = false,
                        showVolumeBar = false,
                        scrollTrackTitle = false,
                        scrollArtistName = true,
                        scrollAlbumName = true,
                    ),
                    trackSwipes = TrackSwipeSettings(
                        libraryRight = TrackSwipeAction.AddToPlaylist,
                        libraryLeft = TrackSwipeAction.ToggleFavorite,
                        queueRight = TrackSwipeAction.GoToAlbum,
                        queueLeft = TrackSwipeAction.Remove,
                        relatedLeft = TrackSwipeAction.GoToArtist,
                        playlistEditRight = TrackSwipeAction.MoveDown,
                        playlistEditLeft = TrackSwipeAction.MoveToBottom,
                    ),
                ),
                playback = PlaybackSettings(
                    replayGainMode = ReplayGainMode.Album,
                    sampleRateConverter = SampleRateConverter.Sinc32,
                    sampleRateMatching = SampleRateMatching.Strict,
                    crossfadeDurationSeconds = 6,
                    radioDjs = listOf(RadioDjPreset(id = "dj", name = " Road DJ ")),
                ),
                visualizer = VisualizerSettings(selectedVisualizer = "Waveform"),
            ),
        )

        val decoded = SettingsSyncJson.decode(SettingsSyncJson.encode(document))
        val profile = decoded.serverProfiles.single()

        assertEquals(CurrentSettingsSyncSchemaVersion, decoded.schemaVersion)
        assertEquals("desktop", decoded.lastWriterDeviceId)
        assertEquals("goosepod", profile.id)
        assertEquals("Goosepod Navidrome", profile.displayName)
        assertEquals("ursasmar", profile.username)
        assertEquals("https://navidrome.lan", profile.primaryUrl)
        assertEquals("https://navidrome.tailnet.example", profile.secondaryUrls.single().url)
        assertEquals("Tailscale", profile.secondaryUrls.single().label)
        assertTrue(profile.tls.insecureSkipTlsVerification)
        assertEquals("/certs/navidrome.pem", profile.tls.customCertificatePath)
        assertEquals("X-Proxy-User", profile.customHeaders.single().name)
        assertEquals("ursasmar", profile.customHeaders.single().value)
        assertEquals(ReplayGainMode.Album, decoded.preferences.playback.replayGainMode)
        assertEquals(SampleRateConverter.Sinc32, decoded.preferences.playback.sampleRateConverter)
        assertEquals(SampleRateMatching.Strict, decoded.preferences.playback.sampleRateMatching)
        assertEquals("Road DJ", decoded.preferences.playback.radioDjs.single().name)
        assertEquals("Waveform", decoded.preferences.visualizer.selectedVisualizer)
        assertTrue(decoded.preferences.interfaceSettings.startPlayingOnLaunch)
        assertFalse(decoded.preferences.interfaceSettings.nowPlaying.showAlbumYear)
        assertFalse(decoded.preferences.interfaceSettings.nowPlaying.showAudioInfo)
        assertFalse(decoded.preferences.interfaceSettings.nowPlaying.showVolumeBar)
        assertFalse(decoded.preferences.interfaceSettings.nowPlaying.scrollTrackTitle)
        assertTrue(decoded.preferences.interfaceSettings.nowPlaying.scrollArtistName)
        assertTrue(decoded.preferences.interfaceSettings.nowPlaying.scrollAlbumName)
        assertEquals(TrackSwipeAction.AddToPlaylist, decoded.preferences.interfaceSettings.trackSwipes.libraryRight)
        assertEquals(TrackSwipeAction.ToggleFavorite, decoded.preferences.interfaceSettings.trackSwipes.libraryLeft)
        assertEquals(TrackSwipeAction.GoToAlbum, decoded.preferences.interfaceSettings.trackSwipes.queueRight)
        assertEquals(TrackSwipeAction.Remove, decoded.preferences.interfaceSettings.trackSwipes.queueLeft)
        assertEquals(TrackSwipeAction.GoToArtist, decoded.preferences.interfaceSettings.trackSwipes.relatedLeft)
        assertEquals(TrackSwipeAction.MoveDown, decoded.preferences.interfaceSettings.trackSwipes.playlistEditRight)
        assertEquals(TrackSwipeAction.MoveToBottom, decoded.preferences.interfaceSettings.trackSwipes.playlistEditLeft)
    }

    @Test
    fun normalizationDropsBlankProfilesAndDoesNotPersistSecretHeaderValues() {
        val document = SettingsSyncDocument(
            lastWriterDeviceId = " ",
            serverProfiles = listOf(
                SettingsSyncServerProfile(
                    id = "blank",
                    displayName = "Blank",
                    username = "",
                    primaryUrl = " ",
                ),
                SettingsSyncServerProfile(
                    id = "source",
                    displayName = "",
                    username = "user",
                    primaryUrl = "https://server.example/",
                    secondaryUrls = listOf(
                        SettingsSyncServerEndpoint("https://server.example"),
                        SettingsSyncServerEndpoint(" "),
                    ),
                    customHeaders = listOf(
                        SettingsSyncHeaderDefinition(name = "Authorization", value = "Bearer secret", valueIsSecret = true),
                        SettingsSyncHeaderDefinition(name = " "),
                    ),
                ),
            ),
        ).normalized()

        val profile = document.serverProfiles.single()
        val secretHeader = profile.customHeaders.single()

        assertNull(document.lastWriterDeviceId)
        assertEquals("https://server.example", profile.primaryUrl)
        assertEquals("https://server.example", profile.displayName)
        assertTrue(profile.secondaryUrls.isEmpty())
        assertEquals("Authorization", secretHeader.name)
        assertNull(secretHeader.value)
        assertTrue(secretHeader.valueIsSecret)
        assertFalse(SettingsSyncJson.encode(document).contains("Bearer secret"))
    }
}
