package app.naviamp.domain.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AlbumArtworkPreferencesTest {
    @Test
    fun albumOverrideTakesPrecedenceAndClearingRestoresGlobalChoice() {
        val global = InterfaceSettings(nowPlaying = NowPlayingDisplaySettings(showTrackCover = false))
        val selected = global.withAlbumArtworkPreference("source-a", "edition-1", AlbumArtworkPreference.Track)

        assertTrue(selected.preferTrackArtwork("source-a", "edition-1"))
        assertFalse(selected.preferTrackArtwork("source-a", "edition-2"))
        assertFalse(selected.preferTrackArtwork("source-b", "edition-1"))
        assertFalse(selected.withAlbumArtworkPreference("source-a", "edition-1", AlbumArtworkPreference.Inherit)
            .preferTrackArtwork("source-a", "edition-1"))
        assertTrue(selected.copy(nowPlaying = NowPlayingDisplaySettings(showTrackCover = true))
            .preferTrackArtwork("source-b", "edition-1"))
    }

    @Test
    fun importedOverridesNormalizeUnknownAndDuplicateEntries() {
        val normalized = InterfaceSettings(albumArtworkOverrides = listOf(
            AlbumArtworkOverride(" source ", " album ", "Track"),
            AlbumArtworkOverride("source", "album", "Album"),
            AlbumArtworkOverride("source", "other", "unknown"),
            AlbumArtworkOverride("source", "third", "Inherit"),
            AlbumArtworkOverride("", "empty", "Album"),
        )).normalized()

        assertEquals(listOf(AlbumArtworkOverride("source", "album", "Track")), normalized.albumArtworkOverrides)
        assertEquals(AlbumArtworkPreference.Track, normalized.albumArtworkPreference("source", "album"))
    }

    @Test
    fun settingsSyncRoundTripAndOlderDocumentDefault() {
        val selected = InterfaceSettings().withAlbumArtworkPreference(
            "source", "album", AlbumArtworkPreference.Album,
        )
        val document = buildSettingsSyncDocument(
            SettingsSyncLocalSnapshot(interfaceSettings = selected), 42L, "device",
        )
        val decoded = SettingsSyncJson.decode(SettingsSyncJson.encode(document))
        assertEquals(AlbumArtworkPreference.Album,
            decoded.preferences.interfaceSettings.albumArtworkPreference("source", "album"))
        val older = SettingsSyncJson.decode("""{"schemaVersion":1,"preferences":{"interfaceSettings":{}}}""")
        assertEquals(AlbumArtworkPreference.Inherit,
            older.preferences.interfaceSettings.albumArtworkPreference("source", "album"))
    }
}
