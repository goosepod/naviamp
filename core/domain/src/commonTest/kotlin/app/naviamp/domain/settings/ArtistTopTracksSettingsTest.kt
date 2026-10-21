package app.naviamp.domain.settings

import kotlin.test.*

class ArtistTopTracksSettingsTest {
    @Test fun defaultsAndSupportedStepsNormalizeForOlderAndImportedSettings() {
        assertTrue(InterfaceSettings().showArtistTopTracks)
        assertEquals(10, InterfaceSettings().artistTopTracksLimit)
        assertEquals(listOf(5,10,15,20,25,30,35,40,45,50), ArtistTopTracksLimits)
        for ((raw, expected) in listOf(Int.MIN_VALUE to 5, 0 to 5, 12 to 10, 13 to 15, 49 to 50, Int.MAX_VALUE to 50)) {
            assertEquals(expected, InterfaceSettings(artistTopTracksLimit = raw).normalized().artistTopTracksLimit)
        }
        val older = SettingsSyncJson.decode("{}")
        assertTrue(older.preferences.interfaceSettings.showArtistTopTracks)
        assertEquals(10, older.preferences.interfaceSettings.artistTopTracksLimit)
        val imported = SettingsSyncJson.decode("""{"preferences":{"interfaceSettings":{"artistTopTracksLimit":18}}}""")
        assertEquals(20, imported.preferences.interfaceSettings.artistTopTracksLimit)
    }

    @Test fun everyChoiceAndVisibilityRoundTripsInPortableSettings() {
        for (visible in listOf(false, true)) for (limit in ArtistTopTracksLimits) {
            val original = InterfaceSettings(showArtistTopTracks = visible, artistTopTracksLimit = limit)
            val document = buildSettingsSyncDocument(SettingsSyncLocalSnapshot(interfaceSettings = original), 1L, "test")
            val decoded = SettingsSyncJson.decode(SettingsSyncJson.encode(document))
            assertEquals(original, decoded.preferences.interfaceSettings)
        }
        assertFails { SettingsSyncJson.decode("""{"preferences":{"interfaceSettings":{"artistTopTracksLimit":"invalid"}}}""") }
    }
}
