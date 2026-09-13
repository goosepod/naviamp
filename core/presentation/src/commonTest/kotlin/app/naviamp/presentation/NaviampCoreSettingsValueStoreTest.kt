package app.naviamp.presentation

import app.naviamp.domain.Genre
import app.naviamp.domain.radio.MaxRecentRadioStreams
import app.naviamp.domain.radio.genreRecentRadioStream
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.NowPlayingDisplaySettings
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.settings.RecentRadioKind
import app.naviamp.domain.settings.RecentRadioStream
import app.naviamp.domain.settings.SavedAlbum
import app.naviamp.domain.settings.SavedInternetRadioStation
import app.naviamp.domain.settings.SavedTrack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class NaviampCoreSettingsValueStoreTest {
    @Test
    fun ownsPortableSerializationDefaultsAndNormalization() {
        val values = MemorySettingsValues()
        val catalog = naviampCoreSettingsValueCatalog(values)
        val settings = catalog.storedSettings

        assertEquals(InterfaceSettings(), settings.loadInterface())
        settings.saveInterface(InterfaceSettings(
            albumBlurRadiusDp = 999,
            nowPlaying = NowPlayingDisplaySettings(splitPaneBackgroundOpacityPercent = 42),
        ))
        assertEquals(48, settings.loadInterface().albumBlurRadiusDp)
        assertEquals(42, settings.loadInterface().nowPlaying.splitPaneBackgroundOpacityPercent)
        catalog.savePlayback(PlaybackSettings(crossfadeDurationSeconds = 999))
        assertEquals(999, settings.loadPlayback().crossfadeDurationSeconds)

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
