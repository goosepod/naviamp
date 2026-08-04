package app.naviamp.domain.media

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import kotlin.test.Test
import kotlin.test.assertEquals

class ArtistCatalogTracksTest {
    private val first = Album(AlbumId("first"), "First", "Artist", null, null)
    private val second = Album(AlbumId("second"), "Second", "Artist", null, null)
    private val detail = ArtistDetails(
        artist = Artist(ArtistId("artist"), "Artist"),
        albums = listOf(first, second),
    )

    @Test
    fun preservesProviderOrderWithoutDisplayOverride() {
        assertEquals(listOf(first, second), orderedArtistCatalogAlbums(detail))
    }

    @Test
    fun followsDisplayedAlbumOrderAndIgnoresStaleIds() {
        assertEquals(
            listOf(second, first),
            orderedArtistCatalogAlbums(detail, listOf("second", "missing", "first")),
        )
    }
}
