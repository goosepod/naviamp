package app.naviamp.desktop

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistId
import app.naviamp.domain.cache.LibrarySnapshot
import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryFreshnessTest {
    @Test
    fun nextLibraryLimitOnlyGrowsWhenVisibleRowsReachCurrentLimit() {
        assertEquals(
            50,
            nextLibraryLimit(
                snapshot = LibrarySnapshot(artists = artists(49)),
                tab = DesktopLibraryTab.Artists,
                currentLimit = 50,
                pageSize = 50,
            ),
        )
        assertEquals(
            100,
            nextLibraryLimit(
                snapshot = LibrarySnapshot(artists = artists(50)),
                tab = DesktopLibraryTab.Artists,
                currentLimit = 50,
                pageSize = 50,
            ),
        )
        assertEquals(
            100,
            nextLibraryLimit(
                snapshot = LibrarySnapshot(albums = albums(50)),
                tab = DesktopLibraryTab.Albums,
                currentLimit = 50,
                pageSize = 50,
            ),
        )
    }

    private fun artists(count: Int): List<Artist> =
        (0 until count).map { index ->
            Artist(
                id = ArtistId("artist-$index"),
                name = "Artist $index",
            )
        }

    private fun albums(count: Int): List<Album> =
        (0 until count).map { index ->
            Album(
                id = AlbumId("album-$index"),
                title = "Album $index",
                artistName = "Artist",
                coverArtId = null,
                recentlyAddedAtIso8601 = null,
                releaseYear = null,
            )
        }
}
