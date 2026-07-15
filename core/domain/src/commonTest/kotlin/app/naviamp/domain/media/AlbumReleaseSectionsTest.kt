package app.naviamp.domain.media

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumId
import app.naviamp.domain.settings.AlbumSortOrder
import kotlin.test.Test
import kotlin.test.assertEquals

class AlbumReleaseSectionsTest {
    @Test
    fun groupsKnownReleaseTypesInStableDisplayOrder() {
        val groups = listOf(
            album("single", "Single"),
            album("album", "Album"),
            album("remix", "Album", "Remixes"),
            album("ep", "EP"),
            album("live", "Live Album"),
            album("soundtrack", "Soundtrack"),
            album("compilation", "Compilation"),
        ).groupedByReleaseSection()

        assertEquals(
            listOf(
                AlbumReleaseSection.Albums,
                AlbumReleaseSection.Eps,
                AlbumReleaseSection.Singles,
                AlbumReleaseSection.Live,
                AlbumReleaseSection.Compilations,
                AlbumReleaseSection.Remixes,
                AlbumReleaseSection.Soundtracks,
            ),
            groups.map { it.section },
        )
        assertEquals("remix", groups.first { it.section == AlbumReleaseSection.Remixes }.albums.single().id.value)
    }

    @Test
    fun missingReleaseTypeFallsBackToAlbums() {
        assertEquals(AlbumReleaseSection.Albums, album("unknown").releaseSection())
    }

    @Test
    fun unrecognizedReleaseTypeUsesOtherReleases() {
        assertEquals(AlbumReleaseSection.Other, album("spoken", "Spoken Word").releaseSection())
    }

    @Test
    fun sortsAlbumsByYearInEitherDirectionOrByTitle() {
        val albums = listOf(
            album("middle", releaseYear = 2010),
            album("zebra", releaseYear = 2000),
            album("alpha", releaseYear = 2020),
            album("unknown"),
        )

        assertEquals(
            listOf("zebra", "middle", "alpha", "unknown"),
            albums.sortedForAlbumDisplay(AlbumSortOrder.ReleaseYearAscending).map { it.id.value },
        )
        assertEquals(
            listOf("alpha", "middle", "zebra", "unknown"),
            albums.sortedForAlbumDisplay(AlbumSortOrder.ReleaseYearDescending).map { it.id.value },
        )
        assertEquals(
            listOf("alpha", "middle", "unknown", "zebra"),
            albums.sortedForAlbumDisplay(AlbumSortOrder.Title).map { it.id.value },
        )
    }

    private fun album(id: String, vararg releaseTypes: String): Album =
        album(id, *releaseTypes, releaseYear = null)

    private fun album(id: String, vararg releaseTypes: String, releaseYear: Int?): Album =
        Album(
            id = AlbumId(id),
            title = id,
            artistName = "Artist",
            coverArtId = null,
            recentlyAddedAtIso8601 = null,
            releaseYear = releaseYear,
            releaseTypes = releaseTypes.toList(),
        )
}
