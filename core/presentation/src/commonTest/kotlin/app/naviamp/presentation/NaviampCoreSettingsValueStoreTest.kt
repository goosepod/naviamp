package app.naviamp.presentation

import app.naviamp.domain.Genre
import app.naviamp.domain.radio.MaxRecentRadioStreams
import app.naviamp.domain.radio.genreRecentRadioStream
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.NowPlayingDisplaySettings
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.settings.effectiveSonicSimilarityEnabled
import app.naviamp.domain.settings.RecentRadioKind
import app.naviamp.domain.settings.RecentRadioStream
import app.naviamp.domain.settings.SavedAlbum
import app.naviamp.domain.settings.SavedInternetRadioStation
import app.naviamp.domain.settings.SavedTrack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NaviampCoreSettingsValueStoreTest {
    @Test fun oldDuplicateStationsAreReconciledDurablyAndOptionsSurviveReopening() {
        val values = MemorySettingsValues()
        val station = genreRecentRadioStream(Genre("Jazz")).copy(
            sourceId = "account-a", id = "genre:Jazz:session:2", startedAtEpochMillis = 2,
            sessionTracks = listOf(SavedTrack("one", "One", artistName = "Artist")),
        )
        // Store the old JSON directly so the save path cannot pre-reconcile it.
        val catalog = naviampCoreSettingsValueCatalog(values).storedSettings
        catalog.saveRecentRadioStreams(listOf(station))
        val key = KeyRecentRadio
        values.entries[key] = kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(RecentRadioStream.serializer()),
            listOf(station, station.copy(id = "genre:Jazz:session:1", startedAtEpochMillis = 1)),
        )
        assertEquals(listOf(station), catalog.loadRecentRadioStreams())
        val persisted = values.entries[key]
        assertEquals(listOf(station), naviampCoreSettingsValueCatalog(values).storedSettings.loadRecentRadioStreams())
        assertEquals(persisted, values.entries[key])
        val configured = station.copy(stationSeedIds = listOf("Jazz", "Rock"),
            generationOptions = app.naviamp.domain.radio.RecentRadioGenerationOptions(minimumTracks = 999))
        catalog.saveRecentRadioStreams(listOf(configured))
        val reopened = naviampCoreSettingsValueCatalog(values).storedSettings.loadRecentRadioStreams().single()
        assertEquals(configured.copy(generationOptions = configured.generationOptions?.normalized()), reopened)
    }

    @Test fun artistSectionLayoutsPersistIndependentlyAndOlderSettingsUseLists() {
        val values = MemorySettingsValues()
        val catalog = naviampCoreSettingsValueCatalog(values)
        for (playlists in app.naviamp.domain.settings.AlbumCollectionLayout.entries) {
            for (appearances in app.naviamp.domain.settings.AlbumCollectionLayout.entries) {
                val settings = InterfaceSettings(artistPlaylistLayout = playlists, artistAppearanceAlbumLayout = appearances)
                catalog.storedSettings.saveInterface(settings)
                assertEquals(settings, naviampCoreSettingsValueCatalog(values).storedSettings.loadInterface())
            }
        }
        values.entries[KeyInterface] = "{}"
        val older = catalog.storedSettings.loadInterface()
        assertEquals(app.naviamp.domain.settings.AlbumCollectionLayout.List, older.artistPlaylistLayout)
        assertEquals(app.naviamp.domain.settings.AlbumCollectionLayout.List, older.artistAppearanceAlbumLayout)
    }
    @Test fun fontChoiceAndExactSizesPersistLocallyAndOlderPresetsMigrate() {
        val values = MemorySettingsValues()
        val catalog = naviampCoreSettingsValueCatalog(values)
        val settings = InterfaceSettings(
            fontFamily = app.naviamp.domain.settings.InterfaceFontFamily.System,
            installedFontFamily = "Local Font",
            generalFontSize = app.naviamp.domain.settings.InterfaceFontSize(135),
            nowPlayingFontSize = app.naviamp.domain.settings.InterfaceFontSize(80),
        )
        catalog.storedSettings.saveInterface(settings)
        assertEquals(settings, naviampCoreSettingsValueCatalog(values).storedSettings.loadInterface())
        values.entries[KeyInterface] = """{"generalFontSize":"Large","nowPlayingFontSize":"Small","showArtistPlaylists":false}"""
        val older = catalog.storedSettings.loadInterface()
        assertEquals(app.naviamp.domain.settings.InterfaceFontSize(115), older.generalFontSize)
        assertEquals(app.naviamp.domain.settings.InterfaceFontSize(90), older.nowPlayingFontSize)
        assertEquals(app.naviamp.domain.settings.InterfaceFontFamily.NunitoSans, older.fontFamily)
        assertFalse(older.showArtistPlaylists)
    }
    @Test fun storedFontSizesSurviveUpgradeRestartAndExportImport() {
        // Local values as written by v2.12.0 (presets only) and by the percentage builds of #625.
        val stored = mapOf(
            """{"generalFontSize":"Small","nowPlayingFontSize":"Small","showArtistPlaylists":true}""" to (90 to 90),
            """{"generalFontSize":"Large","nowPlayingFontSize":"Standard"}""" to (115 to 100),
            """{"generalFontSize":"Small","nowPlayingFontSize":"Small","fontFamily":"Inter","installedFontFamily":null,""" +
                """"generalFontSizePercent":90,"nowPlayingFontSizePercent":90}""" to (90 to 90),
            """{"generalFontSize":"Large","nowPlayingFontSize":"Large","generalFontSizePercent":150,"nowPlayingFontSizePercent":150}""" to (150 to 150),
            """{"generalFontSize":"Large","nowPlayingFontSize":"Small","generalFontSizePercent":125,"nowPlayingFontSizePercent":85}""" to (125 to 85),
            // A value written without the compatibility transform keeps its sizes too.
            """{"generalFontSize":90,"nowPlayingFontSize":150}""" to (90 to 150),
        )
        for ((json, sizes) in stored) {
            fun assertSizes(settings: InterfaceSettings, stage: String) {
                assertEquals(sizes, settings.generalFontSize.percent to settings.nowPlayingFontSize.percent, "$stage: $json")
            }
            val values = MemorySettingsValues()
            values.entries[KeyInterface] = json
            val loaded = naviampCoreSettingsValueCatalog(values).storedSettings.loadInterface()
            assertSizes(loaded, "load")
            naviampCoreSettingsValueCatalog(values).storedSettings.saveInterface(loaded.copy(showArtistPlaylists = false))
            val restarted = naviampCoreSettingsValueCatalog(values).storedSettings.loadInterface()
            assertSizes(restarted, "restart")
            assertFalse(restarted.showArtistPlaylists)
            val written = values.entries.getValue(KeyInterface)
            assertTrue("\"generalFontSizePercent\":${sizes.first}" in written, "Older versions need the preset key: $written")
            assertTrue(Regex("\"generalFontSize\":\"(Small|Standard|Large)\"").containsMatchIn(written), written)
            val document = app.naviamp.domain.settings.SettingsSyncJson.decode(app.naviamp.domain.settings.SettingsSyncJson.encode(
                app.naviamp.domain.settings.buildSettingsSyncDocument(
                    app.naviamp.domain.settings.SettingsSyncLocalSnapshot(interfaceSettings = restarted), 1L, "phone",
                ),
            ))
            val imported = MemorySettingsValues()
            naviampCoreSettingsValueCatalog(imported).storedSettings.saveInterface(document.preferences.interfaceSettings)
            assertSizes(naviampCoreSettingsValueCatalog(imported).storedSettings.loadInterface(), "export/import")
        }
    }

    @Test fun artistPlaylistVisibilityPersistsAndOlderSettingsDefaultOn() {
        val values = MemorySettingsValues()
        val catalog = naviampCoreSettingsValueCatalog(values)
        assertEquals(true, catalog.storedSettings.loadInterface().showArtistPlaylists)
        catalog.storedSettings.saveInterface(InterfaceSettings(showArtistPlaylists = false))
        assertEquals(false, naviampCoreSettingsValueCatalog(values).storedSettings.loadInterface().showArtistPlaylists)
    }

    @Test fun artistTopTracksPersistAndOlderValuesUseDefaults() {
        val values = MemorySettingsValues()
        val catalog = naviampCoreSettingsValueCatalog(values)
        assertEquals(10, catalog.storedSettings.loadInterface().artistTopTracksLimit)
        catalog.storedSettings.saveInterface(InterfaceSettings(showArtistTopTracks = false, artistTopTracksLimit = 50))
        val restored = naviampCoreSettingsValueCatalog(values).storedSettings.loadInterface()
        assertFalse(restored.showArtistTopTracks)
        assertEquals(50, restored.artistTopTracksLimit)
        values.entries[KeyInterface] = """{"artistTopTracksLimit":18}"""
        assertEquals(20, catalog.storedSettings.loadInterface().artistTopTracksLimit)
        values.entries[KeyInterface] = "{}"
        assertEquals(true, catalog.storedSettings.loadInterface().showArtistTopTracks)
        assertEquals(10, catalog.storedSettings.loadInterface().artistTopTracksLimit)
    }
    @Test
    fun independentRadioAndAutoplaySizesPersistWithDefaultsAndNormalization() {
        val values = MemorySettingsValues()
        val catalog = naviampCoreSettingsValueCatalog(values)
        assertEquals(10, catalog.storedSettings.loadPlayback().radioMinimumTracks)
        assertEquals(10, catalog.storedSettings.loadPlayback().sonicAutoplayMinimumTracks)
        catalog.savePlayback(PlaybackSettings(radioMinimumTracks = 20, sonicAutoplayMinimumTracks = 70))
        val restored = naviampCoreSettingsValueCatalog(values).storedSettings.loadPlayback()
        assertEquals(20, restored.radioMinimumTracks)
        assertEquals(70, restored.sonicAutoplayMinimumTracks)
        values.entries[KeyPlayback] = """{"radioMinimumTracks":-1,"sonicAutoplayMinimumTracks":999}"""
        val normalized = naviampCoreSettingsValueCatalog(values).storedSettings.loadPlayback()
        assertEquals(10, normalized.radioMinimumTracks)
        assertEquals(100, normalized.sonicAutoplayMinimumTracks)
    }

    @Test
    fun olderSonicPreferenceDefaultsToInstantMixAndExplicitChoiceRoundTrips() {
        val values = MemorySettingsValues().apply {
            entries[KeyPlayback] = """{"sonicSimilarityEnabled":false}"""
        }
        val catalog = naviampCoreSettingsValueCatalog(values)

        assertEquals(false, catalog.storedSettings.loadPlayback().effectiveSonicSimilarityEnabled())

        catalog.savePlayback(
            PlaybackSettings(
                sonicSimilarityEnabled = false,
                sonicSimilarityPreferenceConfigured = true,
            ),
        )
        val reloaded = naviampCoreSettingsValueCatalog(values).storedSettings.loadPlayback()
        assertFalse(reloaded.effectiveSonicSimilarityEnabled())
        assertEquals(true, reloaded.sonicSimilarityPreferenceConfigured)
    }

    @Test
    fun languageSurvivesRecreatingTheSettingsCatalog() {
        val values = MemorySettingsValues()
        for (language in app.naviamp.domain.settings.InterfaceLanguage.entries) {
            naviampCoreSettingsValueCatalog(values).storedSettings.saveInterface(
                InterfaceSettings(language = language),
            )
            assertEquals(language, naviampCoreSettingsValueCatalog(values).storedSettings.loadInterface().language)
        }
    }

    @Test
    fun ownsPortableSerializationDefaultsAndNormalization() {
        val values = MemorySettingsValues()
        val catalog = naviampCoreSettingsValueCatalog(values)
        val settings = catalog.storedSettings

        assertEquals(InterfaceSettings(), settings.loadInterface())
        settings.saveInterface(InterfaceSettings(
            albumBlurRadiusDp = 999,
            keepScreenAwake = true,
            squareAlbumArtwork = true,
            nowPlaying = NowPlayingDisplaySettings(
                splitPaneBackgroundOpacityPercent = 42,
                keepTelevisionControlsVisible = true,
                fillArtworkArea = true,
            ),
        ))
        assertEquals(48, settings.loadInterface().albumBlurRadiusDp)
        assertEquals(true, naviampCoreSettingsValueCatalog(values).storedSettings.loadInterface().keepScreenAwake)
        assertEquals(true, naviampCoreSettingsValueCatalog(values).storedSettings.loadInterface().squareAlbumArtwork)
        assertEquals(42, settings.loadInterface().nowPlaying.splitPaneBackgroundOpacityPercent)
        assertEquals(true, naviampCoreSettingsValueCatalog(values).storedSettings.loadInterface().nowPlaying.keepTelevisionControlsVisible)
        assertEquals(true, naviampCoreSettingsValueCatalog(values).storedSettings.loadInterface().nowPlaying.fillArtworkArea)
        catalog.savePlayback(PlaybackSettings(crossfadeDurationSeconds = 999))
        assertEquals(12, settings.loadPlayback().crossfadeDurationSeconds)

        settings.saveRecentRadioStreams(
            (1..55).map { index -> genreRecentRadioStream(Genre("Genre $index")) },
        )
        assertEquals(MaxRecentRadioStreams, settings.loadRecentRadioStreams().size)
        assertEquals("genre:Genre 50", settings.loadRecentRadioStreams().last().id)

        val session = genreRecentRadioStream(Genre("Ambient")).copy(
            id = "genre:Ambient:session:123",
            sourceId = "server-a",
            startedAtEpochMillis = 123L,
            sessionTracks = listOf(SavedTrack("track-1", "Track", artistName = "Artist")),
        )
        settings.saveRecentRadioStreams(listOf(session))
        assertEquals(session, settings.loadRecentRadioStreams().single())

        values.entries["naviamp.interface"] = "not-json"
        assertEquals(InterfaceSettings(), settings.loadInterface())
        assertFalse(values.entries.values.any { "NSUserDefaults" in it })
    }

    @Test
    fun providerIdentitySettingsMigrationIsSourceScopedVersionedAndRoundTrips() {
        val values = MemorySettingsValues()
        val settings = naviampCoreSettingsValueCatalog(values).storedSettings
        val oldAlbum = "old-album"
        val oldTrack = "old-track"
        val oldStation = "old-station"
        val migrated = mapOf(oldAlbum to "new-album", oldTrack to "new-track", oldStation to "new-station")
        settings.saveRecentRadioStreams(
            listOf(
                RecentRadioStream(
                    id = "album:$oldAlbum",
                    label = "Album radio",
                    kind = RecentRadioKind.Album,
                    album = SavedAlbum(oldAlbum, "Album", "Artist", coverArtId = oldAlbum),
                    coverArtIds = listOf(oldAlbum),
                    sourceId = "server-a",
                    sessionTracks = listOf(SavedTrack(oldTrack, "Track", albumId = oldAlbum, artistName = "Artist")),
                ),
                RecentRadioStream(
                    id = "album:$oldAlbum",
                    label = "Other server",
                    kind = RecentRadioKind.Album,
                    sourceId = "server-b",
                ),
            ),
        )
        settings.saveRecentInternetRadioStations(
            listOf(
                SavedInternetRadioStation(oldStation, "Station", "https://a", sourceId = "server-a"),
                SavedInternetRadioStation(oldStation, "Other", "https://b", sourceId = "server-b"),
                SavedInternetRadioStation(oldStation, "Legacy", "https://legacy"),
            ),
        )
        val migration = naviampCoreProviderIdentitySettingsMigrationRepository(values)
        val transform: (String) -> String = { migrated[it] ?: it }

        assertEquals(null, migration.providerIdentitySettingsVersion("server-a"))
        assertEquals(true, migration.migrateProviderIdentitySettings("server-a", 2L, transform))
        assertEquals(2L, migration.providerIdentitySettingsVersion("server-a"))
        assertEquals(false, migration.migrateProviderIdentitySettings("server-a", 2L, transform))

        val streams = settings.loadRecentRadioStreams()
        assertEquals("album:new-album", streams[0].id)
        assertEquals("new-album", streams[0].album?.id)
        assertEquals("new-track", streams[0].sessionTracks.single().id)
        assertEquals("album:$oldAlbum", streams[1].id)
        val stations = settings.loadRecentInternetRadioStations()
        assertEquals("new-station", stations[0].id)
        assertEquals(oldStation, stations[1].id)
        assertEquals(oldStation, stations[2].id)
    }
}

private class MemorySettingsValues : NaviampCoreSettingsValueStore {
    val entries = mutableMapOf<String, String>()
    override fun read(key: String): String? = entries[key]
    override fun write(key: String, value: String) {
        entries[key] = value
    }
}
