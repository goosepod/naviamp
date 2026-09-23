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
    fun everyFontSizeCombinationSurvivesExportNormalizationAndIndependentReset() {
        for (general in InterfaceFontSize.entries) for (player in InterfaceFontSize.entries) {
            val settings = InterfaceSettings(generalFontSize = general, nowPlayingFontSize = player)
            val document = buildSettingsSyncDocument(SettingsSyncLocalSnapshot(interfaceSettings = settings), 1L, "test")
            val imported = SettingsSyncJson.decode(SettingsSyncJson.encode(document)).preferences.interfaceSettings.normalized()
            assertEquals(settings, imported)
            assertEquals(player, imported.copy(generalFontSize = InterfaceFontSize.Standard).normalized().nowPlayingFontSize)
            assertEquals(general, imported.copy(nowPlayingFontSize = InterfaceFontSize.Standard).normalized().generalFontSize)
        }
        for (field in listOf("generalFontSize", "nowPlayingFontSize")) {
            kotlin.test.assertFailsWith<kotlinx.serialization.SerializationException> {
                SettingsSyncJson.decode("""{"preferences":{"interfaceSettings":{"$field":"Unknown"}}}""")
            }
        }
    }

    @Test
    fun independentFontSizesRoundTripAndOlderExportsUseStandard() {
        val settings = InterfaceSettings(
            generalFontSize = InterfaceFontSize.Large,
            nowPlayingFontSize = InterfaceFontSize.Small,
        )
        val decoded = SettingsSyncJson.decode(SettingsSyncJson.encode(
            SettingsSyncDocument(preferences = SettingsSyncPreferences(interfaceSettings = settings)),
        )).preferences.interfaceSettings

        assertEquals(InterfaceFontSize.Large, decoded.generalFontSize)
        assertEquals(InterfaceFontSize.Small, decoded.nowPlayingFontSize)

        val older = SettingsSyncJson.decode(
            """{"preferences":{"interfaceSettings":{}}}""",
        ).preferences.interfaceSettings
        assertEquals(InterfaceFontSize.Standard, older.generalFontSize)
        assertEquals(InterfaceFontSize.Standard, older.nowPlayingFontSize)
    }

    @Test fun keepScreenAwakeRoundTripsAndOlderExportsDefaultOff() {
        for (enabled in listOf(false, true)) {
            val document = buildSettingsSyncDocument(SettingsSyncLocalSnapshot(
                interfaceSettings = InterfaceSettings(keepScreenAwake = enabled)), 1L, "test")
            val imported = SettingsSyncJson.decode(SettingsSyncJson.encode(document)).preferences.interfaceSettings.normalized()
            assertEquals(enabled, imported.keepScreenAwake)
        }
        assertFalse(SettingsSyncJson.decode("{}") .preferences.interfaceSettings.keepScreenAwake)
        assertFalse(SettingsSyncJson.decode("""{"preferences":{"interfaceSettings":{}}}""")
            .preferences.interfaceSettings.keepScreenAwake)
        kotlin.test.assertFailsWith<kotlinx.serialization.SerializationException> {
            SettingsSyncJson.decode("""{"preferences":{"interfaceSettings":{"keepScreenAwake":"invalid"}}}""")
        }
    }

    @Test
    fun interfaceLanguageRoundTripsMissingValuesDefaultAndUnknownValuesAreRejected() {
        for (language in InterfaceLanguage.entries) {
            val document = SettingsSyncDocument(preferences = SettingsSyncPreferences(
                interfaceSettings = InterfaceSettings(language = language),
            ))
            assertEquals(language, SettingsSyncJson.decode(SettingsSyncJson.encode(document))
                .preferences.interfaceSettings.language)
        }
        assertEquals(InterfaceLanguage.System, SettingsSyncJson.decode("""{"preferences":{}}""")
            .preferences.interfaceSettings.language)
        kotlin.test.assertFailsWith<kotlinx.serialization.SerializationException> {
            SettingsSyncJson.decode("""{"preferences":{"interfaceSettings":{"language":"FutureLanguage"}}}""")
        }
    }

    @Test
    fun playerWorkspacePreferenceRoundTripsAndOlderExportsDefaultToSplit() {
        for (layout in WideNowPlayingLayout.entries) {
            val document = SettingsSyncDocument(preferences = SettingsSyncPreferences(
                interfaceSettings = InterfaceSettings(nowPlaying = NowPlayingDisplaySettings(wideLayout = layout)),
            ))
            assertEquals(layout, SettingsSyncJson.decode(SettingsSyncJson.encode(document))
                .preferences.interfaceSettings.nowPlaying.wideLayout)
        }
        assertEquals(WideNowPlayingLayout.Split, SettingsSyncJson.decode("""{"preferences":{}}""")
            .preferences.interfaceSettings.nowPlaying.wideLayout)
    }

    @Test
    fun playbackSourcePreferenceRoundTripsAndOlderExportsDefaultOff() {
        for (enabled in listOf(false, true)) {
            val document = buildSettingsSyncDocument(
                SettingsSyncLocalSnapshot(
                    interfaceSettings = InterfaceSettings(
                        nowPlaying = NowPlayingDisplaySettings(showPlaybackSource = enabled),
                    ),
                ),
                1L,
                "test",
            )
            assertEquals(
                enabled,
                SettingsSyncJson.decode(SettingsSyncJson.encode(document))
                    .preferences.interfaceSettings.nowPlaying.showPlaybackSource,
            )
        }
        assertFalse(SettingsSyncJson.decode("""{"preferences":{"interfaceSettings":{"nowPlaying":{}}}}""")
            .preferences.interfaceSettings.nowPlaying.showPlaybackSource)
    }

    @Test
    fun sonicSimilarityChoiceRoundTripsAndOlderExportsUseAutomaticSupport() {
        val disabled = PlaybackSettings(
            sonicSimilarityEnabled = false,
            sonicSimilarityPreferenceConfigured = true,
        )
        val decoded = SettingsSyncJson.decode(
            SettingsSyncJson.encode(
                SettingsSyncDocument(preferences = SettingsSyncPreferences(playback = disabled)),
            ),
        ).preferences.playback

        assertFalse(decoded.effectiveSonicSimilarityEnabled())
        assertTrue(decoded.sonicSimilarityPreferenceConfigured)

        val older = SettingsSyncJson.decode(
            """{"preferences":{"playback":{"sonicSimilarityEnabled":false}}}""",
        ).preferences.playback
        assertTrue(older.effectiveSonicSimilarityEnabled())
        assertFalse(older.sonicSimilarityPreferenceConfigured)
    }

    @Test
    fun splitPaneOpacityRoundTripsDefaultsAndNormalizes() {
        val document = SettingsSyncDocument(preferences = SettingsSyncPreferences(
            interfaceSettings = InterfaceSettings(
                nowPlaying = NowPlayingDisplaySettings(splitPaneBackgroundOpacityPercent = 42),
            ),
        ))
        assertEquals(42, SettingsSyncJson.decode(SettingsSyncJson.encode(document))
            .preferences.interfaceSettings.nowPlaying.splitPaneBackgroundOpacityPercent)
        assertEquals(DefaultSplitPaneBackgroundOpacityPercent, SettingsSyncJson.decode("{}")
            .preferences.interfaceSettings.nowPlaying.splitPaneBackgroundOpacityPercent)
        assertEquals(0, NowPlayingDisplaySettings(splitPaneBackgroundOpacityPercent = -1)
            .normalized().splitPaneBackgroundOpacityPercent)
        assertEquals(100, NowPlayingDisplaySettings(splitPaneBackgroundOpacityPercent = 101)
            .normalized().splitPaneBackgroundOpacityPercent)
    }

    @Test
    fun auroraTonesRoundTripAndKeepExistingSerializedNames() {
        for (tone in AuroraTone.entries) {
            val document = SettingsSyncDocument(preferences = SettingsSyncPreferences(
                interfaceSettings = InterfaceSettings(auroraTone = tone),
            ))
            assertEquals(tone, SettingsSyncJson.decode(SettingsSyncJson.encode(document))
                .preferences.interfaceSettings.auroraTone)
        }
        val existing = SettingsSyncJson.decode("""{"preferences":{"interfaceSettings":{"auroraTone":"Dark"}}}""")
        assertEquals(AuroraTone.Dark, existing.preferences.interfaceSettings.auroraTone)
        assertEquals("Balanced", existing.preferences.interfaceSettings.auroraTone.label)
    }

    @Test
    fun olderExportsUseAuroraDefaultsAndImportedValuesAreBounded() {
        val older = SettingsSyncJson.decode("""{"preferences":{"interfaceSettings":{"auroraTone":"Light"}}}""")
        assertEquals(3, older.preferences.interfaceSettings.auroraColorSteps)
        assertEquals(45, older.preferences.interfaceSettings.auroraAngleDegrees)
        val invalid = SettingsSyncJson.decode("""{"preferences":{"interfaceSettings":{"auroraColorSteps":20,"auroraAngleDegrees":-45}}}""")
        assertEquals(5, invalid.preferences.interfaceSettings.auroraColorSteps)
        assertEquals(0, invalid.preferences.interfaceSettings.auroraAngleDegrees)
    }

    @Test
    fun artistReleasePreferencesRoundTripAndOlderExportsUseSharedDefaults() {
        val settings = InterfaceSettings(
            albumSortOrder = AlbumSortOrder.ReleaseYearDescending,
            libraryAlbumSortOrder = LibraryAlbumSortOrder.RecentlyAdded,
            groupAlbumsByReleaseType = false,
        )
        val decoded = SettingsSyncJson.decode(SettingsSyncJson.encode(
            SettingsSyncDocument(preferences = SettingsSyncPreferences(interfaceSettings = settings)),
        )).preferences.interfaceSettings

        assertEquals(AlbumSortOrder.ReleaseYearDescending, decoded.albumSortOrder)
        assertEquals(LibraryAlbumSortOrder.RecentlyAdded, decoded.libraryAlbumSortOrder)
        assertFalse(decoded.groupAlbumsByReleaseType)

        val older = SettingsSyncJson.decode("{}").preferences.interfaceSettings
        assertEquals(AlbumSortOrder.ReleaseYearAscending, older.albumSortOrder)
        assertEquals(LibraryAlbumSortOrder.Title, older.libraryAlbumSortOrder)
        assertTrue(older.groupAlbumsByReleaseType)
    }

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
                    selectedMusicFolderIds = listOf(" collection ", "music", "collection"),
                ),
            ),
            preferences = SettingsSyncPreferences(
                interfaceSettings = InterfaceSettings(
                    checkForUpdates = true,
                    applicationUpdateChannel = ApplicationUpdateChannel.Beta,
                    startPlayingOnLaunch = true,
                    showArtistInformation = false,
                    showAlbumInformation = false,
                    appBackgroundStyle = AppBackgroundStyle.AlbumBlur,
                    auroraTone = AuroraTone.Light,
                    auroraColorSteps = 5,
                    auroraAngleDegrees = 135,
                    albumBlurRadiusDp = 40,
                    singleColorHex = "#123456",
                    homeSectionPresentations = mapOf(
                        "navibeat-mixes" to HomeSectionPresentationSettings(
                            homeLayout = HomeSectionLayout.Grid,
                            pageLayout = HomeSectionPageLayout.List,
                            visible = false,
                        ),
                    ),
                    homeSectionOrder = listOf("navibeat-mixes", "mixes-for-you", "future-section"),
                    globalKeyboardShortcuts = GlobalKeyboardShortcutSettings(
                        enabled = true,
                        bindingsByPlatform = mapOf(
                            DesktopShortcutPlatform.Windows to mapOf(
                                GlobalShortcutAction.PlayPause to KeyboardShortcutBinding(
                                    key = KeyboardShortcutKey.F8,
                                    control = true,
                                    alt = true,
                                ),
                            ),
                        ),
                    ),
                    nowPlaying = NowPlayingDisplaySettings(
                        showAlbumYear = false,
                        albumYearPreference = NowPlayingAlbumYearPreference.Release,
                        showTrackCover = true,
                        showAudioInfo = false,
                        showPlaybackSource = true,
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
                    lyricsTimingPreference = LyricsTimingPreference.WordSynced,
                    lyricsDisplayPreference = LyricsDisplayPreference.LineSynced,
                    sonicSimilarityEnabled = false,
                    sonicSimilarityPreferenceConfigured = true,
                    radioDjs = listOf(RadioDjPreset(id = "dj", name = " Road DJ ")),
                ),
                visualizer = VisualizerSettings(selectedVisualizer = "Waveform"),
                recentRadioStreams = listOf(
                    RecentRadioStream(
                        id = "library:session:123",
                        label = "Library radio",
                        kind = RecentRadioKind.Library,
                        sourceId = "goosepod",
                        startedAtEpochMillis = 123L,
                        sessionTracks = listOf(
                            SavedTrack("track-1", "Track", artistName = "Artist"),
                        ),
                    ),
                ),
                recentInternetRadioStations = listOf(
                    SavedInternetRadioStation(
                        id = "radio-1",
                        name = "Radio",
                        streamUrl = "https://radio.example/stream",
                        sourceId = "goosepod",
                    ),
                ),
            ),
        )

        val decoded = SettingsSyncJson.decode(SettingsSyncJson.encode(document))
        val expected = document.normalized()
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
        assertEquals(listOf("collection", "music"), profile.selectedMusicFolderIds)
        assertEquals(ReplayGainMode.Album, decoded.preferences.playback.replayGainMode)
        assertEquals(SampleRateConverter.Sinc32, decoded.preferences.playback.sampleRateConverter)
        assertEquals(SampleRateMatching.Strict, decoded.preferences.playback.sampleRateMatching)
        assertFalse(decoded.preferences.playback.effectiveSonicSimilarityEnabled())
        assertTrue(decoded.preferences.playback.sonicSimilarityPreferenceConfigured)
        assertEquals("Road DJ", decoded.preferences.playback.radioDjs.single().name)
        assertEquals("Waveform", decoded.preferences.visualizer.selectedVisualizer)
        assertEquals("goosepod", decoded.preferences.recentRadioStreams.single().sourceId)
        assertEquals("track-1", decoded.preferences.recentRadioStreams.single().sessionTracks.single().id)
        assertEquals("goosepod", decoded.preferences.recentInternetRadioStations.single().sourceId)
        assertTrue(decoded.preferences.interfaceSettings.checkForUpdates)
        assertEquals(ApplicationUpdateChannel.Beta, decoded.preferences.interfaceSettings.applicationUpdateChannel)
        assertTrue(decoded.preferences.interfaceSettings.startPlayingOnLaunch)
        assertEquals(
            HomeSectionPresentationSettings(
                HomeSectionLayout.Grid,
                HomeSectionPageLayout.List,
                visible = false,
            ),
            decoded.preferences.interfaceSettings.homeSectionPresentation("navibeat-mixes"),
        )
        assertEquals(
            listOf("navibeat-mixes", "mixes-for-you", "future-section"),
            decoded.preferences.interfaceSettings.homeSectionOrder,
        )
        assertTrue(decoded.preferences.interfaceSettings.globalKeyboardShortcuts.enabled)
        assertEquals(
            KeyboardShortcutBinding(KeyboardShortcutKey.F8, control = true, alt = true),
            decoded.preferences.interfaceSettings.globalKeyboardShortcuts
                .resolvedBindings(DesktopShortcutPlatform.Windows)[GlobalShortcutAction.PlayPause],
        )
        assertFalse(decoded.preferences.interfaceSettings.showArtistInformation)
        assertFalse(decoded.preferences.interfaceSettings.showAlbumInformation)
        assertEquals(AppBackgroundStyle.AlbumBlur, decoded.preferences.interfaceSettings.appBackgroundStyle)
        assertEquals(AuroraTone.Light, decoded.preferences.interfaceSettings.auroraTone)
        assertEquals(5, decoded.preferences.interfaceSettings.auroraColorSteps)
        assertEquals(135, decoded.preferences.interfaceSettings.auroraAngleDegrees)
        assertEquals(40, decoded.preferences.interfaceSettings.albumBlurRadiusDp)
        assertEquals("#123456", decoded.preferences.interfaceSettings.singleColorHex)
        assertFalse(decoded.preferences.interfaceSettings.nowPlaying.showAlbumYear)
        assertFalse(decoded.preferences.interfaceSettings.nowPlaying.showAudioInfo)
        assertTrue(decoded.preferences.interfaceSettings.nowPlaying.showPlaybackSource)
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
        assertEquals(LyricsTimingPreference.WordSynced, decoded.preferences.playback.lyricsTimingPreference)
        assertEquals(LyricsDisplayPreference.LineSynced, decoded.preferences.playback.lyricsDisplayPreference)
        assertEquals(expected.preferences.interfaceSettings, decoded.preferences.interfaceSettings)
        assertEquals(expected.preferences.playback, decoded.preferences.playback)
    }

    @Test
    fun interfaceBackgroundDefaultsAndNormalizesHexColor() {
        assertEquals(AppBackgroundStyle.Aurora, InterfaceSettings().appBackgroundStyle)
        assertEquals(AuroraTone.Dark, InterfaceSettings().auroraTone)
        assertEquals("Balanced", InterfaceSettings().auroraTone.label)
        assertEquals(3, InterfaceSettings().auroraColorSteps)
        assertEquals(45, InterfaceSettings().auroraAngleDegrees)
        assertEquals(2, InterfaceSettings(auroraColorSteps = 1).normalized().auroraColorSteps)
        assertEquals(5, InterfaceSettings(auroraColorSteps = 9).normalized().auroraColorSteps)
        assertEquals(0, InterfaceSettings(auroraAngleDegrees = -1).normalized().auroraAngleDegrees)
        assertEquals(180, InterfaceSettings(auroraAngleDegrees = 999).normalized().auroraAngleDegrees)
        assertEquals(DefaultAlbumBlurRadiusDp, InterfaceSettings().albumBlurRadiusDp)
        assertEquals(MaxAlbumBlurRadiusDp, InterfaceSettings(albumBlurRadiusDp = 999).normalized().albumBlurRadiusDp)
        assertEquals(MinAlbumBlurRadiusDp, InterfaceSettings(albumBlurRadiusDp = -1).normalized().albumBlurRadiusDp)
        assertEquals("#A1B2C3", InterfaceSettings(singleColorHex = "a1b2c3").normalized().singleColorHex)
        assertEquals(DefaultSingleColorHex, InterfaceSettings(singleColorHex = "not-a-color").normalized().singleColorHex)
        assertEquals(NowPlayingAlbumYearPreference.Original, NowPlayingDisplaySettings().albumYearPreference)
        assertEquals(false, NowPlayingDisplaySettings().showTrackCover)
    }

    @Test
    fun auroraToneKeepsOldDarkValueBalancedAndRoundTripsNewDark() {
        val existing = SettingsSyncDocument(
            preferences = SettingsSyncPreferences(
                interfaceSettings = InterfaceSettings(auroraTone = AuroraTone.Dark),
            ),
        )
        val existingJson = SettingsSyncJson.encode(existing)
        assertTrue(existingJson.contains("\"auroraTone\": \"Dark\""))
        assertEquals("Balanced", SettingsSyncJson.decode(existingJson).preferences.interfaceSettings.auroraTone.label)

        val newDark = existing.copy(
            preferences = existing.preferences.copy(
                interfaceSettings = existing.preferences.interfaceSettings.copy(auroraTone = AuroraTone.DeepDark),
            ),
        )
        assertEquals(
            AuroraTone.DeepDark,
            SettingsSyncJson.decode(SettingsSyncJson.encode(newDark)).preferences.interfaceSettings.auroraTone,
        )
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
