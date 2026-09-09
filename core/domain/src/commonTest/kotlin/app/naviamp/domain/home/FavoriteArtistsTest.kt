package app.naviamp.domain.home

import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistId
import app.naviamp.domain.settings.FavoriteArtistSort
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.SettingsSyncLocalSnapshot
import app.naviamp.domain.settings.buildSettingsSyncDocument
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlin.test.Test
import kotlin.test.assertEquals

class FavoriteArtistsTest {
    @Test
    fun allSortsResolveMissingAndEqualTimestampsDeterministically() {
        val artists = listOf(
            Artist(ArtistId("z"), "Zed", "2026-02-01T00:00:00Z"),
            Artist(ArtistId("a2"), "Alpha", "2026-01-01T00:00:00Z"),
            Artist(ArtistId("a1"), "Alpha", "2026-01-01T00:00:00Z"),
            Artist(ArtistId("b"), "Beta", "favorite"),
        )
        assertEquals(listOf("a1", "a2", "b", "z"), artists.sortedFavoriteArtists(FavoriteArtistSort.Name, emptyMap()).map { it.id.value })
        assertEquals(listOf("z", "a1", "a2", "b"), artists.sortedFavoriteArtists(FavoriteArtistSort.DateFavorited, emptyMap()).map { it.id.value })
        assertEquals(listOf("b", "a1", "a2", "z"), artists.sortedFavoriteArtists(FavoriteArtistSort.LastRadioPlayed,
            mapOf(ArtistId("b") to "2026-03-01T00:00:00Z")).map { it.id.value })
    }

    @Test
    fun sortSurvivesPersistenceAndIsIncludedInSyncedPreferences() {
        val settings = InterfaceSettings(favoriteArtistSort = FavoriteArtistSort.LastRadioPlayed)
        assertEquals(settings, Json.decodeFromString<InterfaceSettings>(Json.encodeToString(settings)))
        val document = buildSettingsSyncDocument(SettingsSyncLocalSnapshot(interfaceSettings = settings), 1L, "device")
        assertEquals(FavoriteArtistSort.LastRadioPlayed, document.preferences.interfaceSettings.favoriteArtistSort)
        assertEquals(FavoriteArtistSort.Name, Json.decodeFromString<InterfaceSettings>("{}").favoriteArtistSort)
    }
}
