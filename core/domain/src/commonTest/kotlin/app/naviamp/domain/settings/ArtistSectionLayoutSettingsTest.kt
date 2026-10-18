package app.naviamp.domain.settings

import kotlin.test.*

class ArtistSectionLayoutSettingsTest {
    @Test fun olderSettingsDefaultBothSectionsToLists() {
        val settings = SettingsSyncJson.decode("{}").preferences.interfaceSettings
        assertEquals(AlbumCollectionLayout.List, settings.artistPlaylistLayout)
        assertEquals(AlbumCollectionLayout.List, settings.artistAppearanceAlbumLayout)
        val partial = SettingsSyncJson.decode("""{"preferences":{"interfaceSettings":{"artistPlaylistLayout":"Grid"}}}""").preferences.interfaceSettings
        assertEquals(AlbumCollectionLayout.Grid, partial.artistPlaylistLayout)
        assertEquals(AlbumCollectionLayout.List, partial.artistAppearanceAlbumLayout)
    }

    @Test fun independentLayoutsRoundTripThroughPortableSettings() {
        for (playlists in AlbumCollectionLayout.entries) for (appearances in AlbumCollectionLayout.entries) {
            val settings = InterfaceSettings(artistPlaylistLayout = playlists,
                artistAppearanceAlbumLayout = appearances, albumCollectionLayout = AlbumCollectionLayout.Grid,
                showArtistPlaylists = false)
            val document = buildSettingsSyncDocument(SettingsSyncLocalSnapshot(interfaceSettings = settings), 1L, "test")
            assertEquals(settings, SettingsSyncJson.decode(SettingsSyncJson.encode(document)).preferences.interfaceSettings)
            assertEquals(settings, settings.normalized())
        }
    }

    @Test fun unsupportedLayoutsAreRejectedInsteadOfChangingOtherPreferences() {
        assertFails { SettingsSyncJson.decode("""{"preferences":{"interfaceSettings":{"artistPlaylistLayout":"Carousel"}}}""") }
        assertFails { SettingsSyncJson.decode("""{"preferences":{"interfaceSettings":{"artistAppearanceAlbumLayout":12}}}""") }
    }
}
